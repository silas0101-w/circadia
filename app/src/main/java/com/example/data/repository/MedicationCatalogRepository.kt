package com.example.data.repository

import android.content.Context
import com.example.data.remote.HalfLifeSource
import com.example.data.remote.MedicationLookupService
import com.example.data.remote.OpenFdaPharmacokinetics
import com.example.data.remote.RemoteMedicationItem
import com.example.domain.DoseOption
import com.example.domain.SubstanceCatalog
import com.example.domain.SubstanceCategory
import com.example.domain.SubstanceReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class MedicationSearchResult(
    val curatedMatches: List<SubstanceReference>,
    val remoteMatches: List<RemoteMedicationItem>,
    val fromOfflineCache: Boolean,
    val networkUnavailable: Boolean
)

/**
 * Hybrid medication catalog repository combining:
 * 1) Instant bundled curated pharmacokinetics dataset ([SubstanceCatalog])
 * 2) Live free NIH RxTerms search & openFDA half-life extraction ([MedicationLookupService])
 * 3) Bounded app-private persistent JSON cache (`medication_catalog_cache.json`) for offline fallback
 */
class MedicationCatalogRepository(
    context: Context,
    private val lookupService: MedicationLookupService = MedicationLookupService(),
    private val cacheFile: File = File(context.applicationContext.filesDir, CACHE_FILE_NAME)
) {
    private val mutex = Mutex()
    private var cacheLoaded = false
    private val searchCache = LinkedHashMap<String, List<RemoteMedicationItem>>(64, 0.75f, true)
    private val pkCache = LinkedHashMap<String, OpenFdaPharmacokinetics>(128, 0.75f, true)

    suspend fun searchCatalog(
        query: String,
        categoryFilter: SubstanceCategory? = null
    ): MedicationSearchResult {
        val trimmed = query.trim()
        val curated = SubstanceCatalog.search(trimmed, categoryFilter)
        if (trimmed.length < 2) {
            return MedicationSearchResult(
                curatedMatches = curated,
                remoteMatches = emptyList(),
                fromOfflineCache = false,
                networkUnavailable = false
            )
        }

        ensureCacheLoaded()
        val cacheKey = trimmed.lowercase(Locale.US)

        // Serve exact prior query from cache immediately if available
        val cachedExact = mutex.withLock { searchCache[cacheKey] }
        if (cachedExact != null) {
            val filteredCached = filterRemoteByCategory(cachedExact, categoryFilter)
            return MedicationSearchResult(
                curatedMatches = curated,
                remoteMatches = filteredCached,
                fromOfflineCache = true,
                networkUnavailable = false
            )
        }

        return try {
            val liveItems = lookupService.searchRxTerms(trimmed)
            if (liveItems.isNotEmpty()) {
                mutex.withLock {
                    putSearchCacheLocked(cacheKey, liveItems)
                    persistCacheLocked()
                }
            }
            MedicationSearchResult(
                curatedMatches = curated,
                remoteMatches = filterRemoteByCategory(liveItems, categoryFilter),
                fromOfflineCache = false,
                networkUnavailable = false
            )
        } catch (_: Exception) {
            val offlineMatches = mutex.withLock {
                searchCache.values
                    .flatten()
                    .distinctBy { it.id }
                    .filter {
                        it.displayName.lowercase(Locale.US).contains(cacheKey) ||
                            it.cleanName.lowercase(Locale.US).contains(cacheKey)
                    }
                    .take(12)
            }
            MedicationSearchResult(
                curatedMatches = curated,
                remoteMatches = filterRemoteByCategory(offlineMatches, categoryFilter),
                fromOfflineCache = offlineMatches.isNotEmpty(),
                networkUnavailable = true
            )
        }
    }

    suspend fun resolvePharmacokinetics(
        medication: RemoteMedicationItem
    ): OpenFdaPharmacokinetics {
        // 1. Check curated dataset first for verified clinical pharmacokinetics
        val curatedMatch = SubstanceCatalog.findByNameOrGeneric(medication.cleanName)
        if (curatedMatch != null) {
            return OpenFdaPharmacokinetics(
                halfLifeHours = curatedMatch.halfLifeHours,
                peakOnsetMinutes = curatedMatch.peakOnsetMinutes,
                excerpt = curatedMatch.mechanismSummary,
                source = HalfLifeSource.CURATED
            )
        }

        ensureCacheLoaded()
        val pkKey = medication.cleanName.lowercase(Locale.US).trim()

        // 2. Check app-private cache
        val cachedPk = mutex.withLock { pkCache[pkKey] }
        if (cachedPk != null && cachedPk.halfLifeHours != null) {
            return cachedPk.copy(source = HalfLifeSource.CACHED_OPEN_FDA)
        }

        // 3. Query openFDA Drug Label API
        val livePk = runCatching {
            lookupService.fetchPharmacokineticsFromOpenFda(
                drugName = medication.cleanName,
                rxcui = medication.rxcuis.firstOrNull()
            )
        }.getOrNull()

        if (livePk?.halfLifeHours != null) {
            mutex.withLock {
                putPkCacheLocked(pkKey, livePk)
                persistCacheLocked()
            }
            return livePk
        }

        // 4. Safe editable fallback based on inferred category
        return OpenFdaPharmacokinetics(
            halfLifeHours = defaultHalfLifeForCategory(medication.inferredCategory),
            peakOnsetMinutes = 45,
            excerpt = null,
            source = HalfLifeSource.ESTIMATED
        )
    }

    internal suspend fun saveCacheSnapshotForTesting(
        query: String,
        items: List<RemoteMedicationItem>,
        pkEntryKey: String? = null,
        pkEntry: OpenFdaPharmacokinetics? = null
    ) {
        ensureCacheLoaded()
        mutex.withLock {
            putSearchCacheLocked(query.trim().lowercase(Locale.US), items)
            if (pkEntryKey != null && pkEntry != null) {
                putPkCacheLocked(pkEntryKey.trim().lowercase(Locale.US), pkEntry)
            }
            persistCacheLocked()
        }
    }

    private fun filterRemoteByCategory(
        items: List<RemoteMedicationItem>,
        categoryFilter: SubstanceCategory?
    ): List<RemoteMedicationItem> {
        if (categoryFilter == null) return items
        return items.filter { it.inferredCategory == categoryFilter }
    }

    private fun defaultHalfLifeForCategory(category: SubstanceCategory): Double = when (category) {
        SubstanceCategory.STIMULANT -> 6.0
        SubstanceCategory.DEPRESSANT -> 6.0
        SubstanceCategory.MEDICATION -> 5.0
        SubstanceCategory.METABOLIC -> 3.0
        SubstanceCategory.CUSTOM -> 4.5
    }

    private suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (cacheLoaded) return@withLock
                runCatching {
                    if (cacheFile.exists() && cacheFile.length() <= MAX_CACHE_FILE_BYTES) {
                        val raw = cacheFile.readText(Charsets.UTF_8)
                        deserializeCacheLocked(raw)
                    }
                }
                cacheLoaded = true
            }
        }
    }

    private fun putSearchCacheLocked(key: String, items: List<RemoteMedicationItem>) {
        searchCache[key] = items.take(15)
        while (searchCache.size > MAX_SEARCH_CACHE_ENTRIES) {
            val eldestKey = searchCache.entries.firstOrNull()?.key ?: break
            searchCache.remove(eldestKey)
        }
    }

    private fun putPkCacheLocked(key: String, pk: OpenFdaPharmacokinetics) {
        pkCache[key] = pk
        while (pkCache.size > MAX_PK_CACHE_ENTRIES) {
            val eldestKey = pkCache.entries.firstOrNull()?.key ?: break
            pkCache.remove(eldestKey)
        }
    }

    private suspend fun persistCacheLocked() = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().apply {
                put("version", 1)
                put("searches", JSONObject().apply {
                    searchCache.forEach { (queryKey, items) ->
                        put(queryKey, JSONArray().apply {
                            items.forEach { item ->
                                put(JSONObject().apply {
                                    put("id", item.id)
                                    put("displayName", item.displayName)
                                    put("cleanName", item.cleanName)
                                    put("routeForm", item.routeForm)
                                    put("category", item.inferredCategory.name)
                                    put("rxcuis", JSONArray(item.rxcuis))
                                    put("strengths", JSONArray().apply {
                                        item.strengthOptions.forEach { opt ->
                                            put(JSONObject().apply {
                                                put("amount", opt.amount)
                                                put("unit", opt.unit)
                                                put("label", opt.label)
                                                put("form", opt.form)
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    }
                })
                put("pk", JSONObject().apply {
                    pkCache.forEach { (drugKey, pk) ->
                        if (pk.halfLifeHours != null) {
                            put(drugKey, JSONObject().apply {
                                put("halfLifeHours", pk.halfLifeHours)
                                if (pk.peakOnsetMinutes != null) put("peakOnsetMinutes", pk.peakOnsetMinutes)
                                if (!pk.excerpt.isNullOrBlank()) put("excerpt", pk.excerpt)
                            })
                        }
                    }
                })
            }
            cacheFile.parentFile?.mkdirs()
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tempFile.writeText(root.toString(), Charsets.UTF_8)
            if (!tempFile.renameTo(cacheFile)) {
                cacheFile.writeText(root.toString(), Charsets.UTF_8)
                tempFile.delete()
            }
        }
    }

    private fun deserializeCacheLocked(raw: String) {
        val root = JSONObject(raw)
        val searchesObj = root.optJSONObject("searches")
        if (searchesObj != null) {
            val keys = searchesObj.keys()
            while (keys.hasNext()) {
                val queryKey = keys.next()
                val arr = searchesObj.optJSONArray(queryKey) ?: continue
                val items = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val catName = obj.optString("category", "MEDICATION")
                        val cat = SubstanceCategory.entries.find { it.name == catName }
                            ?: SubstanceCategory.MEDICATION
                        val rxcuisArr = obj.optJSONArray("rxcuis")
                        val rxcuis = buildList {
                            if (rxcuisArr != null) {
                                for (r in 0 until rxcuisArr.length()) {
                                    add(rxcuisArr.optString(r, ""))
                                }
                            }
                        }.filter { it.isNotBlank() }
                        val strengthsArr = obj.optJSONArray("strengths")
                        val strengths = buildList {
                            if (strengthsArr != null) {
                                for (s in 0 until strengthsArr.length()) {
                                    val sObj = strengthsArr.optJSONObject(s) ?: continue
                                    val amt = sObj.optDouble("amount", Double.NaN)
                                    if (amt.isFinite() && amt > 0.0) {
                                        add(
                                            DoseOption(
                                                amount = amt,
                                                unit = sObj.optString("unit", "mg"),
                                                label = sObj.optString("label", ""),
                                                form = sObj.optString("form", "")
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        add(
                            RemoteMedicationItem(
                                id = obj.optString("id"),
                                displayName = obj.optString("displayName"),
                                cleanName = obj.optString("cleanName"),
                                routeForm = obj.optString("routeForm"),
                                rxcuis = rxcuis,
                                strengthOptions = strengths,
                                inferredCategory = cat
                            )
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    putSearchCacheLocked(queryKey, items)
                }
            }
        }

        val pkObj = root.optJSONObject("pk")
        if (pkObj != null) {
            val keys = pkObj.keys()
            while (keys.hasNext()) {
                val drugKey = keys.next()
                val obj = pkObj.optJSONObject(drugKey) ?: continue
                val hl = obj.optDouble("halfLifeHours", Double.NaN)
                if (hl.isFinite() && hl in 0.25..200.0) {
                    val tmax = if (obj.has("peakOnsetMinutes")) obj.optInt("peakOnsetMinutes") else null
                    val excerpt = obj.optString("excerpt", "").ifBlank { null }
                    putPkCacheLocked(
                        drugKey,
                        OpenFdaPharmacokinetics(
                            halfLifeHours = hl,
                            peakOnsetMinutes = tmax,
                            excerpt = excerpt,
                            source = HalfLifeSource.CACHED_OPEN_FDA
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val CACHE_FILE_NAME = "medication_catalog_cache.json"
        private const val MAX_SEARCH_CACHE_ENTRIES = 50
        private const val MAX_PK_CACHE_ENTRIES = 120
        private const val MAX_CACHE_FILE_BYTES = 2 * 1024 * 1024L
    }
}
