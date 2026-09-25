<div align="center">

# ⬛ NullFlow

**Absolute silence. A zero-trust focus shield for Android.**

**Landing page:** [shutupchat.com/nullflow](https://shutupchat.com/nullflow)

[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android)](#requirements)
[![Root](https://img.shields.io/badge/Root-Not_Required-2E3440?style=for-the-badge)](#requirements)
[![License](https://img.shields.io/badge/License-MIT-4C566A?style=for-the-badge)](#license)
[![Size](https://img.shields.io/badge/Size-~12_MB-88C0D0?style=for-the-badge)](#install)

Select the apps that distract you. Flip the switch. <br>
NullFlow silently drops their network traffic into the void through a local VPN tunnel.

</div>

---

## 🕳️ The Mechanics of Silence

NullFlow leverages Android's `VpnService` API to create a local black hole (IPv4 + IPv6). When active, DNS queries and TCP/UDP connections from blocked apps are intercepted and dropped at the socket level.

The apps still function locally, but their feeds, notifications, and updates are entirely cut off.

```text
       [ YOUR DEVICE ]                                        [ THE INTERNET ]
                                                              
 📱 Distracting App ──(Network Request)──> [ 🛡️ NullFlow ] ──> 🕳️ THE VOID
 📱 Allowed App     ──(Network Request)──> [ 🌐 Android  ] ──> 🌍 CONNECTED
```

*   **Zero Overhead:** Allowed apps bypass the tunnel entirely (kernel routes by UID).
*   **Airtight:** Both IPv4 and IPv6 are routed. Apps cannot bypass the shield.
*   **Unobtrusive:** Uses standard VPN permissions. No root required.

---

## ⚡ Feature Matrix

| Core Systems | Description |
| :--- | :--- |
| **Reactor Core** | Hero UI toggle with live intercept heat-pulsing based on real-time blocking. |
| **Session Engine** | Set recurring focus windows via precise `AlarmManager` triggers (survives reboots). |
| **Tactical Pass** | *[BETA]* A 2-minute network leash to temporarily pause the shield without breaking your session. |
| **Surgical Bypass** | Pause a single app's block (2 to 30 mins) to grab an OTP without editing your mode. |
| **Focus Modes** | Build infinite profiles (e.g., *Deep Work*, *Reading*, *Sleep*). |
| **Telemetry HUD** | Track intercept counts, top offenders, 7-day heatmaps, and total focus time. |
| **Conflict Detection** | Gracefully disengages if another VPN claims the Android slot (no false positives). |
| **OEM Routing** | Built-in *Don'tKillMyApp* autostart routing for Xiaomi, Oppo, Vivo, and OnePlus. |

---

## 🔒 Absolute Privacy Doctrine

> **NullFlow is a shield, not a sensor.** 

1. **No Outbound Calls:** NullFlow makes absolutely zero network requests. 
2. **Local Processing:** The VPN tunnel exists only on your device; packets never leave the hardware.
3. **Zero Telemetry:** No analytics, no crash reporting, no tracking.
4. **Offline Storage:** All modes, schedules, and stats are stored entirely offline via Room (SQLite).

---

## 🚀 Deployment Protocol

<details>
<summary><b>Option A: From the Landing Page</b></summary>
<br>

The [landing page](https://shutupchat.com/nullflow) has a "Download APK" button that scrolls to the deployment protocol (compile from source or grab a pre-built release).

</details>

<details>
<summary><b>Option B: Quick Install (GitHub Releases)</b></summary>
<br>

1. Navigate to [Releases](https://github.com/codezmr/nullflow/releases).
2. Download `NullFlow-v1.4.0.apk`.
3. Install the APK (ensure "Install unknown apps" is enabled).
4. Complete the 3-step onboarding.
5. Add the NullFlow tile to your Quick Settings panel.
</details>

<details>
<summary><b>Option C: Compile from Source</b></summary>
<br>

```bash
git clone https://github.com/codezmr/nullflow.git
cd nullflow
./gradlew assembleDebug

# APK generated at: app/build/outputs/apk/debug/NullFlow.apk
```
</details>

---

## 🧬 System Architecture

NullFlow is built on a modern, reactive Android stack:
*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **Database:** Room (SQLite)
*   **SDK:** Min 30 (Android 11) / Target 34

```text
nullflow/
├── app/src/main/java/com/codezmr/nullflow/
│   ├── vpn/              # VpnService, packet filtering, HeatState engine
│   ├── data/             # Room DB, FocusSchedules, OEM Autostart routing
│   ├── service/          # Boot/Session Receivers, AlarmManager scheduling
│   ├── ui/               # Compose Screens (Reactor Core, Dashboards, Sheets)
│   ├── tile/             # Quick Settings Tile Service integration
│   ├── MainActivity.kt   # App entry point & session reconciliation
│   └── AppLog.kt         # Local-only file logger (opt-in)
└── build.gradle.kts      # AGP 8.5.2 / Gradle 8.7
```

---

<div align="center">
  <p><b>License:</b> <a href="LICENSE">MIT</a> &nbsp; | &nbsp; <b>Contributions:</b> <a href="CONTRIBUTING.md">PRs Welcome</a></p>
</div>
