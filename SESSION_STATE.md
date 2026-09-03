# NullFlow — Session State

> **Read this first to resume.** Last updated: 2026-09-03.
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

## ✅ Current Status: BUILT — OFF now fully cleans up (VPN icon + notif)

**Built 2026-09-03 (commit `1ee1e86`):** `NullFlow.apk` (23 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

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
