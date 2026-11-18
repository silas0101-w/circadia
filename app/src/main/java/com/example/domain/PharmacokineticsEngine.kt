package com.example.domain

import com.example.data.local.SubstanceLogEntity
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

enum class SeverityTier(val label: String) {
    LOW("Low"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
    CRITICAL("Critical")
}

data class EntryClearanceInfo(
    val entry: SubstanceLogEntity,
    val remainingAtBedtimeAmount: Double,
    val percentRemainingAtBedtime: Double,
    val remainingNowAmount: Double,
    val hoursUntilTenPercentFromNow: Double,
    val impactPoints: Double
)

data class CurvePoint(
    val hourOffsetFromStart: Float,
    val normalizedLoad: Float
)

data class DayPharmacokineticSummary(
    val dateIso: String,
    val bedtimeMillis: Long,
    val disruptionScore: Int,
    val severityTier: SeverityTier,
    val entryInfos: List<EntryClearanceInfo>,
    val decayCurve: List<CurvePoint>,
    val curveStartMillis: Long,
    val curveEndMillis: Long
)

data class EditImpactPreview(
    val beforeEntryRemainingAtBed: Double?,
    val beforeEntryPercentAtBed: Double?,
    val afterEntryRemainingAtBed: Double,
    val afterEntryPercentAtBed: Double,
    val afterHoursUntilCleared: Double,
    val beforeDayScore: Int,
    val afterDayScore: Int,
    val scoreDelta: Int,
    val afterSeverityTier: SeverityTier,
    val isMovedToDifferentDate: Boolean,
    val movedTargetScoreWithoutMove: Int
)

object PharmacokineticsEngine {

    /**
     * First-order elimination with smooth absorption ramp up to peakOnsetMinutes.
     */
    fun activeAmountAt(
        dosageAmount: Double,
        takenAtMillis: Long,
        halfLifeHours: Double,
        peakOnsetMinutes: Int,
        targetMillis: Long
    ): Double {
        if (!dosageAmount.isFinite() || !halfLifeHours.isFinite() || dosageAmount <= 0.0 || targetMillis < takenAtMillis) {
            return 0.0
        }
        val elapsedHours = (targetMillis - takenAtMillis) / 3_600_000.0
        val onsetHours = (peakOnsetMinutes.coerceAtLeast(5)) / 60.0
        val safeHalfLife = halfLifeHours.coerceIn(0.25, 200.0)

        val result = if (elapsedHours < onsetHours) {
            val absorptionFraction = (elapsedHours / onsetHours).coerceIn(0.15, 1.0)
            dosageAmount * absorptionFraction
        } else {
            val decayHours = elapsedHours - onsetHours
            dosageAmount * 0.5.pow(decayHours / safeHalfLife)
        }
        return if (result.isFinite()) result else 0.0
    }

    /**
     * Calculates remaining hours from [nowMillis] until active amount drops below 10% of initial dose.
     */
    fun hoursUntilClearedFromNow(
        takenAtMillis: Long,
        halfLifeHours: Double,
        peakOnsetMinutes: Int,
        nowMillis: Long
    ): Double {
        val safeHalfLife = (if (halfLifeHours.isFinite()) halfLifeHours else 4.0).coerceIn(0.25, 200.0)
        val onsetHours = peakOnsetMinutes.coerceAtLeast(5) / 60.0
        val totalClearanceHours = onsetHours + safeHalfLife * 3.321928
        val clearAtMillis = takenAtMillis + (totalClearanceHours * 3_600_000.0).toLong()
        val remainingHours = (clearAtMillis - nowMillis) / 3_600_000.0
        return if (remainingHours.isFinite()) remainingHours.coerceAtLeast(0.0) else 0.0
    }

    fun analyzeDay(
        dateIso: String,
        entries: List<SubstanceLogEntity>,
        targetBedtimeMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): DayPharmacokineticSummary {
        val bedtimeMillis = DateTimeUtils.bedtimeMillisForDate(dateIso, targetBedtimeMinutes)
        val dayStartMillis = DateTimeUtils.parseIsoToCalendar(dateIso).timeInMillis + 6 * 3_600_000L
        val curveStart = minOf(
            dayStartMillis,
            entries.minOfOrNull { it.takenAtMillis } ?: dayStartMillis
        )
        val curveEnd = maxOf(bedtimeMillis + 4 * 3_600_000L, curveStart + 18 * 3_600_000L)

        var totalWeightedLoad = 0.0
        val infos = entries.map { entry ->
            val safeDosage = if (entry.dosageAmount.isFinite()) entry.dosageAmount.coerceAtLeast(0.0) else 0.0
            val safeHalfLife = if (entry.halfLifeHours.isFinite()) entry.halfLifeHours.coerceIn(0.25, 200.0) else 4.0
            val safeWeight = if (entry.disruptionWeight.isFinite()) entry.disruptionWeight.coerceIn(0.05, 10.0) else 1.0

            val ref = SubstanceCatalog.findById(entry.substanceId)
            val baseline = (ref?.referenceBaselineDose ?: defaultBaselineForUnit(entry.dosageUnit, safeDosage))
                .takeIf { it.isFinite() }?.coerceAtLeast(0.5) ?: 50.0
            val onsetMin = ref?.peakOnsetMinutes ?: 30

            val remAtBed = activeAmountAt(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                peakOnsetMinutes = onsetMin,
                targetMillis = bedtimeMillis
            )
            val remNow = activeAmountAt(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                peakOnsetMinutes = onsetMin,
                targetMillis = nowMillis
            )
            val pctAtBed = if (safeDosage > 0.0) (remAtBed / safeDosage * 100.0).coerceIn(0.0, 100.0) else 0.0
            val hoursToClear = hoursUntilClearedFromNow(
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                peakOnsetMinutes = onsetMin,
                nowMillis = nowMillis
            )

            val normalizedAtBed = (remAtBed / baseline) * safeWeight
            val hoursBeforeBed = (bedtimeMillis - entry.takenAtMillis) / 3_600_000.0
            val lateFactor = when {
                hoursBeforeBed <= 0.0 -> 1.45
                hoursBeforeBed <= 2.0 -> 1.25
                hoursBeforeBed <= 4.0 -> 1.1
                else -> 1.0
            }
            val entryLoad = normalizedAtBed * lateFactor
            if (entryLoad.isFinite()) {
                totalWeightedLoad += entryLoad
            }

            EntryClearanceInfo(
                entry = entry,
                remainingAtBedtimeAmount = remAtBed,
                percentRemainingAtBedtime = pctAtBed,
                remainingNowAmount = remNow,
                hoursUntilTenPercentFromNow = hoursToClear,
                impactPoints = (entryLoad * 32.0).takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
            )
        }

        val score = if (!totalWeightedLoad.isFinite() || totalWeightedLoad <= 0.001) {
            0
        } else {
            (100.0 * (1.0 - exp(-0.55 * totalWeightedLoad)))
                .takeIf { it.isFinite() }
                ?.roundToInt()
                ?.coerceIn(0, 100)
                ?: 0
        }

        val tier = severityFromScore(score)

        val steps = 32
        val spanMillis = (curveEnd - curveStart).coerceAtLeast(3_600_000L)
        val rawLoads = (0..steps).map { idx ->
            val sampleMillis = curveStart + (spanMillis * idx) / steps
            val hourOffset = (sampleMillis - curveStart) / 3_600_000f
            val load = entries.sumOf { entry ->
                val safeDosage = if (entry.dosageAmount.isFinite()) entry.dosageAmount.coerceAtLeast(0.0) else 0.0
                val safeHalfLife = if (entry.halfLifeHours.isFinite()) entry.halfLifeHours.coerceIn(0.25, 200.0) else 4.0
                val safeWeight = if (entry.disruptionWeight.isFinite()) entry.disruptionWeight.coerceIn(0.05, 10.0) else 1.0
                val ref = SubstanceCatalog.findById(entry.substanceId)
                val baseline = (ref?.referenceBaselineDose ?: defaultBaselineForUnit(entry.dosageUnit, safeDosage))
                    .takeIf { it.isFinite() }?.coerceAtLeast(0.5) ?: 50.0
                val onsetMin = ref?.peakOnsetMinutes ?: 30
                val amt = activeAmountAt(
                    dosageAmount = safeDosage,
                    takenAtMillis = entry.takenAtMillis,
                    halfLifeHours = safeHalfLife,
                    peakOnsetMinutes = onsetMin,
                    targetMillis = sampleMillis
                )
                val pointLoad = (amt / baseline) * safeWeight
                if (pointLoad.isFinite()) pointLoad else 0.0
            }
            val safeFloatLoad = load.toFloat().let { if (it.isFinite()) it else 0f }
            hourOffset to safeFloatLoad
        }
        val peakLoad = (rawLoads.maxOfOrNull { it.second } ?: 1f).let { if (it.isFinite()) it else 1f }.coerceAtLeast(1f)
        val curve = rawLoads.map { (h, l) ->
            CurvePoint(hourOffsetFromStart = h, normalizedLoad = (l / peakLoad).coerceIn(0f, 1f))
        }

        return DayPharmacokineticSummary(
            dateIso = dateIso,
            bedtimeMillis = bedtimeMillis,
            disruptionScore = score,
            severityTier = tier,
            entryInfos = infos,
            decayCurve = curve,
            curveStartMillis = curveStart,
            curveEndMillis = curveEnd
        )
    }

    /**
     * Pure in-memory preview showing how adding, editing, or moving an entry changes bedtime residue
     * and the day's overall Sleep Disruption Score before saving to Room.
     */
    fun previewEditImpact(
        originalDateIso: String,
        originalDayEntries: List<SubstanceLogEntity>,
        targetDayEntries: List<SubstanceLogEntity>,
        editingEntry: SubstanceLogEntity?,
        candidateEntry: SubstanceLogEntity,
        targetBedtimeMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): EditImpactPreview {
        val beforeOriginalSummary = analyzeDay(
            dateIso = originalDateIso,
            entries = originalDayEntries,
            targetBedtimeMinutes = targetBedtimeMinutes,
            nowMillis = nowMillis
        )
        val beforeInfo = editingEntry?.let { old ->
            beforeOriginalSummary.entryInfos.find { it.entry.id == old.id }
        }

        val isMoved = editingEntry != null && candidateEntry.dateIso != originalDateIso
        val targetDate = candidateEntry.dateIso

        val baseEntriesForTarget = if (isMoved) targetDayEntries else originalDayEntries
        val updatedEntriesForTarget = if (editingEntry != null && !isMoved) {
            baseEntriesForTarget.map { if (it.id == editingEntry.id) candidateEntry else it }
        } else {
            baseEntriesForTarget.filterNot { editingEntry != null && it.id == editingEntry.id } + candidateEntry
        }

        val afterTargetSummary = analyzeDay(
            dateIso = targetDate,
            entries = updatedEntriesForTarget,
            targetBedtimeMinutes = targetBedtimeMinutes,
            nowMillis = nowMillis
        )
        val afterInfo = afterTargetSummary.entryInfos.lastOrNull {
            if (editingEntry != null) it.entry.id == editingEntry.id else it.entry.substanceName == candidateEntry.substanceName && it.entry.takenAtMillis == candidateEntry.takenAtMillis
        } ?: afterTargetSummary.entryInfos.last()

        val originalDayWithoutMoved = if (isMoved) {
            analyzeDay(
                dateIso = originalDateIso,
                entries = originalDayEntries.filterNot { it.id == editingEntry?.id },
                targetBedtimeMinutes = targetBedtimeMinutes,
                nowMillis = nowMillis
            ).disruptionScore
        } else {
            beforeOriginalSummary.disruptionScore
        }

        val beforeScoreForComparison = if (isMoved) {
            analyzeDay(
                dateIso = targetDate,
                entries = targetDayEntries,
                targetBedtimeMinutes = targetBedtimeMinutes,
                nowMillis = nowMillis
            ).disruptionScore
        } else {
            beforeOriginalSummary.disruptionScore
        }

        return EditImpactPreview(
            beforeEntryRemainingAtBed = beforeInfo?.remainingAtBedtimeAmount,
            beforeEntryPercentAtBed = beforeInfo?.percentRemainingAtBedtime,
            afterEntryRemainingAtBed = afterInfo.remainingAtBedtimeAmount,
            afterEntryPercentAtBed = afterInfo.percentRemainingAtBedtime,
            afterHoursUntilCleared = afterInfo.hoursUntilTenPercentFromNow,
            beforeDayScore = beforeScoreForComparison,
            afterDayScore = afterTargetSummary.disruptionScore,
            scoreDelta = afterTargetSummary.disruptionScore - beforeScoreForComparison,
            afterSeverityTier = afterTargetSummary.severityTier,
            isMovedToDifferentDate = isMoved,
            movedTargetScoreWithoutMove = originalDayWithoutMoved
        )
    }

    fun severityFromScore(score: Int): SeverityTier = when {
        score < 25 -> SeverityTier.LOW
        score < 50 -> SeverityTier.MODERATE
        score < 75 -> SeverityTier.SEVERE
        else -> SeverityTier.CRITICAL
    }

    fun computeSmartDoseStep(unit: String, currentDosage: Double): Double {
        return when {
            unit.equals("mg", ignoreCase = true) && currentDosage >= 50.0 -> 25.0
            unit.equals("mg", ignoreCase = true) && currentDosage >= 10.0 -> 5.0
            unit.equals("mg", ignoreCase = true) -> 1.0
            else -> 0.5
        }
    }

    private fun defaultBaselineForUnit(unit: String, fallbackAmount: Double): Double {
        return when (unit.lowercase()) {
            "mg" -> 50.0
            "drinks", "cups", "servings" -> 1.5
            else -> fallbackAmount.coerceAtLeast(1.0)
        }
    }
}
