# NullFlow — Architecture Polish & UX Improvements (Feature Spec)

> **Status:** APPROVED — all cross-questions answered "Option A / defaults".
> **Package:** `com.codezmr.nullflow`
> **Companion decisions:** see inline notes; all Q1–Q6 = Option A.

---

## 1. Critical State & Service Fixes

### 1.1 QS Onboarding Bypass (Q3 = A)
**Problem:** Activating the shield via the QS tile bypasses the mandatory
Welcome/Consent screen.
**Fix:** In `FocusTileService.onClick()`, check `Settings.hasOnboarded`
(SharedPreferences) BEFORE showing the panel. If `false` → do NOT show the
panel; launch `MainActivity` (which shows Welcome) and collapse the shade.
- Use `Settings.get(context).hasOnboarded`.
- Launch via `startActivityAndCollapse` with a **PendingIntent** (see 1.4 note —
  `startActivityAndCollapse(Intent)` is disallowed on Android 15).

### 1.2 Frozen Notification Timer (Q1 = A)
**Problem:** The foreground notification timer only updates every 30s
(`delay(30_000)`), so it appears static between jumps.
**Fix:** Change `startTimerUpdates()` to tick **every second**
(`delay(1_000)`). Reliable across OEMs (vs. `Chronometer` in `RemoteViews`,
which breaks on skinned OS).

### 1.3 "End session" State Desync (Q2 = A)
**Problem:** The notification's "End session" sends only `ACTION_STOP` to the
service → tunnel stops + notification removed, but the **Room session is never
ended** and the profile is never deactivated. Result: tile (reads in-memory
`isShieldRunning`) shows OFF, but the panel (reads Room `runningSession` +
`activeProfile`) shows ON.
**Fix:** Make "End session" go through the SAME full stop path as the app
toggle — it must also end the Room session + deactivate the profile.
- Cleanest approach: the notification "End session" action should trigger the
  full stop (service teardown **and** Room cleanup). Since a `PendingIntent`
  from a notification can only target a service/broadcast/activity, the
  service's `ACTION_STOP`/`ACTION_STOP_SHIELD` handler will also perform the
  Room session-end + profile-deactivation (idempotent, guarded by
  "only if a running session exists").

### 1.4 `startActivityAndCollapse` PendingIntent (found in logs)
**Problem:** `startActivityAndCollapse(Intent)` throws
`UnsupportedOperationException` on Android 15 — it requires a **PendingIntent**.
**Fix:** Build a `PendingIntent.getActivity(...)` and pass that to
`startActivityAndCollapse(pendingIntent)`. Applies to both the onboarding gate
(1.1) and the "+ Create / Edit Modes" action.

---

## 2. UI & Aesthetic Polish

### 2.1 App Picker Search + Suggested Cluster (Q4 = A)
**File:** `ui/AppPickerSheet.kt`
- **Sticky search bar:** an `OutlinedTextField` at the top of the sheet that
  filters the app list (by label, case-insensitive). Filters the ENTIRE unified
  list (Suggested + main).
- **Suggested cluster:** a "Suggested" group at the very top = the
  high-distraction apps that are **actually installed**, sourced from the
  existing `PackageManagerRepo.targetPackages` list (WhatsApp, Instagram,
  TikTok, Snapchat, Twitter/X, Facebook, Discord, Telegram, etc.). The rest
  appear alphabetically below.
- Need `PackageManagerRepo` to expose which packages are "suggested" (or a
  helper that returns the suggested subset).

### 2.2 Neumorphic Hero Switch 3D (Q5 = A)
**File:** `ui/MainScreen.kt` (`HeroToggle` only — NOT the onboarding
`BreathingHero`, which stays flat/ethereal).
- Add a dark, offset drop-shadow to the **bottom-right** and a subtle
  semi-transparent white highlight to the **top-left** for a 3D extruded
  hardware feel.
- Implement via layered `Modifier.shadow` / gradient overlays on the existing
  circular switch (keep current size + colors).

### 2.3 Brand Consistency — Checkbox Accent
**File:** `ui/AppPickerSheet.kt`
- Override the default Material 3 blue on the app-picker checkboxes with the
  app's electric-cyan `#00E5FF` via `CheckboxDefaults.colors(checkedColor = …)`.

---

## 3. Quick Settings Panel Upgrade (Q6 = A)
**File:** `ui/tile/TileFocusPanel.kt`
- **Visual mode selector:** replace the "N apps shielded" text under each mode
  with a horizontal row of the target app icons.
- **Data:** add a `ProfileWithApps` Room query (profile + its blocked package
  names). Render a `LazyRow` of app icons (24dp, `CircleShape`, 8dp spacing).
- **Icon loading:** helper composable that loads `Drawable`/`Bitmap` icons via
  `PackageManager` from the saved package names; cache in memory.
- **Scroll hint:** `horizontalScroll` + a right-edge gradient fade so users
  know the list extends off-screen. Show ALL icons (scrollable), no hard cap.

---

## Verification
- `./gradlew assembleDebug` compiles clean.
- All Room interactions off the main thread (`Dispatchers.IO`).
- No UI leaks when the tile panel is dismissed.
- Device checks:
  - Notification timer ticks every second.
  - "End session" → tile AND panel both show OFF (no desync).
  - Tile tap before onboarding → forces Welcome screen.
  - App picker: search filters all; Suggested cluster at top; cyan checkboxes.
  - Hero toggle has 3D extruded look.
  - Tile panel shows app-icon rows per mode with right-edge fade.
