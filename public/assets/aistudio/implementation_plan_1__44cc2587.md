# Gemini-Style 3-Dot Generating Animation & Fluid UI Motion System

This update replaces standard circular spinners and linear loading bars with a signature Gemini-inspired bouncing three-dot wave in Circadia's theme accent colors, and introduces cohesive spring-based motion, animated counters, chart transitions, and screen crossfades so the entire interface feels responsive and fluid.

## User Review & Critical Decisions

> [!IMPORTANT]
> All core motion and design choices below reflect your confirmed preferences from our clarification step:

- **Confirmed Decision 1 — 3-Dot Indicator Style**: A rhythmic, phase-shifted bouncing three-dot wave (`rememberInfiniteTransition`) using Circadia's accent palette (`WarmAmber400`, `SoftTeal300`, and `SleepIndigo400`) with subtle vertical lift, scale pulse, and soft glow halo.
- **Confirmed Decision 2 — Universal Loading Replacement**: The 3-dot bouncing indicator replaces both the `CircularProgressIndicator` in the AI Sleep Impact sheet and the `LinearProgressIndicator` in the Health Sync screen, and also appears inline when syncing or generating reports.
- **Confirmed Decision 3 — Comprehensive UI Smoothness Scope**: All four motion domains will be implemented:
  1. Smooth navigation tab transitions and `LazyColumn` item placement animations.
  2. Animated score counters, color transitions, and chart curve/bar reveals.
  3. Spring-animated calendar day selection and tactile press-scale micro-interactions.
  4. `AnimatedContent` state crossfades inside bottom sheets, banners, and cards.

---

## 1. Overview & Core Concept

- **What It Does**: Elevates Circadia from static state jumps to a choreographed, physics-driven experience. Waiting for AI pharmacology analysis or Health Sync displays a calm, conversational 3-dot wave, while every dose adjustment, date switch, and screen change animates smoothly.
- **Target Audience / Persona**: Evening and daytime users logging stimulants, medications, or sleep sessions who want immediate, tactile visual feedback as their circadian risk score and bedtime clearance curve update in real time.
- **Key Value**: Eliminates jarring layout pops, abrupt spinner swaps, and instant chart jumps—making complex pharmacokinetic math feel tangible and alive.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **AI Sleep Impact Generation**: Tapping *Analyze Sleep Impact* or *Analyze Why & Severity* opens the analysis sheet with the bouncing 3-dot wave and an animated status subtitle. When streaming completes, the sheet smoothly crossfades (`AnimatedContent`) from the 3-dot wave into the structured severity badge, biological mechanism card, and staggered mitigation tips.
  2. **Health Sync Operations**: Triggering Google Fit sync or importing a Samsung Health / Health Connect file displays an elevated status pill featuring the compact 3-dot wave alongside the active sync message, sliding in and out smoothly.
  3. **Live Dose & Time Adjustments**: Tapping inline quick-stepper pills (`+50 mg`, `-30m`) or editing an entry smoothly interpolates the Disruption Score counter, gauge arc, severity tint, and clearance decay curve rather than snapping between values.
  4. **Tab & Calendar Exploration**: Switching between *Today*, *History*, *Health Sync*, and *Settings* uses a fast fade-and-slide transition, while tapping dates on the History calendar springs the selected cell scale and smoothly animates the 14-day trend bars and sleep stage distribution bar.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Nocturnal bioluminescence—deep midnight slate surfaces (`MidnightSlate950` / `MidnightSlate900`) paired with luminous, smooth-moving accents.
  - *Color Palette & Mood*:
    - Dot 1: Warm Amber (`#F5A623` — primary circadian accent)
    - Dot 2: Soft Teal (`#5EEAD4` — restorative deep-sleep accent)
    - Dot 3: Sleep Indigo (`#818CF8` — REM architecture accent)
  - *Typography & Hierarchy*: Tabular numeric transitions on large score readouts (`displayMedium`) so digits never jitter horizontally while counting up or down.

- **Interactive Feedback & Motion**:
  - **3-Dot Generating Wave**: Each dot runs a 1,050 ms cycle with a 140 ms phase offset per dot, combining vertical translation (`-7.dp` peak bounce), scale (`0.85f` to `1.18f`), and alpha (`0.55f` to `1.0f`) with a subtle radial glow underneath.
  - **Sub-300ms Spring Physics**: Interactive controls (calendar cells, quick-step pills, primary action buttons, and expandable cards) use medium-bouncy spring specifications (`DampingRatioMediumBouncy`, `StiffnessMediumLow`) so touch release feels immediate and never sluggish.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Reusable 3-Dot Wave Component with Size Presets**
  - *Chosen Approach*: Build a dedicated composable supporting both a hero display size (for the AI Analysis sheet loading state with a subtle ambient container) and a compact inline size (for Health Sync working banners and button indicators).
  - *Why*: Ensures visual consistency across all asynchronous states while fitting naturally into both spacious modal sheets and compact status bars.
  - *Alternatives Considered*: Separate custom animations per screen, which would fragment the visual language and duplicate infinite transition state.

- **Decision 2: Physics-Backed State Interpolation Over Fixed Delays**
  - *Chosen Approach*: Use interruptible Compose state animations (`animateIntAsState`, `animateFloatAsState`, `animateColorAsState`, and `AnimatedContent`) driven directly by ViewModel state emissions.
  - *Why*: If a user rapidly taps `+50 mg` three times in a row, interruptible spring animations smoothly redirect mid-flight without queuing or blocking touch input.
  - *Alternatives Considered*: Fixed-duration choreographed coroutines that block interaction until finished.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Motion Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         Circadia App Root & NavHost                       │
│   • Fade + subtle horizontal slide transitions between 4 top-level tabs   │
└───────────────┬───────────────────────────┬───────────────────────────────┘
                │                           │
                ▼                           ▼
┌───────────────────────────────┐ ┌─────────────────────────────────────────┐
│     Shared Motion Primitives  │ │      Animated Charts & Data Readouts    │
│ • GeminiThreeDotsLoader       │ │ • DisruptionScoreGauge (sweep + count   │
│   (3-dot phase-shifted wave   │ │   + animated severity color)            │
│    in Amber/Teal/Indigo)      │ │ • ClearanceDecayChart (progressive      │
│ • pressScaleClickable         │ │   path reveal + pulsing "Now" beacon)   │
│   (InteractionSource spring)  │ │ • HistoryTrendChart & SleepStageBar     │
│ • AnimatedContent crossfades  │ │   (spring-interpolated bar heights/     │
│   for sheets & status banners │ │    stage weights)                       │
└───────────────┬───────────────┘ └─────────────────┬───────────────────────┘
                │                                   │
                ▼                                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                   Screen & Sheet Interactive Surfaces                     │
│ • AiImpactSheet: Crossfades Loading (3-Dot Wave) ──► Success / Error      │
│ • HealthSyncScreen: AnimatedVisibility working banner with 3-Dot Wave     │
│ • TodayScreen & HistoryScreen: LazyColumn animateItem() + spring calendar │
│ • SubstanceEntryCard: Spring border/elevation + smooth quick-stepper tray │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- No database schema migrations are required; all motion states derive reactively from existing `TodayUiState`, `HistoryUiState`, `HealthSyncUiState`, and `AiAnalysisUiState` flows.
- `AiAnalysisUiState` transitions (`Idle` -> `Loading` -> `Success` / `Error`) drive `AnimatedContent` inside the AI sheet so the 3-dot wave morphs seamlessly into the analysis cards.

### Interactive Component & State Mapping
- **Gemini 3-Dot Wave Loader**:
  - Uses `rememberInfiniteTransition` with 3 staggered phase offsets (`0ms`, `140ms`, `280ms`).
  - Renders three rounded dots in `WarmAmber400`, `SoftTeal300`, and `SleepIndigo400` with synchronized vertical bounce, scale, and soft ambient glow.
- **AI Analysis Sheet**:
  - Replaces `CircularProgressIndicator` with the hero `GeminiThreeDotsLoader` and a gently pulsing status label.
  - Wraps the `Loading`, `Success`, and `Error` branches in `AnimatedContent` (fade + vertical slide + size transform) so results unfold smoothly once Gemini or NVIDIA NIM responds.
- **Health Sync Screen & Settings Sync Status**:
  - Replaces the raw `LinearProgressIndicator` with an `AnimatedVisibility` card containing the compact 3-dot loader and active sync label.
  - Animates status/error message banners and correlation list items (`animateItem()`).
- **Disruption Score Gauge & Charts**:
  - `DisruptionScoreGauge`: Animates the integer score readout (`animateIntAsState`), the arc sweep (`animateFloatAsState` with spring physics), and the severity badge color (`animateColorAsState`).
  - `ClearanceDecayChart`: Animates horizontal path reveal on entry and adds a subtle breathing halo around the current-time ("Now") marker on the decay curve.
  - `HistoryTrendChart` & `SleepStageBar`: Animates bar heights and sleep-stage proportional weights using spring physics when switching dates on the History calendar.
- **Navigation, Calendar & Cards**:
  - `NavHost`: Configures smooth `fadeIn` + `slideIntoContainer` / `fadeOut` + `slideOutOfContainer` transitions across all top-level destinations.
  - `HistoryScreen` Calendar Grid: Animates day cell scale (`animateFloatAsState` with bouncy spring) and container/border colors (`animateColorAsState`) on date selection, plus crossfades the selected day summary header.
  - `SubstanceEntryCard`: Applies `Modifier.animateItem()` in `TodayScreen` and `HistoryScreen` timelines, animates the expand/collapse chevron rotation (`animateFloatAsState`), animates card border/background tint, and adds tactile press-scale feedback to quick-step pills.
