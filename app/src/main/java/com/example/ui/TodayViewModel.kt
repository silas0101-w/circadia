package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AiEngineMode
import com.example.data.local.SubstanceLogEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.remote.GeminiAnalysisException
import com.example.data.repository.ParsedAiReport
import com.example.data.repository.SleepImpactAiRepository
import com.example.data.repository.SleepImpactAiRepository.Companion.toParsed
import com.example.data.repository.SubstanceRepository
import com.example.domain.DateTimeUtils
import com.example.domain.DayConcentrationChartModel
import com.example.domain.DayPharmacokineticSummary
import com.example.domain.DecayModelMode
import com.example.domain.PharmacokineticsEngine
import com.example.domain.StructuredSessionMetadata
import com.example.domain.SubstanceConcentrationSeries
import android.util.Log
import com.example.data.local.SafeFileReader
import com.example.ui.components.formatCompactAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data class Loading(val targetLabel: String) : AiAnalysisUiState
    data class Success(val report: ParsedAiReport) : AiAnalysisUiState
    data class Error(
        val message: String,
        val technicalDetails: String? = null,
        val failureKind: com.example.data.remote.AiFailureKind = com.example.data.remote.AiFailureKind.UNKNOWN,
        val billingUrl: String? = null,
        val lastEntry: SubstanceLogEntity? = null
    ) : AiAnalysisUiState
}

data class TodayUiState(
    val dateIso: String = DateTimeUtils.todayIso(),
    val settings: UserSettings = UserSettings(),
    val summary: DayPharmacokineticSummary = PharmacokineticsEngine.analyzeDay(
        dateIso = DateTimeUtils.todayIso(),
        entries = emptyList(),
        targetBedtimeMinutes = 22 * 60 + 30
    ),
    val concentrationChartModel: DayConcentrationChartModel = PharmacokineticsEngine.buildDayConcentrationChartModel(
        dateIso = DateTimeUtils.todayIso(),
        entries = emptyList(),
        targetBedtimeMinutes = 22 * 60 + 30
    ),
    val entryConcentrationSeries: Map<Long, SubstanceConcentrationSeries> = emptyMap(),
    val entriesByDate: Map<String, List<SubstanceLogEntity>> = emptyMap(),
    val savedDayReport: ParsedAiReport? = null,
    val isDayReportStale: Boolean = false,
    val entryReports: Map<String, ParsedAiReport> = emptyMap(),
    val staleEntryIds: Set<Long> = emptySet(),
    val nowMillis: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val substanceRepo: SubstanceRepository,
    private val aiRepo: SleepImpactAiRepository,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    private val todayIsoFlow = tickerFlow
        .map { DateTimeUtils.dateIsoFromMillis(it) }
        .distinctUntilChanged()

    private val todayReportsFlow = todayIsoFlow.flatMapLatest { activeDateIso ->
        aiRepo.observeReportsForDate(activeDateIso)
    }

    val uiState: StateFlow<TodayUiState> = combine(
        substanceRepo.observeAllLogs(),
        prefsRepo.settingsFlow,
        todayReportsFlow,
        substanceRepo.dateMutations,
        tickerFlow
    ) { allLogs, settings, reports, mutations, nowMillis ->
        val activeTodayIso = DateTimeUtils.dateIsoFromMillis(nowMillis)
        val grouped = allLogs.groupBy { it.dateIso }
        val todayLogs = grouped[activeTodayIso].orEmpty().sortedBy { it.takenAtMillis }
        val summary = PharmacokineticsEngine.analyzeDay(
            dateIso = activeTodayIso,
            entries = todayLogs,
            targetBedtimeMinutes = settings.targetBedtimeMinutes,
            nowMillis = nowMillis,
            decayModel = settings.decayModelMode
        )
        val chartModel = PharmacokineticsEngine.buildDayConcentrationChartModel(
            dateIso = activeTodayIso,
            entries = todayLogs,
            targetBedtimeMinutes = settings.targetBedtimeMinutes,
            nowMillis = nowMillis,
            model = settings.decayModelMode
        )
        val perEntrySeries = todayLogs.mapIndexed { idx, entry ->
            entry.id to PharmacokineticsEngine.generateEntryConcentrationCurve(
                entry = entry,
                bedtimeMillis = summary.bedtimeMillis,
                nowMillis = nowMillis,
                model = settings.decayModelMode,
                seriesColorIndex = idx
            )
        }.toMap()

        val parsedReports = reports.map { it.toParsed() }
        val dayReport = parsedReports.find { it.scopeKey == "day:$activeTodayIso" }
        val byScope = parsedReports.associateBy { it.scopeKey }

        val latestMutation = mutations[activeTodayIso] ?: 0L
        val isDayStale = dayReport != null && (
            todayLogs.any { it.updatedAtMillis > dayReport.createdAtMillis } ||
                latestMutation > dayReport.createdAtMillis
            )

        val staleEntries = todayLogs.mapNotNull { entry ->
            val r = byScope["entry:${entry.id}"]
            if (r != null && entry.updatedAtMillis > r.createdAtMillis) entry.id else null
        }.toSet()

        TodayUiState(
            dateIso = activeTodayIso,
            settings = settings,
            summary = summary,
            concentrationChartModel = chartModel,
            entryConcentrationSeries = perEntrySeries,
            entriesByDate = grouped,
            savedDayReport = dayReport,
            isDayReportStale = isDayStale,
            entryReports = byScope,
            staleEntryIds = staleEntries,
            nowMillis = nowMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState()
    )

    private val _aiState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val aiState: StateFlow<AiAnalysisUiState> = _aiState.asStateFlow()

    private val _undoEvent = MutableStateFlow<UndoEvent?>(null)
    val undoEvent: StateFlow<UndoEvent?> = _undoEvent.asStateFlow()

    private var activeAnalysisJob: Job? = null
    private var lastAnalysisRequestAtMs: Long = 0L

    fun setDecayModelMode(mode: DecayModelMode) {
        viewModelScope.launch {
            prefsRepo.updateDecayModelMode(mode)
        }
    }

    fun saveOrUpdateEntry(
        targetDateIso: String,
        substanceId: String,
        substanceName: String,
        category: String,
        dosageAmount: Double,
        dosageUnit: String,
        takenAtMillis: Long,
        halfLifeHours: Double,
        disruptionWeight: Double,
        notes: String,
        existingId: Long? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleanName = SafeFileReader.sanitizeText(substanceName, 80)
            if (cleanName.isBlank()) return@launch
            val cleanCategory = SafeFileReader.sanitizeText(category, 32).ifBlank { "Custom" }
            val cleanUnit = SafeFileReader.sanitizeText(dosageUnit, 16).ifBlank { "mg" }
            val cleanNotes = SafeFileReader.sanitizeText(notes, 300)
            val safeDosage = dosageAmount.takeIf { it.isFinite() }?.coerceIn(0.01, 10_000.0) ?: 1.0
            val safeHalfLife = halfLifeHours.takeIf { it.isFinite() }?.coerceIn(0.25, 200.0) ?: 4.0
            val safeWeight = disruptionWeight.takeIf { it.isFinite() }?.coerceIn(0.05, 10.0) ?: 1.0

            if (existingId != null && existingId > 0L) {
                val previous = uiState.value.entriesByDate.values
                    .asSequence()
                    .flatten()
                    .find { it.id == existingId }

                val updated = SubstanceLogEntity(
                    id = existingId,
                    dateIso = targetDateIso,
                    substanceId = substanceId,
                    substanceName = cleanName,
                    category = cleanCategory,
                    dosageAmount = safeDosage,
                    dosageUnit = cleanUnit,
                    takenAtMillis = takenAtMillis,
                    halfLifeHours = safeHalfLife,
                    disruptionWeight = safeWeight,
                    notes = cleanNotes,
                    updatedAtMillis = now,
                    syncId = previous?.resolvedSyncId() ?: "log_$existingId"
                )
                substanceRepo.updateLog(updated)
                if (previous != null && previous.dateIso != targetDateIso) {
                    substanceRepo.markDateMutated(previous.dateIso, now)
                    substanceRepo.deleteEntryReport(existingId)
                }
                if (previous != null) {
                    val msg = if (previous.dateIso != targetDateIso) {
                        "Moved $cleanName to ${DateTimeUtils.formatDisplayDate(targetDateIso)}"
                    } else {
                        "Updated $cleanName (${formatCompactAmount(safeDosage)} $cleanUnit)"
                    }
                    _undoEvent.value = UndoEvent(
                        message = msg,
                        snapshot = UndoSnapshot.Edited(previous)
                    )
                }
            } else {
                val entity = SubstanceLogEntity(
                    id = 0L,
                    dateIso = targetDateIso,
                    substanceId = substanceId,
                    substanceName = cleanName,
                    category = cleanCategory,
                    dosageAmount = safeDosage,
                    dosageUnit = cleanUnit,
                    takenAtMillis = takenAtMillis,
                    halfLifeHours = safeHalfLife,
                    disruptionWeight = safeWeight,
                    notes = cleanNotes,
                    updatedAtMillis = now
                )
                substanceRepo.addLog(entity)
            }
        }
    }

    fun applyInlineQuickStep(
        entry: SubstanceLogEntity,
        doseDelta: Double,
        timeDeltaMillis: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newDosage = (entry.dosageAmount + doseDelta).coerceAtLeast(0.25)
            val dayStart = DateTimeUtils.combineDateAndTime(entry.dateIso, 0, 0)
            val dayEnd = DateTimeUtils.combineDateAndTime(entry.dateIso, 23, 59)
            val newTakenAt = (entry.takenAtMillis + timeDeltaMillis).coerceIn(dayStart, dayEnd)

            val updatedNotes = if (doseDelta != 0.0) {
                StructuredSessionMetadata.scaleQuantityForDoseRatio(
                    substanceId = entry.substanceId,
                    fullNotes = entry.notes,
                    oldDose = entry.dosageAmount,
                    newDose = newDosage
                )
            } else {
                entry.notes
            }

            val updated = entry.copy(
                dosageAmount = newDosage,
                takenAtMillis = newTakenAt,
                notes = updatedNotes,
                updatedAtMillis = now
            )
            substanceRepo.updateLog(updated)
            val label = if (doseDelta != 0.0) {
                "${entry.substanceName} → ${formatCompactAmount(newDosage)} ${entry.dosageUnit}"
            } else {
                "${entry.substanceName} → ${DateTimeUtils.formatTime(newTakenAt)}"
            }
            _undoEvent.value = UndoEvent(
                message = "Adjusted $label",
                snapshot = UndoSnapshot.Edited(entry)
            )
        }
    }

    fun confirmDeleteEntry(entry: SubstanceLogEntity) {
        viewModelScope.launch {
            substanceRepo.deleteLog(entry.id, entry.dateIso)
            _undoEvent.value = UndoEvent(
                message = "Deleted ${entry.substanceName}",
                snapshot = UndoSnapshot.Deleted(entry)
            )
        }
    }

    fun undoLastAction(snapshot: UndoSnapshot) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            when (snapshot) {
                is UndoSnapshot.Edited -> {
                    val prev = snapshot.previousEntity
                    val currentDate = uiState.value.entriesByDate.values
                        .asSequence()
                        .flatten()
                        .find { it.id == prev.id }
                        ?.dateIso
                    substanceRepo.updateLog(prev.copy(updatedAtMillis = now))
                    if (currentDate != null && currentDate != prev.dateIso) {
                        substanceRepo.markDateMutated(currentDate, now)
                    }
                }
                is UndoSnapshot.Deleted -> {
                    val restored = snapshot.deletedEntity.copy(updatedAtMillis = now)
                    substanceRepo.addLog(restored)
                }
            }
        }
    }

    fun consumeUndoEvent() {
        _undoEvent.value = null
    }

    fun analyzeDay(forceGeminiFallback: Boolean = false, forceRefresh: Boolean = false) {
        if (activeAnalysisJob?.isActive == true || _aiState.value is AiAnalysisUiState.Loading) return
        val currentLogs = uiState.value.summary.entryInfos.map { it.entry }
        if (currentLogs.isEmpty()) {
            _aiState.value = AiAnalysisUiState.Error("Log at least one substance today before analyzing sleep impact.")
            return
        }
        val now = System.currentTimeMillis()
        if (!forceGeminiFallback && !forceRefresh && now - lastAnalysisRequestAtMs < AI_COOLDOWN_MS) {
            _aiState.value = AiAnalysisUiState.Error("Please wait a moment before starting another analysis.")
            return
        }
        lastAnalysisRequestAtMs = now
        activeAnalysisJob = viewModelScope.launch {
            val modeLabel = resolveProviderLabel(uiState.value.settings, forceGeminiFallback)
            _aiState.value = AiAnalysisUiState.Loading("Analyzing today's stack via $modeLabel...")
            try {
                val report = aiRepo.analyzeDay(
                    dateIso = uiState.value.dateIso,
                    entries = currentLogs,
                    forceGeminiFallback = forceGeminiFallback
                )
                _aiState.value = AiAnalysisUiState.Success(report)
            } catch (e: CancellationException) {
                lastAnalysisRequestAtMs = 0L
                throw e
            } catch (e: GeminiAnalysisException) {
                lastAnalysisRequestAtMs = 0L
                Log.e("TodayViewModel", "Day Gemini analysis failed: ${e.technicalDetails}", e)
                _aiState.value = AiAnalysisUiState.Error(
                    message = e.userMessage,
                    technicalDetails = e.technicalDetails,
                    failureKind = e.failureKind,
                    billingUrl = e.billingUrl,
                    lastEntry = null
                )
            } catch (e: Exception) {
                lastAnalysisRequestAtMs = 0L
                Log.e("TodayViewModel", "Day analysis failed", e)
                val (kind, billing) = classifyNonGeminiFailure(e)
                val safeMsg = if (e is IllegalStateException || e is IllegalArgumentException) {
                    e.message ?: "Sleep impact analysis failed."
                } else {
                    e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Sleep impact analysis failed. Please check your connection and settings."
                }
                _aiState.value = AiAnalysisUiState.Error(
                    message = safeMsg,
                    technicalDetails = formatErrorDetails(e),
                    failureKind = kind,
                    billingUrl = billing,
                    lastEntry = null
                )
            }
        }
    }

    fun analyzeSingleEntry(entry: SubstanceLogEntity, forceGeminiFallback: Boolean = false) {
        val cached = uiState.value.entryReports["entry:${entry.id}"]
        val isStale = cached != null && entry.updatedAtMillis > cached.createdAtMillis
        if (!forceGeminiFallback && cached != null && !isStale) {
            _aiState.value = AiAnalysisUiState.Success(cached)
            return
        }
        runEntryAnalysis(entry, forceGeminiFallback, forceRefresh = forceGeminiFallback)
    }

    fun refreshSingleEntryAnalysis(entry: SubstanceLogEntity, forceGeminiFallback: Boolean = false) {
        runEntryAnalysis(entry, forceGeminiFallback, forceRefresh = true)
    }

    private fun runEntryAnalysis(
        entry: SubstanceLogEntity,
        forceGeminiFallback: Boolean,
        forceRefresh: Boolean
    ) {
        if (activeAnalysisJob?.isActive == true || _aiState.value is AiAnalysisUiState.Loading) return
        val now = System.currentTimeMillis()
        if (!forceGeminiFallback && !forceRefresh && now - lastAnalysisRequestAtMs < AI_COOLDOWN_MS) {
            _aiState.value = AiAnalysisUiState.Error(
                message = "Please wait a moment before starting another analysis.",
                lastEntry = entry
            )
            return
        }
        lastAnalysisRequestAtMs = now
        activeAnalysisJob = viewModelScope.launch {
            val modeLabel = resolveProviderLabel(uiState.value.settings, forceGeminiFallback)
            _aiState.value = AiAnalysisUiState.Loading("Analyzing ${entry.substanceName} via $modeLabel...")
            try {
                val report = aiRepo.analyzeSingleEntry(
                    entry = entry,
                    forceGeminiFallback = forceGeminiFallback
                )
                _aiState.value = AiAnalysisUiState.Success(report)
            } catch (e: CancellationException) {
                lastAnalysisRequestAtMs = 0L
                throw e
            } catch (e: GeminiAnalysisException) {
                lastAnalysisRequestAtMs = 0L
                Log.e("TodayViewModel", "Entry Gemini analysis failed: ${e.technicalDetails}", e)
                _aiState.value = AiAnalysisUiState.Error(
                    message = e.userMessage,
                    technicalDetails = e.technicalDetails,
                    failureKind = e.failureKind,
                    billingUrl = e.billingUrl,
                    lastEntry = entry
                )
            } catch (e: Exception) {
                lastAnalysisRequestAtMs = 0L
                Log.e("TodayViewModel", "Entry analysis failed", e)
                val (kind, billing) = classifyNonGeminiFailure(e)
                val safeMsg = if (e is IllegalStateException || e is IllegalArgumentException) {
                    e.message ?: "Analysis failed for ${entry.substanceName}."
                } else {
                    e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Analysis failed for ${entry.substanceName}. Please check your connection and settings."
                }
                _aiState.value = AiAnalysisUiState.Error(
                    message = safeMsg,
                    technicalDetails = formatErrorDetails(e),
                    failureKind = kind,
                    billingUrl = billing,
                    lastEntry = entry
                )
            }
        }
    }

    private fun classifyNonGeminiFailure(
        e: Throwable
    ): Pair<com.example.data.remote.AiFailureKind, String?> {
        val msg = (e.message ?: "").lowercase()
        return when {
            msg.contains("prepayment") || msg.contains("credits are depleted") || msg.contains("billing") ->
                com.example.data.remote.AiFailureKind.BILLING_DEPLETED to com.example.data.remote.AI_STUDIO_BILLING_URL
            msg.contains("nvidia") && (
                msg.contains("api key") || msg.contains("authentication failed") ||
                    msg.contains("empty") || msg.contains("401") || msg.contains("403") ||
                    msg.contains("settings")
                ) ->
                com.example.data.remote.AiFailureKind.CONFIGURATION_REQUIRED to null
            msg.contains("timed out") || e is java.net.SocketTimeoutException ->
                com.example.data.remote.AiFailureKind.TIMEOUT to null
            e is java.net.UnknownHostException || e is java.io.IOException ->
                com.example.data.remote.AiFailureKind.NETWORK to null
            else -> com.example.data.remote.AiFailureKind.UNKNOWN to null
        }
    }

    private fun formatErrorDetails(e: Throwable): String = buildString {
        append("${e::class.java.simpleName}: ${e.message ?: "No details"}")
        e.cause?.let { cause ->
            append("\nCause: ${cause::class.java.simpleName}: ${cause.message ?: "No details"}")
        }
    }

    fun showSavedReport(report: ParsedAiReport) {
        _aiState.value = AiAnalysisUiState.Success(report)
    }

    fun dismissAiSheet() {
        activeAnalysisJob?.cancel()
        activeAnalysisJob = null
        _aiState.value = AiAnalysisUiState.Idle
    }

    private fun resolveProviderLabel(settings: UserSettings, forceGemini: Boolean): String {
        return when {
            forceGemini || settings.aiEngineMode == AiEngineMode.GEMINI_ONLY -> "Gemini 3.8 Flash"
            settings.nvidiaApiKey.isNotBlank() -> "NVIDIA NIM"
            else -> "Gemini 3.8 Flash"
        }
    }

    companion object {
        private const val AI_COOLDOWN_MS = 3_000L

        fun factory(
            substanceRepo: SubstanceRepository,
            aiRepo: SleepImpactAiRepository,
            prefsRepo: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodayViewModel(substanceRepo, aiRepo, prefsRepo) as T
            }
        }
    }
}
