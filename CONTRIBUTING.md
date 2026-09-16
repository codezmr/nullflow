# Contributing to NullFlow

Thanks for your interest in contributing!

## Development setup

1. Clone the repo
2. Open `apps/NullFlow/` in Android Studio (or use Gradle from CLI)
3. Build: `./gradlew assembleDebug`
4. Install: `adb install -r app/build/outputs/apk/debug/NullFlow.apk`

## Project structure

- `apps/NullFlow/app/src/main/java/com/codezmr/nullflow/`
  - `vpn/` — VpnService, packet filtering
  - `data/` — Room entities, DAO, database
  - `ui/` — Compose screens and components
  - `service/` — Foreground service

## Guidelines

- Follow existing code style (Kotlin, Compose)
- No new dependencies without discussion
- All UI changes must work in the Quick Settings tile context (no Dialog/AlertDialog)
- Log with `AppLog` (logcat only, tag `NullFlow`)
- Keep the VPN tunnel local-only — no network calls from the app itself

## Submitting changes

1. Fork and create a feature branch
2. Make your changes
3. Test on a real device (emulator VPN support is limited)
4. Submit a PR with a clear description
