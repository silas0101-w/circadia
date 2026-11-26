# Fix Built-in Firebase AI Logic (No-API-Key Gemini) Integration

## Root Causes Identified
1. **Streaming + Strict `responseSchema` Incompatibility**: `GeminiImpactService` currently calls `generateContentStream()` on a model configured with `responseMimeType = "application/json"`, `responseSchema`, `systemInstruction`, and `maxOutputTokens`. In Firebase AI Logic (`Firebase.ai`), structured JSON schema output is designed for unary `generateContent()` calls; combining streaming with schema enforcement or failing on ANY exception (not just timeouts) prevented fallback to `gemini-3.5-flash-lite`.
2. **Primary Fallback Only Triggered on Timeouts**: Previously, `GeminiImpactService` only tried `gemini-3.5-flash-lite` on `TimeoutCancellationException`. Any HTTP 400/403/404/500 or schema error from the primary call immediately threw `"Firebase AI sleep impact analysis failed. Please try again."` without attempting the fallback model or a plain-text JSON fallback.
3. **Swallowed Diagnostic Error Context**: Both `GeminiImpactService` and `TodayViewModel` / `HistoryViewModel` replaced real Firebase AI exceptions with generic text, hiding the specific cause and technical details from the UI.
4. **Backend Provisioning & App Check Attestation Refresh**: Re-running `ProvisionFirebase` with `requested_products: ["firestore", "ai_logic"]` and the active debug keystore fingerprints ensures Firebase AI Logic and App Check debug attestation are actively registered on the backend for `com.aistudio.circadia.k7m2vx`.

---

## Implementation Plan

### 1. Re-Provision Firebase AI Logic & App Check (`ProvisionFirebase`)
- Call `rpc_action` (`service_name: "fax"`, `method_name: "ProvisionFirebase"`) with:
  - `platform: "android"`
  - `package_name: "com.aistudio.circadia.k7m2vx"`
  - `project_id: "ai-using-python"`
  - `sha1_certificate` and `sha256_certificate` extracted from `debug.keystore`
  - `requested_products: ["firestore", "ai_logic"]`
- Verify the SHA-1 `certificate_hash` in `app/google-services.json`.

### 2. Harden `AppCheckHelper.kt`
- Ensure `configureAppCheck(context, intent)`:
  - Keeps synchronous `.commit()` when writing `FIREBASE_APPCHECK_DEBUG_TOKEN` from the launch `Intent` to `com.google.firebase.appcheck.debug.store.$persistenceKey`.
  - Guards against redundant re-installation of `DebugAppCheckProviderFactory` across activity recreations so an active App Check token is not invalidated mid-session.
  - Exposes a lightweight diagnostic status helper (`AppCheckDiagnostics.lastStatusSummary`) for technical error reporting if an AI call fails.

### 3. Refactor `GeminiImpactService.kt` with 3-Stage Unary Recovery Cascade
- Use `Firebase.ai.generativeModel(...)` (the default Firebase AI Logic entry point per the `firebase-ai-logic-android` specification) initialized lazily via `by lazy`.
- Embed the clinical sleep physiologist instructions directly in the structured prompt as well as the model configuration, avoiding parameter rejection across model variants.
- Replace `generateContentStream()` with unary `withTimeout(60_000L) { model.generateContent(prompt) }` and implement the 3-tier execution cascade:
  1. **Tier 1 — Primary Structured JSON (`gemini-3.8-flash`)**: Unary `generateContent` with `responseMimeType = "application/json"` and `responseSchema = impactSchema`.
  2. **Tier 2 — Fast Fallback Structured JSON (`gemini-3.5-flash-lite`)**: Triggered on *any* non-cancellation failure from Tier 1 (not just timeouts).
  3. **Tier 3 — Plain-Text JSON Prompt Fallback (`gemini-3.8-flash` → `gemini-3.5-flash-lite`)**: Uses a plain `Firebase.ai.generativeModel(...)` without `responseSchema` constraints, instructing the model to return a JSON object with `severityRating`, `biologicalMechanism`, and `mitigationTips`, and parses it cleanly (supporting both raw JSON and markdown-fenced JSON blocks).
- Define a rich `GeminiAnalysisException(userMessage: String, technicalDetails: String, cause: Throwable?)` so that if all tiers fail, the service provides both a clear human-readable reason (e.g., App Check attestation 403, network offline, quota/rate limit, or service unavailable) and the full technical details (tier error chain, exception class, and HTTP/Firebase status code).

### 4. Surface Specific Reason & Expandable Technical Details in `AiImpactSheet.kt`
- Update `AiAnalysisUiState.Error` in `TodayViewModel.kt` and `HistoryViewModel.kt` to carry:
  - `message: String` (specific human-readable failure reason)
  - `technicalDetails: String? = null` (multi-tier diagnostic summary & exception message)
  - `lastEntry: SubstanceLogEntity? = null`
- Update `TodayViewModel` and `HistoryViewModel` `catch` blocks to preserve `GeminiAnalysisException` messages and technical details rather than overwriting them with a generic string, and log full exceptions with `Log.e(...)`.
- Update `AiImpactSheet.kt` error card:
  - Display the specific failure reason prominently.
  - Add an interactive **"Show Technical Details"** / **"Hide Technical Details"** toggle chip inside the error card (`Modifier.testTag("toggle_ai_error_details")`) that expands a monospace diagnostic box showing the exact exception chain and App Check status.

### 5. Verification & Unit Tests
- Add unit tests in `CircadiaUnitTest.kt` verifying:
  - `GeminiImpactService.parseFlexibleImpactJson(...)` parses raw JSON, markdown-fenced JSON, and JSON with surrounding commentary.
  - `GeminiImpactService.classifyFailureReason(...)` produces specific, accurate user-facing messages and technical details for App Check 403, quota 429, network, and timeout exceptions.
- Run `compile_applet` and `gradle :app:testDebugUnitTest` to verify a clean build and passing test suite.
