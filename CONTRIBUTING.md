# Contributing to NullFlow

Thanks for your interest in contributing!

## Development setup

1. Clone the repo
2. Open the repo root in Android Studio (or use Gradle from CLI)
3. Build: `./gradlew assembleDebug`
4. Install: `adb install -r app/build/outputs/apk/debug/NullFlow.apk`

## Project structure

- `app/src/main/java/com/codezmr/nullflow/`
  - `vpn/` - VpnService, packet filtering
  - `data/` - Room entities, DAO, database
  - `ui/` - Compose screens and components
  - `service/` - Foreground service

## Guidelines

- Follow existing code style (Kotlin, Compose)
- No new dependencies without discussion
- All UI changes must work in the Quick Settings tile context (no Dialog/AlertDialog)
- Log with `AppLog` (logcat only, tag `NullFlow`)
- Keep the VPN tunnel local-only - no network calls from the app itself

## Branch strategy

```
master (default, protected, always deployable)
  ^
  |  PR / merge
  |
feature/<name>   bugfix/<name>   chore/<name>
```

| Branch type | Naming | Example |
|-------------|--------|---------|
| Feature | `feature/<short-name>` | `feature/allowlist-mode` |
| Bug fix | `bugfix/<short-name>` | `bugfix/swipe-gate` |
| Chores/docs | `chore/<short-name>` | `chore/update-readme` |

Rules:
- `master` is the default branch. Releases are tagged here (e.g. `v1.0.0`).
- Create short-lived branches per task. Merge to `master`, then delete the branch.
- Branch names: lowercase, hyphens, no slashes in the name part.
- Solo work: direct push to `master` is fine. External contributions: PR to `master`.

## Submitting changes

1. Fork and create a branch (`feature/<name>`, `bugfix/<name>`, or `chore/<name>`)
2. Make your changes
3. Test on a real device (emulator VPN support is limited)
4. Submit a PR to `master` with a clear description
5. Delete your branch after merge
