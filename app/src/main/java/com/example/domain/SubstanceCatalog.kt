package com.example.domain

import android.content.Context
import com.example.R
import org.json.JSONObject
import java.util.Locale

enum class SubstanceCategory(val label: String) {
    STIMULANT("Stimulant"),
    DEPRESSANT("Depressant"),
    MEDICATION("Medication"),
    METABOLIC("Metabolic / Meal"),
    CUSTOM("Custom")
}

data class DoseOption(
    val amount: Double,
    val unit: String,
    val label: String,
    val form: String = ""
)

data class SubstanceReference(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val category: SubstanceCategory,
    val defaultUnit: String,
    val defaultDosage: Double,
    val referenceBaselineDose: Double,
    val halfLifeHours: Double,
    val peakOnsetMinutes: Int,
    val disruptionWeight: Double,
    val mechanismSummary: String,
    val genericName: String = "",
    val formulation: String = "",
    val doseOptions: List<DoseOption> = emptyList()
)

object SubstanceCatalog {

    private val LEGACY_ID_ALIASES = mapOf(
        "corticosteroids" to "prednisone",
        "beta_blockers" to "propranolol_ir",
        "cannabis_thc" to "cannabis_thc_vaped"
    )

    @Volatile
    private var loadedItems: List<SubstanceReference> = defaultFallbackItems()

    val items: List<SubstanceReference>
        get() = loadedItems

    /**
     * Loads the pre-populated JSON reference dataset from res/raw/substances_reference.json.
     */
    fun initializeFromResources(context: Context) {
        runCatching {
            val jsonText = context.resources
                .openRawResource(R.raw.substances_reference)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val parsed = parseFromJson(jsonText)
            if (parsed.isNotEmpty()) {
                loadedItems = parsed
            }
        }
    }

    fun parseFromJson(jsonText: String): List<SubstanceReference> {
        val root = JSONObject(jsonText)
        val arr = root.getJSONArray("substances")
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val catName = obj.optString("category", "CUSTOM").uppercase(Locale.US)
                val category = SubstanceCategory.entries.find { it.name == catName }
                    ?: SubstanceCategory.CUSTOM
                val aliasesArr = obj.optJSONArray("aliases")
                val aliases = buildList {
                    if (aliasesArr != null) {
                        for (a in 0 until aliasesArr.length()) {
                            add(aliasesArr.optString(a).lowercase(Locale.US))
                        }
                    }
                }
                val defaultUnit = obj.getString("defaultUnit")
                val defaultDosage = obj.getDouble("defaultDosage")
                val formulation = obj.optString("formulation", "").trim()
                val genericName = obj.optString("genericName", "").trim()

                val commonDosesArr = obj.optJSONArray("commonDoses")
                val doseOptions = buildList {
                    if (commonDosesArr != null && commonDosesArr.length() > 0) {
                        for (d in 0 until commonDosesArr.length()) {
                            val doseVal = commonDosesArr.optDouble(d, Double.NaN)
                            if (doseVal.isFinite() && doseVal > 0.0) {
                                add(
                                    DoseOption(
                                        amount = doseVal,
                                        unit = defaultUnit,
                                        label = "${formatDoseChipNumber(doseVal)} $defaultUnit",
                                        form = formulation
                                    )
                                )
                            }
                        }
                    } else if (defaultDosage.isFinite() && defaultDosage > 0.0) {
                        add(
                            DoseOption(
                                amount = defaultDosage,
                                unit = defaultUnit,
                                label = "${formatDoseChipNumber(defaultDosage)} $defaultUnit",
                                form = formulation
                            )
                        )
                    }
                }

                add(
                    SubstanceReference(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        aliases = aliases,
                        category = category,
                        defaultUnit = defaultUnit,
                        defaultDosage = defaultDosage,
                        referenceBaselineDose = obj.getDouble("referenceBaselineDose"),
                        halfLifeHours = obj.getDouble("halfLifeHours"),
                        peakOnsetMinutes = obj.getInt("peakOnsetMinutes"),
                        disruptionWeight = obj.getDouble("disruptionWeight"),
                        mechanismSummary = obj.getString("sleepArchitectureEffect"),
                        genericName = genericName,
                        formulation = formulation,
                        doseOptions = doseOptions
                    )
                )
            }
        }
    }

    fun findById(id: String): SubstanceReference? {
        val direct = loadedItems.find { it.id == id }
        if (direct != null) return direct
        val mapped = LEGACY_ID_ALIASES[id] ?: return null
        return loadedItems.find { it.id == mapped }
    }

    /**
     * Attempts to find a curated reference by exact or close medication name / generic name,
     * used to supply verified pharmacokinetics when a live API drug matches a curated formulation.
     */
    fun findByNameOrGeneric(drugName: String): SubstanceReference? {
        val normalized = drugName.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return null
        return loadedItems.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            ?: loadedItems.firstOrNull {
                it.genericName.isNotBlank() && it.genericName.equals(normalized, ignoreCase = true)
            }
            ?: loadedItems.firstOrNull { it.aliases.any { alias -> alias == normalized } }
    }

    fun search(query: String, categoryFilter: SubstanceCategory? = null): List<SubstanceReference> {
        val trimmed = query.trim().lowercase(Locale.US)
        val filtered = loadedItems.filter { item ->
            val matchesCategory = categoryFilter == null || item.category == categoryFilter
            val matchesQuery = trimmed.isEmpty() ||
                item.name.lowercase(Locale.US).contains(trimmed) ||
                item.genericName.lowercase(Locale.US).contains(trimmed) ||
                item.formulation.lowercase(Locale.US).contains(trimmed) ||
                item.category.label.lowercase(Locale.US).contains(trimmed) ||
                item.aliases.any { it.contains(trimmed) }
            matchesCategory && matchesQuery
        }
        if (trimmed.isEmpty()) return filtered

        return filtered.sortedBy { item ->
            val lowerName = item.name.lowercase(Locale.US)
            val lowerGeneric = item.genericName.lowercase(Locale.US)
            when {
                lowerName == trimmed -> 0
                lowerName.startsWith(trimmed) -> 1
                item.aliases.any { it == trimmed } -> 2
                lowerGeneric.startsWith(trimmed) -> 3
                item.aliases.any { it.startsWith(trimmed) } -> 4
                lowerName.contains(trimmed) -> 5
                else -> 6
            }
        }
    }

    internal fun formatDoseChipNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    private fun defaultFallbackItems(): List<SubstanceReference> = listOf(
        SubstanceReference(
            id = "caffeine_coffee",
            name = "Drip Coffee",
            aliases = listOf("coffee", "espresso", "latte", "tea"),
            category = SubstanceCategory.STIMULANT,
            defaultUnit = "mg",
            defaultDosage = 95.0,
            referenceBaselineDose = 100.0,
            halfLifeHours = 5.0,
            peakOnsetMinutes = 35,
            disruptionWeight = 1.0,
            mechanismSummary = "Competitive A1/A2A adenosine receptor antagonism masks sleep pressure and delays slow-wave sleep.",
            genericName = "Caffeine",
            formulation = "Brewed Cup",
            doseOptions = listOf(
                DoseOption(45.0, "mg", "45 mg", "Brewed Cup"),
                DoseOption(95.0, "mg", "95 mg", "Brewed Cup"),
                DoseOption(140.0, "mg", "140 mg", "Brewed Cup"),
                DoseOption(190.0, "mg", "190 mg", "Brewed Cup")
            )
        )
    )
}
