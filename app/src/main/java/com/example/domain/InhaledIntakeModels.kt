package com.example.domain

import java.util.Locale
import kotlin.math.round

enum class CannabisIntakeMethod(
    val label: String,
    val substanceId: String,
    val entryTitle: String,
    val defaultHalfLifeHours: Double,
    val peakOnsetMinutes: Int
) {
    VAPED(
        label = "Vaped",
        substanceId = "cannabis_thc_vaped",
        entryTitle = "Cannabis (Vaped)",
        defaultHalfLifeHours = 4.5,
        peakOnsetMinutes = 10
    ),
    SMOKED(
        label = "Smoked",
        substanceId = "cannabis_thc_smoked",
        entryTitle = "Cannabis (Smoked)",
        defaultHalfLifeHours = 4.5,
        peakOnsetMinutes = 10
    ),
    DRANK_ATE(
        label = "Drank / Ate",
        substanceId = "cannabis_thc_edible",
        entryTitle = "Cannabis (Drank / Ate)",
        defaultHalfLifeHours = 8.0,
        peakOnsetMinutes = 75
    )
}

enum class CannabinoidType(
    val label: String,
    val shortLabel: String,
    val halfLifeHours: Double,
    val disruptionWeight: Double,
    val decarbFactor: Double
) {
    DELTA_9_THC("Delta-9 THC", "Δ9-THC", 4.5, 1.15, 1.0),
    DELTA_8_THC("Delta-8 THC", "Δ8-THC", 4.5, 0.85, 1.0),
    THCA("THCA", "THCA", 4.5, 1.05, 0.877),
    HHC("HHC", "HHC", 5.0, 0.95, 1.0),
    CBD("CBD", "CBD", 6.0, 0.20, 1.0),
    CBN("CBN", "CBN", 6.0, 0.35, 1.0),
    CBG("CBG", "CBG", 5.0, 0.45, 1.0),
    CUSTOM("Custom", "Custom", 5.0, 0.75, 1.0)
}

enum class CannabisQuantityMode(val label: String) {
    PUFFS("Puffs / Hits"),
    GRAMS("Grams (g)")
}

data class CannabinoidEntry(
    val rowId: Long,
    val type: CannabinoidType = CannabinoidType.DELTA_9_THC,
    val customName: String = "",
    val percentText: String = "80"
) {
    fun resolvedDisplayName(): String {
        return if (type == CannabinoidType.CUSTOM) {
            customName
                .replace(Regex("[\\[\\]·,]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(20)
                .ifBlank { "Custom Cannabinoid" }
        } else {
            type.shortLabel
        }
    }
}

data class CannabinoidDoseBreakdown(
    val displayName: String,
    val percent: Double,
    val estimatedMg: Double
)

data class CannabisSessionEstimate(
    val totalActiveMg: Double,
    val weightedHalfLifeHours: Double,
    val weightedDisruptionWeight: Double,
    val peakOnsetMinutes: Int,
    val breakdown: List<CannabinoidDoseBreakdown>,
    val totalPercent: Double
)

enum class NicotineDeliveryMethod(
    val label: String,
    val substanceId: String
) {
    SMOKED_TOBACCO("Smoked Tobacco", "nicotine_smoked_tobacco"),
    VAPED_NICOTINE("Vaped Nicotine", "nicotine_vape")
}

enum class SmokedTobaccoType(
    val label: String,
    val shortLabel: String,
    val mgPerUnit: Double,
    val pluralUnit: String
) {
    ULTRA_LIGHT_CIG("Ultra Light Cigarette", "Ultra Light Cig", 0.6, "cigs"),
    LIGHT_CIG("Light / Gold Cigarette", "Light Cig", 0.9, "cigs"),
    FULL_FLAVOR_CIG("Full Flavor / Red Cigarette", "Full Flavor Cig", 1.3, "cigs"),
    CIGARILLO("Cigarillo / Blunt", "Cigarillo", 2.0, "cigarillos"),
    CIGAR("Cigar", "Cigar", 5.0, "cigars"),
    PIPE_RYO("Pipe / Roll-Your-Own", "Pipe / RYO", 1.5, "bowls")
}

enum class VapeStrengthUnit(val label: String, val suffix: String) {
    PERCENT("% Nicotine", "%"),
    MG_PER_ML("mg/mL", "mg/mL")
}

data class NicotineSessionEstimate(
    val absorbedMg: Double,
    val halfLifeHours: Double,
    val disruptionWeight: Double,
    val mgPerMlEquivalent: Double? = null
)

data class ParsedCannabisEditState(
    val method: CannabisIntakeMethod,
    val quantityMode: CannabisQuantityMode,
    val quantityText: String,
    val puffLengthSecondsText: String,
    val cannabinoids: List<CannabinoidEntry>
)

data class ParsedNicotineEditState(
    val method: NicotineDeliveryMethod,
    val smokedType: SmokedTobaccoType,
    val smokedCountText: String,
    val vapeStrengthUnit: VapeStrengthUnit,
    val vapeStrengthText: String,
    val vapePuffsText: String,
    val vapePuffLengthSecondsText: String
)

object InhaledIntakeCalculator {

    const val BASELINE_PUFF_SECONDS = 3.0
    const val MIN_PUFF_SECONDS = 0.5
    const val MAX_PUFF_SECONDS = 15.0

    // Effective active extract/flower mass transferred per standard 3-second puff
    private const val VAPE_EFFECTIVE_MG_PER_3S_PUFF = 3.15
    private const val SMOKE_EFFECTIVE_MG_PER_3S_PUFF = 12.25

    // Transfer efficiency when logging by weight in grams
    private const val VAPE_GRAM_TRANSFER_EFFICIENCY = 0.45
    private const val SMOKE_GRAM_TRANSFER_EFFICIENCY = 0.25

    // Absorbed e-liquid equivalent (mL) per standard 3-second nicotine vape puff (0.004 mL * 60% retention)
    private const val NICOTINE_VAPE_ABSORBED_ML_PER_3S_PUFF = 0.0024

    fun estimateCannabisSession(
        method: CannabisIntakeMethod,
        quantityMode: CannabisQuantityMode,
        quantityValue: Double,
        puffLengthSeconds: Double,
        cannabinoids: List<CannabinoidEntry>
    ): CannabisSessionEstimate {
        if (method == CannabisIntakeMethod.DRANK_ATE) {
            return CannabisSessionEstimate(
                totalActiveMg = quantityValue.takeIf { it.isFinite() && it > 0.0 } ?: 10.0,
                weightedHalfLifeHours = method.defaultHalfLifeHours,
                weightedDisruptionWeight = 1.2,
                peakOnsetMinutes = method.peakOnsetMinutes,
                breakdown = emptyList(),
                totalPercent = 0.0
            )
        }

        val safeQty = quantityValue.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val safePuffSec = puffLengthSeconds
            .takeIf { it.isFinite() && it > 0.0 }
            ?.coerceIn(MIN_PUFF_SECONDS, MAX_PUFF_SECONDS)
            ?: BASELINE_PUFF_SECONDS
        val durationFactor = safePuffSec / BASELINE_PUFF_SECONDS

        val effectiveMaterialMg = when (quantityMode) {
            CannabisQuantityMode.PUFFS -> {
                val per3s = if (method == CannabisIntakeMethod.VAPED) {
                    VAPE_EFFECTIVE_MG_PER_3S_PUFF
                } else {
                    SMOKE_EFFECTIVE_MG_PER_3S_PUFF
                }
                safeQty * per3s * durationFactor
            }
            CannabisQuantityMode.GRAMS -> {
                val efficiency = if (method == CannabisIntakeMethod.VAPED) {
                    VAPE_GRAM_TRANSFER_EFFICIENCY
                } else {
                    SMOKE_GRAM_TRANSFER_EFFICIENCY
                }
                safeQty * 1000.0 * efficiency
            }
        }

        var totalPercent = 0.0
        var totalMg = 0.0
        var weightedHalfLifeSum = 0.0
        var weightedDisruptionSum = 0.0
        val breakdown = mutableListOf<CannabinoidDoseBreakdown>()

        for (row in cannabinoids) {
            val pct = row.percentText.trim().replace(',', '.').toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.coerceAtMost(100.0)
                ?: continue
            totalPercent += pct
            val estMg = roundToTwoDecimals(effectiveMaterialMg * (pct / 100.0) * row.type.decarbFactor)
            totalMg += estMg
            weightedHalfLifeSum += estMg * row.type.halfLifeHours
            weightedDisruptionSum += estMg * row.type.disruptionWeight
            breakdown.add(
                CannabinoidDoseBreakdown(
                    displayName = row.resolvedDisplayName(),
                    percent = pct,
                    estimatedMg = estMg
                )
            )
        }

        val finalTotalMg = roundToTwoDecimals(totalMg)
        val weightedHalfLife = if (totalMg > 0.0) {
            roundToOneDecimal(weightedHalfLifeSum / totalMg)
        } else {
            method.defaultHalfLifeHours
        }
        val weightedDisruption = if (totalMg > 0.0) {
            roundToTwoDecimals((weightedDisruptionSum / totalMg).coerceIn(0.15, 1.4))
        } else {
            1.08
        }

        return CannabisSessionEstimate(
            totalActiveMg = finalTotalMg,
            weightedHalfLifeHours = weightedHalfLife,
            weightedDisruptionWeight = weightedDisruption,
            peakOnsetMinutes = method.peakOnsetMinutes,
            breakdown = breakdown,
            totalPercent = roundToOneDecimal(totalPercent)
        )
    }

    fun estimateSmokedTobacco(
        type: SmokedTobaccoType,
        count: Double
    ): NicotineSessionEstimate {
        val safeCount = count.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val mg = roundToTwoDecimals(safeCount * type.mgPerUnit)
        return NicotineSessionEstimate(
            absorbedMg = mg,
            halfLifeHours = 2.0,
            disruptionWeight = 1.18
        )
    }

    fun estimateVapedNicotine(
        strengthValue: Double,
        strengthUnit: VapeStrengthUnit,
        puffs: Double,
        puffLengthSeconds: Double
    ): NicotineSessionEstimate {
        val safeStrength = strengthValue.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val mgPerMl = when (strengthUnit) {
            VapeStrengthUnit.PERCENT -> safeStrength * 10.0
            VapeStrengthUnit.MG_PER_ML -> safeStrength
        }.coerceAtMost(100.0)

        val safePuffs = puffs.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val safePuffSec = puffLengthSeconds
            .takeIf { it.isFinite() && it > 0.0 }
            ?.coerceIn(MIN_PUFF_SECONDS, MAX_PUFF_SECONDS)
            ?: BASELINE_PUFF_SECONDS
        val durationFactor = safePuffSec / BASELINE_PUFF_SECONDS

        val mg = roundToTwoDecimals(
            mgPerMl * safePuffs * NICOTINE_VAPE_ABSORBED_ML_PER_3S_PUFF * durationFactor
        )
        return NicotineSessionEstimate(
            absorbedMg = mg,
            halfLifeHours = 2.0,
            disruptionWeight = 1.15,
            mgPerMlEquivalent = roundToOneDecimal(mgPerMl)
        )
    }

    private fun roundToTwoDecimals(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return round(value * 100.0) / 100.0
    }

    private fun roundToOneDecimal(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return round(value * 10.0) / 10.0
    }
}

object StructuredSessionMetadata {

    private const val CANNABIS_PREFIX = "[Cannabis:"
    private const val NICOTINE_PREFIX = "[Nicotine:"

    private val CANNABIS_IDS = setOf(
        "cannabis_thc",
        "cannabis_thc_vaped",
        "cannabis_thc_smoked",
        "cannabis_thc_edible"
    )

    private val NICOTINE_BUILDER_IDS = setOf(
        "nicotine_smoked_tobacco",
        "nicotine_vape"
    )

    fun isCannabisSubstanceId(id: String): Boolean = id in CANNABIS_IDS

    fun isNicotineBuilderSubstanceId(id: String): Boolean = id in NICOTINE_BUILDER_IDS

    fun defaultCannabisMethodForId(id: String): CannabisIntakeMethod = when (id) {
        "cannabis_thc_smoked" -> CannabisIntakeMethod.SMOKED
        "cannabis_thc_edible" -> CannabisIntakeMethod.DRANK_ATE
        else -> CannabisIntakeMethod.VAPED
    }

    fun defaultNicotineMethodForId(id: String): NicotineDeliveryMethod = when (id) {
        "nicotine_smoked_tobacco" -> NicotineDeliveryMethod.SMOKED_TOBACCO
        else -> NicotineDeliveryMethod.VAPED_NICOTINE
    }

    private const val MAX_NOTES_LENGTH = 300
    private const val MAX_STRUCTURED_TAG_LENGTH = 220

    fun formatCannabisTag(
        method: CannabisIntakeMethod,
        quantityMode: CannabisQuantityMode,
        quantityText: String,
        puffLengthSecondsText: String,
        cannabinoids: List<CannabinoidEntry>,
        oralDoseText: String
    ): String {
        if (method == CannabisIntakeMethod.DRANK_ATE) {
            val cleanDose = oralDoseText.trim().take(16).ifBlank { "10" }
            return "$CANNABIS_PREFIX ${method.label} · $cleanDose mg]"
        }
        val cleanQty = quantityText.trim().take(16).ifBlank { "1" }
        val amountSegment = when (quantityMode) {
            CannabisQuantityMode.PUFFS -> {
                val sec = puffLengthSecondsText.trim().take(12).ifBlank { "3" }
                "$cleanQty puffs (${sec}s)"
            }
            CannabisQuantityMode.GRAMS -> "$cleanQty g"
        }
        val basePrefix = "$CANNABIS_PREFIX ${method.label} · $amountSegment"
        val fittedTokens = mutableListOf<String>()
        for (row in cannabinoids) {
            val pct = row.percentText.trim().take(10)
            if (pct.isBlank() || pct.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false) {
                continue
            }
            val token = "${row.resolvedDisplayName()} $pct%"
            val candidateSegment = (fittedTokens + token).joinToString(", ")
            if (basePrefix.length + 3 + candidateSegment.length + 1 <= MAX_STRUCTURED_TAG_LENGTH) {
                fittedTokens.add(token)
            } else {
                break
            }
        }
        val cannSegment = fittedTokens.joinToString(", ")

        return if (cannSegment.isNotBlank()) {
            "$basePrefix · $cannSegment]"
        } else {
            "$basePrefix]"
        }
    }

    fun formatNicotineTag(
        method: NicotineDeliveryMethod,
        smokedType: SmokedTobaccoType,
        smokedCountText: String,
        vapeStrengthUnit: VapeStrengthUnit,
        vapeStrengthText: String,
        vapePuffsText: String,
        vapePuffLengthSecondsText: String
    ): String {
        return when (method) {
            NicotineDeliveryMethod.SMOKED_TOBACCO -> {
                val count = smokedCountText.trim().take(16).ifBlank { "1" }
                "$NICOTINE_PREFIX Smoked Tobacco · ${smokedType.shortLabel} × $count]"
            }
            NicotineDeliveryMethod.VAPED_NICOTINE -> {
                val strength = vapeStrengthText.trim().take(16).ifBlank { "5" }
                val strengthPart = when (vapeStrengthUnit) {
                    VapeStrengthUnit.PERCENT -> {
                        val equiv = strength.replace(',', '.').toDoubleOrNull()?.times(10.0)
                        val equivStr = equiv?.let { " (${SubstanceCatalog.formatDoseChipNumber(it)} mg/mL)" }.orEmpty()
                        "$strength%$equivStr"
                    }
                    VapeStrengthUnit.MG_PER_ML -> "$strength mg/mL"
                }
                val puffs = vapePuffsText.trim().take(16).ifBlank { "10" }
                val sec = vapePuffLengthSecondsText.trim().take(12).ifBlank { "3" }
                "$NICOTINE_PREFIX Vaped · $strengthPart · $puffs puffs (${sec}s)]"
            }
        }
    }

    fun mergeTagWithUserNotes(
        tag: String,
        userNotes: String,
        maxTotalLength: Int = MAX_NOTES_LENGTH
    ): String {
        val safeTag = tag.trim()
        val cleanNotes = stripStructuredTag(userNotes)
            .replace(Regex("[\\p{Cntrl}&&[^\t ]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanNotes.isEmpty()) return safeTag.take(maxTotalLength)
        val remainingBudget = (maxTotalLength - safeTag.length - 1).coerceAtLeast(0)
        if (remainingBudget <= 0) return safeTag.take(maxTotalLength)
        val fittedNotes = cleanNotes.take(remainingBudget).trim()
        return if (fittedNotes.isEmpty()) safeTag else "$safeTag\n$fittedNotes"
    }

    /**
     * Proportionally scales puff count, gram weight, or smoked tobacco count inside structured
     * session notes when the user taps inline +/- dose quick-step controls on a logged card.
     */
    fun scaleQuantityForDoseRatio(
        substanceId: String,
        fullNotes: String,
        oldDose: Double,
        newDose: Double
    ): String {
        if (!oldDose.isFinite() || !newDose.isFinite() || oldDose <= 0.0 || newDose <= 0.0) {
            return fullNotes
        }
        val trimmed = fullNotes.trim()
        val userNotes = stripStructuredTag(fullNotes)

        if (trimmed.startsWith(CANNABIS_PREFIX)) {
            val state = parseCannabisStateFromNotes(substanceId, fullNotes) ?: return fullNotes
            if (state.method == CannabisIntakeMethod.DRANK_ATE) {
                val updatedTag = formatCannabisTag(
                    method = state.method,
                    quantityMode = state.quantityMode,
                    quantityText = state.quantityText,
                    puffLengthSecondsText = state.puffLengthSecondsText,
                    cannabinoids = state.cannabinoids,
                    oralDoseText = formatCompactDecimal(newDose)
                )
                return mergeTagWithUserNotes(updatedTag, userNotes)
            }
            val oldQty = state.quantityText.replace(',', '.').toDoubleOrNull() ?: return fullNotes
            if (oldQty <= 0.0) return fullNotes
            val ratio = newDose / oldDose
            val minQty = if (state.quantityMode == CannabisQuantityMode.GRAMS) 0.01 else 0.25
            val scaledQty = (oldQty * ratio).coerceIn(minQty, 10_000.0)
            val updatedTag = formatCannabisTag(
                method = state.method,
                quantityMode = state.quantityMode,
                quantityText = formatCompactDecimal(scaledQty),
                puffLengthSecondsText = state.puffLengthSecondsText,
                cannabinoids = state.cannabinoids,
                oralDoseText = formatCompactDecimal(newDose)
            )
            return mergeTagWithUserNotes(updatedTag, userNotes)
        }

        if (trimmed.startsWith(NICOTINE_PREFIX)) {
            val state = parseNicotineStateFromNotes(substanceId, fullNotes) ?: return fullNotes
            val ratio = newDose / oldDose
            return when (state.method) {
                NicotineDeliveryMethod.SMOKED_TOBACCO -> {
                    val oldCount = state.smokedCountText.replace(',', '.').toDoubleOrNull() ?: return fullNotes
                    if (oldCount <= 0.0) return fullNotes
                    val scaledCount = (oldCount * ratio).coerceIn(0.1, 1_000.0)
                    val updatedTag = formatNicotineTag(
                        method = state.method,
                        smokedType = state.smokedType,
                        smokedCountText = formatCompactDecimal(scaledCount),
                        vapeStrengthUnit = state.vapeStrengthUnit,
                        vapeStrengthText = state.vapeStrengthText,
                        vapePuffsText = state.vapePuffsText,
                        vapePuffLengthSecondsText = state.vapePuffLengthSecondsText
                    )
                    mergeTagWithUserNotes(updatedTag, userNotes)
                }
                NicotineDeliveryMethod.VAPED_NICOTINE -> {
                    val oldPuffs = state.vapePuffsText.replace(',', '.').toDoubleOrNull() ?: return fullNotes
                    if (oldPuffs <= 0.0) return fullNotes
                    val scaledPuffs = (oldPuffs * ratio).coerceIn(0.25, 10_000.0)
                    val updatedTag = formatNicotineTag(
                        method = state.method,
                        smokedType = state.smokedType,
                        smokedCountText = state.smokedCountText,
                        vapeStrengthUnit = state.vapeStrengthUnit,
                        vapeStrengthText = state.vapeStrengthText,
                        vapePuffsText = formatCompactDecimal(scaledPuffs),
                        vapePuffLengthSecondsText = state.vapePuffLengthSecondsText
                    )
                    mergeTagWithUserNotes(updatedTag, userNotes)
                }
            }
        }

        return fullNotes
    }

    fun stripStructuredTag(fullNotes: String): String {
        val trimmed = fullNotes.trim()
        if (!trimmed.startsWith(CANNABIS_PREFIX) && !trimmed.startsWith(NICOTINE_PREFIX)) {
            return fullNotes
        }
        val closeIdx = trimmed.indexOf(']')
        if (closeIdx < 0) return fullNotes
        return trimmed.substring(closeIdx + 1).trimStart('\n', '\r', ' ')
    }

    /**
     * Splits an entry's notes into compact UI breakdown pills and remaining plain user notes.
     */
    fun extractBreakdownPillsAndUserNotes(fullNotes: String): Pair<List<String>, String> {
        val trimmed = fullNotes.trim()
        val isCannabis = trimmed.startsWith(CANNABIS_PREFIX)
        val isNicotine = trimmed.startsWith(NICOTINE_PREFIX)
        if (!isCannabis && !isNicotine) {
            return emptyList<String>() to fullNotes
        }
        val closeIdx = trimmed.indexOf(']')
        if (closeIdx < 0) return emptyList<String>() to fullNotes

        val prefixLen = if (isCannabis) CANNABIS_PREFIX.length else NICOTINE_PREFIX.length
        val inner = trimmed.substring(prefixLen, closeIdx).trim()
        val userNotes = trimmed.substring(closeIdx + 1).trim()

        val parts = inner.split("·").map { it.trim() }.filter { it.isNotEmpty() }
        val pills = mutableListOf<String>()
        if (isCannabis) {
            // Merge method + amount as first pill, then split comma-separated cannabinoids into individual pills
            if (parts.size >= 2) {
                pills.add("${parts[0]} · ${parts[1]}")
            } else if (parts.isNotEmpty()) {
                pills.add(parts[0])
            }
            if (parts.size >= 3) {
                parts[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    pills.add(it)
                }
            }
        } else {
            parts.forEach { pills.add(it) }
        }
        return pills to userNotes
    }

    fun parseCannabisStateFromNotes(substanceId: String, fullNotes: String): ParsedCannabisEditState? {
        val trimmed = fullNotes.trim()
        if (!trimmed.startsWith(CANNABIS_PREFIX)) return null
        val closeIdx = trimmed.indexOf(']')
        if (closeIdx < 0) return null
        val inner = trimmed.substring(CANNABIS_PREFIX.length, closeIdx).trim()
        val parts = inner.split("·").map { it.trim() }
        if (parts.isEmpty()) return null

        val method = CannabisIntakeMethod.entries.find {
            it.label.equals(parts[0], ignoreCase = true)
        } ?: defaultCannabisMethodForId(substanceId)

        var qtyMode = if (method == CannabisIntakeMethod.SMOKED) CannabisQuantityMode.GRAMS else CannabisQuantityMode.PUFFS
        var qtyText = if (qtyMode == CannabisQuantityMode.PUFFS) "3" else "0.25"
        var puffSecText = "3"

        val amountPart = parts.getOrNull(1).orEmpty()
        val puffMatch = Regex("""(\d+(?:\.\d+)?)\s*puffs?(?:\s*\((\d+(?:\.\d+)?)s\))?""", RegexOption.IGNORE_CASE)
            .find(amountPart)
        val gramMatch = Regex("""(\d+(?:\.\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
            .find(amountPart)

        when {
            puffMatch != null -> {
                qtyMode = CannabisQuantityMode.PUFFS
                qtyText = puffMatch.groupValues[1]
                puffSecText = puffMatch.groupValues[2].ifBlank { "3" }
            }
            gramMatch != null -> {
                qtyMode = CannabisQuantityMode.GRAMS
                qtyText = gramMatch.groupValues[1]
            }
        }

        val cannPart = parts.getOrNull(2).orEmpty()
        val parsedCannabinoids = cannPart.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexedNotNull { idx, token ->
                val match = Regex("""^(.+?)\s+(\d+(?:\.\d+)?)%$""").find(token) ?: return@mapIndexedNotNull null
                val name = match.groupValues[1].trim()
                val pct = match.groupValues[2].trim()
                val matchedType = CannabinoidType.entries.find {
                    it != CannabinoidType.CUSTOM &&
                        (it.shortLabel.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true))
                }
                if (matchedType != null) {
                    CannabinoidEntry(rowId = idx.toLong() + 1L, type = matchedType, percentText = pct)
                } else {
                    CannabinoidEntry(
                        rowId = idx.toLong() + 1L,
                        type = CannabinoidType.CUSTOM,
                        customName = name,
                        percentText = pct
                    )
                }
            }
            .ifEmpty {
                listOf(
                    CannabinoidEntry(
                        rowId = 1L,
                        type = CannabinoidType.DELTA_9_THC,
                        percentText = if (method == CannabisIntakeMethod.SMOKED) "22" else "80"
                    )
                )
            }

        return ParsedCannabisEditState(
            method = method,
            quantityMode = qtyMode,
            quantityText = qtyText,
            puffLengthSecondsText = puffSecText,
            cannabinoids = parsedCannabinoids
        )
    }

    fun parseNicotineStateFromNotes(substanceId: String, fullNotes: String): ParsedNicotineEditState? {
        val trimmed = fullNotes.trim()
        if (!trimmed.startsWith(NICOTINE_PREFIX)) return null
        val closeIdx = trimmed.indexOf(']')
        if (closeIdx < 0) return null
        val inner = trimmed.substring(NICOTINE_PREFIX.length, closeIdx).trim()
        val parts = inner.split("·").map { it.trim() }
        if (parts.isEmpty()) return null

        val isSmoked = parts[0].contains("Smoked", ignoreCase = true)
        return if (isSmoked) {
            val detail = parts.getOrNull(1).orEmpty()
            val match = Regex("""^(.+?)\s*[×x]\s*(\d+(?:\.\d+)?)$""").find(detail)
            val label = match?.groupValues?.get(1)?.trim().orEmpty()
            val count = match?.groupValues?.get(2)?.trim().orEmpty().ifBlank { "1" }
            val smokedType = SmokedTobaccoType.entries.find {
                it.shortLabel.equals(label, ignoreCase = true) || it.label.equals(label, ignoreCase = true)
            } ?: SmokedTobaccoType.FULL_FLAVOR_CIG
            ParsedNicotineEditState(
                method = NicotineDeliveryMethod.SMOKED_TOBACCO,
                smokedType = smokedType,
                smokedCountText = count,
                vapeStrengthUnit = VapeStrengthUnit.PERCENT,
                vapeStrengthText = "5",
                vapePuffsText = "10",
                vapePuffLengthSecondsText = "3"
            )
        } else {
            val strengthPart = parts.getOrNull(1).orEmpty()
            val pctMatch = Regex("""(\d+(?:\.\d+)?)%""").find(strengthPart)
            val mgMlMatch = Regex("""(\d+(?:\.\d+)?)\s*mg/mL""", RegexOption.IGNORE_CASE).find(strengthPart)
            val (unit, strengthText) = when {
                pctMatch != null -> VapeStrengthUnit.PERCENT to pctMatch.groupValues[1]
                mgMlMatch != null -> VapeStrengthUnit.MG_PER_ML to mgMlMatch.groupValues[1]
                else -> VapeStrengthUnit.PERCENT to "5"
            }
            val puffPart = parts.getOrNull(2).orEmpty()
            val puffMatch = Regex("""(\d+(?:\.\d+)?)\s*puffs?(?:\s*\((\d+(?:\.\d+)?)s\))?""", RegexOption.IGNORE_CASE)
                .find(puffPart)
            ParsedNicotineEditState(
                method = NicotineDeliveryMethod.VAPED_NICOTINE,
                smokedType = SmokedTobaccoType.FULL_FLAVOR_CIG,
                smokedCountText = "1",
                vapeStrengthUnit = unit,
                vapeStrengthText = strengthText,
                vapePuffsText = puffMatch?.groupValues?.get(1)?.ifBlank { "10" } ?: "10",
                vapePuffLengthSecondsText = puffMatch?.groupValues?.get(2)?.ifBlank { "3" } ?: "3"
            )
        }
    }

    fun formatCompactDecimal(value: Double): String {
        if (!value.isFinite()) return "0"
        val rounded = round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }
}
