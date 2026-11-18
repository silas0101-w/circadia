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
import com.example.data.repository.SubstanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val substanceRepo: SubstanceRepository
) : ViewModel() {

    val settingsState: StateFlow<UserSettings> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

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
            substanceRepo: SubstanceRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(prefsRepo, substanceRepo) as T
            }
        }
    }
}
