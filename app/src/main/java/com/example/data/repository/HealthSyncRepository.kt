package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.CircadiaDao
import com.example.data.local.SafeFileReader
import com.example.data.local.SleepSessionEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.GoogleFitRestService
import com.example.domain.DateTimeUtils
import com.example.domain.SleepSessionMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class HealthSyncRepository(
    private val dao: CircadiaDao,
    private val prefsRepo: UserPreferencesRepository,
    private val googleFitService: GoogleFitRestService
) {
    fun observeAllSleepSessions(): Flow<List<SleepSessionEntity>> =
        dao.observeAllSleepSessions()

    fun observeSleepSessionForDate(dateIso: String): Flow<SleepSessionEntity?> =
        dao.observeSleepSessionForDate(dateIso)

    suspend fun startGoogleOAuth(): Uri {
        val settings = prefsRepo.getCurrentSettings()
        if (settings.googleClientId.isNotBlank()) {
            val pkce = googleFitService.buildPkceAuthRequest(settings.googleClientId)
            prefsRepo.savePendingOAuthPkce(pkce.codeVerifier, pkce.state)
            return pkce.authUri
        }
        prefsRepo.clearPendingOAuthPkce()
        return googleFitService.buildGoogleLoginPageUri()
    }

    suspend fun completeGoogleWebSignIn() {
        prefsRepo.markGoogleAccountSignedIn(true)
    }

    suspend fun cancelPendingOAuth() {
        prefsRepo.clearPendingOAuthPkce()
    }

    suspend fun handleGoogleOAuthRedirect(uri: Uri): Int {
        try {
            val error = uri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                throw IllegalStateException("Google OAuth authorization was declined or failed.")
            }
            val code = uri.getQueryParameter("code")
                ?: throw IllegalStateException("OAuth redirect missing authorization code.")
            val returnedState = uri.getQueryParameter("state").orEmpty()
            val settings = prefsRepo.getCurrentSettings()
            val expectedState = settings.pendingOAuthState

            if (expectedState.isBlank() || returnedState.isBlank() ||
                !MessageDigest.isEqual(
                    expectedState.toByteArray(Charsets.UTF_8),
                    returnedState.toByteArray(Charsets.UTF_8)
                )
            ) {
                throw IllegalStateException("OAuth state verification failed. Please retry connecting Google Fit.")
            }
            if (settings.googleClientId.isBlank() || settings.pendingOAuthCodeVerifier.isBlank()) {
                throw IllegalStateException("Missing OAuth PKCE session. Please tap Sign in with Google again.")
            }

            val tokenResp = googleFitService.exchangeAuthorizationCode(
                clientId = settings.googleClientId,
                code = code,
                codeVerifier = settings.pendingOAuthCodeVerifier
            )
            prefsRepo.saveGoogleAccessToken(tokenResp.accessToken, tokenResp.expiresInSeconds)
            return syncGoogleFitSessions()
        } finally {
            prefsRepo.clearPendingOAuthPkce()
        }
    }

    suspend fun syncGoogleFitSessions(): Int {
        val settings = prefsRepo.getCurrentSettings()
        if (!settings.hasValidOAuthToken) {
            if (settings.googleAccountSignedIn) {
                throw IllegalStateException(
                    "Google account signed in. Add a Google Fit OAuth Client ID in Settings > Advanced to sync Fitness REST data, or import a CSV/JSON sleep export below."
                )
            }
            throw IllegalStateException("Google Fit session expired or not connected. Please sign in again.")
        }
        val sessions = googleFitService.fetchRecentSleepSessions(settings.googleAccessToken)
        if (sessions.isEmpty()) return 0
        val merged = SleepSessionMerger.mergeWithExisting(dao.getAllSleepSessionsOnce(), sessions)
        if (merged.isNotEmpty()) {
            dao.upsertSleepSessions(merged)
        }
        return merged.size
    }

    suspend fun disconnectGoogleFit() {
        prefsRepo.clearGoogleAuth()
    }

    /**
     * Parses Samsung Health CSV/JSON or Health Connect / Google Takeout JSON sleep exports via SAF.
     */
    suspend fun importSleepFile(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val raw = SafeFileReader.readUtf8Bounded(context, uri)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw IllegalStateException("The selected file is empty.")
        }

        val parsed: List<SleepSessionEntity> = if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching { parseJsonSleepExport(trimmed) }
                .getOrElse { throw IllegalArgumentException("Selected JSON sleep file could not be parsed.") }
        } else {
            parseCsvSleepExport(trimmed)
        }

        if (parsed.isEmpty()) {
            throw IllegalStateException(
                "No valid sleep rows found. Expected Samsung Health / Health Connect CSV or JSON sleep fields."
            )
        }

        val merged = SleepSessionMerger.mergeWithExisting(dao.getAllSleepSessionsOnce(), parsed)
        dao.upsertSleepSessions(merged)
        merged.size
    }

    private fun parseJsonSleepExport(raw: String): List<SleepSessionEntity> {
        val array = if (raw.startsWith("[")) {
            JSONArray(raw)
        } else {
            val obj = JSONObject(raw)
            obj.optJSONArray("sleepSessions")
                ?: obj.optJSONArray("sessions")
                ?: obj.optJSONArray("sleep")
                ?: obj.optJSONArray("data")
                ?: JSONArray().apply { put(obj) }
        }

        val results = mutableListOf<SleepSessionEntity>()
        val maxItems = minOf(array.length(), SafeFileReader.MAX_IMPORT_RECORDS)
        for (i in 0 until maxItems) {
            val item = array.optJSONObject(i) ?: continue
            val startMs = parseTimestampMillis(
                item.opt("startTime") ?: item.opt("start_time") ?: item.opt("bedtimeMillis")
            )
            val endMs = parseTimestampMillis(
                item.opt("endTime") ?: item.opt("end_time") ?: item.opt("wakeTimeMillis")
            )

            val dateIso = item.optString("dateIso").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                ?: if (endMs > 0L) DateTimeUtils.dateIsoFromMillis(endMs)
                else if (startMs > 0L) DateTimeUtils.dateIsoFromMillis(startMs)
                else item.optString("date").take(10)

            if (!dateIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) continue

            var remMin = item.optInt("remMinutes", item.optInt("rem_minutes", item.optInt("rem", 0)))
            var deepMin = item.optInt("deepMinutes", item.optInt("deep_minutes", item.optInt("deep", 0)))
            var lightMin = item.optInt("lightMinutes", item.optInt("light_minutes", item.optInt("light", 0)))
            var awakeMin = item.optInt("awakeMinutes", item.optInt("awake_minutes", item.optInt("awake", 0)))

            // Health Connect / Google Takeout stages array support
            val stagesArr = item.optJSONArray("stages")
            if (stagesArr != null && stagesArr.length() > 0) {
                remMin = 0; deepMin = 0; lightMin = 0; awakeMin = 0
                val maxStages = minOf(stagesArr.length(), 1000)
                for (sIdx in 0 until maxStages) {
                    val st = stagesArr.optJSONObject(sIdx) ?: continue
                    val stStart = parseTimestampMillis(st.opt("startTime"))
                    val stEnd = parseTimestampMillis(st.opt("endTime"))
                    val durMin = if (stEnd > stStart && stStart > 0) {
                        ((stEnd - stStart) / 60_000L).toInt().coerceIn(0, 1440)
                    } else {
                        st.optInt("minutes", 0).coerceIn(0, 1440)
                    }
                    val stageName = st.optString("stage").lowercase()
                    val stageCode = st.optInt("stageType", -1)
                    when {
                        stageName.contains("rem") || stageCode == 6 -> remMin += durMin
                        stageName.contains("deep") || stageCode == 5 -> deepMin += durMin
                        stageName.contains("light") || stageCode == 4 || stageCode == 2 -> lightMin += durMin
                        stageName.contains("awake") || stageCode == 1 -> awakeMin += durMin
                    }
                }
            }

            remMin = remMin.coerceIn(0, 1440)
            deepMin = deepMin.coerceIn(0, 1440)
            lightMin = lightMin.coerceIn(0, 1440)
            awakeMin = awakeMin.coerceIn(0, 1440)

            val totalMin = when {
                item.has("totalDurationMinutes") -> item.optInt("totalDurationMinutes")
                item.has("durationMinutes") -> item.optInt("durationMinutes")
                item.has("sleep_duration") -> normalizeDurationToMinutes(item.optDouble("sleep_duration"))
                endMs > startMs && startMs > 0 -> ((endMs - startMs) / 60_000L).toInt()
                else -> remMin + deepMin + lightMin
            }.coerceIn(1, 1440)

            val rhr = when {
                item.has("restingHeartRateBpm") && !item.isNull("restingHeartRateBpm") -> item.optInt("restingHeartRateBpm")
                item.has("resting_heart_rate") && !item.isNull("resting_heart_rate") -> item.optInt("resting_heart_rate")
                item.has("heart_rate") && !item.isNull("heart_rate") -> item.optInt("heart_rate")
                else -> null
            }?.takeIf { it in 25..220 }

            val sourceLabel = SafeFileReader.sanitizeText(item.optString("source"), maxLen = 48)
                .ifBlank { "Imported JSON" }
            results.add(
                SleepSessionEntity(
                    dateIso = dateIso,
                    source = sourceLabel,
                    totalDurationMinutes = totalMin,
                    remMinutes = remMin,
                    deepMinutes = deepMin,
                    lightMinutes = if (lightMin > 0) lightMin else (totalMin - remMin - deepMin - awakeMin).coerceIn(0, 1440),
                    awakeMinutes = awakeMin,
                    restingHeartRateBpm = rhr,
                    bedtimeMillis = startMs.coerceAtLeast(0L),
                    wakeTimeMillis = endMs.coerceAtLeast(0L)
                )
            )
        }
        return results
    }

    private fun parseCsvSleepExport(raw: String): List<SleepSessionEntity> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        // Samsung Health CSV exports often have a metadata row first: "com.samsung.shealth.sleep,..."
        var headerIndex = 0
        if (lines[0].startsWith("com.samsung", ignoreCase = true) ||
            (!lines[0].contains("time", ignoreCase = true) && !lines[0].contains("date", ignoreCase = true) && !lines[0].contains("duration", ignoreCase = true))
        ) {
            if (lines.size >= 3) headerIndex = 1
        }

        val headers = splitCsvLine(lines[headerIndex]).map { it.trim().lowercase() }
        val results = mutableListOf<SleepSessionEntity>()
        val maxRowIndex = minOf(lines.size, headerIndex + 1 + SafeFileReader.MAX_IMPORT_RECORDS)

        for (r in (headerIndex + 1) until maxRowIndex) {
            val cols = splitCsvLine(lines[r])
            if (cols.isEmpty()) continue
            val row = headers.indices.associate { idx -> headers[idx] to cols.getOrElse(idx) { "" }.trim() }

            val startMs = parseTimestampMillis(row["start_time"] ?: row["bedtime"] ?: row["com.samsung.health.sleep.start_time"])
            val endMs = parseTimestampMillis(row["end_time"] ?: row["wake_time"] ?: row["com.samsung.health.sleep.end_time"])

            val rawDate = row["date"] ?: row["date_iso"] ?: row["day_time"] ?: ""
            val dateIso = when {
                rawDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*")) -> rawDate.take(10)
                endMs > 0L -> DateTimeUtils.dateIsoFromMillis(endMs)
                startMs > 0L -> DateTimeUtils.dateIsoFromMillis(startMs)
                else -> ""
            }
            if (!dateIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) continue

            val remRaw = row["rem_duration"] ?: row["rem_minutes"] ?: row["rem"] ?: "0"
            val deepRaw = row["deep_duration"] ?: row["deep_minutes"] ?: row["deep"] ?: "0"
            val lightRaw = row["light_duration"] ?: row["light_minutes"] ?: row["light"] ?: "0"
            val awakeRaw = row["awake_duration"] ?: row["awake_minutes"] ?: row["awake"] ?: "0"
            val totalRaw = row["sleep_duration"] ?: row["total_minutes"] ?: row["duration_minutes"] ?: row["duration"] ?: ""

            val remMin = normalizeDurationToMinutes(remRaw.toDoubleOrNull() ?: 0.0).coerceIn(0, 1440)
            val deepMin = normalizeDurationToMinutes(deepRaw.toDoubleOrNull() ?: 0.0).coerceIn(0, 1440)
            val lightMin = normalizeDurationToMinutes(lightRaw.toDoubleOrNull() ?: 0.0).coerceIn(0, 1440)
            val awakeMin = normalizeDurationToMinutes(awakeRaw.toDoubleOrNull() ?: 0.0).coerceIn(0, 1440)

            val totalDouble = totalRaw.toDoubleOrNull()?.takeIf { it.isFinite() }
            val hoursDouble = row["hours"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
            val totalMin = when {
                totalDouble != null -> normalizeDurationToMinutes(totalDouble)
                hoursDouble != null -> (hoursDouble * 60.0).roundToInt()
                endMs > startMs && startMs > 0 -> ((endMs - startMs) / 60_000L).toInt()
                else -> remMin + deepMin + lightMin
            }.coerceIn(1, 1440)

            val rhr = (row["resting_heart_rate"] ?: row["rhr"] ?: row["heart_rate"])
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?.roundToInt()
                ?.takeIf { it in 25..220 }

            val isSamsung = lines[0].contains("samsung", ignoreCase = true) || headers.any { it.contains("samsung") }
            results.add(
                SleepSessionEntity(
                    dateIso = dateIso,
                    source = if (isSamsung) "Samsung Health CSV" else "Imported CSV",
                    totalDurationMinutes = totalMin,
                    remMinutes = remMin,
                    deepMinutes = deepMin,
                    lightMinutes = if (lightMin > 0) lightMin else (totalMin - remMin - deepMin - awakeMin).coerceIn(0, 1440),
                    awakeMinutes = awakeMin,
                    restingHeartRateBpm = rhr,
                    bedtimeMillis = startMs.coerceAtLeast(0L),
                    wakeTimeMillis = endMs.coerceAtLeast(0L)
                )
            )
        }
        return results
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    private fun normalizeDurationToMinutes(value: Double): Int {
        if (!value.isFinite() || value <= 0.0) return 0
        return when {
            value > 20_000.0 -> (value / 60_000.0).roundToInt().coerceIn(0, 1440) // milliseconds
            value > 1_440.0 -> (value / 60.0).roundToInt().coerceIn(0, 1440) // seconds
            else -> value.roundToInt().coerceIn(0, 1440) // already minutes
        }
    }

    private fun parseTimestampMillis(raw: Any?): Long {
        if (raw == null) return 0L
        if (raw is Number) {
            val v = raw.toLong()
            return if (v in 1..9_999_999_999L) v * 1000L else v
        }
        val str = raw.toString().trim()
        if (str.isBlank()) return 0L
        str.toLongOrNull()?.let { v ->
            return if (v in 1..9_999_999_999L) v * 1000L else v
        }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in formats) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.parse(str)?.time
            }.getOrNull()?.let { return it }
        }
        return 0L
    }
}
