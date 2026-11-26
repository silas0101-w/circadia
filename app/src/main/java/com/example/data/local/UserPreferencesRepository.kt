package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.NvidiaNimService
import com.example.domain.DecayModelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.circadiaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "circadia_settings"
)

enum class AiEngineMode(val label: String, val description: String) {
    AUTO_NVIDIA_THEN_GEMINI(
        "Auto (NVIDIA NIM -> Gemini)",
        "Uses NVIDIA NIM when an API key is saved, otherwise uses built-in Gemini AI."
    ),
    NVIDIA_ONLY(
        "NVIDIA NIM Only",
        "Always calls your configured NVIDIA NIM endpoint and model."
    ),
    GEMINI_ONLY(
        "Built-in Gemini AI",
        "Uses Firebase AI Logic (gemini-3.8-flash) with no external API key needed."
    )
}

data class PendingCloudDeletions(
    val logDocIds: Set<String> = emptySet(),
    val sleepDocIds: Set<String> = emptySet(),
    val reportDocIds: Set<String> = emptySet()
)

data class UserSettings(
    val nvidiaApiKey: String = "",
    val nvidiaModelId: String = DEFAULT_NVIDIA_MODEL,
    val nvidiaEndpointUrl: String = DEFAULT_NVIDIA_ENDPOINT,
    val aiEngineMode: AiEngineMode = AiEngineMode.AUTO_NVIDIA_THEN_GEMINI,
    val targetBedtimeMinutes: Int = 22 * 60 + 30, // 10:30 PM
    val googleClientId: String = "",
    val googleAccessToken: String = "",
    val googleTokenExpiresAtMillis: Long = 0L,
    val googleAccountSignedIn: Boolean = false,
    val pendingOAuthCodeVerifier: String = "",
    val pendingOAuthState: String = "",
    val persistentDbEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val lastCloudDbSyncMillis: Long = 0L,
    val firebaseUserEmail: String = "",
    val decayModelMode: DecayModelMode = DecayModelMode.ONSET_RAMP
) {
    fun isOAuthTokenValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        googleAccessToken.isNotBlank() && nowMillis < googleTokenExpiresAtMillis - 30_000L

    fun isGoogleConnectedAt(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isOAuthTokenValid(nowMillis) || googleAccountSignedIn

    val hasValidOAuthToken: Boolean
        get() = isOAuthTokenValid()

    val isGoogleConnected: Boolean
        get() = isGoogleConnectedAt()

    companion object {
        const val DEFAULT_NVIDIA_MODEL = "meta/llama-3.3-70b-instruct"
        const val DEFAULT_NVIDIA_ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
    }
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val NVIDIA_API_KEY = stringPreferencesKey("nvidia_api_key")
        val NVIDIA_MODEL_ID = stringPreferencesKey("nvidia_model_id")
        val NVIDIA_ENDPOINT_URL = stringPreferencesKey("nvidia_endpoint_url")
        val AI_ENGINE_MODE = stringPreferencesKey("ai_engine_mode")
        val TARGET_BEDTIME_MINUTES = intPreferencesKey("target_bedtime_minutes")
        val GOOGLE_CLIENT_ID = stringPreferencesKey("google_client_id")
        val GOOGLE_ACCESS_TOKEN = stringPreferencesKey("google_access_token")
        val GOOGLE_TOKEN_EXPIRES_AT = longPreferencesKey("google_token_expires_at")
        val GOOGLE_ACCOUNT_SIGNED_IN = booleanPreferencesKey("google_account_signed_in")
        val PENDING_OAUTH_VERIFIER = stringPreferencesKey("pending_oauth_verifier")
        val PENDING_OAUTH_STATE = stringPreferencesKey("pending_oauth_state")
        val PERSISTENT_DB_ENABLED = booleanPreferencesKey("persistent_db_enabled")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val LAST_CLOUD_DB_SYNC_MILLIS = longPreferencesKey("last_cloud_db_sync_millis")
        val FIREBASE_USER_EMAIL = stringPreferencesKey("firebase_user_email")
        val DECAY_MODEL_MODE = stringPreferencesKey("decay_model_mode")
        val PENDING_DELETED_LOGS = stringSetPreferencesKey("pending_deleted_cloud_logs")
        val PENDING_DELETED_SLEEP = stringSetPreferencesKey("pending_deleted_cloud_sleep")
        val PENDING_DELETED_REPORTS = stringSetPreferencesKey("pending_deleted_cloud_reports")
    }

    val settingsFlow: Flow<UserSettings> = context.circadiaDataStore.data.map { prefs ->
        val modeName = prefs[Keys.AI_ENGINE_MODE] ?: AiEngineMode.AUTO_NVIDIA_THEN_GEMINI.name
        val mode = AiEngineMode.entries.find { it.name == modeName } ?: AiEngineMode.AUTO_NVIDIA_THEN_GEMINI
        val rawEndpoint = prefs[Keys.NVIDIA_ENDPOINT_URL]?.takeIf { it.isNotBlank() }
            ?: UserSettings.DEFAULT_NVIDIA_ENDPOINT
        val safeEndpoint = runCatching {
            NvidiaNimService.validateEndpointUrl(rawEndpoint).toString()
        }.getOrDefault(UserSettings.DEFAULT_NVIDIA_ENDPOINT)

        val decayModeName = prefs[Keys.DECAY_MODEL_MODE] ?: DecayModelMode.ONSET_RAMP.name
        val decayMode = DecayModelMode.entries.find { it.name == decayModeName }
            ?: DecayModelMode.ONSET_RAMP

        UserSettings(
            nvidiaApiKey = KeystoreCrypto.decrypt(prefs[Keys.NVIDIA_API_KEY] ?: ""),
            nvidiaModelId = prefs[Keys.NVIDIA_MODEL_ID]?.takeIf { it.isNotBlank() }
                ?: UserSettings.DEFAULT_NVIDIA_MODEL,
            nvidiaEndpointUrl = safeEndpoint,
            aiEngineMode = mode,
            targetBedtimeMinutes = prefs[Keys.TARGET_BEDTIME_MINUTES] ?: (22 * 60 + 30),
            googleClientId = prefs[Keys.GOOGLE_CLIENT_ID] ?: "",
            googleAccessToken = KeystoreCrypto.decrypt(prefs[Keys.GOOGLE_ACCESS_TOKEN] ?: ""),
            googleTokenExpiresAtMillis = prefs[Keys.GOOGLE_TOKEN_EXPIRES_AT] ?: 0L,
            googleAccountSignedIn = prefs[Keys.GOOGLE_ACCOUNT_SIGNED_IN] ?: false,
            pendingOAuthCodeVerifier = KeystoreCrypto.decrypt(prefs[Keys.PENDING_OAUTH_VERIFIER] ?: ""),
            pendingOAuthState = KeystoreCrypto.decrypt(prefs[Keys.PENDING_OAUTH_STATE] ?: ""),
            persistentDbEnabled = prefs[Keys.PERSISTENT_DB_ENABLED] ?: false,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: false,
            lastCloudDbSyncMillis = prefs[Keys.LAST_CLOUD_DB_SYNC_MILLIS] ?: 0L,
            firebaseUserEmail = prefs[Keys.FIREBASE_USER_EMAIL] ?: "",
            decayModelMode = decayMode
        )
    }

    suspend fun getCurrentSettings(): UserSettings = settingsFlow.first()

    suspend fun getPendingCloudDeletions(): PendingCloudDeletions {
        val prefs = context.circadiaDataStore.data.first()
        return PendingCloudDeletions(
            logDocIds = prefs[Keys.PENDING_DELETED_LOGS] ?: emptySet(),
            sleepDocIds = prefs[Keys.PENDING_DELETED_SLEEP] ?: emptySet(),
            reportDocIds = prefs[Keys.PENDING_DELETED_REPORTS] ?: emptySet()
        )
    }

    suspend fun addPendingDeletedLog(docId: String) {
        if (docId.isBlank()) return
        context.circadiaDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_DELETED_LOGS] ?: emptySet()
            prefs[Keys.PENDING_DELETED_LOGS] = (current + docId).take(500).toSet()
        }
    }

    suspend fun removePendingDeletedLogs(docIds: Set<String>) {
        if (docIds.isEmpty()) return
        context.circadiaDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_DELETED_LOGS] ?: emptySet()
            prefs[Keys.PENDING_DELETED_LOGS] = current - docIds
        }
    }

    suspend fun addPendingDeletedSleep(docId: String) {
        if (docId.isBlank()) return
        context.circadiaDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_DELETED_SLEEP] ?: emptySet()
            prefs[Keys.PENDING_DELETED_SLEEP] = (current + docId).take(500).toSet()
        }
    }

    suspend fun removePendingDeletedSleep(docIds: Set<String>) {
        if (docIds.isEmpty()) return
        context.circadiaDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_DELETED_SLEEP] ?: emptySet()
            prefs[Keys.PENDING_DELETED_SLEEP] = current - docIds
        }
    }

    suspend fun addPendingDeletedReport(docId: String) {
        if (docId.isBlank()) return
        context.circadiaDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_DELETED_REPORTS] ?: emptySet()
            prefs[Keys.PENDING_DELETED_REPORTS] = (current + docId).take(500).toSet()
        }
    }

    suspend fun removePendingDeletedReports(docIds: Set<String>) {
        if (docIds.isEmpty()) return
        context.circadiaDataStore.edit { prefs ->
            val current = prefs[Keys.PENDING_DELETED_REPORTS] ?: emptySet()
            prefs[Keys.PENDING_DELETED_REPORTS] = current - docIds
        }
    }

    suspend fun clearAllPendingCloudDeletions() {
        context.circadiaDataStore.edit { prefs ->
            prefs.remove(Keys.PENDING_DELETED_LOGS)
            prefs.remove(Keys.PENDING_DELETED_SLEEP)
            prefs.remove(Keys.PENDING_DELETED_REPORTS)
        }
    }

    suspend fun updateNvidiaSettings(
        apiKey: String,
        modelId: String,
        endpointUrl: String,
        engineMode: AiEngineMode
    ) {
        val candidateUrl = endpointUrl.trim().ifBlank { UserSettings.DEFAULT_NVIDIA_ENDPOINT }
        val validatedUrl = NvidiaNimService.validateEndpointUrl(candidateUrl).toString()

        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.NVIDIA_API_KEY] = KeystoreCrypto.encrypt(apiKey.trim())
            prefs[Keys.NVIDIA_MODEL_ID] = modelId.trim().ifBlank { UserSettings.DEFAULT_NVIDIA_MODEL }
            prefs[Keys.NVIDIA_ENDPOINT_URL] = validatedUrl
            prefs[Keys.AI_ENGINE_MODE] = engineMode.name
        }
    }

    suspend fun updateTargetBedtime(minutesOfDay: Int) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.TARGET_BEDTIME_MINUTES] = minutesOfDay.coerceIn(0, 23 * 60 + 59)
        }
    }

    suspend fun updateDecayModelMode(mode: DecayModelMode) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.DECAY_MODEL_MODE] = mode.name
        }
    }

    suspend fun setPersistentDbEnabled(enabled: Boolean) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.PERSISTENT_DB_ENABLED] = enabled
            if (!enabled) {
                prefs[Keys.CLOUD_SYNC_ENABLED] = false
            }
        }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.CLOUD_SYNC_ENABLED] = enabled
        }
    }

    suspend fun updateLastCloudDbSync(timestampMillis: Long) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.LAST_CLOUD_DB_SYNC_MILLIS] = timestampMillis
        }
    }

    suspend fun updateFirebaseUserEmail(email: String) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.FIREBASE_USER_EMAIL] = email.trim()
        }
    }

    suspend fun updateGoogleClientId(clientId: String) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_CLIENT_ID] = clientId.trim()
        }
    }

    suspend fun savePendingOAuthPkce(codeVerifier: String, state: String) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.PENDING_OAUTH_VERIFIER] = KeystoreCrypto.encrypt(codeVerifier)
            prefs[Keys.PENDING_OAUTH_STATE] = KeystoreCrypto.encrypt(state)
        }
    }

    suspend fun clearPendingOAuthPkce() {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.PENDING_OAUTH_VERIFIER] = ""
            prefs[Keys.PENDING_OAUTH_STATE] = ""
        }
    }

    suspend fun saveGoogleAccessToken(accessToken: String, expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCESS_TOKEN] = KeystoreCrypto.encrypt(accessToken)
            prefs[Keys.GOOGLE_TOKEN_EXPIRES_AT] = expiresAt
            prefs[Keys.GOOGLE_ACCOUNT_SIGNED_IN] = true
            prefs[Keys.PENDING_OAUTH_VERIFIER] = ""
            prefs[Keys.PENDING_OAUTH_STATE] = ""
        }
    }

    suspend fun markGoogleAccountSignedIn(signedIn: Boolean) {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCOUNT_SIGNED_IN] = signedIn
            if (!signedIn) {
                prefs[Keys.GOOGLE_ACCESS_TOKEN] = ""
                prefs[Keys.GOOGLE_TOKEN_EXPIRES_AT] = 0L
            }
            prefs[Keys.PENDING_OAUTH_VERIFIER] = ""
            prefs[Keys.PENDING_OAUTH_STATE] = ""
        }
    }

    suspend fun clearGoogleAuth() {
        context.circadiaDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCESS_TOKEN] = ""
            prefs[Keys.GOOGLE_TOKEN_EXPIRES_AT] = 0L
            prefs[Keys.GOOGLE_ACCOUNT_SIGNED_IN] = false
            prefs[Keys.PENDING_OAUTH_VERIFIER] = ""
            prefs[Keys.PENDING_OAUTH_STATE] = ""
        }
    }
}
