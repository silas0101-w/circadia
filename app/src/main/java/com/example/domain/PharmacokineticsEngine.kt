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

enum class DecayModelMode(val label: String, val formulaSummary: String) {
    ONSET_RAMP(
        label = "Onset Ramp",
        formulaSummary = "15%→100% absorption to peak onset, then C(t) = D × 0.5^((Δt - onset) / t½)"
    ),
    PURE_HALF_LIFE(
        label = "Pure Half-Life",
        formulaSummary = "C(t) = D × 0.5^(Δt / t½) starting immediately at intake"
    )
}

data class ConcentrationSnapshot(
    val remainingAmount: Double,
    val percentRemaining: Double,
    val elapsedHoursFromIntake: Double,
    val modelUsed: DecayModelMode
)

data class ConcentrationPoint(
    val timestampMillis: Long,
    val hourOffsetFromStart: Float,
    val remainingAmount: Double,
    val percentRemaining: Float
)

data class SubstanceConcentrationSeries(
    val entry: SubstanceLogEntity,
    val seriesColorIndex: Int,
    val peakOnsetMinutes: Int,
    val points: List<ConcentrationPoint>,
    val bedtimeSnapshot: ConcentrationSnapshot,
    val nowSnapshot: ConcentrationSnapshot?,
    val clearedAtMillis: Long
)

data class DayConcentrationChartModel(
    val dateIso: String,
    val decayModel: DecayModelMode,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val bedtimeMillis: Long,
    val series: List<SubstanceConcentrationSeries>,
    val sharedUnitLabel: String?,
    val combinedMaxSingleUnit: Double?
)

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
    val curveEndMillis: Long,
    val decayModel: DecayModelMode = DecayModelMode.ONSET_RAMP
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

    private const val MS_PER_HOUR = 3_600_000.0
    private const val LOG2_OF_10 = 3.321928094887362

    /**
     * Mathematical helper returning remaining active substance concentration at [targetMillis]
     * in both exact logged dosage units and percentage of the initial dose.
     *
     * Supports:
     * - [DecayModelMode.PURE_HALF_LIFE]: C(t) = D * 0.5^(dt / t_half) for t >= t0
     * - [DecayModelMode.ONSET_RAMP]: linear absorption ramp (15% -> 100%) up to peakOnsetMinutes,
     *   followed by exponential decay C(t) = D * 0.5^((dt - t_onset) / t_half).
     */
    fun calculateRemainingConcentration(
        dosageAmount: Double,
        takenAtMillis: Long,
        halfLifeHours: Double,
        targetMillis: Long,
        peakOnsetMinutes: Int = 30,
        model: DecayModelMode = DecayModelMode.PURE_HALF_LIFE
    ): ConcentrationSnapshot {
        if (!dosageAmount.isFinite() ||
            !halfLifeHours.isFinite() ||
            dosageAmount <= 0.0 ||
            targetMillis < takenAtMillis
        ) {
            return ConcentrationSnapshot(
                remainingAmount = 0.0,
                percentRemaining = 0.0,
                elapsedHoursFromIntake = 0.0,
                modelUsed = model
            )
        }

        val elapsedHours = (targetMillis - takenAtMillis) / MS_PER_HOUR
        val safeHalfLife = halfLifeHours.coerceIn(0.25, 200.0)

        val rawRemaining = when (model) {
            DecayModelMode.PURE_HALF_LIFE -> {
                dosageAmount * 0.5.pow(elapsedHours / safeHalfLife)
            }
            DecayModelMode.ONSET_RAMP -> {
                val onsetHours = peakOnsetMinutes.coerceAtLeast(5) / 60.0
                if (elapsedHours < onsetHours) {
                    val absorptionFraction = (elapsedHours / onsetHours).coerceIn(0.15, 1.0)
                    dosageAmount * absorptionFraction
                } else {
                    val decayHours = elapsedHours - onsetHours
                    dosageAmount * 0.5.pow(decayHours / safeHalfLife)
                }
            }
        }

        val safeRemaining = if (rawRemaining.isFinite()) {
            rawRemaining.coerceIn(0.0, dosageAmount)
        } else {
            0.0
        }
        val percent = ((safeRemaining / dosageAmount) * 100.0)
            .takeIf { it.isFinite() }
            ?.coerceIn(0.0, 100.0)
            ?: 0.0

        return ConcentrationSnapshot(
            remainingAmount = safeRemaining,
            percentRemaining = percent,
            elapsedHoursFromIntake = elapsedHours,
            modelUsed = model
        )
    }

    /**
     * Convenience helper returning the remaining dosage amount at [targetMillis].
     */
    fun activeAmountAt(
        dosageAmount: Double,
        takenAtMillis: Long,
        halfLifeHours: Double,
        peakOnsetMinutes: Int,
        targetMillis: Long,
        model: DecayModelMode = DecayModelMode.ONSET_RAMP
    ): Double = calculateRemainingConcentration(
        dosageAmount = dosageAmount,
        takenAtMillis = takenAtMillis,
        halfLifeHours = halfLifeHours,
        targetMillis = targetMillis,
        peakOnsetMinutes = peakOnsetMinutes,
        model = model
    ).remainingAmount

    fun clearanceTimestampMillis(
        takenAtMillis: Long,
        halfLifeHours: Double,
        peakOnsetMinutes: Int = 30,
        model: DecayModelMode = DecayModelMode.ONSET_RAMP
    ): Long {
        val safeHalfLife = (if (halfLifeHours.isFinite()) halfLifeHours else 4.0).coerceIn(0.25, 200.0)
        val onsetHours = if (model == DecayModelMode.ONSET_RAMP) {
            peakOnsetMinutes.coerceAtLeast(5) / 60.0
        } else {
            0.0
        }
        val totalClearanceHours = onsetHours + safeHalfLife * LOG2_OF_10
        return takenAtMillis + (totalClearanceHours * MS_PER_HOUR).toLong()
    }

    /**
     * Calculates remaining hours from [nowMillis] until active amount drops below 10% of initial dose.
     */
    fun hoursUntilClearedFromNow(
        takenAtMillis: Long,
        halfLifeHours: Double,
        peakOnsetMinutes: Int,
        nowMillis: Long,
        model: DecayModelMode = DecayModelMode.ONSET_RAMP
    ): Double {
        val clearAtMillis = clearanceTimestampMillis(
            takenAtMillis = takenAtMillis,
            halfLifeHours = halfLifeHours,
            peakOnsetMinutes = peakOnsetMinutes,
            model = model
        )
        val remainingHours = (clearAtMillis - nowMillis) / MS_PER_HOUR
        return if (remainingHours.isFinite()) remainingHours.coerceAtLeast(0.0) else 0.0
    }

    /**
     * Generates a time-series curve for a single logged substance from intake past bedtime/clearance,
     * including exact coordinates at peak onset (when using ONSET_RAMP) and target bedtime.
     */
    fun generateEntryConcentrationCurve(
        entry: SubstanceLogEntity,
        bedtimeMillis: Long,
        nowMillis: Long? = null,
        model: DecayModelMode = DecayModelMode.ONSET_RAMP,
        seriesColorIndex: Int = 0,
        steps: Int = 48
    ): SubstanceConcentrationSeries {
        val safeDosage = if (entry.dosageAmount.isFinite()) entry.dosageAmount.coerceAtLeast(0.0) else 0.0
        val safeHalfLife = if (entry.halfLifeHours.isFinite()) entry.halfLifeHours.coerceIn(0.25, 200.0) else 4.0
        val ref = SubstanceCatalog.findById(entry.substanceId)
        val onsetMin = ref?.peakOnsetMinutes ?: 30
        val onsetMillis = onsetMin.coerceAtLeast(5) * 60_000L

        val startMillis = entry.takenAtMillis
        val naturalEnd = startMillis + (safeHalfLife * 2.5 * MS_PER_HOUR).toLong()
        val cappedEnd = minOf(naturalEnd, startMillis + 36L * 3_600_000L)
        val endMillis = maxOf(
            bedtimeMillis + 3L * 3_600_000L,
            cappedEnd,
            startMillis + 6L * 3_600_000L
        )

        val sampleTimes = buildSampleTimestamps(
            windowStartMillis = startMillis,
            windowEndMillis = endMillis,
            steps = steps.coerceAtLeast(12),
            extraTimestamps = buildList {
                if (model == DecayModelMode.ONSET_RAMP) {
                    add(startMillis + onsetMillis)
                }
                add(bedtimeMillis)
                if (nowMillis != null) add(nowMillis)
            }
        )

        val points = sampleTimes.map { ts ->
            val snap = calculateRemainingConcentration(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                targetMillis = ts,
                peakOnsetMinutes = onsetMin,
                model = model
            )
            ConcentrationPoint(
                timestampMillis = ts,
                hourOffsetFromStart = ((ts - startMillis) / MS_PER_HOUR).toFloat(),
                remainingAmount = snap.remainingAmount,
                percentRemaining = snap.percentRemaining.toFloat()
            )
        }

        val bedtimeSnapshot = calculateRemainingConcentration(
            dosageAmount = safeDosage,
            takenAtMillis = entry.takenAtMillis,
            halfLifeHours = safeHalfLife,
            targetMillis = bedtimeMillis,
            peakOnsetMinutes = onsetMin,
            model = model
        )
        val nowSnapshot = nowMillis?.let {
            calculateRemainingConcentration(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                targetMillis = it,
                peakOnsetMinutes = onsetMin,
                model = model
            )
        }
        val clearedAtMillis = clearanceTimestampMillis(
            takenAtMillis = entry.takenAtMillis,
            halfLifeHours = safeHalfLife,
            peakOnsetMinutes = onsetMin,
            model = model
        )

        return SubstanceConcentrationSeries(
            entry = entry,
            seriesColorIndex = seriesColorIndex,
            peakOnsetMinutes = onsetMin,
            points = points,
            bedtimeSnapshot = bedtimeSnapshot,
            nowSnapshot = nowSnapshot,
            clearedAtMillis = clearedAtMillis
        )
    }

    /**
     * Generates an aligned multi-series concentration decay model across all entries logged on [dateIso].
     */
    fun buildDayConcentrationChartModel(
        dateIso: String,
        entries: List<SubstanceLogEntity>,
        targetBedtimeMinutes: Int,
        nowMillis: Long? = null,
        model: DecayModelMode = DecayModelMode.ONSET_RAMP,
        steps: Int = 48
    ): DayConcentrationChartModel {
        val bedtimeMillis = DateTimeUtils.bedtimeMillisForDate(dateIso, targetBedtimeMinutes)
        val dayStartMillis = DateTimeUtils.parseIsoToCalendar(dateIso).timeInMillis + 6L * 3_600_000L
        val sortedEntries = entries.sortedBy { it.takenAtMillis }
        val windowStartMillis = minOf(
            dayStartMillis,
            sortedEntries.minOfOrNull { it.takenAtMillis } ?: dayStartMillis
        )
        val windowEndMillis = maxOf(
            bedtimeMillis + 4L * 3_600_000L,
            windowStartMillis + 18L * 3_600_000L
        )

        val extraTimestamps = buildList {
            add(bedtimeMillis)
            if (nowMillis != null) add(nowMillis)
            sortedEntries.forEach { entry ->
                if (entry.takenAtMillis > windowStartMillis) {
                    add(entry.takenAtMillis - 1L)
                }
                add(entry.takenAtMillis)
                if (model == DecayModelMode.ONSET_RAMP) {
                    val onsetMin = SubstanceCatalog.findById(entry.substanceId)?.peakOnsetMinutes ?: 30
                    add(entry.takenAtMillis + onsetMin.coerceAtLeast(5) * 60_000L)
                }
            }
        }

        val alignedTimestamps = buildSampleTimestamps(
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            steps = steps.coerceAtLeast(16),
            extraTimestamps = extraTimestamps
        )

        val seriesList = sortedEntries.mapIndexed { index, entry ->
            val safeDosage = if (entry.dosageAmount.isFinite()) entry.dosageAmount.coerceAtLeast(0.0) else 0.0
            val safeHalfLife = if (entry.halfLifeHours.isFinite()) entry.halfLifeHours.coerceIn(0.25, 200.0) else 4.0
            val onsetMin = SubstanceCatalog.findById(entry.substanceId)?.peakOnsetMinutes ?: 30

            val points = alignedTimestamps.map { ts ->
                val snap = calculateRemainingConcentration(
                    dosageAmount = safeDosage,
                    takenAtMillis = entry.takenAtMillis,
                    halfLifeHours = safeHalfLife,
                    targetMillis = ts,
                    peakOnsetMinutes = onsetMin,
                    model = model
                )
                ConcentrationPoint(
                    timestampMillis = ts,
                    hourOffsetFromStart = ((ts - windowStartMillis) / MS_PER_HOUR).toFloat(),
                    remainingAmount = snap.remainingAmount,
                    percentRemaining = snap.percentRemaining.toFloat()
                )
            }

            val bedtimeSnapshot = calculateRemainingConcentration(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                targetMillis = bedtimeMillis,
                peakOnsetMinutes = onsetMin,
                model = model
            )
            val nowSnapshot = nowMillis?.let {
                calculateRemainingConcentration(
                    dosageAmount = safeDosage,
                    takenAtMillis = entry.takenAtMillis,
                    halfLifeHours = safeHalfLife,
                    targetMillis = it,
                    peakOnsetMinutes = onsetMin,
                    model = model
                )
            }
            val clearedAtMillis = clearanceTimestampMillis(
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                peakOnsetMinutes = onsetMin,
                model = model
            )

            SubstanceConcentrationSeries(
                entry = entry,
                seriesColorIndex = index,
                peakOnsetMinutes = onsetMin,
                points = points,
                bedtimeSnapshot = bedtimeSnapshot,
                nowSnapshot = nowSnapshot,
                clearedAtMillis = clearedAtMillis
            )
        }

        val distinctNormalizedUnits = sortedEntries
            .map { it.dosageUnit.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        val sharedUnitLabel = if (distinctNormalizedUnits.size == 1 && sortedEntries.isNotEmpty()) {
            sortedEntries.first().dosageUnit.trim()
        } else {
            null
        }
        val combinedMaxSingleUnit = if (sharedUnitLabel != null) {
            seriesList.maxOfOrNull { s ->
                s.points.maxOfOrNull { it.remainingAmount } ?: 0.0
            }?.takeIf { it.isFinite() && it > 0.0 }
        } else {
            null
        }

        return DayConcentrationChartModel(
            dateIso = dateIso,
            decayModel = model,
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            bedtimeMillis = bedtimeMillis,
            series = seriesList,
            sharedUnitLabel = sharedUnitLabel,
            combinedMaxSingleUnit = combinedMaxSingleUnit
        )
    }

    private fun buildSampleTimestamps(
        windowStartMillis: Long,
        windowEndMillis: Long,
        steps: Int,
        extraTimestamps: List<Long>
    ): List<Long> {
        val span = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)
        val allTimes = LinkedHashSet<Long>(steps + extraTimestamps.size + 2)
        for (i in 0..steps) {
            allTimes.add(windowStartMillis + (span * i) / steps)
        }
        extraTimestamps.forEach { ts ->
            if (ts in windowStartMillis..windowEndMillis) {
                allTimes.add(ts)
            }
        }
        return allTimes.toList().sorted()
    }

    fun analyzeDay(
        dateIso: String,
        entries: List<SubstanceLogEntity>,
        targetBedtimeMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        decayModel: DecayModelMode = DecayModelMode.ONSET_RAMP
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

            val bedSnap = calculateRemainingConcentration(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                targetMillis = bedtimeMillis,
                peakOnsetMinutes = onsetMin,
                model = decayModel
            )
            val remAtBed = bedSnap.remainingAmount
            val pctAtBed = bedSnap.percentRemaining
            val remNow = calculateRemainingConcentration(
                dosageAmount = safeDosage,
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                targetMillis = nowMillis,
                peakOnsetMinutes = onsetMin,
                model = decayModel
            ).remainingAmount

            val hoursToClear = hoursUntilClearedFromNow(
                takenAtMillis = entry.takenAtMillis,
                halfLifeHours = safeHalfLife,
                peakOnsetMinutes = onsetMin,
                nowMillis = nowMillis,
                model = decayModel
            )

            val normalizedAtBed = (remAtBed / baseline) * safeWeight
            val hoursBeforeBed = (bedtimeMillis - entry.takenAtMillis) / MS_PER_HOUR
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
                    targetMillis = sampleMillis,
                    model = decayModel
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
            curveEndMillis = curveEnd,
            decayModel = decayModel
        )
    }

    /**
     * Pure in-memory preview showing how adding, editing, or moving an entry changes bedtime residue
     * and the day's overall Sleep Disruption Score before saving.
     */
    fun previewEditImpact(
        originalDateIso: String,
        originalDayEntries: List<SubstanceLogEntity>,
        targetDayEntries: List<SubstanceLogEntity>,
        editingEntry: SubstanceLogEntity?,
        candidateEntry: SubstanceLogEntity,
        targetBedtimeMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        decayModel: DecayModelMode = DecayModelMode.ONSET_RAMP
    ): EditImpactPreview {
        val beforeOriginalSummary = analyzeDay(
            dateIso = originalDateIso,
            entries = originalDayEntries,
            targetBedtimeMinutes = targetBedtimeMinutes,
            nowMillis = nowMillis,
            decayModel = decayModel
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
            nowMillis = nowMillis,
            decayModel = decayModel
        )
        val afterInfo = afterTargetSummary.entryInfos.lastOrNull {
            if (editingEntry != null) it.entry.id == editingEntry.id else it.entry.substanceName == candidateEntry.substanceName && it.entry.takenAtMillis == candidateEntry.takenAtMillis
        } ?: afterTargetSummary.entryInfos.last()

        val originalDayWithoutMoved = if (isMoved) {
            analyzeDay(
                dateIso = originalDateIso,
                entries = originalDayEntries.filterNot { it.id == editingEntry?.id },
                targetBedtimeMinutes = targetBedtimeMinutes,
                nowMillis = nowMillis,
                decayModel = decayModel
            ).disruptionScore
        } else {
            beforeOriginalSummary.disruptionScore
        }

        val beforeScoreForComparison = if (isMoved) {
            analyzeDay(
                dateIso = targetDate,
                entries = targetDayEntries,
                targetBedtimeMinutes = targetBedtimeMinutes,
                nowMillis = nowMillis,
                decayModel = decayModel
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
