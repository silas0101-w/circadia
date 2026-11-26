package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.domain.DoseOption
import com.example.domain.SubstanceCatalog
import com.example.domain.SubstanceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.round

private const val TAG = "MedicationLookupService"

enum class HalfLifeSource(val label: String) {
    CURATED("Curated PK"),
    OPEN_FDA("openFDA Label"),
    CACHED_OPEN_FDA("openFDA (Cached)"),
    ESTIMATED("Editable Estimate")
}

data class RemoteMedicationItem(
    val id: String,
    val displayName: String,
    val cleanName: String,
    val routeForm: String,
    val rxcuis: List<String>,
    val strengthOptions: List<DoseOption>,
    val inferredCategory: SubstanceCategory
)

data class OpenFdaPharmacokinetics(
    val halfLifeHours: Double?,
    val peakOnsetMinutes: Int?,
    val excerpt: String?,
    val source: HalfLifeSource
)

/**
 * Free, keyless public medication lookup client backed by:
 * 1) NIH NLM Clinical Tables RxTerms API v3 (https://clinicaltables.nlm.nih.gov/api/rxterms/v3/search)
 * 2) U.S. FDA openFDA Drug Label API (https://api.fda.gov/drug/label.json)
 *
 * Enforces HTTPS host allowlisting, public-IP-only DNS, strict timeouts, and bounded payload reads.
 */
class MedicationLookupService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(PublicOnlyDns)
        .build()
) {
    private object PublicOnlyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val safe = addresses.filterNot { isRestrictedAddress(it) }
            if (safe.isEmpty()) {
                throw UnknownHostException("Blocked restricted network address for host: $hostname")
            }
            return safe
        }
    }

    suspend fun searchRxTerms(
        query: String,
        maxResults: Int = 14
    ): List<RemoteMedicationItem> = withContext(Dispatchers.IO) {
        val cleaned = query.trim().take(80)
        if (cleaned.length < 2) return@withContext emptyList()

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST_NIH_CLINICAL_TABLES)
            .addPathSegments("api/rxterms/v3/search")
            .addQueryParameter("terms", cleaned)
            .addQueryParameter("maxList", maxResults.coerceIn(1, 20).toString())
            .addQueryParameter("ef", "STRENGTHS_AND_FORMS,RXCUIS,DISPLAY_NAME_SYNONYM")
            .build()

        validateAllowedUrl(url)

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Circadia-Android/1.0")
            .get()
            .build()

        withTimeout(12_000L) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("NIH RxTerms HTTP ${response.code}")
                }
                val raw = readBoundedBody(response.body?.byteStream(), MAX_RXTERMS_BYTES)
                parseRxTermsResponse(raw)
            }
        }
    }

    suspend fun fetchPharmacokineticsFromOpenFda(
        drugName: String,
        rxcui: String? = null
    ): OpenFdaPharmacokinetics? = withContext(Dispatchers.IO) {
        val queries = buildList {
            val cleanRxcui = rxcui?.trim()?.filter { it.isDigit() }
            if (!cleanRxcui.isNullOrBlank()) {
                add("""openfda.rxcui:"$cleanRxcui"""")
            }
            val cleanName = sanitizeDrugNameForFda(drugName)
            if (cleanName.length >= 2) {
                add("""openfda.brand_name:"$cleanName"+openfda.generic_name:"$cleanName"""")
            }
        }

        for (searchExpr in queries) {
            val found = runCatching { queryOpenFdaLabel(searchExpr) }
                .onFailure { err ->
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "openFDA lookup skipped/failed for $searchExpr: ${err.message}")
                    }
                }
                .getOrNull()
            if (found?.halfLifeHours != null) {
                return@withContext found
            }
        }
        null
    }

    private suspend fun queryOpenFdaLabel(searchExpr: String): OpenFdaPharmacokinetics? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST_OPEN_FDA)
            .addPathSegments("drug/label.json")
            .addQueryParameter("search", searchExpr)
            .addQueryParameter("limit", "3")
            .build()

        validateAllowedUrl(url)

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "Circadia-Android/1.0")
            .get()
            .build()

        return withTimeout(12_000L) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }
                val raw = readBoundedBody(response.body?.byteStream(), MAX_OPENFDA_BYTES)
                parseOpenFdaLabelResponse(raw)
            }
        }
    }

    companion object {
        private const val HOST_NIH_CLINICAL_TABLES = "clinicaltables.nlm.nih.gov"
        private const val HOST_OPEN_FDA = "api.fda.gov"
        private val ALLOWED_HOSTS = setOf(HOST_NIH_CLINICAL_TABLES, HOST_OPEN_FDA)
        private const val MAX_RXTERMS_BYTES = 512 * 1024
        private const val MAX_OPENFDA_BYTES = 1_500 * 1024

        private val STRENGTH_REGEX = Regex(
            """(\d+(?:\.\d+)?)\s*(mg|mcg|ug|µg|g|ml|mL|units?|iu|meq|%|mg/ml|mg/5\s*ml|mcg/hr|mg/24\s*hr)\b(.*)$""",
            RegexOption.IGNORE_CASE
        )

        private val HALF_LIFE_RANGE_REGEX = Regex(
            """(?:elimination|terminal|mean|apparent|plasma|biological|effective)?\s*(?:half[\s-]?life|t\s*1/2|t½)[^.;\n]{0,75}?(\d+(?:\.\d+)?)\s*(?:to|-|–|—)\s*(\d+(?:\.\d+)?)\s*(hours?|hrs?|h\b|minutes?|mins?|min\b|days?|d\b)""",
            RegexOption.IGNORE_CASE
        )

        private val HALF_LIFE_SINGLE_REGEX = Regex(
            """(?:elimination|terminal|mean|apparent|plasma|biological|effective)?\s*(?:half[\s-]?life|t\s*1/2|t½)[^.;\n]{0,75}?(?:is|of|approximately|about|around|~|≈|:|\baverages?\b|\branges?\b)?\s*(\d+(?:\.\d+)?)\s*(hours?|hrs?|h\b|minutes?|mins?|min\b|days?|d\b)""",
            RegexOption.IGNORE_CASE
        )

        private val TMAX_REGEX = Regex(
            """(?:peak\s+plasma\s+concentrations?|tmax|time\s+to\s+peak)[^.;\n]{0,65}?(\d+(?:\.\d+)?)(?:\s*(?:to|-|–)\s*(\d+(?:\.\d+)?))?\s*(hours?|hrs?|h\b|minutes?|mins?|min\b)""",
            RegexOption.IGNORE_CASE
        )

        private val STIMULANT_KEYWORDS = listOf(
            "amphetamine", "dextroamphetamine", "lisdexamfetamine", "adderall", "vyvanse",
            "dexedrine", "mydayis", "methylphenidate", "dexmethylphenidate", "ritalin", "concerta",
            "focalin", "jornay", "quillivant", "daytrana", "azstarys", "modafinil", "armodafinil",
            "provigil", "nuvigil", "caffeine", "nicotine", "pseudoephedrine", "phenylephrine",
            "sudafed", "phentermine", "albuterol", "levalbuterol", "theophylline", "ephedrine"
        )

        private val DEPRESSANT_KEYWORDS = listOf(
            "alprazolam", "xanax", "lorazepam", "ativan", "clonazepam", "klonopin", "diazepam",
            "valium", "temazepam", "triazolam", "zolpidem", "ambien", "eszopiclone", "lunesta",
            "zaleplon", "sonata", "oxycodone", "hydrocodone", "morphine", "tramadol", "codeine",
            "fentanyl", "buprenorphine", "cyclobenzaprine", "baclofen", "tizanidine", "carisoprodol",
            "phenobarbital", "ethanol", "alcohol", "dronabinol", "cannabidiol"
        )

        fun validateAllowedUrl(url: HttpUrl) {
            require(url.scheme.equals("https", ignoreCase = true)) {
                "Medication lookup must use HTTPS."
            }
            val host = url.host.lowercase(Locale.US)
            require(host in ALLOWED_HOSTS) {
                "Untrusted medication lookup host: $host"
            }
        }

        private fun isRestrictedAddress(addr: InetAddress): Boolean {
            return addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isMulticastAddress
        }

        private fun readBoundedBody(stream: java.io.InputStream?, maxBytes: Int): String {
            if (stream == null) return ""
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            var total = 0
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val allowed = minOf(read, maxBytes - total)
                    if (allowed > 0) {
                        out.write(buffer, 0, allowed)
                        total += allowed
                    }
                    if (total >= maxBytes) break
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }

        /**
         * Parses the 4-element NIH RxTerms v3 JSON array:
         * `[totalCount, [codes], {"STRENGTHS_AND_FORMS": [[...]], "RXCUIS": [[...]]}, [[displayName], ...]]`
         */
        fun parseRxTermsResponse(rawJson: String): List<RemoteMedicationItem> {
            val trimmed = rawJson.trim()
            if (!trimmed.startsWith("[")) return emptyList()
            val root = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
            if (root.length() < 4) return emptyList()

            val extraFields = root.optJSONObject(2) ?: JSONObject()
            val strengthsMatrix = extraFields.optJSONArray("STRENGTHS_AND_FORMS")
            val rxcuisMatrix = extraFields.optJSONArray("RXCUIS")
            val displayRows = root.optJSONArray(3) ?: return emptyList()

            val results = mutableListOf<RemoteMedicationItem>()
            val seenIds = mutableSetOf<String>()

            for (i in 0 until displayRows.length()) {
                val row = displayRows.optJSONArray(i) ?: continue
                val rawDisplay = row.optString(0, "").trim()
                if (rawDisplay.isEmpty()) continue

                val (cleanName, routeForm) = splitDisplayNameAndRoute(rawDisplay)
                val normalizedId = "rxterms_" + rawDisplay
                    .lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .take(64)
                if (!seenIds.add(normalizedId)) continue

                val rxcuis = buildList {
                    val arr = rxcuisMatrix?.optJSONArray(i)
                    if (arr != null) {
                        for (r in 0 until arr.length()) {
                            val code = arr.optString(r, "").trim()
                            if (code.isNotEmpty()) add(code)
                        }
                    }
                }

                val strengthOptions = buildList {
                    val arr = strengthsMatrix?.optJSONArray(i)
                    val seenLabels = mutableSetOf<String>()
                    if (arr != null) {
                        for (s in 0 until arr.length()) {
                            val rawStrength = arr.optString(s, "").trim()
                            val parsed = parseStrengthOption(rawStrength)
                            if (parsed != null && seenLabels.add(parsed.label.lowercase(Locale.US))) {
                                add(parsed)
                            }
                        }
                    }
                }.sortedBy { it.amount }

                results.add(
                    RemoteMedicationItem(
                        id = normalizedId,
                        displayName = if (routeForm.isNotEmpty()) "$cleanName ($routeForm)" else cleanName,
                        cleanName = cleanName,
                        routeForm = routeForm.ifBlank { "Medication" },
                        rxcuis = rxcuis,
                        strengthOptions = strengthOptions,
                        inferredCategory = inferCategoryFromName(cleanName)
                    )
                )
            }
            return results
        }

        /**
         * Parses an NIH RxTerms strength/form string like `"20 mg Cap ER"`, `"500 mcg Tab"`,
         * `"10 mg/5 mL Soln"`, or `"2.5 mg Tab"` into a structured [DoseOption].
         */
        fun parseStrengthOption(rawStrength: String): DoseOption? {
            val cleaned = rawStrength.trim()
            if (cleaned.isEmpty()) return null
            val match = STRENGTH_REGEX.find(cleaned) ?: return null
            val amount = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            val rawUnit = match.groupValues[2].trim()
            val normalizedUnit = normalizeUnit(rawUnit)
            val trailingForm = match.groupValues[3]
                .trim()
                .removePrefix("-")
                .removePrefix("/")
                .trim()
                .take(24)

            val formattedAmount = SubstanceCatalog.formatDoseChipNumber(amount)
            val shortForm = trailingForm
                .replace("Tab", "Tab", ignoreCase = true)
                .replace("Cap", "Cap", ignoreCase = true)
                .trim()
            val chipLabel = if (shortForm.isNotEmpty() && shortForm.length <= 14) {
                "$formattedAmount $normalizedUnit $shortForm"
            } else {
                "$formattedAmount $normalizedUnit"
            }

            return DoseOption(
                amount = amount,
                unit = normalizedUnit,
                label = chipLabel,
                form = shortForm
            )
        }

        /**
         * Parses openFDA `/drug/label.json` response to extract elimination half-life and Tmax.
         */
        fun parseOpenFdaLabelResponse(rawJson: String): OpenFdaPharmacokinetics? {
            val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            val results = root.optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val labelObj = results.optJSONObject(i) ?: continue
                val sections = buildList {
                    labelObj.optJSONArray("pharmacokinetics")?.let { arr ->
                        for (j in 0 until arr.length()) add(arr.optString(j, ""))
                    }
                    labelObj.optJSONArray("clinical_pharmacology")?.let { arr ->
                        for (j in 0 until arr.length()) add(arr.optString(j, ""))
                    }
                }
                for (section in sections) {
                    if (section.isBlank()) continue
                    val parsed = extractPharmacokineticsFromLabelText(section)
                    if (parsed?.halfLifeHours != null) {
                        return parsed
                    }
                }
            }
            return null
        }

        /**
         * Pure text parser for FDA Clinical Pharmacology / Pharmacokinetics label prose.
         * Handles single values, ranges (averaged), minutes, hours, and days.
         */
        fun extractPharmacokineticsFromLabelText(text: String): OpenFdaPharmacokinetics? {
            if (text.isBlank()) return null
            val normalized = text.replace(Regex("\\s+"), " ").trim()

            var extractedHours: Double? = null
            var matchedRange: IntRange? = null

            val rangeMatch = HALF_LIFE_RANGE_REGEX.find(normalized)
            if (rangeMatch != null) {
                val low = rangeMatch.groupValues[1].toDoubleOrNull()
                val high = rangeMatch.groupValues[2].toDoubleOrNull()
                val unit = rangeMatch.groupValues[3]
                if (low != null && high != null && low > 0.0 && high >= low) {
                    val avg = (low + high) / 2.0
                    extractedHours = convertToHours(avg, unit)
                    matchedRange = rangeMatch.range
                }
            }

            if (extractedHours == null) {
                val singleMatch = HALF_LIFE_SINGLE_REGEX.find(normalized)
                if (singleMatch != null) {
                    val value = singleMatch.groupValues[1].toDoubleOrNull()
                    val unit = singleMatch.groupValues[2]
                    if (value != null && value > 0.0) {
                        extractedHours = convertToHours(value, unit)
                        matchedRange = singleMatch.range
                    }
                }
            }

            val safeHalfLife = extractedHours
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.coerceIn(0.25, 200.0)
                ?.let { round(it * 10.0) / 10.0 }

            val tmaxMinutes = extractTmaxMinutes(normalized)

            if (safeHalfLife == null && tmaxMinutes == null) return null

            val excerpt = matchedRange?.let { range ->
                val start = (range.first - 24).coerceAtLeast(0)
                val end = (range.last + 70).coerceAtMost(normalized.length)
                normalized.substring(start, end).trim().take(150)
            }

            return OpenFdaPharmacokinetics(
                halfLifeHours = safeHalfLife,
                peakOnsetMinutes = tmaxMinutes,
                excerpt = excerpt,
                source = HalfLifeSource.OPEN_FDA
            )
        }

        private fun extractTmaxMinutes(normalizedText: String): Int? {
            val match = TMAX_REGEX.find(normalizedText) ?: return null
            val first = match.groupValues[1].toDoubleOrNull() ?: return null
            val second = match.groupValues[2].toDoubleOrNull()
            val unit = match.groupValues[3]
            val avg = if (second != null && second >= first) (first + second) / 2.0 else first
            val hours = convertToHours(avg, unit) ?: return null
            return (hours * 60.0).toInt().coerceIn(10, 240)
        }

        private fun convertToHours(value: Double, unit: String): Double? {
            val u = unit.trim().lowercase(Locale.US)
            return when {
                u.startsWith("min") -> value / 60.0
                u.startsWith("h") -> value
                u.startsWith("d") -> value * 24.0
                else -> null
            }
        }

        private fun normalizeUnit(raw: String): String {
            val lower = raw.lowercase(Locale.US).replace(Regex("\\s+"), "")
            return when {
                lower == "ug" || lower == "µg" -> "mcg"
                lower == "ml" -> "mL"
                lower == "mg/ml" -> "mg/mL"
                lower == "mg/5ml" -> "mg/5mL"
                lower == "iu" -> "IU"
                lower == "meq" -> "mEq"
                lower.startsWith("unit") -> "units"
                else -> lower
            }
        }

        private fun splitDisplayNameAndRoute(rawDisplay: String): Pair<String, String> {
            val openIdx = rawDisplay.lastIndexOf('(')
            val closeIdx = rawDisplay.lastIndexOf(')')
            val (namePart, routePart) = if (openIdx > 0 && closeIdx > openIdx) {
                rawDisplay.substring(0, openIdx).trim() to
                    rawDisplay.substring(openIdx + 1, closeIdx).trim()
            } else {
                rawDisplay.trim() to ""
            }
            return formatMedicationTitle(namePart) to formatMedicationTitle(routePart)
        }

        private fun formatMedicationTitle(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val isAllUpper = trimmed.any { it.isLetter() } && trimmed.none { it.isLowerCase() }
            if (!isAllUpper) return trimmed
            val preservedAcronyms = setOf("XR", "ER", "IR", "SR", "XL", "CR", "LA", "DR", "HCL", "HFA", "OTC", "PM", "AM", "CD", "ODT", "IV", "IM")
            return trimmed.split(Regex("\\s+")).joinToString(" ") { token ->
                val upper = token.uppercase(Locale.US)
                if (upper in preservedAcronyms) {
                    if (upper == "HCL") "HCl" else upper
                } else {
                    token.lowercase(Locale.US).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                    }
                }
            }
        }

        internal fun sanitizeDrugNameForFda(rawName: String): String {
            return rawName
                .substringBefore("(")
                .replace(Regex("""["\\]"""), " ")
                .replace(Regex("""\b(XR|ER|IR|SR|XL|CR|LA|DR|HCl|Oral|Pill|Tablet|Capsule)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(60)
        }

        internal fun inferCategoryFromName(name: String): SubstanceCategory {
            val lower = name.lowercase(Locale.US)
            return when {
                STIMULANT_KEYWORDS.any { lower.contains(it) } -> SubstanceCategory.STIMULANT
                DEPRESSANT_KEYWORDS.any { lower.contains(it) } -> SubstanceCategory.DEPRESSANT
                else -> SubstanceCategory.MEDICATION
            }
        }
    }
}
