package com.example.domain

import android.content.Context
import com.example.R
import org.json.JSONObject

enum class SubstanceCategory(val label: String) {
    STIMULANT("Stimulant"),
    DEPRESSANT("Depressant"),
    MEDICATION("Medication"),
    METABOLIC("Metabolic / Meal"),
    CUSTOM("Custom")
}

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
    val mechanismSummary: String
)

object SubstanceCatalog {

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
                val catName = obj.optString("category", "CUSTOM").uppercase()
                val category = SubstanceCategory.entries.find { it.name == catName }
                    ?: SubstanceCategory.CUSTOM
                val aliasesArr = obj.optJSONArray("aliases")
                val aliases = buildList {
                    if (aliasesArr != null) {
                        for (a in 0 until aliasesArr.length()) {
                            add(aliasesArr.optString(a).lowercase())
                        }
                    }
                }
                add(
                    SubstanceReference(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        aliases = aliases,
                        category = category,
                        defaultUnit = obj.getString("defaultUnit"),
                        defaultDosage = obj.getDouble("defaultDosage"),
                        referenceBaselineDose = obj.getDouble("referenceBaselineDose"),
                        halfLifeHours = obj.getDouble("halfLifeHours"),
                        peakOnsetMinutes = obj.getInt("peakOnsetMinutes"),
                        disruptionWeight = obj.getDouble("disruptionWeight"),
                        mechanismSummary = obj.getString("sleepArchitectureEffect")
                    )
                )
            }
        }
    }

    fun findById(id: String): SubstanceReference? = loadedItems.find { it.id == id }

    fun search(query: String, categoryFilter: SubstanceCategory? = null): List<SubstanceReference> {
        val trimmed = query.trim().lowercase()
        return loadedItems.filter { item ->
            val matchesCategory = categoryFilter == null || item.category == categoryFilter
            val matchesQuery = trimmed.isEmpty() ||
                item.name.lowercase().contains(trimmed) ||
                item.category.label.lowercase().contains(trimmed) ||
                item.aliases.any { it.contains(trimmed) }
            matchesCategory && matchesQuery
        }
    }

    private fun defaultFallbackItems(): List<SubstanceReference> = listOf(
        SubstanceReference(
            id = "caffeine_coffee",
            name = "Caffeine (Coffee)",
            aliases = listOf("coffee", "espresso", "latte", "tea"),
            category = SubstanceCategory.STIMULANT,
            defaultUnit = "mg",
            defaultDosage = 95.0,
            referenceBaselineDose = 100.0,
            halfLifeHours = 5.0,
            peakOnsetMinutes = 35,
            disruptionWeight = 1.0,
            mechanismSummary = "Competitive A1/A2A adenosine receptor antagonism masks sleep pressure and delays slow-wave sleep."
        )
    )
}
