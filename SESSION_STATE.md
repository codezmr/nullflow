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

## ✅ Current Status: ALL CODE DONE (Phases 1-4 + Onboarding) — AWAITING BUILD PERMISSION

**Done so far:**
- Local git repo initialized (no remote, ever).
- `doc/APP_IDEA.md` — full app idea + branding + 5-phase architecture (read-only reference).
- Gradle wrapper copied from SnapTriage (Gradle 8.7, cached).
- `local.properties` → `sdk.dir=/home/mohmmad/Android/Sdk`.
- **Phase 1 COMPLETE:** root + app `build.gradle.kts` (compileSdk 34, minSdk 30,
  Room 2.6.1 + KSP 1.9.22-1.0.17, coroutines 1.7.1, APK renamed to
  `NullFlow-debug-v1.0.apk`), `settings.gradle.kts`, `gradle.properties`,
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

**Next:**
- **Phase 5: ASK ZAMIR BEFORE BUILDING.** `./gradlew assembleDebug --no-daemon`
  → `app/build/outputs/apk/debug/NullFlow-debug-v1.0.apk`.
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

**APK rename:** `app/build.gradle.kts` has an `android.applicationVariants.all { ... outputFileName = "NullFlow-debug-v1.0.apk" }` block (legacy API — `androidComponents.outputFileName` doesn't exist in AGP 8.5.2).

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

- **APK:** `apps/NullFlow/app/build/outputs/apk/debug/NullFlow-debug-v1.0.apk`
- **Build:** `cd apps/NullFlow && ./gradlew assembleDebug --no-daemon`
- **Install:** `adb install -r apps/NullFlow/app/build/outputs/apk/debug/NullFlow-debug-v1.0.apk`
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
