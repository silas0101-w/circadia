package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AiEngineMode
import com.example.data.local.AiImpactReportEntity
import com.example.data.local.CircadiaDao
import com.example.data.local.SafeFileReader
import com.example.data.local.SubstanceLogEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.GeminiImpactService
import com.example.data.remote.ImpactAnalysisResult
import com.example.data.remote.NvidiaNimService
import com.example.domain.DateTimeUtils
import com.example.domain.PharmacokineticsEngine
import com.example.domain.SubstanceCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.util.Locale

private const val AI_REPO_TAG = "SleepImpactAiRepo"

data class ParsedAiReport(
    val scopeKey: String,
    val dateIso: String,
    val providerUsed: String,
    val modelUsed: String,
    val severityRating: String,
    val biologicalMechanism: String,
    val mitigationTips: List<String>,
    val createdAtMillis: Long
)

class SleepImpactAiRepository(
    private val dao: CircadiaDao,
    private val prefsRepo: UserPreferencesRepository,
    private val nvidiaService: NvidiaNimService,
    private val geminiService: GeminiImpactService
) {

    fun observeReportsForDate(dateIso: String): Flow<List<AiImpactReportEntity>> =
        dao.observeReportsForDate(dateIso)

    fun observeReport(scopeKey: String): Flow<AiImpactReportEntity?> =
        dao.observeReport(scopeKey)

    suspend fun analyzeDay(
        dateIso: String,
        entries: List<SubstanceLogEntity>,
        forceGeminiFallback: Boolean = false
    ): ParsedAiReport {
        require(entries.isNotEmpty()) { "Log at least one substance before running analysis." }
        val settings = prefsRepo.getCurrentSettings()
        val summary = PharmacokineticsEngine.analyzeDay(
            dateIso = dateIso,
            entries = entries,
            targetBedtimeMinutes = settings.targetBedtimeMinutes
        )

        val prompt = buildString {
            appendLine("<user_intake_log>")
            appendLine("Date: $dateIso")
            appendLine("Target Bedtime: ${DateTimeUtils.formatMinutesOfDay(settings.targetBedtimeMinutes)}")
            appendLine("Calculated Pharmacokinetic Sleep Disruption Score: ${summary.disruptionScore}/100 (${summary.severityTier.label})")
            appendLine("Logged Substances:")
            summary.entryInfos.forEachIndexed { idx, info ->
                val e = info.entry
                val safeName = SafeFileReader.sanitizeText(e.substanceName, 80)
                val safeCategory = SafeFileReader.sanitizeText(e.category, 32)
                val safeUnit = SafeFileReader.sanitizeText(e.dosageUnit, 16)
                val timeStr = DateTimeUtils.formatTime(e.takenAtMillis)
                val remBedStr = String.format(Locale.US, "%.1f", info.remainingAtBedtimeAmount)
                val pctBedStr = String.format(Locale.US, "%.0f", info.percentRemainingAtBedtime)
                val ref = SubstanceCatalog.findById(e.substanceId)
                appendLine(
                    "${idx + 1}. $safeName ($safeCategory): ${e.dosageAmount} $safeUnit taken at $timeStr | " +
                        "half-life=${e.halfLifeHours}h | active at bedtime=$remBedStr $safeUnit ($pctBedStr%)"
                )
                if (ref != null) {
                    appendLine("   Known pharmacology: ${ref.mechanismSummary}")
                }
            }
            appendLine("</user_intake_log>")
        }

        return executeAndPersist(
            scopeKey = "day:$dateIso",
            dateIso = dateIso,
            promptPayload = prompt,
            forceGeminiFallback = forceGeminiFallback
        )
    }

    suspend fun analyzeSingleEntry(
        entry: SubstanceLogEntity,
        forceGeminiFallback: Boolean = false
    ): ParsedAiReport {
        val settings = prefsRepo.getCurrentSettings()
        val bedtimeMillis = DateTimeUtils.bedtimeMillisForDate(entry.dateIso, settings.targetBedtimeMinutes)
        val ref = SubstanceCatalog.findById(entry.substanceId)
        val remAtBed = PharmacokineticsEngine.activeAmountAt(
            dosageAmount = entry.dosageAmount,
            takenAtMillis = entry.takenAtMillis,
            halfLifeHours = entry.halfLifeHours,
            peakOnsetMinutes = ref?.peakOnsetMinutes ?: 30,
            targetMillis = bedtimeMillis
        )
        val pctAtBed = if (entry.dosageAmount > 0) (remAtBed / entry.dosageAmount * 100.0) else 0.0

        val safeName = SafeFileReader.sanitizeText(entry.substanceName, 80)
        val safeCategory = SafeFileReader.sanitizeText(entry.category, 32)
        val safeUnit = SafeFileReader.sanitizeText(entry.dosageUnit, 16)
        val safeNotes = SafeFileReader.sanitizeText(entry.notes, 300)

        val prompt = buildString {
            appendLine("<user_intake_log>")
            appendLine("Single Substance Impact Focus:")
            appendLine("Substance: $safeName ($safeCategory)")
            appendLine("Dosage: ${entry.dosageAmount} $safeUnit")
            appendLine("Time Taken: ${DateTimeUtils.formatTime(entry.takenAtMillis)}")
            appendLine("Target Bedtime: ${DateTimeUtils.formatMinutesOfDay(settings.targetBedtimeMinutes)}")
            appendLine("Half-life: ${entry.halfLifeHours} hours")
            appendLine(
                "Estimated Active at Bedtime: ${String.format(Locale.US, "%.1f", remAtBed)} $safeUnit " +
                    "(${String.format(Locale.US, "%.0f", pctAtBed)}%)"
            )
            if (ref != null) {
                appendLine("Reference mechanism: ${ref.mechanismSummary}")
            }
            if (safeNotes.isNotBlank()) {
                appendLine("User notes: $safeNotes")
            }
            appendLine("</user_intake_log>")
        }

        return executeAndPersist(
            scopeKey = "entry:${entry.id}",
            dateIso = entry.dateIso,
            promptPayload = prompt,
            forceGeminiFallback = forceGeminiFallback
        )
    }

    private suspend fun executeAndPersist(
        scopeKey: String,
        dateIso: String,
        promptPayload: String,
        forceGeminiFallback: Boolean
    ): ParsedAiReport {
        val settings = prefsRepo.getCurrentSettings()

        val result: ImpactAnalysisResult = when {
            forceGeminiFallback || settings.aiEngineMode == AiEngineMode.GEMINI_ONLY -> {
                geminiService.analyzeSleepImpact(promptPayload)
            }
            settings.aiEngineMode == AiEngineMode.NVIDIA_ONLY -> {
                if (settings.nvidiaApiKey.isBlank()) {
                    throw IllegalStateException(
                        "NVIDIA NIM Only mode is selected, but no NVIDIA API key is saved in Settings."
                    )
                }
                nvidiaService.analyzeSleepImpact(
                    apiKey = settings.nvidiaApiKey,
                    modelId = settings.nvidiaModelId,
                    endpointUrl = settings.nvidiaEndpointUrl,
                    promptPayload = promptPayload
                )
            }
            else -> {
                // AUTO_NVIDIA_THEN_GEMINI
                if (settings.nvidiaApiKey.isNotBlank()) {
                    try {
                        nvidiaService.analyzeSleepImpact(
                            apiKey = settings.nvidiaApiKey,
                            modelId = settings.nvidiaModelId,
                            endpointUrl = settings.nvidiaEndpointUrl,
                            promptPayload = promptPayload
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IllegalArgumentException) {
                        throw e
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.w(AI_REPO_TAG, "NVIDIA NIM failed; falling back to built-in Gemini.", e)
                        }
                        geminiService.analyzeSleepImpact(promptPayload)
                    }
                } else {
                    geminiService.analyzeSleepImpact(promptPayload)
                }
            }
        }

        val entity = AiImpactReportEntity(
            scopeKey = scopeKey,
            dateIso = dateIso,
            providerUsed = result.providerUsed,
            modelUsed = result.modelUsed,
            severityRating = result.severityRating,
            biologicalMechanism = result.biologicalMechanism,
            mitigationTipsJson = JSONArray(result.mitigationTips).toString(),
            createdAtMillis = System.currentTimeMillis()
        )
        dao.upsertReport(entity)
        return entity.toParsed()
    }

    companion object {
        fun AiImpactReportEntity.toParsed(): ParsedAiReport {
            val arr = runCatching { JSONArray(mitigationTipsJson) }.getOrNull()
            val tips = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i).trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
            }
            return ParsedAiReport(
                scopeKey = scopeKey,
                dateIso = dateIso,
                providerUsed = providerUsed,
                modelUsed = modelUsed,
                severityRating = severityRating,
                biologicalMechanism = biologicalMechanism,
                mitigationTips = tips,
                createdAtMillis = createdAtMillis
            )
        }
    }
}
