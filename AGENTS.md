# NullFlow - AI Agent Instructions

This file provides context and rules for AI agents working on the NullFlow codebase.

## Project Overview

NullFlow is a per-app internet kill-switch for Android. It uses a local `VpnService` tunnel to silently drop network traffic from blocked apps while the rest of the phone works normally. No root, no cloud, no data leaves the device.

- **Package:** `com.codezmr.nullflow`
- **Min SDK:** 30 (Android 11)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room (SQLite)
- **VPN:** Android `VpnService` API (IPv4 + IPv6)

## Build Commands

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (minified + resource-shrunk)
./gradlew :app:assembleRelease

# Lint
./gradlew :app:lintDebug

# APK locations
# Debug:   app/build/outputs/apk/debug/NullFlow.apk
# Release: app/build/outputs/apk/release/NullFlow.apk
```

## Architecture

```
app/src/main/java/com/codezmr/nullflow/
├── vpn/
│   └── FocusVpnService.kt      # VpnService: tunnel, packet reader, heat engine, notification HUD
├── data/
│   ├── FocusDatabase.kt        # Room DB (v5)
│   ├── FocusDao.kt             # DAO: profiles, sessions, intercepts, telemetry
│   ├── FocusProfile.kt         # Entity: named block list
│   ├── BlockedApp.kt           # Entity: app assigned to a profile
│   ├── FocusSession.kt         # Entity: focus session (start/end time)
│   ├── InterceptLog.kt         # Entity: one row per intercepted connection
│   ├── AppInterceptStats.kt    # Query result: per-app intercept count
│   ├── DailyFocusStats.kt      # Query result: per-day focus + intercepts
│   ├── PeakHourStats.kt        # Query result: peak intercept hour
│   ├── ProfileWithCount.kt     # Query result: profile + app count
│   ├── Settings.kt             # SharedPreferences (onboarding, prefs, flags)
│   ├── SystemHealth.kt         # Battery-optimization helpers
│   ├── OemSettingsHelper.kt    # Don'tKillMyApp autostart routing
│   └── PackageManagerRepo.kt   # Installed apps query (IO dispatcher)
├── ui/
│   ├── MainScreen.kt           # Dashboard: Reactor Core hero, mode selector, telemetry console
│   ├── OnboardingScreen.kt     # 3-step onboarding (hook, permissions, enhancements)
│   ├── CreateModeScreen.kt     # Full-screen mode creation
│   ├── AppPickerSheet.kt       # App picker (reusable content + sheet wrapper)
│   ├── ModeManagerSheet.kt     # Mode manager (create, rename, delete)
│   ├── SettingsScreen.kt       # Settings (shield, schedule, diagnostics)
│   ├── Buttons.kt              # Design system components
│   ├── Haptics.kt              # Haptic feedback
│   ├── NullFlowTheme.kt        # Compose theme
│   ├── DossierGenerator.kt     # Shareable 9:16 focus card
│   ├── DossierShare.kt         # Share intent helper
│   └── tile/
│       ├── TileFocusPanel.kt   # QS tile focus panel (Compose in BottomSheetDialog)
│       └── AppIconLoader.kt    # Async app icon loading + caching
├── tile/
│   └── FocusTileService.kt     # Quick Settings tile service
├── service/
│   └── BootReceiver.kt         # Boot receiver (auto-start shield)
├── MainActivity.kt             # Entry point, session reconciliation, QS tile
└── AppLog.kt                   # Logcat + optional file logger
```

## Critical Rules

### VPN Service

- **NEVER** use `addDisallowedApplication()` - it shows a system dialog. Use `addAllowedApplication()` per blocked app + `setBlocking(true)` for a silent blackhole.
- The tunnel is **protocol-agnostic**: one multiplexed fd handles TCP, UDP/QUIC, IPv4, and IPv6.
- **IPv6 route** must be added (`addAddress("fd00::2", 128)` + `addRoute("::", 0)`) or apps bypass via IPv6. Wrap in try-catch (some OEM kernels reject it).
- The tunnel **cannot be edited** after `establish()`. To change blocked apps: close old fd, re-establish new tunnel. Service/notification stay alive.
- `VpnService.prepare(context)` returns `null` if already authorized. Always check before starting.
- **Permission gate:** `onToggle()` MUST check `VpnService.prepare(context) == null` before starting the shield. If not granted, route to the system VPN dialog.

### Room Database

- Current version: **v5**. Migrations must be **non-destructive** (no `fallbackToDestructiveMigration`).
- Entities: `FocusProfile`, `BlockedApp`, `FocusSession`, `InterceptLog`.
- `InterceptLog` is the source of truth for intercept counts (not a per-app counter column).
- Batch inserts: use `insertInterceptLogs(List<InterceptLog>)` for performance.

### UI / Compose

- **No Dialog/AlertDialog** in the Quick Settings tile context (it crashes). Use `BottomSheetDialog` for the tile panel.
- **No em-dashes** (`—`) in any code, strings, or comments. Use hyphens (`-`) instead.
- **No emoji** in UI strings. Use Canvas-drawn icons or vector drawables.
- Touch targets: minimum **48dp** (Material spec).
- The QS tile panel uses `RemoteViews`-safe layouts only (LinearLayout, no ConstraintLayout).
- App icons: use `AppIconLoader` (async, cached). Never load icons on the main thread.

### Logging

- Use `AppLog.d()`, `AppLog.w()`, `AppLog.e()` (tag: `NullFlow`).
- `AppLog.w()` takes only a message (no throwable). `AppLog.e()` takes message + optional throwable.
- File logging is **OFF by default**. Toggle in Settings > Diagnostics.
- Log meaningful business identifiers (profile ID, session ID, package name). Never log packet contents (zero-data privacy).

### Privacy

- **Zero-data privacy:** the tunnel fd yields only a raw byte stream. Never parse IP headers to identify which app sent a packet. Attribution is round-robin (proxy).
- No network calls from the app itself. No analytics, no telemetry, no crash reporting.
- All data stored locally in Room. VPN tunnel is local-only.

### OEM Compatibility

- Chinese OEMs (Xiaomi, Oppo, Vivo, OnePlus) aggressively kill background apps. Use `OemSettingsHelper` to route to their proprietary autostart settings.
- The OEM kill warning card appears on the dashboard when the OS kills the shield mid-session (detected via session reconciliation in `MainActivity`).
- The warning is backed by a `Settings.showOemKillWarning` flag (not derived from session reason, so it doesn't get stuck).

### Play Store Policy

- **NEVER** use `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (restricted permission). Use `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` instead.
- **NEVER** say "VPN" in user-facing UI. Call it "Local Focus Shield" or "Local Privacy Shield".
- Pre-frame the VPN permission: show a micro-text disclaimer before the user taps the button, so they expect Android's scary VPN dialog.

## Code Style

- Kotlin, no comments unless explaining non-obvious logic.
- Follow existing patterns (see neighboring files).
- No new dependencies without discussion.
- Use `try-with-resources` equivalent (Kotlin `use {}`) for resource cleanup.
- No hardcoded URLs, API keys, passwords, or configuration.
- Use connection pooling / prepared statements for any DB access (Room handles this).

## Git Workflow

- **Default branch:** `master` (always deployable, releases tagged here).
- **Branch naming:** `feature/<name>`, `bugfix/<name>`, `chore/<name>` (lowercase, hyphens).
- **Commit style:** short imperative subject. Example: `feat: add allowlist mode`
- **Solo work:** direct push to `master` is fine.
- **External contributions:** PR to `master`.
- **Releases:** tag on `master` (e.g. `v1.0.0`), create GitHub Release with APK.

## Testing

- Test on a **real device** (emulator VPN support is limited).
- No automated test suite yet. Manual verification is the standard.
- Key flows to verify after changes:
  1. Onboarding (3 steps, permission gates)
  2. Shield toggle (on/off, permission check)
  3. App picker (search, select, done)
  4. Mode manager (create, rename, delete)
  5. QS tile (toggle, panel, live timer)
  6. Notification HUD (timer, intercept count, end session)
  7. Telemetry console (intercept counts, heatmap, per-app detail)
  8. Settings (all toggles, diagnostics)

## File Locations

| What | Where |
|------|-------|
| VPN engine | `vpn/FocusVpnService.kt` |
| Room DB | `data/FocusDatabase.kt`, `data/FocusDao.kt` |
| Dashboard UI | `ui/MainScreen.kt` |
| Onboarding | `ui/OnboardingScreen.kt` |
| QS tile | `tile/FocusTileService.kt`, `ui/tile/TileFocusPanel.kt` |
| Notification HUD | `res/layout/notification_focus_hud.xml` |
| Settings | `ui/SettingsScreen.kt` |
| App prefs | `data/Settings.kt` |
| OEM routing | `data/OemSettingsHelper.kt` |
| Logging | `AppLog.kt` |
| ProGuard rules | `app/proguard-rules.pro` |
| Build config | `app/build.gradle.kts` |
