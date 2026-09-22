# NullFlow

A focus shield for Android. Select the apps that distract you, flip the switch, and NullFlow silently drops their network traffic through a local VPN tunnel - no root, no cloud, no data leaves your device.

## How it works

NullFlow uses Android's `VpnService` API to create a local tunnel (IPv4 + IPv6). When the shield is active, DNS queries and TCP/UDP connections from blocked apps are intercepted and dropped at the socket level. The apps still "work" locally but cannot reach the internet - notifications, feeds, and updates simply stop.

- **Local only** - all traffic handling happens on-device
- **No root** - uses the standard VPN permission (granted once during onboarding)
- **Per-app rules** - block exactly the apps you choose, per focus mode
- **IPv4 + IPv6** - both address families are routed into the tunnel, so apps can't bypass via IPv6
- **Split tunneling** - only blocked apps' traffic enters the tunnel (kernel routes by UID); allowed apps bypass it entirely, so there's zero overhead for the rest of your phone
- **Scheduled sessions** - recurring windows auto start/stop the shield via exact `AlarmManager` alarms (survive reboot, timezone/DST-safe)
- **Per-app temp allow** - bypass the block for a single app during a session for 2/5/10/20/30 min (in-memory only, auto re-blocks on expiry, never edits the saved mode)

## Features

| Feature | Description |
|---------|-------------|
| Reactor Core | Hero toggle with live intercept heat - color and pulse driven by real-time blocking activity |
| Scheduled Sessions | Recurring focus windows - auto start/stop the shield on a schedule (e.g. weekdays 6-9 PM) |
| Tactical Pass (BETA) | 2-minute network leash - temporarily pause the shield without ending your session |
| Per-app temp allow | Pause a single blocked app for 2/5/10/20/30 min (e.g. grab an OTP) without ending the session or editing the mode |
| Remove app from mode | Permanently remove an app from the saved focus mode from the active session (two-tap confirm) |
| Focus modes | Create multiple profiles (e.g. "Deep Work", "No Social") |
| App picker | Search and select which apps to silence per mode |
| Quick Settings tile | Toggle the shield from the notification shade (live timer + intercept count) |
| Session tracking | Start/stop times, duration, total focus time |
| Telemetry dashboard | Intercept counts, top blocked apps, per-app blocked detail, 7-day heatmap |
| Mode manager | Create, rename, delete focus modes from the dashboard |
| OEM kill warning | Don'tKillMyApp autostart routing for Xiaomi, Oppo, Vivo, OnePlus + dashboard warning card |
| Slot conflict detection | Gracefully turns OFF when another VPN takes the Android VPN slot (no false "Shield ON" state) |
| Notification HUD | Live focus-session notification with timer + intercept count |
| File logging | Optional shareable log file (off by default, toggle in Settings) |

## Install

### From GitHub Releases

1. Go to [Releases](https://github.com/codezmr/nullflow/releases)
2. Download `NullFlow-v1.3.0.apk`
3. Install on your device (enable "Install unknown apps" for your browser)
4. Open the app, complete the 3-step onboarding (grant the VPN permission when prompted)
5. Add the "NullFlow" tile to your Quick Settings panel

### From source

```bash
git clone https://github.com/codezmr/nullflow.git
cd nullflow
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/NullFlow.apk
```

## Requirements

- Android 11 (API 30) or higher
- No root
- ~12 MB storage (release APK)

## Architecture

```
nullflow/
├── app/src/main/java/com/codezmr/nullflow/
│   ├── vpn/              # VpnService, packet filtering, heat engine
│   │   ├── FocusVpnService.kt  # Tunnel, packet reader, HeatState, notification HUD
│   │   └── HeatState.kt        # Live intercept heat (0..1) + count
│   ├── data/             # Room database (profiles, sessions, intercepts, schedules)
│   │   ├── FocusDatabase.kt
│   │   ├── FocusDao.kt
│   │   ├── FocusSchedule.kt    # Recurring focus window (minutes-from-midnight + day mask)
│   │   ├── Settings.kt         # SharedPreferences (onboarding, prefs, flags)
│   │   ├── SystemHealth.kt     # Battery-optimization helpers
│   │   └── OemSettingsHelper.kt# Don'tKillMyApp autostart routing
│   ├── service/          # Background receivers + scheduling engine
│   │   ├── BootReceiver.kt     # Auto-start shield + re-arm schedules on boot
│   │   ├── SessionReceiver.kt  # Alarm receiver: start/stop shield on schedule
│   │   └── ScheduleManager.kt  # Computes next occurrence, arms exact alarms
│   ├── ui/               # Jetpack Compose screens
│   │   ├── tile/         # Quick Settings tile panel
│   │   ├── MainScreen.kt       # Dashboard (Reactor Core, mode selector, stats)
│   │   ├── OnboardingScreen.kt # 3-step onboarding (hook, permissions, enhancements)
│   │   ├── CreateModeScreen.kt # Full-screen mode creation
│   │   ├── ScheduleEditorScreen.kt # Schedule manager (create/edit/toggle/delete)
│   │   ├── AppPickerSheet.kt   # App picker (reusable content + sheet wrapper)
│   │   ├── ModeManagerSheet.kt
│   │   ├── SettingsScreen.kt
│   │   └── Buttons.kt          # Design system components
│   ├── tile/             # Quick Settings tile service
│   │   └── FocusTileService.kt
│   ├── MainActivity.kt   # Entry point, session reconciliation, QS tile
│   └── AppLog.kt         # Logcat + optional file logger
├── build.gradle.kts
└── settings.gradle.kts
```

### Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room (SQLite)
- **VPN:** Android `VpnService` API (IPv4 + IPv6)
- **Min SDK:** 30, **Target SDK:** 34
- **Build:** Gradle 8.7, AGP 8.5.2

## Privacy

- No network calls (except the blocked apps' own traffic, which is dropped)
- No analytics, no telemetry, no crash reporting
- All data stored locally in Room (SQLite)
- VPN tunnel is local-only - packets never leave the device
- File logging is off by default; when enabled, logs stay on-device and can be shared manually

## License

MIT - see [LICENSE](LICENSE).

## Contributing

PRs welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
