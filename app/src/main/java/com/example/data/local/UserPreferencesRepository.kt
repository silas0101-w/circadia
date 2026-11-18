package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.NvidiaNimService
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
    val pendingOAuthState: String = ""
) {
    val hasValidOAuthToken: Boolean
        get() = googleAccessToken.isNotBlank() && System.currentTimeMillis() < googleTokenExpiresAtMillis - 30_000L

    val isGoogleConnected: Boolean
        get() = hasValidOAuthToken || googleAccountSignedIn

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
    }

    val settingsFlow: Flow<UserSettings> = context.circadiaDataStore.data.map { prefs ->
        val modeName = prefs[Keys.AI_ENGINE_MODE] ?: AiEngineMode.AUTO_NVIDIA_THEN_GEMINI.name
        val mode = AiEngineMode.entries.find { it.name == modeName } ?: AiEngineMode.AUTO_NVIDIA_THEN_GEMINI
        val rawEndpoint = prefs[Keys.NVIDIA_ENDPOINT_URL]?.takeIf { it.isNotBlank() }
            ?: UserSettings.DEFAULT_NVIDIA_ENDPOINT
        val safeEndpoint = runCatching {
            NvidiaNimService.validateEndpointUrl(rawEndpoint).toString()
        }.getOrDefault(UserSettings.DEFAULT_NVIDIA_ENDPOINT)

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
            pendingOAuthState = KeystoreCrypto.decrypt(prefs[Keys.PENDING_OAUTH_STATE] ?: "")
        )
    }

    suspend fun getCurrentSettings(): UserSettings = settingsFlow.first()

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
