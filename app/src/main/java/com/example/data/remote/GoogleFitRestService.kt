package com.example.data.remote

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.SleepSessionEntity
import com.example.domain.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class PkceAuthRequest(
    val authUri: Uri,
    val codeVerifier: String,
    val state: String
)

data class OAuthTokenResponse(
    val accessToken: String,
    val expiresInSeconds: Long
)

class GoogleFitRestService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {
    companion object {
        private const val TAG = "GoogleFitRestService"
        const val APP_REDIRECT_SCHEME = "com.aistudio.circadia.k7m2vx"
        const val APP_REDIRECT_PATH = "/oauth2redirect"
        const val REDIRECT_URI = "$APP_REDIRECT_SCHEME:$APP_REDIRECT_PATH"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val GOOGLE_SIGNIN_ENDPOINT = "https://accounts.google.com/ServiceLogin"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val FIT_BASE = "https://www.googleapis.com/fitness/v1/users/me"
        private const val SCOPES =
            "https://www.googleapis.com/auth/fitness.sleep.read https://www.googleapis.com/auth/fitness.heart_rate.read"

        private fun safeHttpErrorMessage(contextLabel: String, statusCode: Int): String = when (statusCode) {
            401, 403 -> "$contextLabel authentication failed ($statusCode). Please reconnect Google Fit."
            429 -> "$contextLabel rate limit reached ($statusCode). Please try again shortly."
            in 500..599 -> "$contextLabel service is temporarily unavailable ($statusCode)."
            else -> "$contextLabel request failed ($statusCode)."
        }
    }

    fun buildGoogleLoginPageUri(): Uri {
        return Uri.parse(GOOGLE_SIGNIN_ENDPOINT).buildUpon()
            .appendQueryParameter("hl", "en")
            .appendQueryParameter("continue", "https://myaccount.google.com/")
            .build()
    }

    fun buildPkceAuthRequest(clientId: String): PkceAuthRequest {
        val random = SecureRandom()
        val verifierBytes = ByteArray(48)
        random.nextBytes(verifierBytes)
        val codeVerifier = Base64.encodeToString(
            verifierBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val stateBytes = ByteArray(16)
        random.nextBytes(stateBytes)
        val state = Base64.encodeToString(
            stateBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "online")
            .appendQueryParameter("prompt", "consent")
            .build()

        return PkceAuthRequest(
            authUri = authUri,
            codeVerifier = codeVerifier,
            state = state
        )
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        code: String,
        codeVerifier: String
    ): OAuthTokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId.trim())
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        withTimeout(30_000L) {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "OAuth token exchange HTTP ${response.code}: ${raw.take(160)}")
                    }
                    throw IllegalStateException(safeHttpErrorMessage("OAuth token exchange", response.code))
                }
                val json = JSONObject(raw)
                val token = json.getString("access_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                OAuthTokenResponse(accessToken = token, expiresInSeconds = expiresIn)
            }
        }
    }

    /**
     * Fetches the last 14 days of Google Fit sleep sessions (activityType=72), sleep stage segments,
     * and resting/nocturnal heart rate averages.
     */
    suspend fun fetchRecentSleepSessions(
        accessToken: String,
        daysBack: Int = 14
    ): List<SleepSessionEntity> = withContext(Dispatchers.IO) {
        val endMillis = System.currentTimeMillis()
        val startMillis = endMillis - daysBack * 86_400_000L

        val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startIso = isoUtc.format(Date(startMillis))
        val endIso = isoUtc.format(Date(endMillis))

        val sessionsUrl = "$FIT_BASE/sessions?startTime=${Uri.encode(startIso)}&endTime=${Uri.encode(endIso)}&activityType=72"
        val sessionsRequest = Request.Builder()
            .url(sessionsUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val sessionsJson = withTimeout(30_000L) {
            client.newCall(sessionsRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Google Fit sessions HTTP ${response.code}: ${raw.take(160)}")
                    }
                    throw IllegalStateException(safeHttpErrorMessage("Google Fit", response.code))
                }
                JSONObject(raw)
            }
        }

        val sessionArray = sessionsJson.optJSONArray("session") ?: JSONArray()
        val results = mutableListOf<SleepSessionEntity>()

        for (i in 0 until sessionArray.length()) {
            val s = sessionArray.getJSONObject(i)
            val sStart = s.optLong("startTimeMillis", 0L)
            val sEnd = s.optLong("endTimeMillis", 0L)
            if (sStart <= 0L || sEnd <= sStart) continue

            val stages = fetchSleepStagesAndHeartRate(accessToken, sStart, sEnd)
            val totalMin = ((sEnd - sStart) / 60_000L).toInt().coerceAtLeast(1)
            val wakeDateIso = DateTimeUtils.dateIsoFromMillis(sEnd)

            results.add(
                SleepSessionEntity(
                    dateIso = wakeDateIso,
                    source = "Google Fit REST",
                    totalDurationMinutes = totalMin,
                    remMinutes = stages.remMinutes,
                    deepMinutes = stages.deepMinutes,
                    lightMinutes = stages.lightMinutes.ifZero((totalMin - stages.remMinutes - stages.deepMinutes - stages.awakeMinutes).coerceAtLeast(0)),
                    awakeMinutes = stages.awakeMinutes,
                    restingHeartRateBpm = stages.avgHeartRateBpm,
                    bedtimeMillis = sStart,
                    wakeTimeMillis = sEnd
                )
            )
        }
        results
    }

    private data class StageBreakdown(
        val remMinutes: Int,
        val deepMinutes: Int,
        val lightMinutes: Int,
        val awakeMinutes: Int,
        val avgHeartRateBpm: Int?
    )

    private fun fetchSleepStagesAndHeartRate(
        accessToken: String,
        startMillis: Long,
        endMillis: Long
    ): StageBreakdown {
        val body = JSONObject().apply {
            put("aggregateBy", JSONArray().apply {
                put(JSONObject().apply { put("dataTypeName", "com.google.sleep.segment") })
                put(JSONObject().apply { put("dataTypeName", "com.google.heart_rate.bpm") })
            })
            put("startTimeMillis", startMillis)
            put("endTimeMillis", endMillis)
        }

        val request = Request.Builder()
            .url("$FIT_BASE/dataset:aggregate")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use StageBreakdown(0, 0, 0, 0, null)
                val json = JSONObject(response.body?.string().orEmpty())
                val buckets = json.optJSONArray("bucket") ?: return@use StageBreakdown(0, 0, 0, 0, null)

                var remMs = 0L
                var deepMs = 0L
                var lightMs = 0L
                var awakeMs = 0L
                val hrValues = mutableListOf<Double>()

                for (b in 0 until buckets.length()) {
                    val datasets = buckets.getJSONObject(b).optJSONArray("dataset") ?: continue
                    for (d in 0 until datasets.length()) {
                        val ds = datasets.getJSONObject(d)
                        val dataSourceId = ds.optString("dataSourceId")
                        val points = ds.optJSONArray("point") ?: continue
                        for (p in 0 until points.length()) {
                            val pt = points.getJSONObject(p)
                            val values = pt.optJSONArray("value") ?: continue
                            if (values.length() == 0) continue

                            if (dataSourceId.contains("sleep", ignoreCase = true)) {
                                val stageCode = values.getJSONObject(0).optInt("intVal", 0)
                                val pStartNs = pt.optLong("startTimeNanos", 0L)
                                val pEndNs = pt.optLong("endTimeNanos", 0L)
                                val durMs = ((pEndNs - pStartNs) / 1_000_000L).coerceAtLeast(0L)
                                when (stageCode) {
                                    1 -> awakeMs += durMs
                                    2, 4 -> lightMs += durMs
                                    5 -> deepMs += durMs
                                    6 -> remMs += durMs
                                }
                            } else if (dataSourceId.contains("heart_rate", ignoreCase = true)) {
                                val bpm = values.getJSONObject(0).optDouble("fpVal", Double.NaN)
                                if (!bpm.isNaN() && bpm in 30.0..180.0) {
                                    hrValues.add(bpm)
                                }
                            }
                        }
                    }
                }

                val avgHr = if (hrValues.isNotEmpty()) hrValues.average().roundToInt() else null
                StageBreakdown(
                    remMinutes = (remMs / 60_000L).toInt(),
                    deepMinutes = (deepMs / 60_000L).toInt(),
                    lightMinutes = (lightMs / 60_000L).toInt(),
                    awakeMinutes = (awakeMs / 60_000L).toInt(),
                    avgHeartRateBpm = avgHr
                )
            }
        }.getOrElse { StageBreakdown(0, 0, 0, 0, null) }
    }

    private fun Int.ifZero(fallback: Int): Int = if (this == 0) fallback else this
}
