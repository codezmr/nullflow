# NullFlow - App Details & UI Flow (for Gemini AI)

> Copy everything below the line into Gemini.

---

## 1. What the app is

**NullFlow** - a per-app internet kill-switch for Android. A "selective Offline
Switch" that kills internet access for specific, intrusive apps (WhatsApp,
Instagram, etc.) while the rest of the phone works normally. Senders see a
"single tick". **No data ever leaves the phone.**

- **Package:** `com.codezmr.nullflow`
- **Company:** Codezmr
- **Tagline:** "Disconnect on your terms."
- **Category:** Productivity / Tools
- **One-liner:** A digital boundary tool - a temporary escape hatch that mutes
  the noise without turning off Wi-Fi or uninstalling apps.

## 2. How it works (the tech, hidden from the user)

- Uses a **local Android VPN tunnel** as a "blackhole".
- Only the **blocked apps** are routed INTO the tunnel; the tunnel goes nowhere
  (`addAddress("10.0.0.2",32)` + `addRoute("0.0.0.0",0)` +
  `addAllowedApplication(pkg)` per blocked app + `setBlocking(true)`).
  Their packets are silently dropped → the app just sees "no internet".
- **Every other app bypasses the VPN** and keeps full internet.
- The UI **never says "VPN"** - it's called the **"Local Privacy Shield"** /
  "Focus Wall".
- Runs as a **foreground service** with a persistent notification
  ("Focus Session Active" + live timer + an "End session" button).

## 3. Tech stack (locked)

| Component | Version |
|---|---|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose (Material 3), BOM 2024.02.00, Compiler 1.5.8 |
| DB | Room 2.6.1 + KSP 1.9.22-1.0.17 |
| Concurrency | Coroutines 1.7.1 |
| Build | Gradle 8.7, AGP 8.5.2 |
| SDK | compileSdk 34, minSdk 30, JDK 17 target |
| Permissions | VIBRATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE(+_DATA_SYNC), QUERY_ALL_PACKAGES |

## 4. Data model (Room, v3)

- **FocusProfile** (id, name, isActive) - a named block list ("Deep Work", "Gym Mode"…).
- **BlockedApp** (profileId, packageName, appName) - apps assigned to a profile.
- **FocusSession** (id, profileId, startTime, endTime) - a focus session for stats.
- **InterceptLog** (id, packageName, timestamp) - one row per intercepted
  (blackholed) connection attempt. The source of truth for the Telemetry
  Console. Written by the VPN service in 2s batches (in-memory buffer → one
  multi-row insert), attributed to a shielded package via round-robin
  (privacy-correct: the raw byte stream never reveals the sender).
- **DAO** exposes CRUD + reactive `Flow` emitters (profiles, active profile,
  running session, blocked apps, total focused ms, completed count) **plus
  telemetry aggregations**: `getInterceptionsByApp(limit)` (per-app counts,
  top N), `getTotalIntercepts()`, `getPeakInterceptHour()` (hour of day with
  the most intercepts), `getDailyTelemetry(dayStart)` (7-day focus+intercept
  series for the heatmap).
- **PackageManagerRepo** lists installed apps (filters out system apps), caches
  labels + icons for the picker.

## 5. UI / UX design language

- **Big Tech minimalist** - one screen, one hero control.
- **Neumorphic dark aesthetic** - pure black / deep charcoal surfaces, soft
  shadows, an **icy-blue / electric-blue / neon-cyan** LED accent palette.
- **Haptic feedback** on every meaningful action (tick on touch, heavier
  "engage" on activation).
- **Bottom sheets over new screens** - the user stays grounded on the main
  interface.
- **Visual state change** - background animates from a neutral dark
  (`#1E222B`) to a deeper "rest mode" dark (`#0A0C10`) when the shield is ON.

## 6. Screen-by-screen UI flow

### Screen A - Welcome / Onboarding (`OnboardingScreen`)
Shown on **every app open**. For returning users the permission checklist is
already checked, so it's a single "Enter NullFlow" tap. Fresh installs walk the
full setup. It is **scrollable** with a premium look.

Top → bottom:
1. **Ambient glow background** - two soft radial gradients (electric-blue
   top-left, neon-cyan bottom-right) for depth.
2. **Breathing hero** - a 3D matte circular toggle icon with a 4-second
   looping icy-blue LED pulse (mirrors resting heart rate → calming).
3. **Value prop** - big, stark, centered: *"Silence the noise. Keep the
   connection."*
4. **Privacy pill** - a rounded badge with a 🔒 icon: *"100% local · 0 bytes
   leave this phone"* (the "Halo" anchor before permissions).
5. **Rotating tip card** - 30 short lines (10 TIP + 10 TRICK + 10 MOTIVATE).
   Auto-swaps every 6s (fade) AND swaps on tap. The tag cycles
   TIP → TRICK → MOTIVATE → …; each step picks a random line from that tag.
6. **"How it works"** - 3 rich neumorphic feature cards (icon chip + title +
   description):
   - ◉ *Pick the apps to silence* - "Choose any apps. They go dark - everything
     else stays connected."
   - ⚡ *One tap, zero popups* - "Flip the switch. The shield engages instantly,
     right on this phone."
   - ✦ *Your data never moves* - "No servers, no accounts, no tracking. It all
     stays on your device."
7. **"One-time setup"** (only shown if something is still needed) - two
   neumorphic checklist rows. **Already-granted permissions are hidden** so
   returning users see a clean screen:
   - **Notifications** (`POST_NOTIFICATIONS`) - "Shows your focus timer and
     keeps the shield running."
   - **Local Shield** (`VpnService.prepare()`) - "Safely drops network for
     blocked apps. Nothing else."
   - Each row: recessed/unchecked → extruded + neon-cyan border + animated check
     when granted. Tapping launches the real system permission dialog.
8. **Gatekeeper button** - flat ghost ("Complete Setup") → elevates + electric
   blue + gentle pulse ("Enter NullFlow") once all permissions are granted.
   Tapping it marks onboarding done and enters the main screen.
9. **Brand footer** - mini null-ring mark + "codezmr", a hairline divider, and
   "v{version} · © {year} Codezmr · All rights reserved".

### Screen B - Main (`MainScreen`)
The whole app in one screen. Top → bottom:
1. **Wordmark** - "NullFlow" + subtitle "Disconnect on your terms."
2. **THE HERO TOGGLE** - a massive (220dp) circular switch. OFF = dark ring,
   "OFF" text. ON = electric-blue ring + glow + "ON" text. Scales up slightly
   when active. Tapping it is the core action.
3. **Status line** - "Shield active" (blue) / "Shield off" (muted).
4. **Profile row** (if a profile exists) - profile name + "N apps shielded" /
   "No apps yet" + an "Edit apps" link. Tapping opens the app picker.
   - **Fresh install (no profile):** shows "No focus mode yet" + a
     "Choose apps to shield" button that creates a default profile and opens
     the picker.
5. **Bottom dashboard** (pinned to the bottom edge, no weight):
    - **Shield ON:** a live stats row - *this session* (live timer, 1s ticks) ·
      *all-time focus* · *sessions*.
    - **Shield OFF:** the **Focus Telemetry Console** - a cybersecurity-style
      observability hub built strictly from live Room data (zero mock data):
      - **Telemetry header** - 3 glassmorphic metric cards (`#12151C` surface,
        `#222733` border): **Total Uptime** (all-time focus) · **Threats
        Neutralized** (total intercepts) · **Peak Focus Time** (e.g. "09:00 AM").
      - **Interception donut** - thick-ringed `Canvas` chart of the top 3
        most-blocked apps (Cyan `#00E5FF` / Purple `#B44CFF` / Electric Blue
        `#4F8CFF`), animated sweep-in, center "DROPPED" total.
      - **Threat ledger** - `LazyColumn` of the top 5 blocked apps: app icon +
        name + exact intercept count + a `LinearProgressIndicator` scaled to
        the top app's count.
      - **7-day activity heatmap** - 7 rounded boxes, color-lerped `#1A1D24` →
        glowing `#00E5FF` by the daily Focus Score (focus minutes + intercept
        weight); today outlined in cyan.
      - **Empty state** - a pulsing `[ AWAITING NETWORK TELEMETRY ]` wireframe
        when 0 intercepts have been logged (no 0% pie, no crash).
      - Below it all: the "Share my focus dossier" gradient button.

**Hero toggle behavior:**
- **Turn ON:** instant, zero popups (VPN consent was already granted in
  onboarding). Marks the profile active, inserts a session, starts the
  foreground VPN service, plays an "engage" haptic, background dims to rest
  mode. **Guard:** if the profile has 0 blocked apps, it does NOT start the
  shield - it opens the app picker instead.
- **Turn OFF:** stops the service, ends the session, deactivates the profile,
  plays a "disengage" haptic, background brightens.

### Screen C - App Picker (`AppPickerSheet`)
A **bottom sheet** (not a new screen) so the user stays grounded.
1. **Header** - "Choose apps to silence" + live "N selected" count.
2. **App list** - a scrollable `LazyColumn` of installed (non-system) apps,
   each row = app icon (circular) + app name + a multi-select **checkbox**.
   Tapping a row or its checkbox blocks/unblocks that app for the active
   profile (with a haptic tick). Blocking an app marks the profile active.
3. **Done bar** - a full-width "Done · N apps selected" button (or just "Done")
   at the bottom. Tapping it closes the sheet (haptic).

### Dialog - Back / Exit confirmation
Pressing the system **back button or gesture** (on the main screen) shows a
"Leave NullFlow?" dialog with two options:
- **Minimize** - moves the app to the background (shield keeps running).
- **Stay** - dismisses the dialog.

## 7. Background service behavior (`FocusVpnService`)

- **Start:** `startForeground()` is called FIRST (within the 5s Android
  contract), then it reads the active profile's blocked packages from Room and
  establishes the blackhole tunnel. Shows the "Focus Session Active"
  notification with a live timer + "End session" button.
- **Stop:** closes the tunnel, calls `stopForeground(REMOVE)` + cancels the
  notification so the VPN status-bar icon and notification disappear cleanly.
- Returns **`START_NOT_STICKY`** so the system does not auto-restart it after a
  stop (prevents the VPN icon coming back after OFF).
- **Null-intent guard:** `onStartCommand(null)` (system re-delivery after death)
  calls `stopSelf()` and does NOT re-establish.
- **Stale-state reconcile:** on app start, if the service is not running but
  Room still has a running session (app was killed while ON), the session is
  ended and the profile deactivated so the toggle reads OFF.

## 8. Key UX / product decisions

- **Instant toggle** - VPN consent is handled during onboarding, so the main
  toggle is a 1-tap action with zero popups.
- **Effective profile** = `activeProfile ?: profiles.firstOrNull()` - keeps the
  profile row, blocked count, and toggle in agreement (prevents the toggle from
  minting a new empty profile on every tap).
- **0-apps guard** - toggling ON with no blocked apps opens the picker.
- **Welcome screen on every open** - granted permissions hidden for returning
  users.
- **Back = confirm** - never minimizes instantly.
- **Crash-proof logging** - writes to `Download/NullFlow/nullflow.log`
  (MediaStore, no permission) + logcat tag `NullFlow`; a default uncaught
  exception handler captures full stack traces before the process dies.

## 9. Current status

- **Built & working.** APK: `NullFlow.apk` (~32 MB) at
  `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.
- Core flow confirmed working on device: pick apps → Done → toggle ON → shield
  active (blocked apps blackholed) → toggle OFF → clean stop.
- Recent work: the **Focus Telemetry Console** - a live-data observability hub
  (metric cards + interception donut + threat ledger + 7-day heatmap) replacing
  the old radar. Every pixel is a real byte dropped by the VPN; the intercept
  log is written in 2s batches by the VPN service (no per-packet disk I/O).
- **Known watch-outs:** `QUERY_ALL_PACKAGES` is a Play Store scrutiny item (fine
  for sideloaded APK). Compose BOM 2024.02.00 has several API gotchas (documented
  in the repo). Room is at v3 (destructive migration - existing users lose old
  data on upgrade).
