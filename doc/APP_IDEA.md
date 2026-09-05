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
- **Room DB** (`FocusDatabase`, v3):
  - `FocusProfile` (id, name, isActive)
  - `BlockedApp` (profileId, packageName, appName)
  - `FocusSession` (startTime, endTime)
  - `InterceptLog` (id, packageName, timestamp) — one row per intercepted
    (blackholed) connection attempt; the source of truth for the Telemetry
    Console (donut, ledger, heatmap, "Threats Neutralized").
  - DAOs: CRUD + Flow emitters for active profile + telemetry aggregations
    (`getInterceptionsByApp`, `getTotalIntercepts`, `getPeakInterceptHour`,
    `getDailyTelemetry`).
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

## Focus Telemetry Console (the "Data as a Feature" layer)

> A cybersecurity-style observability hub. Standard apps show "Time Saved";
> NullFlow shows *exactly what is happening under the hood* — a GitHub-style
> activity heatmap, a "Threat Level" donut, and a granular breakdown of the
> most aggressive apps trying to break the user's focus. No other digital
> wellbeing app frames screen time with observability (heatmaps, packet drop
> rates). **Every pixel rendered = a real byte dropped by the VPN. Zero mock
> data.**

**Data flow (all live, all reactive `Flow`s on `Dispatchers.IO`):**
1. The VPN packet reader observes a dropped connection (a successful `read()`
   on the tunnel fd) → attributes it to a shielded package via **round-robin**
   (privacy-correct: the raw byte stream never reveals the sender; parsing IP
   headers would leak per-app usage) → enqueues an `InterceptLog` into a
   lock-free in-memory buffer (O(1), no disk I/O on the hot path).
2. A dedicated flush coroutine drains the buffer into ONE multi-row Room insert
   every 2s (5000-row cap) + a final flush on teardown.
3. The DAO exposes reactive aggregations: per-app intercept counts (top 5),
   total intercepts, peak intercept hour, and a 7-day focus+intercept series.
4. `MainScreen` (inactive state) binds them to the console:
   - **Telemetry header** — 3 glassmorphic metric cards (`#12151C` / `#222733`
     border): Total Uptime · Threats Neutralized · Peak Focus Time.
   - **Interception donut** — thick-ringed `Canvas` chart, top 3 apps in
     Cyan `#00E5FF` / Purple `#B44CFF` / Electric Blue `#4F8CFF`.
   - **Threat ledger** — `LazyColumn` of top 5: app icon + name + exact count +
     `LinearProgressIndicator` scaled to the top app's count.
   - **7-day activity heatmap** — 7 rounded boxes, color-lerped `#1A1D24` →
     glowing `#00E5FF` by daily Focus Score (focus minutes + intercept weight).
   - **Empty state** — pulsing `[ AWAITING NETWORK TELEMETRY ]` wireframe when
     0 intercepts (no 0% pie, no crash).

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

---

## Implementation Notes (learned the hard way — 2026-09-03)

### VPN service lifecycle (CRITICAL — caused multiple crashes/lingering icons)
- **`startForeground()` MUST be called within 5s** of `startForegroundService()`,
  or Android kills the app with `ForegroundServiceDidNotStartInTimeException`.
  → Call it **first** in `startShield()`, before reading packages / establishing.
- **Return `START_NOT_STICKY`** (not `START_STICKY`). With STICKY, every STOP
  intent → `stopSelf()` → system re-starts the service → `onStartCommand(null)`
  → re-establishes the tunnel → **VPN icon comes back after OFF**.
- **Guard null intents**: `onStartCommand(null)` = system re-delivery after death.
  Do NOT re-establish; call `stopSelf()`.
- **`onDestroy()` MUST call `stopForeground(STOP_FOREGROUND_REMOVE)`** +
  `NotificationManager.cancel(NOTIF_ID)`, else the foreground notification +
  VPN status-bar icon linger after the shield is off.
- **Distinct PendingIntent request codes** for the notification "End session"
  (code 2) vs the toggle's stop (code 1) — same code = coalescing = "End
  session" does nothing.
- **Stale-state reconcile**: on app start, if `!isShieldRunning` (static flag,
  resets each process) but Room has a running session → end it + deactivate the
  profile. (App killed while ON → service dies, Room state survives.)

### Profile model
- A profile is only "active" once explicitly marked. The UI uses an
  **`effectiveProfile`** = `activeProfile ?: profiles.firstOrNull()` so the
  toggle, profile row, and blocked-count all agree (prevents the toggle from
  minting a new empty profile on every tap).
- **Mark a profile active when an app is blocked** (in the picker), so the
  toggle finds it.

### UI/UX decisions
- **Welcome screen shows on every app open.** Granted permissions are **hidden**
  (returning users see a clean screen + single "Enter" tap). 3 "how it works"
  feature rows + a rotating tip card (30 lines: TIP/TRICK/MOTIVATE, auto 6s +
  tap to swap).
- **App picker has a "Done · N apps selected" button** (not just swipe-down).
- **Back button/gesture shows a confirmation dialog** (Minimize / Stay) instead
  of immediately minimizing.
- **0-apps guard**: toggling ON with no blocked apps opens the picker.

### Crash-proof logging
- `AppLog.kt` writes to public `Download/NullFlow/nullflow.log` (MediaStore, no
  permission) + logcat tag `NullFlow`. Rotates at 2000 lines.
- `MainActivity` installs a `Thread.setDefaultUncaughtExceptionHandler` (before
  `super.onCreate`) that writes the full stack trace to the log before the
  process dies.
- Logging throughout: activity lifecycle, toggle flow, service lifecycle
  (onCreate/onStartCommand/startShield/establish/startForeground/onDestroy/
  onRevoke), notification build, picker block/unblock.
