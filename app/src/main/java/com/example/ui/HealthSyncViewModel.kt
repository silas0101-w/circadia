package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.SleepSessionEntity
import com.example.data.local.SubstanceLogEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.repository.HealthSyncRepository
import com.example.data.repository.SubstanceRepository
import com.example.domain.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SleepCorrelationItem(
    val sleepSession: SleepSessionEntity,
    val eveningDateIso: String,
    val disruptionScore: Int,
    val loggedSubstancesSummary: List<String>
)

data class HealthSyncUiState(
    val settings: UserSettings = UserSettings(),
    val correlations: List<SleepCorrelationItem> = emptyList(),
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class HealthSyncViewModel(
    private val healthSyncRepo: HealthSyncRepository,
    substanceRepo: SubstanceRepository,
    prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val _isWorking = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _pendingAuthUri = MutableStateFlow<Uri?>(null)
    val pendingAuthUri: StateFlow<Uri?> = _pendingAuthUri.asStateFlow()

    val uiState: StateFlow<HealthSyncUiState> = combine(
        prefsRepo.settingsFlow,
        healthSyncRepo.observeAllSleepSessions(),
        substanceRepo.observeAllLogs(),
        _isWorking,
        combine(_statusMessage, _errorMessage) { s, e -> s to e }
    ) { settings, sessions, allLogs, working, messages ->
        val logsByDate = allLogs.groupBy { it.dateIso }
        val correlations = sessions.map { session ->
            // Sleep ending on dateIso corresponds to substances taken on the previous evening
            // (or same day if user logged after midnight; check previous day first, fallback same day)
            val prevDate = DateTimeUtils.previousDateIso(session.dateIso)
            val eveningLogs = logsByDate[prevDate]?.takeIf { it.isNotEmpty() }
                ?: logsByDate[session.dateIso].orEmpty()
            val eveningDateUsed = if (logsByDate[prevDate]?.isNotEmpty() == true) prevDate else session.dateIso

            val summary = com.example.domain.PharmacokineticsEngine.analyzeDay(
                dateIso = eveningDateUsed,
                entries = eveningLogs,
                targetBedtimeMinutes = settings.targetBedtimeMinutes,
                decayModel = settings.decayModelMode
            )
            SleepCorrelationItem(
                sleepSession = session,
                eveningDateIso = eveningDateUsed,
                disruptionScore = summary.disruptionScore,
                loggedSubstancesSummary = eveningLogs.map {
                    val doseStr = com.example.ui.components.formatCompactAmount(it.dosageAmount)
                    "${it.substanceName} ($doseStr ${it.dosageUnit})"
                }
            )
        }

        HealthSyncUiState(
            settings = settings,
            correlations = correlations,
            isWorking = working,
            statusMessage = messages.first,
            errorMessage = messages.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HealthSyncUiState()
    )

    fun startGoogleFitOAuth() {
        if (_isWorking.value) return
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val uri = healthSyncRepo.startGoogleOAuth()
                _pendingAuthUri.value = uri
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = safeUserError(e, "Unable to launch Google Sign-In.")
            }
        }
    }

    fun dismissGoogleSignInSheet() {
        _pendingAuthUri.value = null
        viewModelScope.launch {
            healthSyncRepo.cancelPendingOAuth()
        }
    }

    fun completeGoogleWebSignIn() {
        _pendingAuthUri.value = null
        viewModelScope.launch {
            _errorMessage.value = null
            healthSyncRepo.completeGoogleWebSignIn()
            _statusMessage.value = "Signed in with Google."
        }
    }

    fun handleOAuthRedirect(uri: Uri) {
        _pendingAuthUri.value = null
        viewModelScope.launch {
            _isWorking.value = true
            _errorMessage.value = null
            _statusMessage.value = "Completing Google Sign-In & syncing sleep sessions..."
            try {
                val count = healthSyncRepo.handleGoogleOAuthRedirect(uri)
                _statusMessage.value = "Connected to Google Fit. Synced $count sleep session(s)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = null
                _errorMessage.value = safeUserError(e, "Google Sign-In exchange failed.")
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun syncGoogleFitNow() {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _errorMessage.value = null
            try {
                val count = healthSyncRepo.syncGoogleFitSessions()
                _statusMessage.value = "Synced $count sleep session(s) from Google Fit REST."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = safeUserError(e, "Failed to sync Google Fit sessions.")
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun disconnectGoogleFit() {
        if (_isWorking.value) return
        viewModelScope.launch {
            healthSyncRepo.disconnectGoogleFit()
            _statusMessage.value = "Signed out of Google."
            _errorMessage.value = null
        }
    }

    fun importHealthFile(context: Context, uri: Uri) {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val count = healthSyncRepo.importSleepFile(context, uri)
                _statusMessage.value = "Imported $count sleep session(s) for correlation."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = safeUserError(e, "Failed to parse health export file.")
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    private fun safeUserError(e: Exception, fallback: String): String {
        if (BuildConfig.DEBUG) {
            Log.w("HealthSyncViewModel", fallback, e)
        }
        return if (e is IllegalStateException || e is IllegalArgumentException) {
            e.message ?: fallback
        } else {
            fallback
        }
    }

    companion object {
        fun factory(
            healthSyncRepo: HealthSyncRepository,
            substanceRepo: SubstanceRepository,
            prefsRepo: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HealthSyncViewModel(healthSyncRepo, substanceRepo, prefsRepo) as T
            }
        }
    }
}
