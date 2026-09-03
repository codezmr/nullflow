# NullFlow — App Idea (What We Are Building)

> A digital boundary tool. A selective "Offline Switch" that kills internet
> access for specific, intrusive apps (WhatsApp, Instagram…) while the rest of
> the phone works normally. Senders see a "single tick". No data ever leaves
> the phone.

**Package:** `com.codezmr.nullflow` · **Company:** Codezmr

---

## Branding

**Slogans (pick per surface):**
- Big Tech minimalist: *"Disconnect on your terms."* / *"Silence the noise. Keep the connection."* / *"Offline where it matters."*
- Action & result: *"Turn the world to a single tick."* / *"The escape hatch for your messaging apps."* / *"Pause the app. Keep the internet."*
- Psychological: *"Reclaim your focus. Ghost the noise."* / *"Boundaries for your digital life."* / *"Protect your peace, one app at a time."*

**Play Store:**
- Category: Productivity (or Tools)
- Tags (pick 5): Device Security, Productivity, Focus, Utility, Parental Control
  (only the last one if we add a PIN-lock)
- SEO keywords: app blocker, offline mode, focus timer, block internet access,
  limit app usage · WhatsApp offline, hide online status, single tick WhatsApp,
  block WiFi for apps, digital detox, ADHD focus · Local VPN, loopback firewall,
  rootless firewall, network blocker

**Name check (2026-09-03):** "NullFlow" does not exist on Google Play — clear runway.

---

## The Concept

- **What:** Per-app internet kill-switch via a local Android VPN tunnel
  (a "blackhole" — traffic for blocked apps is routed into a dead-end and
  dropped. Everything else bypasses the VPN entirely).
- **Why:** People are burnt out by constant availability. They don't want to
  turn off Wi-Fi (they still want YouTube/Uber) and don't want to uninstall
  apps. They want a temporary escape hatch.

## The Big Tech UI Approach

- **Hero Toggle:** One massive, beautifully animated switch on the main
  screen (iOS flashlight / Google Home thermostat energy).
- **Haptic Feedback:** Heavy, satisfying vibration on toggle — feels like
  pulling a heavy lever / locking a door.
- **Bottom Sheets over Screens:** App picker slides up from the bottom;
  user stays grounded on the main interface.

## Hiding the Tech (Permission Flow)

- **Never say "VPN" in the UI.** Call it a **"Local Privacy Shield"** /
  "Focus Wall".
- **Pre-Prompt:** Before the Android system VPN consent popup, show a clean
  friendly screen: *"To silence WhatsApp, Android requires us to create a
  Local Shield. No data ever leaves your phone. Tap 'Allow' on the next
  screen to activate."*

## Psychological Retention Hooks

| Concept | UI Implementation |
|---|---|
| **Quantified Relief** | Counter: "45 minutes of deep focus" / "0 interruptions allowed" (focus sessions tracked in Room) |
| **The IKEA Effect** | User names their block lists ("Gym Mode", "Deep Work", "Ghosting Everyone") — ownership |
| **Visual State Change** | When active, background dims/shifts to a deep dark calming palette (animateColorAsState) — "rest mode" |

---

## Architecture (5 Phases)

### Phase 1 — Scaffolding & Permissions
- Gradle: compileSdk 34, minSdk 30, Compose BOM 2024.02.00, Room 2.6.1 + KSP,
  Coroutines 1.7.1.
- APK renamed to `NullFlow.apk` (legacy `applicationVariants` block).
- Manifest: `VIBRATE`, `FOREGROUND_SERVICE`, `QUERY_ALL_PACKAGES`.
- Service: `.vpn.FocusVpnService` with `BIND_VPN_SERVICE` permission +
  `android.net.VpnService` intent-filter.

### Phase 2 — Data & State
- **Room DB** (`FocusDatabase`):
  - `FocusProfile` (id, name, isActive)
  - `BlockedApp` (profileId, packageName, appName)
  - `FocusSession` (startTime, endTime)
  - DAOs: CRUD + Flow emitter for active profile.
- **PackageManagerRepo:** `getInstalledApplications(GET_META_DATA)`, filter out
  system apps, cache labels + icons for the bottom sheet.

### Phase 3 — The VPN Engine ("The Blackhole")
- `FocusVpnService extends VpnService`.
- `onStartCommand` reads active profile's package list from Room.
- **Blackhole logic (CRUCIAL — do NOT use `addDisallowedApplication`):**
  ```kotlin
  val builder = Builder()
  builder.setSession("Focus Wall")
  builder.addAddress("10.0.0.2", 32)
  builder.addRoute("0.0.0.0", 0)          // route all VPN traffic to nowhere
  blockedPackages.forEach { builder.addAllowedApplication(it) }  // ONLY these apps enter the VPN
  builder.setBlocking(true)               // silently DROP their packets
  builder.establish()
  ```
  Only allowed apps' traffic enters the tunnel; it goes nowhere. All other
  apps bypass the VPN and keep internet.
- Foreground notification: "Focus Session Active" + timer.

### Phase 4 — UI/UX (Compose Material 3)
- **Pre-Prompt consent flow:** first toggle → full-screen bottom sheet
  ("Local Privacy Shield" explanation) → "Allow" → `VpnService.prepare(ctx)`
  → if Intent returned, launch via `rememberLauncherForActivityResult`.
- **App Picker:** `ModalBottomSheet` + LazyColumn of installed apps,
  multi-select checkboxes → assign to active `FocusProfile`.
- **Hero Toggle + Haptics:** massive custom switch; `VibrationEffect
  .createOneShot(50, 150)` on touch-down, `createOneShot(100, 255)` on
  successful activation.
- **Visual state:** `animateColorAsState` on background — bright/neutral →
  deep dark (`#121212`) when active.

### Phase 5 — Build & Run
- `./gradlew assembleDebug --no-daemon`
- Output: `app/build/outputs/apk/debug/NullFlow.apk`

---

## Tech Stack (LOCKED — same as SnapTriage, see ../../ANDROID_BUILD_SETUP.md)

| Component | Version |
|---|---|
| Gradle | 8.7 (cached; VPN blocks newer) |
| AGP | 8.5.2 |
| Kotlin | 1.9.22 |
| Compose Compiler | 1.5.8 |
| Compose BOM | 2024.02.00 |
| Room | 2.6.1 + KSP 1.9.22-1.0.17 |
| Coroutines | 1.7.1 |
| JDK | 17 target (JDK 21 installed) |
| SDK | ~/Android/Sdk (android-34, android-36) |

## Verified APIs (from local android-36 android.jar via javap)

- `VpnService.prepare(Context): Intent?` — system consent dialog
- `VpnService.Builder`: `addAllowedApplication(String)`, `addAddress(String, int)`,
  `addRoute(String, int)`, `setBlocking(boolean)`, `setSession(String)`,
  `establish(): ParcelFileDescriptor`
- `Vibrator.vibrate(VibrationEffect)` + `VibrationEffect.createOneShot(ms, amp)`
- `PackageManager.getInstalledApplications(int)` + `ApplicationInfo.loadLabel()/loadIcon()`
