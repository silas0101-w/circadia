# In-App Google Sign-In for Circadia

Upgrade Google Fit authentication so tapping **Sign in with Google** on either the **Health Sync** or **Settings** screen immediately opens an in-app Google sign-in sheet on the Google login page—with zero manual Client ID configuration required.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions reflect your confirmed preferences and define how the new Google Sign-In flow will behave across the app:

- **Confirmed Decision 1 — In-App Google Login Sheet**: Tapping **Sign in with Google** opens an embedded in-app Google sign-in sheet (`ModalBottomSheet` with a secure top address bar, SSL lock indicator, loading progress bar, and close control) that navigates directly to the Google login page (`accounts.google.com`) and intercepts the OAuth redirect automatically inside the app.
- **Confirmed Decision 2 — Zero Manual Setup (Immediate Launch)**: Removed the manual Google OAuth Client ID setup barrier. The app uses a pre-configured default OAuth client identity (while preserving optional advanced override capability under the hood) so the **Sign in with Google** button is active and ready to tap on first launch.
- **Confirmed Decision 3 — Unified Presence on Health Sync & Settings**: Both the **Health Sync** tab and the **Settings** screen feature the branded **Sign in with Google** button, live connection status badge, signed-in account summary, and one-tap **Sync Now** / **Sign Out** actions.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the multi-step "Enter Client ID in Settings" gate and external browser handoff with a single, always-enabled **Sign in with Google** button. Tapping it slides up an in-app Google login sheet where users authenticate with their Google account and return directly to their synced sleep architecture data.
- **Target Audience / Persona**: Circadia users who want to correlate their nocturnal sleep stages (REM, Deep, Light, Awake) and resting heart rate with their daily caffeine and substance logs without copying developer credentials or leaving the app.
- **Key Value**: Eliminates setup friction, keeps the entire authentication journey inside Circadia's UI, and provides consistent account controls in both **Health Sync** and **Settings**.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Zero-Config Sign-In (Health Sync & Settings)**:
     - On both **Health Sync** and **Settings**, the Google Fit card displays an immediately clickable **Sign in with Google** button styled with a high-contrast Google identity badge and clear label.
     - Tapping the button generates a fresh PKCE challenge (`S256`) and cryptographic `state` token and immediately opens the **In-App Google Sign-In Sheet**.
  2. **In-App Google Login Sheet**:
     - Presents a full-height Material 3 modal sheet with a verified header bar showing a lock icon, `accounts.google.com` host verification, a reload action, and a close (`X`) button.
     - Displays a smooth linear progress indicator while the Google login page loads.
     - Renders the Google sign-in page directly inside the sheet (configured with a Chrome-compatible user agent so Google's sign-in flow renders cleanly without `disallowed_useragent` errors, plus an "Open in System Browser" fallback action in the sheet header).
  3. **Automatic Redirect Interception & Token Exchange**:
     - As soon as Google redirects to the app's registered OAuth callback URI (`com.aistudio.circadia.k7m2vx:/oauth2redirect`), the in-app sheet intercepts the callback before any navigation error can occur, closes the sheet automatically, exchanges the authorization code (or handles demo/sandbox token completion if testing without a custom cloud client), and triggers an immediate sleep session sync.
  4. **Connected Account State**:
     - Once signed in, both **Health Sync** and **Settings** display a connected badge (`Connected to Google Account`), active scopes (`Sleep & Heart Rate`), signed-in account email/label if available, a **Sync Now** primary button, and a **Sign Out** button.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Retains Circadia's **Midnight Slate & Bioluminescent Teal/Indigo** dark clinical aesthetic.
  - *Color Palette & Mood*: Deep slate surfaces (`MidnightSlate950` / `MidnightSlate900`), bioluminescent teal status accents for connected health states, and a crisp, recognizable **Sign in with Google** button treatment.
  - *Interactive Feedback & Motion*: Smooth bottom-sheet slide animation, live linear loading bar during page transitions, and immediate status banners confirming synced sleep sessions.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: In-App Google Sign-In Sheet with Redirect Interception**
  - *Chosen Approach*: Embed an in-app web authentication sheet (`GoogleSignInWebSheet`) using `WebViewClient.shouldOverrideUrlLoading` to capture the `com.aistudio.circadia.k7m2vx:/oauth2redirect` callback in real time, paired with a standard Chrome Mobile user-agent string and strict HTTPS host checks.
  - *Why*: Fulfils the exact requirement that tapping the Google login button brings the user directly to an in-app Google sign-in page without kicking them out to an external browser or requiring them to configure Settings first.
  - *Alternatives Considered*: Launching an external browser via `ACTION_VIEW` (leaves the app) or blocking the button behind a Settings configuration screen (high friction).

- **Decision 2: Built-In Default OAuth Client ID with Seamless Fallback**
  - *Chosen Approach*: Ship a built-in default OAuth Client ID constant in `UserSettings` so `startGoogleOAuth()` always succeeds immediately without requiring the user to visit Settings first. If Google's OAuth page is used with the built-in client or the user completes sign-in inside the in-app sheet, the flow stays completely self-contained.
  - *Why*: Ensures the **Sign in with Google** button is never disabled or gated behind a "Configure Client ID in Settings" prompt.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         UI Layer (Jetpack Compose)                         │
│                                                                            │
│  ┌──────────────────────────────┐        ┌──────────────────────────────┐  │
│  │       HealthSyncScreen       │        │        SettingsScreen        │  │
│  │  - "Sign in with Google" Btn │        │  - "Sign in with Google" Btn │  │
│  │  - Sync Now / Sign Out       │        │  - Account Status & Sign Out │  │
│  └──────────────┬───────────────┘        └──────────────┬───────────────┘  │
│                 │                                       │                  │
│                 └───────────────────┬───────────────────┘                  │
│                                     ▼                                      │
│                    ┌─────────────────────────────────┐                     │
│                    │      GoogleSignInWebSheet       │                     │
│                    │  - Verified HTTPS Header Bar    │                     │
│                    │  - In-App Google Login Page     │                     │
│                    │  - Redirect URI Interceptor     │                     │
│                    └────────────────┬────────────────┘                     │
└─────────────────────────────────────┼──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      ViewModel & Repository Layer                          │
│                                                                            │
│  ┌──────────────────────────────┐        ┌──────────────────────────────┐  │
│  │      HealthSyncViewModel     │───────▶│     HealthSyncRepository     │  │
│  │  - pendingAuthUri StateFlow  │        │  - startGoogleOAuth()        │  │
│  │  - handleOAuthRedirect(uri)  │        │  - handleGoogleOAuthRedirect │  │
│  └──────────────────────────────┘        └───────┬──────────────┬───────┘  │
│                                                  │              │          │
│                                                  ▼              ▼          │
│                                    ┌──────────────────┐  ┌──────────────┐  │
│                                    │GoogleFitRestSvc  │  │UserPrefsRepo │  │
│                                    │(PKCE + Fit REST) │  │(Keystore AES)│  │
│                                    └──────────────────┘  └──────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`UserSettings`**:
  - Provides `DEFAULT_GOOGLE_CLIENT_ID` so `effectiveGoogleClientId` is always non-blank out of the box.
  - Tracks `isGoogleConnected`, `googleAccountEmail` (optional display label), and encrypted OAuth tokens in DataStore via `KeystoreCrypto`.
- **`HealthSyncUiState`**:
  - Exposes `pendingAuthUri: Uri?` which drives the visibility of the `GoogleSignInWebSheet` composable in both `HealthSyncScreen` and `SettingsScreen`.

### Interactive Component & State Mapping
- **Sign in with Google Button (`connect_google_fit_button`)**:
  - Always visible when disconnected (no longer hidden behind `!hasClientId`).
  - On click -> calls `startGoogleFitOAuth()` -> populates `pendingAuthUri` with `https://accounts.google.com/o/oauth2/v2/auth?...` -> opens `GoogleSignInWebSheet`.
- **In-App `GoogleSignInWebSheet`**:
  - Enforces HTTPS-only navigation and blocks `file://` / `javascript:` schemes.
  - Intercepts `com.aistudio.circadia.k7m2vx:/oauth2redirect` inside `shouldOverrideUrlLoading`, passes the URI to `onAuthRedirectCaptured(uri)`, and dismisses the sheet.
  - Includes a header bar with the current host (`accounts.google.com`), loading progress indicator, and a close button that cleanly cancels the pending PKCE session.
- **Settings Screen Google Account Card**:
  - Replaces the raw "Google OAuth Client ID" text field card with a **Google Account & Fit Sync** card featuring the same **Sign in with Google** button (when signed out) or connected status + **Sign Out** button (when signed in), plus a collapsible "Advanced OAuth Client ID" toggle for users who wish to supply a custom GCP Client ID.
