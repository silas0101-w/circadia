# Implementation Plan: Opt-In Persistent Database & Optional Cloud Sync

## 1. Overview & User Requirements
By default, **Circadia** will run in **Session-Only (In-Memory) Mode** so no logs, sleep sessions, or AI reports are written to persistent storage unless the user explicitly opts in via **Settings**.

Based on your preferences:
1. **Default State (`OFF`)**: Persistent database storage is **disabled by default**. All logged substances, sleep sessions, and AI impact reports live in a fast, reactive in-memory session store during the current app session.
2. **Settings Toggles (Local Database + Optional Cloud Sync)**:
   - **Enable Persistent Database (On-Device SQLite / Room)**: Toggle in **Settings** (`persistentDbEnabled = false` by default).
   - **Enable Cloud Database Sync**: Sub-option in **Settings** (`cloudSyncEnabled = false` by default, available when Persistent Database is enabled and a Google account is connected) to mirror and restore persistent records across sessions/devices.
3. **Turning Persistence ON (Automatic Session Migration)**:
   - When the user enables the Persistent Database toggle in **Settings**, all entries, sleep sessions, and AI reports created during the current in-memory session are automatically migrated (upserted) into the persistent Room database (and synced to cloud storage if Cloud Sync is also enabled).
4. **Turning Persistence OFF (Keep vs. Clear Confirmation Dialog)**:
   - When the user switches the Persistent Database toggle off, an interactive confirmation dialog asks how to handle previously saved database records:
     - **Keep Saved Records on Disk**: Copies the current records into the active in-memory session, pauses any future database writes, and leaves existing on-disk records intact for when persistence is re-enabled later.
     - **Clear Saved Database Records**: Permanently deletes all saved rows from the on-device SQLite database (and clears cached cloud backup state if requested) while keeping or resetting the active session cleanly.

---

## 2. Architecture & Data Layer Design

### A. Switchable `CircadiaDao` Routing (`SwitchableCircadiaDao`)
Because `SubstanceRepository`, `HealthSyncRepository`, and `SleepImpactAiRepository` all depend on the `CircadiaDao` interface:
- **`InMemoryCircadiaDao`**: A thread-safe, `MutableStateFlow`-backed implementation of `CircadiaDao` that stores `SubstanceLogEntity`, `SleepSessionEntity`, and `AiImpactReportEntity` in memory for the current process session, complete with auto-incrementing IDs and reactive `Flow` queries.
- **`SwitchableCircadiaDao` (Primary `CircadiaDao` injected into repositories)**:
  - Observes `persistentDbEnabled` from `UserPreferencesRepository` (default `false`).
  - Uses `flatMapLatest` on all `observe*()` `Flow` methods (`observeLogsForDate`, `observeAllLogs`, `observeAllSleepSessions`, `observeSleepSessionForDate`, `observeReport`, `observeReportsForDate`) to seamlessly switch between `inMemoryDao` (when `OFF`) and `roomDao` (when `ON`) without requiring app restarts or repository recreation.
  - Routes all `suspend` read/write mutations (`insertLog`, `insertLogs`, `updateLog`, `deleteLogById`, `upsertSleepSessions`, `upsertReport`, `deleteReport`) to the currently active store, and triggers optional Cloud Sync hooks when `persistentDbEnabled && cloudSyncEnabled`.
- **Additional DAO Maintenance Methods**:
  - Add `getAllReportsOnce(): List<AiImpactReportEntity>`, `upsertReports(reports: List<AiImpactReportEntity>)`, and `clearAllTablesData()` (`DELETE FROM substance_logs`, `DELETE FROM sleep_sessions`, `DELETE FROM ai_impact_reports`) to `CircadiaDao`.

### B. Session-to-Persistent Migration & Turn-Off Workflows
- **Enable Persistent Database (`enablePersistentDatabase()`)**:
  1. Snapshots all current session records (`logs`, `sleepSessions`, `aiReports`) from `InMemoryCircadiaDao`.
  2. Inserts/upserts them into the Room `CircadiaDatabase` inside a transaction (`insertLogs`, `upsertSleepSessions`, `upsertReports`), remapping any `entry:<id>` report keys if needed.
  3. Sets `persistentDbEnabled = true` in `UserPreferencesRepository`.
  4. Displays a confirmation banner in Settings (e.g., *"Persistent database enabled · Migrated X session log(s) to storage."*).
- **Disable Persistent Database (`disablePersistentDatabase(clearSavedData: Boolean)`)**:
  1. Snapshots current Room records into `InMemoryCircadiaDao` so the user's current screen state doesn't abruptly blank out while browsing the current session (or clears them if requested).
  2. Sets `persistentDbEnabled = false` (and `cloudSyncEnabled = false`) in `UserPreferencesRepository`.
  3. If `clearSavedData == true`, deletes all saved rows from the on-device Room SQLite database (`clearAllPersistentData()`).
  4. Displays a confirmation banner in Settings indicating whether on-disk records were preserved or cleared.

### C. Optional Cloud Database Sync (`CloudDatabaseSyncService`)
- When **Persistent Database** is enabled, the user can also toggle **Optional Cloud Sync** in the same Settings card.
- Uses authenticated HTTPS cloud storage / Firestore REST sync tied to the user's connected Google account (or prompts the user to connect via the existing in-app Google Sign-In flow if not yet signed in).
- Supports:
  - **Auto-Sync on Write** when `persistentDbEnabled && cloudSyncEnabled`.
  - **Manual "Sync Database Now" button** showing the 3-dot `GeminiSyncStatusBanner` while syncing and a last-synced timestamp badge.

---

## 3. UI & UX Changes (`SettingsScreen.kt` & `TodayScreen.kt`)

### A. Settings Screen — New "Database & Persistence" Card
Placed prominently near the top of **Settings** (`SettingsScreen.kt`):
1. **Status Header & Mode Badge**:
   - Shows an icon (`Storage` / `Memory`) and an animated status pill:
     - When `OFF`: **"Session-Only Mode (Default)"** — *"Logs stay in memory for this session only and are not saved to disk."*
     - When `ON`: **"Persistent SQLite Active"** — *"Logs, sleep sessions & AI reports are saved to encrypted/local device database ($N record(s) stored)."*
2. **Primary Toggle (`Switch`)**:
   - **"Enable Persistent Database"** (`Modifier.testTag("toggle_persistent_database")`).
   - Turning `ON` immediately migrates current session logs into Room.
   - Turning `OFF` opens the **Turn Off Persistent Database** `AlertDialog` with three clear actions:
     - **Keep Saved Data on Disk** (`Modifier.testTag("confirm_disable_keep_data")`)
     - **Clear All Saved Data** (`Modifier.testTag("confirm_disable_clear_data")`)
     - **Cancel** (`Modifier.testTag("cancel_disable_persistence")`)
3. **Expandable Secondary Section (visible when Persistent Database is `ON`)**:
   - **Optional Cloud Database Sync (`Switch`)** (`Modifier.testTag("toggle_cloud_database_sync")`): Syncs persistent database records with the user's Google account.
   - **Database Statistics & Actions Row**:
     - Shows stored record counts (`X logs · Y sleep sessions · Z AI reports`) and last cloud sync time.
     - **"Sync Cloud DB Now"** button (when Cloud Sync is enabled) and **"Clear Saved Database"** button with confirmation.

### B. Subtle Mode Indicator on Today Screen
- In `TodayScreen.kt`, next to the target bedtime pill or header subtitle, display a subtle status chip (`Session Only` vs `Saved to DB`) that taps through or informs the user of their active persistence mode.

---

## 4. Verification & Testing Plan
1. **Unit & Robolectric Tests (`CircadiaUnitTest.kt`)**:
   - Verify default state is `persistentDbEnabled == false` and writes go only to `InMemoryCircadiaDao` without writing to Room.
   - Verify enabling persistence migrates all in-memory session logs, sleep sessions, and AI reports into `CircadiaDatabase` (Room).
   - Verify disabling persistence with `clearSavedData = false` switches back to in-memory mode while retaining Room records on disk.
   - Verify disabling persistence with `clearSavedData = true` wipes all Room tables cleanly.
2. **Build & Test Execution**:
   - Run `compile_applet` and `gradle :app:testDebugUnitTest` to confirm zero build errors and 100% passing unit tests.
