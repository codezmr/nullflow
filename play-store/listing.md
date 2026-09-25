# NullFlow - Play Store Listing

## App Name
NullFlow

## Tagline (30 chars max)
Block apps at the network level

## Short Description (80 chars max)
A local firewall that cuts off internet for the apps that steal your focus.

## Full Description

Distracting apps fight for your attention with infinite feeds, constant notifications, and background updates. NullFlow ends the fight at the network level.

Select the apps that distract you. Flip the switch. NullFlow silently drops their internet traffic into a local black hole - their feeds stop loading, notifications stop arriving, and updates stop syncing - while the rest of your phone works normally.

HOW IT WORKS
NullFlow runs a local firewall on your device using Android's standard network APIs. Traffic from the apps you choose is intercepted and dropped on-device. Traffic from every other app passes through untouched, with zero overhead. No root. No account. No server.

WHAT YOU GET
- Focus Modes: build unlimited profiles (Deep Work, Reading, Sleep) with their own blocked-app lists
- One-tap shield: from the dashboard, the Quick Settings tile, or the notification
- Scheduled sessions: recurring focus windows that start and stop on time, even after a reboot
- Surgical Bypass: pause one app's block for 2-30 minutes to grab an OTP without editing your mode
- Telemetry HUD: intercept counts, top offenders, 7-day heatmap, total focus time

PRIVACY DOCTRINE
NullFlow is a shield, not a sensor.
- Zero outbound network calls. The app cannot reach the internet.
- Zero telemetry. No analytics, no crash reporting, no tracking.
- Everything is stored locally on your device and deleted when you uninstall.
- Open source under the MIT license. Read every line: github.com/codezmr/nullflow

REQUIREMENTS
- Android 11 or newer
- No root required

## Category
Tools > Productivity
(Alternative if rejected under VPN policy: Tools > Device tools, with the
VpnService declaration form + demonstration video attached.)

## Content Rating
Everyone (no violence, no ads, no in-app purchases, no data collection)

## Data Safety Form

### Data shared with us
None. The app does not collect any data.

### Data collected
None.

### Data safety declarations
- We do not collect any data.
- Data is not shared with any third party.
- Users can request deletion of data: all data is stored on-device and is
  permanently deleted when the app is uninstalled.

### Permission justifications

QUERY_ALL_PACKAGES:
"NullFlow is a per-app local firewall. Core functionality requires users to
select any installed application on their device to block network access.
Because the user must be able to target any arbitrary app, declaring static
package names in the <queries> element is technically impossible. App
inventory inspection occurs 100% on-device and package data is never stored,
tracked, or transmitted."

FOREGROUND_SERVICE_SPECIAL_USE:
"Required to sustain the active on-device VpnService network blackhole during
user-initiated focus sessions. Without an active foreground service,
Android's background execution limits kill the process, silently terminating
network isolation and breaking the firewall's core guarantee. It displays a
real-time session timer HUD and shuts down immediately when the session ends."

POST_NOTIFICATIONS:
"Displays the live focus session timer and intercept count in the
notification shade so you can see your focus status at a glance."

RECEIVE_BOOT_COMPLETED:
"Restores your shield automatically after a device restart when auto-start
is enabled, so a reboot does not silently disable your focus protection."

USE_EXACT_ALARM:
"Starts and stops scheduled focus sessions at the exact times you set."

ACCESS_NETWORK_STATE:
"Detects when another app takes over the system network slot so the shield
can pause gracefully instead of failing silently."

## VpnService Declaration (policy form)

- Core functionality statement:
  "NullFlow's entire purpose is to act as a local, per-app network firewall.
  The VpnService API is used to create an on-device tunnel that drops network
  traffic from user-selected apps. No traffic is routed to any external
  server, no traffic is modified, and no data is collected."
- Demonstration video: [UNLISTED YOUTUBE LINK - record per the 5-step script]

## Privacy Policy URL
https://shutupchat.com/nullflow/privacy

## Contact Email
[Your Play Console contact email]

## Assets Checklist
- [x] App icon: play-store/icon-512.png (512x512, 32-bit PNG, 213 KB)
- [x] Feature graphic: play-store/feature-graphic-1024x500.png (1024x500, 24-bit PNG, no alpha, 180 KB)
- [ ] Phone screenshots x4 (9:16, min 1080px) - capture on device:
      1. Reactor Core hero with shield ON (live intercept heat)
      2. Focus Modes list / mode creation
      3. Telemetry HUD (intercept counts + 7-day heatmap)
      4. Surgical Bypass in action (single-app pause timer)
- [ ] 512x512 icon and feature graphic uploaded in Play Console > Store presence
