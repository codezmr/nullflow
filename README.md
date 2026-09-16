# NullFlow

A focus shield for Android. Select the apps that distract you, flip the switch, and NullFlow silently drops their network traffic through a local VPN tunnel — no root, no cloud, no data leaves your device.

## How it works

NullFlow uses Android's `VpnService` API to create a local tunnel. When the shield is active, DNS queries and TCP connections from blocked apps are intercepted and dropped at the socket level. The apps still "work" locally but cannot reach the internet — notifications, feeds, and updates simply stop.

- **Local only** — all traffic handling happens on-device
- **No root** — uses the standard VPN permission (granted once during onboarding)
- **Per-app rules** — block exactly the apps you choose, per focus mode

## Features

| Feature | Description |
|---------|-------------|
| One-tap shield | Circular hero toggle — instant on/off, zero popups |
| Focus modes | Create multiple profiles (e.g. "Deep Work", "No Social") |
| App picker | Search and select which apps to silence per mode |
| Quick Settings tile | Toggle the shield from the notification shade |
| Session tracking | Start/stop times, duration, total focus time |
| Telemetry dashboard | Intercept counts, top blocked apps, 7-day heatmap |
| Mode manager | Create, rename, delete focus modes from the dashboard |

## Install

### From GitHub Releases

1. Go to [Releases](https://github.com/codezmr/nullflow/releases)
2. Download `NullFlow-vX.Y.Z.apk`
3. Install on your device (enable "Install unknown apps" for your browser)
4. Open the app, grant the VPN permission when prompted
5. Add the "Focus Shield" tile to your Quick Settings panel

### From source

```bash
git clone https://github.com/codezmr/nullflow.git
cd apps/NullFlow
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/NullFlow.apk
```

## Requirements

- Android 8.0 (API 26) or higher
- No root
- ~30 MB storage

## Architecture

```
apps/NullFlow/
├── app/src/main/java/com/codezmr/nullflow/
│   ├── vpn/              # VpnService, packet filtering, rule engine
│   ├── data/             # Room database (profiles, sessions, intercepts)
│   ├── ui/               # Jetpack Compose screens
│   │   ├── tile/         # Quick Settings tile panel
│   │   ├── MainScreen.kt # Dashboard (hero toggle, mode selector, stats)
│   │   ├── AppPickerSheet.kt
│   │   ├── ModeManagerSheet.kt
│   │   └── Buttons.kt    # Design system components
│   ├── service/          # Foreground service, notification
│   └── AppLog.kt         # Logcat-only logger
├── build.gradle.kts
└── settings.gradle.kts
```

### Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room (SQLite)
- **VPN:** Android `VpnService` API
- **Min SDK:** 26, **Target SDK:** 34
- **Build:** Gradle 8.7, AGP 8.5.2

## Privacy

- No network calls (except the blocked apps' own traffic, which is dropped)
- No analytics, no telemetry, no crash reporting
- All data stored locally in Room (SQLite)
- VPN tunnel is local-only — packets never leave the device

## License

MIT — see [LICENSE](LICENSE).

## Contributing

PRs welcome. See `doc/` for design specs and architecture notes.
