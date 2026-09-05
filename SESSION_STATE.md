# NullFlow — Session State

> **Read this first to resume.** Last updated: 2026-09-05.
> Goal: per-app internet kill-switch ("selective Offline Switch") via a local
> VPN blackhole. Premium UI: hero toggle + haptics + bottom sheets.
>
> **Brand:** NullFlow · package `com.codezmr.nullflow` · company Codezmr
> Tagline: "Disconnect on your terms." (full branding in doc/APP_IDEA.md)
>
> ⚠️ **HARD RULE: NEVER build the APK without asking Zamir first.**
> Discuss + finish all code changes, THEN ask "build or not?". Wait for the go-ahead.
>
> 🔒 **GIT IS LOCAL-ONLY — NO REMOTE, EVER.** `git init` + `git commit` only.
> NEVER run `git remote add`, `git push`, or any network/git-cloud command.

---

## ✅ Current Status: BUILT — Architecture Polish & UX Improvements (all 6 done)

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.
**Spec:** `doc/POLISH_SPEC.md` (all Q1–Q6 = Option A, approved by Zamir).

### What changed (all Option A)
1. **Q1 — live notification timer:** `FocusVpnService.startTimerUpdates` now
   ticks every **1s** (`delay(1_000)`) instead of 30s.
2. **Q2 — "End session" desync FIXED:** `FocusVpnService.teardown()` now calls
   `clearRoomSession()` (ends running session + deactivates profile) on a fresh
   one-shot scope (serviceScope is already cancelled). EVERY stop path (app
   toggle / notification / QS tile) now keeps Room in lockstep → tile + panel
   can't disagree.
3. **Q3 — QS onboarding gate:** `FocusTileService.onClick()` checks
   `Settings.hasOnboarded`; if false → skips the panel and forces MainActivity
   (Welcome) + collapses. Also fixed `startActivityAndCollapse` to use a
   **PendingIntent** (Intent form is disallowed on Android 15 — was crashing in
   logs).
4. **Q4 — "Focus Matrix" app picker (NO checkboxes):** `AppPickerSheet` fully
   overhauled — sticky dark-glass search bar (filters all), one-tap Preset
   Chips (💬 Social Noise / 🎬 Media Binge / 💬 Chat Drops, from
   `PackageManagerRepo.presets`), Tactile App Cards (unselected `#12151C` +
   `+ ADD` + desaturated icon; selected cyan glow `#00E5FF` + `🔒 SHIELDED` +
   icon halo), micro-spring press (0.96× bouncy) + `Haptics.thud` (40ms).
   Icons via built-in `BitmapPainter` (Coil/Accompanist unavailable offline).
   New `Haptics.thud()`.
5. **Q5 — hero 3D extrusion:** `MainScreen.HeroToggle` now has a dark offset
   drop-shadow (bottom-right, ambient+spot black) + top-left light→dark bevel
   gradient + subtle white radial specular highlight. BreathingHero untouched.
6. **Q6 — tile panel icon rows:** `TileFocusPanel` now shows a scrollable
   `LazyRow` of blocked-app icons (24dp circle, 8dp spacing) + right-edge
   gradient fade per mode. New `ProfileWithAppsRow` Room query
   (`observeProfilesWithApps`), `AppIconLoader.kt` (PackageManager icon loader
   + in-memory cache + `rememberAppIconPainter`).

### ⚠️ Still to verify on device
- Notification timer ticks every second.
- "End session" → tile AND panel both OFF (no desync).
- Tile tap before onboarding → forces Welcome.
- App picker: search filters all; preset chips block/unblock categories;
  tactile cards + spring + haptic; cyan accents.
- Hero toggle 3D look.
- Tile panel: app-icon rows per mode + right-edge fade.

**Next:** Zamir installs, tests all 6. Share `Download/NullFlow/nullflow.log`
if anything misbehaves.

---

## 🚧 In Progress: Architecture Polish & UX Improvements

**Spec:** `doc/POLISH_SPEC.md` (all Q1–Q6 = Option A, approved by Zamir).
**Status:** docs written, GhostShield committed; implementing the 6 items.

### Scope (all Option A)
1. **Q1** — notification timer ticks every 1s (`delay(1_000)` in
   `FocusVpnService.startTimerUpdates`).
2. **Q2** — fix "End session" desync: the service STOP handler also ends the
   Room session + deactivates the profile (so tile + panel agree).
3. **Q3** — QS onboarding gate: `FocusTileService.onClick()` checks
   `Settings.hasOnboarded`; if false → launch MainActivity (Welcome) + collapse.
4. **Q4 (UPGRADED → "Focus Matrix")** — app picker full overhaul, NO
   checkboxes: sticky dark-glass search bar (filters all) + one-tap Preset
   Chips (💬 Social Noise / 🎬 Media Binge / 💬 Chat Drops) + Tactile App Cards
   (unselected `#12151C` + `+ ADD`; selected cyan glow `#00E5FF` + `🔒 SHIELDED`
   + icon halo) + micro-spring press (0.96× bouncy) + thud haptic. Icons via
   built-in `BitmapPainter` (Coil/Accompanist unavailable offline).
5. **Q5** — `HeroToggle` (MainScreen only) gets a 3D extruded look (dark
   bottom-right shadow + white top-left highlight). BreathingHero stays flat.
6. **Q6** — tile panel: replace "N apps shielded" text with a `LazyRow` of
   blocked-app icons (24dp circle, 8dp spacing) + right-edge gradient fade.
   New `ProfileWithApps` Room query + PackageManager icon loader (cached).
7. **Bonus (from logs):** `startActivityAndCollapse` must use a **PendingIntent**
   (Intent form is disallowed on Android 15).

**Next:** implement 1–7, build, device-test.

---

## ✅ Current Status: BUILT — GhostShield tile crash FIXED (decorView owner)

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### 🐛 BUG FIXED: panel crashed on open — "ViewTreeLifecycleOwner not found"
Device log (Motorola Edge 40, Android 15 / API 35): tapping the tile →
`showDialog(FocusPanel)` → `IllegalStateException: ViewTreeLifecycleOwner not
found from android.widget.FrameLayout{... app:id/container}`.
Root cause (confirmed via Gemini): `TileService.showDialog()` uses a
**`TYPE_QS_DIALOG`** window that strips/doesn't propagate lifecycle tags to
children. Compose's `WindowRecomposer` searches up to the **window root
(decorView)** and throws if the tag isn't there. We had attached the owner to
the **ComposeView**, which the search never reached.
**Fix (`FocusTileService.showFocusPanel`):** attach the `PanelOwner` to the
**`dialog.window.decorView`** (the root) via `setViewTree*Owner(owner)` — NOT to
the ComposeView. Order: `setContentView` → attach owners to decorView →
`showDialog`.
> Note: Material 1.11.0's `BottomSheetDialog` is NOT a `ViewModelStoreOwner`
> (Dialog-based, not ComponentDialog), so we still use the lightweight
> `PanelOwner` (LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner),
> just attached to the decorView now. `DisposeOnViewTreeLifecycleDestroyed` kept.

### ⚠️ Still to verify on device
- Tap GhostShield tile → panel opens (NO crash), fully expanded.
- Master switch ON/OFF, mode switch (hot-swap when ON), "+ Create / Edit Modes".
- Dismiss panel repeatedly → no crash, no leak.

**Next:** Zamir installs the new APK, taps the tile, confirms the panel opens.
Share `Download/NullFlow/nullflow.log` if it still crashes.

---

## ✅ Previous Status: BUILT — GhostShield Quick Settings Tile + Focus Panel

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### 🆕 FEATURE: GhostShield — Quick Settings Tile + native Compose Focus Panel
A QS tile ("GhostShield") in the notification shade. Tapping it opens a native
`BottomSheetDialog` (Compose) over the current app to flip the shield + switch
modes in ~0.5s without opening the main app. Spec: `doc/GHOSTSHIELD_SPEC.md`,
questions/decisions: `doc/QUESTIONS_GHOSTSHIELD.md` (all answered "defaults").

**New files:**
- `tile/FocusTileService.kt` — the QS tile. `onStartListening()` reflects state
  (blue glow ACTIVE / grey INACTIVE) + reactively syncs from Room. `onClick()`
  → `showFocusPanel()` (handles `isLocked` via `unlockAndRun`). Panel =
  `BottomSheetDialog` + `ComposeView` rendering `TileFocusPanel`.
  - **Lifecycle (verified best practice, via Gemini):** does NOT implement
    `LifecycleOwner` on the service. Uses a private `PanelOwner`
    (LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner) attached
    via `setViewTree*Owner(owner)`. `ViewCompositionStrategy
    .DisposeOnViewTreeLifecycleDestroyed` → no leaks. Owner destroyed on
    dismiss + `onDestroy` dismisses the dialog (no Window leak).
  - **QS overlay fix:** `behavior.state = STATE_EXPANDED` + `skipCollapsed = true`
    (avoids half-cut sheet). `startActivityAndCollapse` with
    `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
- `ui/tile/TileFocusPanel.kt` — Compose panel: master `Switch` (ON = start
  shield for active profile + insert session; OFF = stop + end session),
  "SELECT MODE" `LazyColumn` (RadioButton + name + "N apps shielded"),
  "+ Create / Edit Modes" → opens main app. All Room writes on `Dispatchers.IO`.
- `data/ProfileWithCount.kt` — (id, name, isActive, appCount) for the panel.
- `res/drawable/ic_hero_toggle.xml` — vector (circle + slash, the null-ring mark).

**Modified files:**
- `vpn/FocusVpnService.kt` — added `ACTION_STOP_SHIELD` + `ACTION_REFRESH_RULES`
  + companion intent builders (`startIntent`/`stopIntent`/`refreshIntent`).
  - **Hot-swap (Q1=A):** `refreshRules()` closes the old tunnel fd + re-`establish()`
    with the new active profile's packages, WITHOUT tearing down the foreground
    service/notification (no flicker). Tunnel can't be edited in place, so
    close+re-establish is the only correct way. Extracted `establishTunnel()`.
  - `ACTION_STOP_SHIELD` → `teardown()` (same as `ACTION_STOP`).
- `data/FocusDao.kt` — added `observeProfilesWithAppCount(): Flow<List<ProfileWithCount>>`.
- `build.gradle.kts` — added `com.google.android.material:material:1.11.0`
  (for `BottomSheetDialog`; cached, VPN-safe).
- `AndroidManifest.xml` — registered `.tile.FocusTileService`
  (`BIND_QUICK_SETTINGS_TILE`, `QS_TILE` intent-filter, label "GhostShield").
- `res/values/themes.xml` — `NullFlow_BottomSheet_Dialog`
  (parent `Theme.Material3.DayNight.BottomSheetDialog`, transparent + dim + floating).
- `res/values/colors.xml` + `strings.xml` — tile tints + `tile_label`.

### ⚠️ Still to verify on device
- Add the "GhostShield" tile to the QS shade (edit tiles) → it appears.
- Tap tile → panel opens over current app, fully expanded (not half-cut).
- Master switch ON → shield engages (VPN icon + notif); OFF → clean stop.
- Switch mode while ON → tunnel hot-swaps (blocked apps change, no notif flicker).
- Switch mode while OFF → just updates the active profile (no auto-start).
- "+ Create / Edit Modes" → panel closes, main app opens.
- Tile icon tints blue when active, grey when off.
- No crash/leak when dismissing the panel repeatedly.

**Next:** Zamir installs the new APK, adds the GhostShield tile, tests the flow.
Share `Download/NullFlow/nullflow.log` if the panel misbehaves (new `TilePanel:`
+ `FocusTileService.` log lines).

---

## ✅ Previous Status: BUILT — deterministic teardown (lingering notif FIXED)

**Built 2026-09-05 (commit `aec5ced`, CLEAN build):** `NullFlow.apk` (23 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### 🐛 BUG FIXED: notification + VPN icon lingered after OFF (service survived)
Log showed: notification "End session" fired STOP #1, then toggle fired STOP #2
+ `endCurrentSession` cleared Room (UI → OFF), **but the service process
survived** — its timer loop kept updating the notification ("00:30 · active")
after the UI said OFF.
Root cause: `stopSelf()` (no arg) only stops the *last* start request. With
multiple pending start requests (notification + toggle), the service lived on.
**Fix (`FocusVpnService`):**
- New **`shouldRun`** instance flag (checked by the timer loop) + a single
  **`teardown()`** helper = the ONLY teardown path.
- `teardown()`: sets `shouldRun=false` + `isShieldRunning=false`, cancels the
  timer scope, closes the fd, `stopForeground(REMOVE)`, `cancel(NOTIF_ID)`,
  `stopSelf()`. **Idempotent** (safe to call repeatedly).
- STOP / null-intent / `onRevoke` / all `startShield` failure paths now call
  `teardown()` (not bare `stopSelf()`).
- Timer loop: `while (shouldRun)` + re-checks before each notify → stops
  updating the instant a STOP arrives.
- `startShield` sets `shouldRun=true` on success.

### ⚠️ Still to verify on device
- Toggle OFF → notification + VPN icon gone **immediately** (no lingering).
- "End session" from notification → same clean stop.
- ON → OFF → ON cycle works (fresh `shouldRun` each start).
- (Carried) welcome screen scroll + premium look, back dialog.

**Next:** Zamir installs `aec5ced`, tests OFF cleanup thoroughly. Share
`Download/NullFlow/nullflow.log` if the icon/notif still lingers (the new
`teardown:` log lines will show exactly what ran).

---

## ✅ Previous Status: BUILT — welcome screen redesigned (scrollable + premium)

**Built 2026-09-03 (commit `3051837`):** `NullFlow.apk` (23 MB).

### 🆕 Welcome screen: scrollable + premium redesign
User feedback: "not scrollable" + "looks like text text only, make it premium".
**Changes (`OnboardingScreen.kt`):**
- **Scrollable**: content now in a `verticalScroll(rememberScrollState())` Column
  (was a fixed Column with `Spacer(weight(1f))` that couldn't scroll).
- **Ambient glow background**: two soft radial gradients (electric-blue top-left,
  neon-cyan bottom-right) for premium depth (`AmbientGlow`).
- **Privacy pill**: the "0 bytes" line is now a rounded badge with a 🔒 icon
  (was bare centered text).
- **Feature cards**: the 3 "how it works" rows are now rich neumorphic cards
  (`FeatureCard`) with an icon chip (48dp rounded square) + title + description
  (was bare `FeatureRow` text lines — removed).
- **Section labels**: uppercase eyebrow labels ("HOW IT WORKS", "ONE-TIME SETUP")
  via `SectionLabel`.
- (Carried) Breathing hero, rotating tip card, hidden granted perms, gatekeeper
  button, brand footer.

### ⚠️ Still to verify on device
- Welcome screen scrolls smoothly on small screens.
- Premium look: glow + cards + pill render correctly (no clipping).
- (Carried from `1149f21`) OFF cleanup, back dialog, tip card.

**Next:** Zamir installs `3051837`, checks the welcome screen look + scroll.
Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT — back dialog + welcome polish + sticky-restart fix

**Built 2026-09-03 (commit `1149f21`):** `NullFlow.apk` (23 MB).

### 🐛 BUG FIXED: VPN icon/notification lingered after OFF (sticky restart)
Root cause (from log): `onStartCommand` returned **`START_STICKY`** for the
start path. Every STOP intent → `stopSelf()` → system **re-started** the service
(sticky) → `onStartCommand(null)` → `else` branch → `startShield()` re-ran →
tunnel re-established → VPN icon came back. Log showed 5 repeated STOPs.
**Fixes (`FocusVpnService`):**
- Start path now returns **`START_NOT_STICKY`** (system won't auto-restart).
- **Null-intent guard**: `onStartCommand(null)` (system re-delivery after death)
  now calls `stopSelf()` and does NOT re-establish the tunnel.
- (Carried from `1ee1e86`): `onDestroy` + STOP path call `stopForeground(REMOVE)`
  + `cancel(NOTIF_ID)`; "End session" uses distinct PendingIntent request code 2.

### 🆕 UX: back-confirmation dialog
Pressing back (button OR gesture) now shows **"Leave NullFlow?"** dialog with
**Minimize** (moveTaskToBack) / **Stay** instead of immediately minimizing.
Wired via `OnBackPressedDispatcher.addCallback` in `MainActivity`.

### 🆕 Welcome screen polish
- **Hide granted permissions**: if a permission is already granted, its checklist
  row is hidden entirely (returning users see a clean screen). "One-time setup"
  label only shows when something is still needed.
- **3 feature rows** ("How it works"): Pick apps / One tap zero popups / Data
  never moves — quiet icon + title + description.
- (Carried) Rotating tip card (30 lines, auto 6s + tap, TIP/TRICK/MOTIVATE).

### 🆕 Debug: notification/service lifecycle logging
`buildNotification` logs title/text; `onDestroy` logs each step (fd close,
stopForeground, cancel) + final state; STOP path logs startId. Easier to trace
why the icon/notif lingers.

### ⚠️ Still to verify on device
- Toggle OFF → VPN icon + notification gone (no sticky re-start).
- Back button/gesture → dialog appears → Minimize/Stay work.
- Welcome: granted perms hidden; feature rows + tip card render.

**Next:** Zamir installs `1149f21`, tests OFF cleanup + back dialog + welcome.
Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT — OFF now fully cleans up (VPN icon + notif)

**Built 2026-09-03 (commit `1ee1e86`):** `NullFlow.apk` (23 MB).

### 🐛 BUGS FIXED: shield didn't fully turn off
User report: (1) "End session" from notification did nothing, (2) VPN icon
stayed in status bar after OFF, (3) "Local Privacy Shield is active"
notification lingered.
Root cause: `onDestroy()` closed the tunnel fd but **never called
`stopForeground()`** → the foreground notification + VPN status-bar icon were
orphaned. Also the notification's "End session" PendingIntent (request code 1)
coalesced with the toggle's stop intent.
**Fixes (`FocusVpnService`):**
- `onDestroy()`: now calls `stopForeground(STOP_FOREGROUND_REMOVE)` +
  `NotificationManager.cancel(NOTIF_ID)` → clears the notification + VPN icon.
- `onStartCommand(ACTION_STOP)`: calls `stopForeground(REMOVE)` immediately
  (instant UI update) before `stopSelf()`.
- "End session" PendingIntent now uses **request code 2** (distinct from the
  toggle's code 1) so the two stop intents never coalesce.

### ⚠️ Still to verify on device
- Toggle OFF → VPN icon + notification disappear immediately.
- "End session" from notification → shield turns off + icon/notif gone.
- Full cycle: ON (icon+notif appear) → OFF (icon+notif gone) → ON again.

**Next:** Zamir installs `1ee1e86`, tests OFF cleanup + notification End-session.
Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT — core flow WORKS + stale-state reconcile

**Built 2026-09-03 (commit `939480c`):** `NullFlow.apk` (23 MB).

### ✅ CORE FLOW CONFIRMED WORKING (from device log)
Pick apps → Done → toggle ON → **Shield ACTIVE — 2 apps blackholed** (no crash,
no profile loop). Toggle OFF → clean stop. The crash fix + profile fix both hold.

### 🐛 BUG FIXED: stale "ON" state after app kill/restart
Symptom: app killed while shield ON → on restart, UI showed `isActive=true`
(`runningSession=true`) even though the service was dead → toggle stuck.
Root cause: Room session + active profile survived the process death, but the
VPN service did not.
**Fix:**
- `FocusVpnService.isShieldRunning` (static `@Volatile`, private set) — true only
  while the tunnel is live in a running process; set true on establish, false in
  `onDestroy`. Resets to false on every fresh process start.
- `MainActivity.onCreate`: if `!isShieldRunning` and a running session exists in
  Room → end it + deactivate the profile (reconcile). Logged as "Reconciled
  stale session".
- Cosmetic: Done-button log now prints the count, not the raw list.

### ⚠️ Known (harmless): repeated ACTION_STOP
Log shows multiple STOP intents for an already-stopped service (notification
"End session" + toggle). `stopSelf()` is idempotent/safe, so no action needed
unless it becomes noisy.

### ⚠️ Still to verify on device
- Kill app while shield ON → reopen → toggle should read OFF (reconciled).
- Full cycle: pick → Done → ON (VPN icon + notif) → kill app → reopen → OFF.

**Next:** Zamir installs `939480c`, tests the kill/restart reconcile. Share
`Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT — profile loop FIXED + Done bar in picker

**Built 2026-09-03 (commit `79bc450`):** `NullFlow.apk` (23 MB).

### 🐛 BUG FIXED: profile-creation loop (confirmed from log)
Symptom: every toggle ON created a NEW empty profile (1→2→3→4→5→6→7) and
showed "0 apps", re-opening the picker in an endless loop.
Root cause: `activeProfile` was always null (a profile is only "active" once
explicitly marked), so `onToggle` fell through to `createDefaultProfile()` on
every tap.
**Fixes:**
- **`effectiveProfile`** (`MainScreen`): the profile the UI points at =
  `activeProfile ?: profiles.firstOrNull()`. The profile row, blocked-app
  count, and toggle all use it → no more duplicate profiles.
- **Mark profile active on block** (`AppPickerSheet.toggleApp`): when an app is
  checked, `clearActive()` + `setActive(profileId, true)` so the toggle finds it.
- **Toggle** now resolves `effectiveProfile?.id ?: createDefaultProfile(dao)`.

### 🆕 UX: "Done" bar in the app picker
The picker had NO way to confirm (only swipe-down). Added a full-width
**"Done · N apps selected"** button at the bottom (closes the sheet, haptic).
Header still shows live "N selected".

### ⚠️ Still to verify on device
- Pick apps → tap **Done** → profile row shows "N apps shielded".
- Toggle ON → shield engages (VPN icon + notification), no new profile minted.
- Toggle OFF → clean stop.

**Next:** Zamir installs `79bc450`, tests the full flow (pick → Done → ON →
OFF). Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT — crash FIXED + welcome screen + rotating tips

**Built 2026-09-03 (commit `c47f867`):** `NullFlow.apk` (23 MB).

### 🐛 CRASH FIXED (root cause confirmed from log)
The ON/OFF crash was **`ForegroundServiceDidNotStartInTimeException`**.
Android requires `startForeground()` within **5s** of `startForegroundService()`.
The old code read blocked apps first; with **0 apps** it called `stopSelf()`
*without ever calling `startForeground()`* → 5s timeout → app killed.
**Fix (`FocusVpnService.startShield`):** call `startForeground()` **FIRST**
(before reading packages / establishing the tunnel), so the 5s contract is
always satisfied, THEN decide to stay active or stop.

### 🆕 UI changes (this build)
- **Welcome screen shows on EVERY app open** (`MainActivity` gate → always
  `OnboardingScreen`). Returning users see the permission checklist already
  checked → single "Enter NullFlow" tap. Fresh installs still walk setup.
- **Rotating tip card** on the welcome screen (upper-middle, between the
  "0 bytes" line and the permission checklist):
  - **30 short lines** in `OnboardingScreen.kt` (`TIPS` pool): 10 TIP +
    10 TRICK + 10 MOTIVATE.
  - **Auto-swap** every 6s (fade out/in via `Animatable`).
  - **Manual swap** on tap (same fade). Small `↻` affordance on the right.
  - **Tag rotates** in fixed order TIP → TRICK → MOTIVATE → …; each step picks
    a random line from that tag's pool.
- **0-apps guard** (`MainScreen.onToggle`): toggling ON with 0 blocked apps now
  opens the app picker instead of starting an empty shield.
- **Profile row** shows `N apps shielded` / `No apps yet`.
- **Fresh-install entry point**: "Choose apps to shield" button (OFF, no
  profile) so apps can be picked BEFORE turning on.

### ⚠️ Still to verify on device
- Toggle ON with apps blocked → does the shield actually engage (VPN icon +
  notification)? (The crash is fixed, but the happy path wasn't tested yet.)
- Tip card auto/manual swap + tag rotation look/feel.

**Next:** Zamir installs `c47f867`, tests: (1) pick apps, (2) toggle ON,
(3) confirm shield works, (4) tip card behavior. Share
`Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT WITH LOGGING — debugging the ON/OFF crash

**Built 2026-09-03 (commit `57e0ddb`):** `NullFlow.apk` (23 MB).
Crash-proof logging (`AppLog.kt` → `Download/NullFlow/nullflow.log`) + crash
handler + full lifecycle logging. This build's log **confirmed** the
`ForegroundServiceDidNotStartInTimeException` root cause (fixed in `c47f867`).

---

## ✅ Previous Status: BUILT — `NullFlow.apk` (23 MB) ready for device test

**Built 2026-09-03:** `BUILD SUCCESSFUL`, APK 23 MB at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.
Manifest verified: package `com.codezmr.nullflow`, label "NullFlow",
permissions VIBRATE / POST_NOTIFICATIONS / FOREGROUND_SERVICE(+_DATA_SYNC) /
QUERY_ALL_PACKAGES. Working tree CLEAN (commit `c88c0fc`).

> ⚠️ **Compose 1.6.1 (BOM 2024.02.00) gotchas hit this build:**
> - `Modifier.shadow` uses `elevation: Dp` — NO `radius`, NO `ambientColor`/`spotColor` params.
> - `infiniteTransition.animateFloat` does NOT exist → use `Animatable` +
>   `LaunchedEffect` ping-pong loop (`animateTo(1f)` / `animateTo(0f)` in `while(true)`).
> - `Canvas` drawscope has NO `strokeWidth` property → use `N.dp.toPx()`.
> - `animateColorAsState` lives in `androidx.compose.animation` (NOT `.core`).
> - KSP 1.9.22-1.0.17 chokes on multi-line `if { } else { }` blocks inside
>   `mutableStateOf(...)` ("Expecting an element") → use `||`/`&&` expressions.
> - `Modifier.fillMaxSize().background(x)` on one line → "Overload resolution
>   ambiguity" → split onto separate lines.

**Next:** Device test — (1) onboarding: breathing hero, both checklist rows →
neon checks, gatekeeper → Enter; (2) main: 1-tap toggle ON (no popups), bg dims,
WhatsApp single-tick; (3) OFF → internet back; (4) Edit apps sheet; (5) stats.

---

## ✅ Previous Status: ALL CODE DONE (Phases 1-4 + Onboarding) — AWAITING BUILD PERMISSION

**Done so far:**
- Local git repo initialized (no remote, ever).
- `doc/APP_IDEA.md` — full app idea + branding + 5-phase architecture (read-only reference).
- Gradle wrapper copied from SnapTriage (Gradle 8.7, cached).
- `local.properties` → `sdk.dir=/home/mohmmad/Android/Sdk`.
- **Phase 1 COMPLETE:** root + app `build.gradle.kts` (compileSdk 34, minSdk 30,
  Room 2.6.1 + KSP 1.9.22-1.0.17, coroutines 1.7.1, APK renamed to
  `NullFlow.apk`), `settings.gradle.kts`, `gradle.properties`,
  `AndroidManifest.xml` (VIBRATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE + _DATA_SYNC,
  QUERY_ALL_PACKAGES + FocusVpnService with BIND_VPN_SERVICE), res/
  (strings, colors, themes, **provided NullFlow icon kit** — adaptive foreground PNG
  + density mipmaps, background `#101014`).
- **Phase 2 COMPLETE:** Room DB (`FocusProfile`, `BlockedApp`, `FocusSession`
  entities + `FocusDao` with CRUD + Flow emitters + `FocusDatabase`),
  `PackageManagerRepo` (filters system apps, caches labels+icons, target allow-list),
  `Settings` (SharedPreferences onboarding flag).
- **Phase 3 COMPLETE:** `FocusVpnService` (blackhole logic via `addAllowedApplication`
  + `setBlocking(true)`, foreground notification with 30s timer, onRevoke handling).
- **Phase 4 COMPLETE:** `NullFlowTheme` (dark, rest-mode palette), `Haptics`
  (tick/engage/disengage), `MainScreen` (hero toggle + animateColorAsState bg +
  live session timer + stats + pre-prompt consent sheet), `AppPickerSheet`
  (ModalBottomSheet + LazyColumn + multi-select checkboxes), `MainActivity`
  (edge-to-edge, onboarding gate, wires DAO + picker sheet).
- **ONBOARDING COMPLETE (Play-review required):** `OnboardingScreen` — neumorphic
  dark aesthetic: BreathingHero (3D matte toggle + 4s icy-blue LED pulse),
  stark value-prop typography, "0 bytes" Halo anchor, two neumorphic tactile
  checklist rows (Notifications `POST_NOTIFICATIONS` + Local Shield
  `VpnService.prepare()`), gatekeeper button (ghost → electric blue + pulse).
  Sequential prompting, real-time check state, heavy haptic on grant.
- **INSTANT TOGGLE (UX upgrade):** VPN consent is handled DURING onboarding,
  so the MainScreen hero toggle is now a **1-tap action with zero popups**.
  PrePromptSheet removed from MainScreen. Toggle ON → startShield() directly.

**Next:**
- **Phase 5: ASK ZAMIR BEFORE BUILDING.** `./gradlew assembleDebug --no-daemon`
  → `app/build/outputs/apk/debug/NullFlow.apk`.
- After build: device-test (onboarding checklist → both checks → Enter, then
  toggle ON → WhatsApp single-tick, OFF → internet back, haptics, bg dim,
  app picker, stats).

---

## 📁 Project Layout (target)

```
3Sep2026_app_freez/
├── SESSION_STATE.md          ← this file
├── doc/APP_IDEA.md           ← idea + architecture (read-only reference)
└── apps/NullFlow/
    ├── build.gradle.kts      ← AGP 8.5.2, Kotlin 1.9.22
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── local.properties      ← sdk.dir=/home/mohmmad/Android/Sdk
    ├── gradlew + gradle/wrapper/  ← Gradle 8.7 (COPIED from SnapTriage — do NOT change)
    └── app/
        ├── build.gradle.kts  ← compileSdk 34, minSdk 30, compose, Room+KSP, coroutines
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/codezmr/nullflow/
            │   ├── MainActivity.kt
            │   ├── data/       ← Room DB, entities, DAOs, PackageManagerRepo
            │   ├── ui/         ← Compose screens (hero toggle, sheets, theme)
            │   └── vpn/        ← FocusVpnService
            └── res/
```

---

## 🧱 Tech Stack (LOCKED — see ../../ANDROID_BUILD_SETUP.md for why)

| Component | Version |
|---|---|
| Gradle | **8.7** (cached; VPN blocks newer downloads) |
| AGP | **8.5.2** |
| Kotlin | **1.9.22** |
| Compose Compiler | **1.5.8** (must match Kotlin) |
| Compose BOM | **2024.02.00** |
| Room | **2.6.1** + KSP **1.9.22-1.0.17** (both cached) |
| Coroutines | **1.7.1** (cached) |
| JDK | 17 (sourceCompat/jvmTarget) |
| SDK | `~/Android/Sdk` (android-34, android-36) |

**APK rename:** `app/build.gradle.kts` has an `android.applicationVariants.all { ... outputFileName = "NullFlow.apk" }` block (legacy API — `androidComponents.outputFileName` doesn't exist in AGP 8.5.2).

---

## 🔑 Key Implementation Decisions

1. **Blackhole VPN (CRUCIAL):** Do NOT use `addDisallowedApplication`.
   Route ONLY blocked apps INTO the VPN dead-end:
   - `addAddress("10.0.0.2", 32)` + `addRoute("0.0.0.0", 0)`
   - `addAllowedApplication(pkg)` for each blocked package
   - `setBlocking(true)` → their packets are silently DROPPED
   - All other apps bypass the VPN → keep internet.
2. **Never say "VPN" in UI** — call it "Local Privacy Shield" / "Focus Wall".
   Pre-prompt screen BEFORE the system consent dialog.
3. **Haptics:** `VibrationEffect.createOneShot(50, 150)` touch-down,
   `createOneShot(100, 255)` on successful activation.
4. **Visual state:** `animateColorAsState` background → deep dark `#121212`
   when active ("rest mode").
5. **App picker:** filter out system apps
   (`(flags & ApplicationInfo.FLAG_SYSTEM) != 0`), cache labels + icons.
6. **Foreground service:** VPN service needs a persistent notification
   ("Focus Session Active" + timer).

---

## 🖥️ Build / Install / Debug

- **APK:** `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`
- **Build:** `cd apps/NullFlow && ./gradlew assembleDebug --no-daemon`
- **Install:** `adb install -r apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`
- **Logcat:** `adb logcat -s NullFlow`

---

## ⚠️ Known / Watch-out

- **Git: LOCAL-ONLY.** NO remote — never add one, never push. Commit locally
  before big changes.
- **VPN-gated:** `services.gradle.org` is dead over this VPN. Never bump the
  Gradle wrapper version.
- **Compose Compiler must match Kotlin** (1.9.22 → 1.5.8).
- **Compose BOM 2024.02.00 gotchas** (from SnapTriage):
  - `Modifier.shadow` needs `import androidx.compose.ui.draw.shadow` AND uses
    `elevation: Dp` (not `radius`).
  - `slideInVertically`/`slideOutVertically` don't take `targetOffsetY`/
    `initialOffsetY` lambda params — use `animationSpec` only.
  - `align` is a `BoxScope`/`RowScope`/`ColumnScope` member — do NOT import
    `androidx.compose.foundation.layout.align`.
- **QUERY_ALL_PACKAGES** is a Play Store scrutiny item — fine for sideloaded
  APK; would need justification for Play release.
- **VpnService.prepare()** returns null if already authorized — handle both
  cases in the consent flow.
