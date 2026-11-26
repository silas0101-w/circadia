package com.example

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

object AppCheckDiagnostics {
    @Volatile
    var lastStatusSummary: String = "App Check not initialized"
        internal set

    internal val isProviderInstalled = AtomicBoolean(false)

    @Volatile
    internal var lastInstalledDebugToken: String? = null
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val app = FirebaseApp.getInstance()
        val projectId = app.options.projectId ?: "unknown"

        if (BuildConfig.DEBUG) {
            val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            val persistenceKey = app.persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            val tokenChanged = !intentDebugToken.isNullOrEmpty() &&
                intentDebugToken != AppCheckDiagnostics.lastInstalledDebugToken

            if (!intentDebugToken.isNullOrEmpty()) {
                // CRITICAL: Synchronous commit() ensures the debug secret is persisted
                // before DebugAppCheckProviderFactory reads it.
                val committed = prefs.edit()
                    .putString(DEBUG_SECRET_KEY, intentDebugToken)
                    .commit()
                AppCheckDiagnostics.lastInstalledDebugToken = intentDebugToken
                Log.d(TAG, "App Check debug secret configured (committed=$committed).")
                AppCheckDiagnostics.lastStatusSummary =
                    "Debug App Check active (intent token committed=$committed, project=$projectId)"
            } else {
                val hasPersistedSecret = !prefs.getString(DEBUG_SECRET_KEY, null).isNullOrBlank()
                if (hasPersistedSecret) {
                    Log.d(TAG, "Using previously persisted App Check debug secret.")
                    AppCheckDiagnostics.lastStatusSummary =
                        "Debug App Check active (persisted debug token, project=$projectId)"
                } else {
                    Log.w(TAG, "No debug token found in Intent extra or SharedPreferences.")
                    AppCheckDiagnostics.lastStatusSummary =
                        "Debug App Check active (auto-generated token; no intent token, project=$projectId)"
                }
            }

            if (AppCheckDiagnostics.isProviderInstalled.compareAndSet(false, true) || tokenChanged) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    if (AppCheckDiagnostics.isProviderInstalled.compareAndSet(false, true)) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    }
                    AppCheckDiagnostics.lastStatusSummary =
                        "Release App Check active (reCAPTCHA Enterprise, project=$projectId)"
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    AppCheckDiagnostics.lastStatusSummary =
                        "Release App Check warning: recaptcha_site_key is blank (project=$projectId)"
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                AppCheckDiagnostics.lastStatusSummary =
                    "Release App Check warning: recaptcha_site_key missing (project=$projectId)"
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        AppCheckDiagnostics.lastStatusSummary =
            "App Check init failed: ${e::class.java.simpleName}: ${e.message ?: "unknown error"}"
    }
}
