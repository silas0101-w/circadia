package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AiEngineMode
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.repository.DatabasePersistenceRepository
import com.example.data.repository.DatabaseStats
import com.example.data.repository.SubstanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CloudAuthState(
    val isSignedIn: Boolean = false,
    val accountLabel: String = ""
)

class SettingsViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val substanceRepo: SubstanceRepository,
    private val persistenceRepo: DatabasePersistenceRepository
) : ViewModel() {

    val settingsState: StateFlow<UserSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val databaseStats: StateFlow<DatabaseStats> = persistenceRepo.observeActiveDatabaseStats().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DatabaseStats()
    )

    val diskDatabaseStats: StateFlow<DatabaseStats> = persistenceRepo.observeDiskDatabaseStats().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DatabaseStats()
    )

    val cloudAuthState: StateFlow<CloudAuthState> = persistenceRepo.observeFirebaseUser()
        .map { user ->
            CloudAuthState(
                isSignedIn = user != null,
                accountLabel = user?.email?.takeIf { it.isNotBlank() }
                    ?: user?.displayName?.takeIf { it.isNotBlank() }
                    ?: if (user != null) "Google Account Connected" else ""
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistenceRepo.currentFirebaseUser().let { user ->
                CloudAuthState(
                    isSignedIn = user != null,
                    accountLabel = user?.email?.takeIf { it.isNotBlank() }
                        ?: user?.displayName?.takeIf { it.isNotBlank() }
                        ?: if (user != null) "Google Account Connected" else ""
                )
            }
        )

    private val _showDisablePersistenceDialog = MutableStateFlow(false)
    val showDisablePersistenceDialog: StateFlow<Boolean> = _showDisablePersistenceDialog.asStateFlow()

    private val _isPersistenceBusy = MutableStateFlow(false)
    val isPersistenceBusy: StateFlow<Boolean> = _isPersistenceBusy.asStateFlow()

    private val _cloudSyncWorking = MutableStateFlow(false)
    val cloudSyncWorking: StateFlow<Boolean> = _cloudSyncWorking.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun onTogglePersistentDatabase(enabled: Boolean) {
        if (enabled) {
            viewModelScope.launch {
                _isPersistenceBusy.value = true
                try {
                    val result = persistenceRepo.enablePersistentDatabase()
                    val totalMigrated = result.migratedLogs +
                        result.migratedSleepSessions +
                        result.migratedReports
                    _feedbackMessage.value = if (totalMigrated > 0) {
                        "Persistent database enabled. Migrated ${result.migratedLogs} log(s), " +
                            "${result.migratedSleepSessions} sleep session(s), and " +
                            "${result.migratedReports} report(s)."
                    } else {
                        "Persistent database enabled. Logs will now save on-device."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w("SettingsViewModel", "Failed to enable persistent DB", e)
                    }
                    _feedbackMessage.value = "Could not enable persistent database."
                } finally {
                    _isPersistenceBusy.value = false
                }
            }
        } else {
            _showDisablePersistenceDialog.value = true
        }
    }

    fun dismissDisablePersistenceDialog() {
        _showDisablePersistenceDialog.value = false
    }

    fun confirmDisablePersistence(clearSavedData: Boolean, clearCloudData: Boolean) {
        _showDisablePersistenceDialog.value = false
        viewModelScope.launch {
            _isPersistenceBusy.value = true
            try {
                val result = persistenceRepo.disablePersistentDatabase(
                    clearSavedData = clearSavedData,
                    clearCloudData = clearCloudData
                )
                _feedbackMessage.value = if (result.clearedSavedData) {
                    if (result.cloudClearError != null) {
                        "Session-Only Mode enabled. Local DB cleared, but cloud cleanup failed: ${result.cloudClearError}"
                    } else {
                        "Session-Only Mode enabled and saved database records cleared."
                    }
                } else {
                    "Session-Only Mode enabled. Saved database records were kept on disk."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("SettingsViewModel", "Failed to disable persistent DB", e)
                }
                _feedbackMessage.value = "Could not update persistence setting."
            } finally {
                _isPersistenceBusy.value = false
            }
        }
    }

    fun onToggleCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (persistenceRepo.currentFirebaseUser() == null) {
                    _feedbackMessage.value = "Sign in with Google below before enabling Cloud Database Sync."
                    return@launch
                }
                prefsRepo.setCloudSyncEnabled(true)
                runCloudSyncInternal(prefix = "Cloud Sync enabled")
            } else {
                prefsRepo.setCloudSyncEnabled(false)
                _feedbackMessage.value = "Cloud Database Sync disabled. On-device SQLite remains active."
            }
        }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            runCloudSyncInternal(prefix = "Cloud DB synced")
        }
    }

    private suspend fun runCloudSyncInternal(prefix: String) {
        _cloudSyncWorking.value = true
        try {
            val result = persistenceRepo.syncCloudNow()
            result.onSuccess { summary ->
                _feedbackMessage.value = "$prefix: pushed ${summary.pushedLogs} / pulled ${summary.pulledLogs} logs, " +
                    "${summary.pushedSleepSessions + summary.pulledSleepSessions} sleep sessions."
            }
            result.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Cloud sync failed."
            }
        } finally {
            _cloudSyncWorking.value = false
        }
    }

    fun clearSavedDatabase(clearCloudData: Boolean) {
        viewModelScope.launch {
            _isPersistenceBusy.value = true
            try {
                val res = persistenceRepo.clearSavedDatabaseOnly(clearCloudData)
                res.onSuccess {
                    _feedbackMessage.value = if (clearCloudData) {
                        "Cleared saved local and cloud database records."
                    } else {
                        "Cleared saved local database records."
                    }
                }
                res.onFailure { err ->
                    _feedbackMessage.value = "Local records cleared; cloud cleanup failed: " +
                        (err.localizedMessage ?: "unknown error")
                }
            } finally {
                _isPersistenceBusy.value = false
            }
        }
    }

    fun onFirebaseGoogleSignedIn(email: String) {
        viewModelScope.launch {
            persistenceRepo.onCloudSyncUserSignedIn(email)
            _feedbackMessage.value = if (email.isNotBlank()) {
                "Signed in as $email for Cloud Database Sync."
            } else {
                "Signed in with Google for Cloud Database Sync."
            }
            val current = prefsRepo.getCurrentSettings()
            if (current.persistentDbEnabled && current.cloudSyncEnabled) {
                runCloudSyncInternal(prefix = "Cloud DB synced")
            }
        }
    }

    fun onFirebaseAuthMessage(message: String) {
        _feedbackMessage.value = message
    }

    fun onFirebaseSignedOut() {
        viewModelScope.launch {
            persistenceRepo.onCloudSyncUserSignedOut()
            _feedbackMessage.value = "Signed out of Cloud Sync Google account."
        }
    }

    fun saveTargetBedtime(minutesOfDay: Int) {
        viewModelScope.launch {
            prefsRepo.updateTargetBedtime(minutesOfDay)
            _feedbackMessage.value = "Target bedtime updated."
        }
    }

    fun saveAiSettings(
        nvidiaApiKey: String,
        modelId: String,
        endpointUrl: String,
        engineMode: AiEngineMode
    ) {
        viewModelScope.launch {
            try {
                prefsRepo.updateNvidiaSettings(
                    apiKey = nvidiaApiKey,
                    modelId = modelId,
                    endpointUrl = endpointUrl,
                    engineMode = engineMode
                )
                _feedbackMessage.value = "AI engine settings saved."
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _feedbackMessage.value = e.message ?: "Invalid NVIDIA endpoint URL."
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("SettingsViewModel", "Failed to save AI settings", e)
                }
                _feedbackMessage.value = "Unable to save AI engine settings."
            }
        }
    }

    fun saveGoogleClientId(clientId: String) {
        viewModelScope.launch {
            prefsRepo.updateGoogleClientId(clientId)
            _feedbackMessage.value = "Google OAuth Client ID saved."
        }
    }

    fun exportJsonBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val count = substanceRepo.exportHistoryToUri(context, uri)
                _feedbackMessage.value = "Exported $count substance log(s) to JSON backup."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("SettingsViewModel", "Backup export failed", e)
                }
                _feedbackMessage.value = if (e is IllegalStateException) {
                    e.message ?: "Export failed."
                } else {
                    "Export failed. Please verify write permissions for the selected destination."
                }
            }
        }
    }

    fun importJsonBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val (logsCount, sessionsCount) = substanceRepo.importHistoryFromUri(context, uri)
                _feedbackMessage.value = "Imported $logsCount log(s) and $sessionsCount sleep session(s)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w("SettingsViewModel", "Backup import failed", e)
                }
                _feedbackMessage.value = if (e is IllegalArgumentException || e is IllegalStateException) {
                    e.message ?: "Import failed."
                } else {
                    "Import failed. Please ensure the selected file is a valid Circadia JSON backup."
                }
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    companion object {
        fun factory(
            prefsRepo: UserPreferencesRepository,
            substanceRepo: SubstanceRepository,
            persistenceRepo: DatabasePersistenceRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(prefsRepo, substanceRepo, persistenceRepo) as T
            }
        }
    }
}
