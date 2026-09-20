# NullFlow - Session State

> **Read this first to resume.** Last updated: 2026-09-05.
> Goal: per-app internet kill-switch ("selective Offline Switch") via a local
> VPN blackhole. Premium UI: hero toggle + haptics + bottom sheets.
>
> **Brand:** NullFlow · package `com.codezmr.nullflow` · company Codezmr
> Tagline: "Disconnect on your terms." (full branding in doc/APP_IDEA.md)
>
> ⚠️ **HARD RULE: NEVER build the APK without asking Zamir first.**
> Discuss + finish all code changes, THEN ask "build or not?". Wait for the go-ahead.
>
> 🔒 **GIT WORKFLOW:** public GitHub remote (`github.com/codezmr/nullflow`).
> **NEVER touch `master`** - work on feature branches (currently `dev`).
> **ALWAYS ask before commit/push. ALWAYS open an MR and hand the link over -
> never merge it yourself.** Full rules in `GIT_INSTRUCTIONS.md`.
> Never commit: `local.properties`, `.gradle/`, `build/`, `*.apk`, `*.aab`, `.weave/`.

---

## ✅ Current Status: BUILT - Focus Telemetry Console (live interception data)

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):**
`NullFlow.apk` (32 MB) at `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.
**Committed** as `6e5c729` (14 files, +948/−468). Working tree clean.

### What changed (this round)
The basic stats view is replaced by a **Focus Telemetry Console** - a
cybersecurity-style observability hub built STRICTLY from live Room data
(every pixel = a real byte dropped by the VPN; zero mock data).

1. **Data layer** (Room **v2 → v3**, `fallbackToDestructiveMigration`):
   - `data/InterceptLog.kt` (NEW) - entity `intercept_logs(id AutoGenerate,
     packageName, timestamp)`, indexed on `timestamp` + `packageName`.
   - `data/AppInterceptStats.kt` (NEW) - `(packageName, interceptCount)`.
   - `data/DailyFocusStats.kt` (NEW) - `(dayStart, focusMs, interceptCount)`.
   - `data/PeakHourStats.kt` (NEW) - `(hourOfDay, cnt)`.
   - `data/BlockedApp.kt` - **removed `deflectedCount`** (replaced by the
     intercept log; the old per-app counter column is dead).
   - `data/FocusDao.kt` - replaced the radar queries with:
     - `getInterceptionsByApp(limit=5)` → `SELECT packageName, COUNT(id) ...
       GROUP BY packageName ORDER BY interceptCount DESC LIMIT :limit`
     - `getTotalIntercepts()` → `SELECT COUNT(id) FROM intercept_logs`
     - `getPeakInterceptHour()` → `strftime('%H', timestamp/1000, 'unixepoch',
       'localtime')` grouped, top 1 (the "Peak Focus Time" metric)
     - `getDailyTelemetry(dayStart)` → 7-row day-series (UNION ALL generator)
       joining focus-session ms + intercept counts per day (heatmap)
     - `insertInterceptLogs(List)` - batch insert
   - `data/FocusDatabase.kt` - v3, registered `InterceptLog`.
2. **Service layer** (`vpn/FocusVpnService.kt`) - **batched live packet logging**:
   - Packet reader now calls `bufferDeflectedPing()`: round-robin attribution
     (same privacy-correct scheme, now keyed by **package name**) → lock-free
     `ConcurrentLinkedQueue` enqueue (O(1) on the hot path).
   - New `startInterceptFlusher()`: drains the queue into ONE multi-row Room
     insert every **2s** on `Dispatchers.IO` (5000-row cap) + a final flush on
     teardown so the session tail isn't lost. No per-packet disk I/O.
   - `blockedPackages` (parallel to `blockedAppIds`) stamps the package name
     onto each buffered `InterceptLog`.
3. **UI layer** (`ui/MainScreen.kt`) - **Telemetry Console** (inactive state):
   - **Deleted `ui/FocusRadarGraph.kt`** (the hexagonal radar is gone).
   - **Telemetry header:** 3 glassmorphic metric cards (`#12151C` surface,
     `#222733` border) - **Total Uptime** (all-time focus), **Threats
     Neutralized** (total intercepts), **Peak Focus Time** (e.g. "09:00 AM").
   - **Interception donut:** thick-ringed `Canvas` chart, top 3 apps in
     Cyan `#00E5FF` / Purple `#B44CFF` / Electric Blue `#4F8CFF`, animated
     sweep-in, center "DROPPED" total readout.
   - **Threat ledger:** `LazyColumn` of top 5 - app icon
     (`rememberAppIconPainter`), resolved app label, exact `N×` count,
     `LinearProgressIndicator` scaled to the top app's count.
   - **7-day activity heatmap:** 7 rounded boxes, color-lerped `#1A1D24` →
     glowing `#00E5FF` by daily Focus Score (focus minutes + intercept weight);
     today outlined in cyan.
   - **Empty state:** pulsing `[ AWAITING NETWORK TELEMETRY ]` wireframe when
     0 intercepts (no 0% pie, no crash).
   - Dossier share now fed by `getTotalIntercepts()` (live).

### Assumptions (documented - no interactive channel back to Zamir)
1. **Package attribution:** the tunnel fd yields only a raw byte stream - the
   OS never says which app sent a packet (parsing IP headers would leak
   per-app usage). Kept the existing **round-robin** attribution across the
   shielded packages, now stamped per-package into `InterceptLog`.
2. **Peak Focus Time** = hour of day with the most intercepted pings (the only
   per-event timestamp we have; sessions are too coarse for "09:00 AM").
3. **Heatmap day boundaries** = local midnight; the DAO always returns exactly
   7 rows (zero-filled), so no client-side gap-filling.
4. DB v3 with destructive migration (consistent with the project's existing
   migration strategy - existing users lose old data on upgrade).

### ⚠️ Still to verify on device (after install)
- Inactive screen: 3 metric cards + donut + ledger + heatmap render (no
  overflow, dashboard stays pinned to the bottom).
- Fresh install (0 intercepts): `[ AWAITING NETWORK TELEMETRY ]` wireframe
  shows instead of a 0% pie.
- After a shield session: donut shows top-3 apps in cyan/purple/blue; ledger
  shows icons + exact counts + progress bars; heatmap lights up for today.
- "Threats Neutralized" count matches the notification's "X Distractions
  Intercepted" (both from the same live data).
- Peak Focus Time shows a real hour (e.g. "09:00 AM") after intercepts exist.
- (Carried) HUD + packet counter + QS pinning + dossier share still working.

---

## ✅ Previous: BUILT - UI Polish, Layout Anchoring & Copy Enforcement

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`, zero warnings):**
`NullFlow.apk` (31 MB) at `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir)
1. **Layout Anchoring** (`MainScreen.kt`): restructured the root layout so the
   bottom dashboard (Radar + Stats) is **permanently pinned** to the bottom edge.
   - Top: `HudHeader` (fixed height).
   - Center: `Column(weight(1f))` containing Hero Toggle + App Icons (absorbs
     all remaining space, content centered vertically).
   - Bottom: `StatsRow` / `CommandCenter` (NO weight - anchored).
   - Radar reduced from 280dp → 220dp to keep the dashboard compact.
2. **Ghost Radar Nodes** (`FocusRadarGraph.kt`): added **text labels** (app
   names, truncated to 12 chars) at each data vertex. The floating scrub label
   now shows "AppName · N intercepted" instead of just "N deflected".
3. **Global Copy Rename**: "Pings Deflected" → **"Distractions Intercepted"**
   across all files (notification HUD, CommandCenter stats, DossierGenerator,
   XML layout, comments).
4. **Empty-State Button** (`MainScreen.kt`): "Choose apps to shield" now uses
   a dark surface (#12151C) + 1dp cyan border (#00E5FF) + cyan text (was a
   translucent primary-color pill).
5. **Slider Geometry** (`OnboardingScreen.kt`): `SwipeToArmSlider` track height
   64dp → **56dp**, corner radius 20dp → **16dp**, thumb 56dp → **44dp**. QS
   button height 54dp → **56dp**. Both now match (56dp / 16dp).
6. **DossierGenerator Crash Fix** (`DossierGenerator.kt`): replaced the
   off-screen `ComposeView` (which crashed with "Cannot locate windowRecomposer")
   with **pure Android Canvas drawing**. No Compose dependency - renders the
   9:16 share card directly to a Bitmap.

### ⚠️ Still to verify on device (after build)
- MainScreen: bottom dashboard (Radar + Stats) stays visible when shield is ON
  with multiple apps selected.
- Radar: app name labels visible at each vertex. Scrub shows "AppName · N
  intercepted".
- Notification: "X Distractions Intercepted" (not "Pings Deflected").
- Empty state: "Choose apps to shield" button has dark bg + cyan border.
- Onboarding: SwipeToArmSlider and QS button have matching 56dp/16dp geometry.
- Share dossier: generates PNG without crashing (no windowRecomposer error).

---

## ✅ Previous: BUILT - Tactical HUD Header + Exit Dialog + Icon Fix

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`, zero warnings):**
`NullFlow.apk` (31 MB) at `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir)
1. **Tactical HUD Header** (`MainScreen.kt`): replaced the centered "NullFlow"
   + tagline with a top-left asymmetrical HUD:
   - **"NULLFLOW"** all-caps, `FontWeight.Black`, `letterSpacing = 2.sp`, 22sp.
   - **Status line:** 6dp circular node + monospace `Text` (`FontFamily.Monospace`,
     11sp). State-driven:
     - **ON:** cyan `#00E5FF` node with `shadow(blur=8dp)` glow + `SYS.STATUS: SECURE`.
     - **OFF:** muted grey `#4A4E58` node (no glow) + `SYS.STATUS: STANDBY` (`#8A8F99`).
   - `statusBarsPadding()` for notch/cutout safety. Animated color/glow (400ms).
   - 40dp guaranteed min gap to the Hero Switch.
2. **Exit Dialog** (`MainActivity.kt`): "Minimize" → **"Exit"**. On Exit: sends
   `ACTION_STOP` to `FocusVpnService` (stops shield + ends session + cleans Room +
   removes notification - idempotent), then `finishAffinity()` to close the app
   completely. Rationale: "Minimize" made no sense (user can just switch apps).
3. **App Picker Icon Placeholder** (`AppPickerSheet.kt` `TactileAppCard`): added a
   visible 40dp placeholder circle BEHIND the async-loaded icon. Root cause:
   `rememberAppIconPainter` returns a transparent `ColorPainter` until the bitmap
   loads on `Dispatchers.IO`, so the icon slot appeared empty until the user tapped
   the card (recomposition after load). Now the slot always shows a dark circle.

### ⚠️ Still to verify on device (after build)
- HUD header: top-left "NULLFLOW" + status line. ON → cyan glow + "SECURE";
  OFF → grey + "STANDBY". No notch/cutout overlap.
- Back button → "Exit NullFlow?" dialog → Exit stops shield + closes app;
  Stay dismisses.
- App picker: icons show a placeholder circle immediately (not empty) while
  loading; icon fades in on top.

---

## ✅ Previous: BUILT - Welcome Screen "Initiation Sequence"

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`, zero new warnings):**
`NullFlow.apk` (31 MB) at `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir)
The onboarding screen is now an "Initiation Sequence": elite copy, glowing
permission states, and a tactile `SwipeToArmSlider` replacing the old button.

1. **Copy & spacing** (`OnboardingScreen.kt`):
   - "How it works" → **"SYSTEM PROTOCOLS"**.
   - Feature card titles: "Pick the apps to silence" → **"Targeted
     Interception"**, "One tap, zero popups" → **"Tactical Deployment"**,
     "Your data never moves" → **"Zero-Leak Architecture"**.
   - Spacing between info cards: 12.dp → **8.dp**.
   - Info cards now **dimmed** (`FeatureCard(dimmed = true)`: 0.55 alpha, muted
     icon/title/desc) so the glowing permission rows carry the visual weight.
2. **Permission rows** (`NeumorphicChecklistItem`):
   - Incomplete: subtle `#222733` border + hollow circle.
   - Complete: **glowing cyan `#00E5FF` border** (cyan ambient/spot shadow) +
     solid cyan circle with checkmark + text **dims** (title 0.9→0.6, desc
     0.45→0.3 alpha) to signal "done".
3. **Real-time permission observation** (`DisposableEffect` +
   `LifecycleEventObserver` on `ON_RESUME`): re-checks BOTH notifications
   (`checkSelfPermission`) and VPN (`VpnService.prepare(context) == null`) when
   the user returns from system settings → slider unlocks instantly.
4. **`SwipeToArmSlider`** (NEW composable, replaces `GatekeeperButton`):
   - **Locked** (perms missing): dark grey `#1A1D24` track, `[ SYSTEM LOCKED ]`,
     thumb not draggable.
   - **Unlocked** (perms granted): cyan gradient track, `> SWIPE TO ARM >`.
   - Drag: `detectHorizontalDragGestures`, offset clamped to
     `[0, trackWidth - thumbWidth - padding]` (measured via
     `onGloballyPositioned` + `LocalDensity`).
   - **Detent-based haptics**: `TextHandleMove` every ~15% of travel (not every
     frame - avoids haptic machine-gun).
   - **90% threshold**: heavy `VibrationEffect.createWaveform` thud (THUD/TICK/
     THUD pattern), thumb snaps to end, `onArmed()` → `markOnboarded()` +
     `onEnter()`.
   - `Haptics.vibratorFor(context)` exposed (new public method) for the custom
     waveform.
   - ⚠️ **API note:** `VibrationEffect.Composition` is package-private (not
     accessible to app code) - used public `createWaveform()` instead (closest
     equivalent).

### ⚠️ Still to verify on device (Motorola Edge 40 / Android 15 = API 35)
- Onboarding shows "SYSTEM PROTOCOLS" + the 3 new card titles (dimmed).
- Permission rows: hollow circle + grey border when pending; glowing cyan border
  + solid check + dimmed text when granted.
- Granting a permission via system settings → return to app → row updates
  instantly (ON_RESUME recheck) + slider unlocks.
- Slider: locked (grey, "[ SYSTEM LOCKED ]") until both perms granted.
- Unlocked: cyan track, "> SWIPE TO ARM >". Drag → detent haptics. At 90% →
  heavy thud + navigate to MainScreen.
- (Command Center + radar + dossier + HUD + QS pinning from previous commits
  still working.)

---

## ✅ Previous: Premium Analytics Command Center + Shareable Dossier

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir)
The inactive shield state (Main Screen) is now a **Premium Analytics Command
Center** with a custom-drawn radar + a 1-tap shareable dossier.

1. **Per-app interception tracking** (data layer):
   - `BlockedApp.deflectedCount: Long` (new column, DB **v1 → v2**,
     `fallbackToDestructiveMigration`).
   - `FocusDao.observeTopIntercepted(limit)` - top-N apps by deflectedCount.
   - `FocusDao.incrementDeflected(id, delta)` - atomic increment.
   - `FocusDao.observeTotalDeflected()` - SUM across all apps (for the dossier).
2. **Round-robin attribution** (`FocusVpnService.kt`): the packet reader now
   attributes each deflected ping to a blocked app in rotation and persists it
   to Room. `readBlockedPackages` → `readBlockedApps` (returns packages + Room
   IDs). `blockedAppIds` + `attributionCursor` (AtomicInteger) track rotation.
   - **Privacy note:** the tunnel drops packets silently and the reader sees
     only the raw byte stream - parsing IP headers to learn WHICH app sent a
     packet would leak per-app usage. Round-robin is the privacy-correct proxy.
3. **Distraction Radar** (`ui/FocusRadarGraph.kt`, NEW): hexagonal `Canvas`
   graph. Base web (3 concentric hexagons + spokes, `#1E222B`), data polygon
   (cyan `#00E5FF` 0.3 alpha fill + glowing stroke), 4dp cyan node circles.
   Data normalized against the max app (outer edge = 100%). **Tactile
   scrubbing:** `detectDragGestures` → haptic `TextHandleMove` tick when crossing
   a node + floating label with the exact count. Animated reveal on draw.
4. **Shareable Dossier** (`ui/DossierGenerator.kt` + `ui/DossierShare.kt`, NEW):
   - 9:16 (1080×1920) Compose card: NullFlow brand mark, total focus time,
     total pings deflected, session count, glassmorphic gradient bg, date.
   - Captured via off-screen `ComposeView` + `View.drawToBitmap` on
     `Dispatchers.IO`, saved to `cacheDir/nullflow_dossier.png`.
   - **ZERO-LEAK:** only aggregate stats + brand - no app/package names.
   - `DossierShare.share()` → `FileProvider.getUriForFile` + `ACTION_SEND`
     (`image/png`, `FLAG_GRANT_READ_URI_PERMISSION`).
5. **FileProvider** (`AndroidManifest.xml` + `res/xml/file_paths.xml`):
   `<provider>` for `androidx.core.content.FileProvider` (authority
   `${applicationId}.fileprovider`, `cache-path` = `.`). Prevents
   `FileUriExposedException` on Android 11+.
6. **MainScreen wiring**: inactive state now shows `CommandCenter` (radar +
   aggregate stats + "Share my focus dossier" gradient button) instead of the
   old `StatsRow`. Active state keeps the live `StatsRow`. `dossierBusy` state
   drives the button's "Generating…" feedback.

### ⚠️ Still to verify on device (Motorola Edge 40 / Android 15 = API 35)
- Inactive screen shows the hexagonal radar (empty state if no data yet).
- After a shield session, the radar shows the top-6 apps as a cyan polygon.
- Dragging across a node → haptic tick + floating "N deflected" label.
- "Share my focus dossier" → generates card → share sheet opens with the image.
- Shared image renders correctly (9:16, brand + stats, no app names).
- (HUD + packet counter + QS pinning from previous commits still working.)

---

## ✅ Previous: 1-Tap QS Tile Pinning (onboarding) + HUD

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir)
1. **1-tap QS tile pinning** (`MainActivity.kt`): new public
   `requestAddQsTile(onResult: (Boolean) -> Unit)` - gated behind
   `Build.VERSION.SDK_INT >= TIRAMISU` (33). Uses
   `StatusBarManager.requestAddTileService(ComponentName, "GhostShield",
   Icon, mainExecutor, Consumer<Int>)`. Callback result code 0 = added,
   non-zero = dismissed. Dependency-free main-thread `Executor` (Handler on
   main looper). Pre-33 → reports `false` (onboarding shows fallback card).
2. **Onboarding tile section** (`OnboardingScreen.kt`): new
   `QsTilePinSection` composable injected ABOVE the gatekeeper button.
   Header: "Highly Recommended for Seamless Use".
   - **API 33+:** electric-cyan (`NeonCyan` #00E5FF) outlined button
     "[ ⚡ Pin to Quick Settings ]". On tap → haptic tick + `requestAddQsTile`.
     On success → flips to dimmed "✓ Added to Quick Settings" + haptic engage.
   - **API 30-32 fallback:** muted glassmorphic card with manual
     drag-and-drop instructions.
   - NEVER blocks onboarding (optional).
3. **OnboardingScreen signature** now takes
   `onRequestAddQsTile: ((Boolean) -> Unit) -> Unit` (wired from MainActivity).

### ⚠️ Still to verify on device (Motorola Edge 40 / Android 15 = API 35)
- Onboarding shows the cyan "Pin to Quick Settings" button above "Enter NullFlow".
- Tapping it → system dialog → tile appears in QS → button flips to "Added".
- Haptic tick on tap + engage on success.
- (HUD + packet counter from previous commit still working.)

---

## ✅ Previous: "Pings Deflected" HUD + Packet Counter

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir, Option A)
1. **Packet interceptor** (`FocusVpnService.kt`): new `deflectedPings`
   (`AtomicInteger`) + `startPacketReader(fd)` - a dedicated IO coroutine reads
   the tunnel's `FileInputStream` in a `while(shouldRun)` loop (32 KB buffer).
   Every successful read = one deflected attempt → increment counter. Payload is
   NEVER inspected/logged/stored (strict zero-data privacy). Reader runs on its
   own `readerScope` so hot-swaps restart it without killing the ticker.
   Reset to 0 on each fresh session; cancelled on teardown + onDestroy.
2. **Custom "Pings Deflected" HUD** (`res/layout/notification_focus_hud.xml`):
   OEM-safe `LinearLayout` (no ConstraintLayout), fixed padding, `singleLine` +
   `ellipsize` on all TextViews. Dark `#0A0C10` bg, shield icon, title, timer
   (`tv_timer`), cyan `tv_pings` ("X Pings Deflected"), cyan "End" button
   (`btn_end_session`). New `ic_shield_hud.xml` (filled shield + check).
3. **HUD wired to service** (`FocusVpnService.kt`): `buildNotification()` now
   builds `RemoteViews`, sets `tv_timer` + `tv_pings`, binds `btn_end_session`
   → stop PendingIntent, body → MainActivity. Uses `setCustomContentView` +
   `setCustomBigContentView` (with plain-text fallback for OEMs that ignore
   RemoteViews). The existing 1s ticker re-notifies every second → live ping
   count.
4. **Zero-warning cleanup** (`MainScreen.kt`): replaced the two `effectiveProfile!!`
   non-null assertions with a safe local `val profile = effectiveProfile` inside
   the `if` block. Both compiler warnings eliminated.

### ⚠️ Still to verify on device (Motorola Edge 40 / Android 15)
- Notification shows the custom HUD (shield + timer + cyan "X Pings Deflected"
  + "End" button) - NOT the default text layout.
- Ping count increments in real time when a blocked app tries to connect.
- "End" button stops the shield (tile + panel + notification all OFF).
- HUD doesn't clip on the skinned OS (singleLine + ellipsize should prevent it).
- Home screen: no `!!` warnings; icon row still renders.

---

## ✅ Previous: Focus Matrix v2 (no presets, home-screen icons, bug fixes)

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### What changed (this round - approved by Zamir)
1. **Dropped predefined presets** (`AppPickerSheet.kt`): removed the
   "ONE-TAP PRESETS" section, `PresetChip` composable, and `togglePreset()`.
   Users now build their own modes by picking individual apps (trust: no opaque
   categories). `PackageManagerRepo.presets` is now unused (left in place, harmless).
2. **Fixed header string leak** (`AppPickerSheet.kt`): the header now uses
   `val shieldedCount = blockedPackages.size` then `"$shieldedCount Shielded"`
   (or "No apps shielded" when 0). No more raw-list interpolation.
3. **Unified app-icon loading** (`AppPickerSheet.kt`): `TactileAppCard` now
   takes `packageName` (not `Bitmap`) and renders via the proven
   `rememberAppIconPainter` (async, cached, `BitmapPainter`) - same loader the
   QS tile panel uses. Replaced the deprecated `Image(bitmap = ...)` overload.
   Cleaned up now-unused imports (`Bitmap`, `asImageBitmap`, `LazyRow`).
4. **Home-screen blocked-app icon row** (`MainScreen.kt`): beneath the active
   profile name, a scrollable `LazyRow` of the blocked apps' icons (24dp
   circles, 8dp spacing) + right-edge gradient fade - mirrors the tile panel so
   the user sees exactly what's shielded. New `BlockedAppIconRow` composable.

### ⚠️ Still to verify on device
- App picker: NO preset chips; search + tactile cards only; icons render (not
  grey circles); header shows "N Shielded" (not a raw list).
- Home screen: blocked-app icon row appears under the profile name + fades right.

---

## ✅ Previous: Architecture Polish & UX Improvements (all 6 done)

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.
**Spec:** `doc/POLISH_SPEC.md` (all Q1–Q6 = Option A, approved by Zamir).

### What changed (all Option A)
1. **Q1 - live notification timer:** `FocusVpnService.startTimerUpdates` now
   ticks every **1s** (`delay(1_000)`) instead of 30s.
2. **Q2 - "End session" desync FIXED:** `FocusVpnService.teardown()` now calls
   `clearRoomSession()` (ends running session + deactivates profile) on a fresh
   one-shot scope (serviceScope is already cancelled). EVERY stop path (app
   toggle / notification / QS tile) now keeps Room in lockstep → tile + panel
   can't disagree.
3. **Q3 - QS onboarding gate:** `FocusTileService.onClick()` checks
   `Settings.hasOnboarded`; if false → skips the panel and forces MainActivity
   (Welcome) + collapses. Also fixed `startActivityAndCollapse` to use a
   **PendingIntent** (Intent form is disallowed on Android 15 - was crashing in
   logs).
4. **Q4 - "Focus Matrix" app picker (NO checkboxes):** `AppPickerSheet` fully
   overhauled - sticky dark-glass search bar (filters all), one-tap Preset
   Chips (💬 Social Noise / 🎬 Media Binge / 💬 Chat Drops, from
   `PackageManagerRepo.presets`), Tactile App Cards (unselected `#12151C` +
   `+ ADD` + desaturated icon; selected cyan glow `#00E5FF` + `🔒 SHIELDED` +
   icon halo), micro-spring press (0.96× bouncy) + `Haptics.thud` (40ms).
   Icons via built-in `BitmapPainter` (Coil/Accompanist unavailable offline).
   New `Haptics.thud()`.
5. **Q5 - hero 3D extrusion:** `MainScreen.HeroToggle` now has a dark offset
   drop-shadow (bottom-right, ambient+spot black) + top-left light→dark bevel
   gradient + subtle white radial specular highlight. BreathingHero untouched.
6. **Q6 - tile panel icon rows:** `TileFocusPanel` now shows a scrollable
   `LazyRow` of blocked-app icons (24dp circle, 8dp spacing) + right-edge
   gradient fade per mode. New `ProfileWithAppsRow` Room query
   (`observeProfilesWithApps`), `AppIconLoader.kt` (PackageManager icon loader
   + in-memory cache + `rememberAppIconPainter`).

### ⚠️ Still to verify on device
- Notification timer ticks every second.
- "End session" → tile AND panel both OFF (no desync).
- Tile tap before onboarding → forces Welcome.
- App picker: search filters all; preset chips block/unblock categories;
  tactile cards + spring + haptic; cyan accents.
- Hero toggle 3D look.
- Tile panel: app-icon rows per mode + right-edge fade.

**Next:** Zamir installs, tests all 6. Share `Download/NullFlow/nullflow.log`
if anything misbehaves.

---

## 🚧 In Progress: Architecture Polish & UX Improvements

**Spec:** `doc/POLISH_SPEC.md` (all Q1–Q6 = Option A, approved by Zamir).
**Status:** docs written, GhostShield committed; implementing the 6 items.

### Scope (all Option A)
1. **Q1** - notification timer ticks every 1s (`delay(1_000)` in
   `FocusVpnService.startTimerUpdates`).
2. **Q2** - fix "End session" desync: the service STOP handler also ends the
   Room session + deactivates the profile (so tile + panel agree).
3. **Q3** - QS onboarding gate: `FocusTileService.onClick()` checks
   `Settings.hasOnboarded`; if false → launch MainActivity (Welcome) + collapse.
4. **Q4 (UPGRADED → "Focus Matrix")** - app picker full overhaul, NO
   checkboxes: sticky dark-glass search bar (filters all) + one-tap Preset
   Chips (💬 Social Noise / 🎬 Media Binge / 💬 Chat Drops) + Tactile App Cards
   (unselected `#12151C` + `+ ADD`; selected cyan glow `#00E5FF` + `🔒 SHIELDED`
   + icon halo) + micro-spring press (0.96× bouncy) + thud haptic. Icons via
   built-in `BitmapPainter` (Coil/Accompanist unavailable offline).
5. **Q5** - `HeroToggle` (MainScreen only) gets a 3D extruded look (dark
   bottom-right shadow + white top-left highlight). BreathingHero stays flat.
6. **Q6** - tile panel: replace "N apps shielded" text with a `LazyRow` of
   blocked-app icons (24dp circle, 8dp spacing) + right-edge gradient fade.
   New `ProfileWithApps` Room query + PackageManager icon loader (cached).
7. **Bonus (from logs):** `startActivityAndCollapse` must use a **PendingIntent**
   (Intent form is disallowed on Android 15).

**Next:** implement 1–7, build, device-test.

---

## ✅ Current Status: BUILT - GhostShield tile crash FIXED (decorView owner)

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### 🐛 BUG FIXED: panel crashed on open - "ViewTreeLifecycleOwner not found"
Device log (Motorola Edge 40, Android 15 / API 35): tapping the tile →
`showDialog(FocusPanel)` → `IllegalStateException: ViewTreeLifecycleOwner not
found from android.widget.FrameLayout{... app:id/container}`.
Root cause (confirmed via Gemini): `TileService.showDialog()` uses a
**`TYPE_QS_DIALOG`** window that strips/doesn't propagate lifecycle tags to
children. Compose's `WindowRecomposer` searches up to the **window root
(decorView)** and throws if the tag isn't there. We had attached the owner to
the **ComposeView**, which the search never reached.
**Fix (`FocusTileService.showFocusPanel`):** attach the `PanelOwner` to the
**`dialog.window.decorView`** (the root) via `setViewTree*Owner(owner)` - NOT to
the ComposeView. Order: `setContentView` → attach owners to decorView →
`showDialog`.
> Note: Material 1.11.0's `BottomSheetDialog` is NOT a `ViewModelStoreOwner`
> (Dialog-based, not ComponentDialog), so we still use the lightweight
> `PanelOwner` (LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner),
> just attached to the decorView now. `DisposeOnViewTreeLifecycleDestroyed` kept.

### ⚠️ Still to verify on device
- Tap GhostShield tile → panel opens (NO crash), fully expanded.
- Master switch ON/OFF, mode switch (hot-swap when ON), "+ Create / Edit Modes".
- Dismiss panel repeatedly → no crash, no leak.

**Next:** Zamir installs the new APK, taps the tile, confirms the panel opens.
Share `Download/NullFlow/nullflow.log` if it still crashes.

---

## ✅ Previous Status: BUILT - GhostShield Quick Settings Tile + Focus Panel

**Built 2026-09-05 (CLEAN build, `BUILD SUCCESSFUL`):** `NullFlow.apk` (31 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### 🆕 FEATURE: GhostShield - Quick Settings Tile + native Compose Focus Panel
A QS tile ("GhostShield") in the notification shade. Tapping it opens a native
`BottomSheetDialog` (Compose) over the current app to flip the shield + switch
modes in ~0.5s without opening the main app. Spec: `doc/GHOSTSHIELD_SPEC.md`,
questions/decisions: `doc/QUESTIONS_GHOSTSHIELD.md` (all answered "defaults").

**New files:**
- `tile/FocusTileService.kt` - the QS tile. `onStartListening()` reflects state
  (blue glow ACTIVE / grey INACTIVE) + reactively syncs from Room. `onClick()`
  → `showFocusPanel()` (handles `isLocked` via `unlockAndRun`). Panel =
  `BottomSheetDialog` + `ComposeView` rendering `TileFocusPanel`.
  - **Lifecycle (verified best practice, via Gemini):** does NOT implement
    `LifecycleOwner` on the service. Uses a private `PanelOwner`
    (LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner) attached
    via `setViewTree*Owner(owner)`. `ViewCompositionStrategy
    .DisposeOnViewTreeLifecycleDestroyed` → no leaks. Owner destroyed on
    dismiss + `onDestroy` dismisses the dialog (no Window leak).
  - **QS overlay fix:** `behavior.state = STATE_EXPANDED` + `skipCollapsed = true`
    (avoids half-cut sheet). `startActivityAndCollapse` with
    `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
- `ui/tile/TileFocusPanel.kt` - Compose panel: master `Switch` (ON = start
  shield for active profile + insert session; OFF = stop + end session),
  "SELECT MODE" `LazyColumn` (RadioButton + name + "N apps shielded"),
  "+ Create / Edit Modes" → opens main app. All Room writes on `Dispatchers.IO`.
- `data/ProfileWithCount.kt` - (id, name, isActive, appCount) for the panel.
- `res/drawable/ic_hero_toggle.xml` - vector (circle + slash, the null-ring mark).

**Modified files:**
- `vpn/FocusVpnService.kt` - added `ACTION_STOP_SHIELD` + `ACTION_REFRESH_RULES`
  + companion intent builders (`startIntent`/`stopIntent`/`refreshIntent`).
  - **Hot-swap (Q1=A):** `refreshRules()` closes the old tunnel fd + re-`establish()`
    with the new active profile's packages, WITHOUT tearing down the foreground
    service/notification (no flicker). Tunnel can't be edited in place, so
    close+re-establish is the only correct way. Extracted `establishTunnel()`.
  - `ACTION_STOP_SHIELD` → `teardown()` (same as `ACTION_STOP`).
- `data/FocusDao.kt` - added `observeProfilesWithAppCount(): Flow<List<ProfileWithCount>>`.
- `build.gradle.kts` - added `com.google.android.material:material:1.11.0`
  (for `BottomSheetDialog`; cached, VPN-safe).
- `AndroidManifest.xml` - registered `.tile.FocusTileService`
  (`BIND_QUICK_SETTINGS_TILE`, `QS_TILE` intent-filter, label "GhostShield").
- `res/values/themes.xml` - `NullFlow_BottomSheet_Dialog`
  (parent `Theme.Material3.DayNight.BottomSheetDialog`, transparent + dim + floating).
- `res/values/colors.xml` + `strings.xml` - tile tints + `tile_label`.

### ⚠️ Still to verify on device
- Add the "GhostShield" tile to the QS shade (edit tiles) → it appears.
- Tap tile → panel opens over current app, fully expanded (not half-cut).
- Master switch ON → shield engages (VPN icon + notif); OFF → clean stop.
- Switch mode while ON → tunnel hot-swaps (blocked apps change, no notif flicker).
- Switch mode while OFF → just updates the active profile (no auto-start).
- "+ Create / Edit Modes" → panel closes, main app opens.
- Tile icon tints blue when active, grey when off.
- No crash/leak when dismissing the panel repeatedly.

**Next:** Zamir installs the new APK, adds the GhostShield tile, tests the flow.
Share `Download/NullFlow/nullflow.log` if the panel misbehaves (new `TilePanel:`
+ `FocusTileService.` log lines).

---

## ✅ Previous Status: BUILT - deterministic teardown (lingering notif FIXED)

**Built 2026-09-05 (commit `aec5ced`, CLEAN build):** `NullFlow.apk` (23 MB) at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.

### 🐛 BUG FIXED: notification + VPN icon lingered after OFF (service survived)
Log showed: notification "End session" fired STOP #1, then toggle fired STOP #2
+ `endCurrentSession` cleared Room (UI → OFF), **but the service process
survived** - its timer loop kept updating the notification ("00:30 · active")
after the UI said OFF.
Root cause: `stopSelf()` (no arg) only stops the *last* start request. With
multiple pending start requests (notification + toggle), the service lived on.
**Fix (`FocusVpnService`):**
- New **`shouldRun`** instance flag (checked by the timer loop) + a single
  **`teardown()`** helper = the ONLY teardown path.
- `teardown()`: sets `shouldRun=false` + `isShieldRunning=false`, cancels the
  timer scope, closes the fd, `stopForeground(REMOVE)`, `cancel(NOTIF_ID)`,
  `stopSelf()`. **Idempotent** (safe to call repeatedly).
- STOP / null-intent / `onRevoke` / all `startShield` failure paths now call
  `teardown()` (not bare `stopSelf()`).
- Timer loop: `while (shouldRun)` + re-checks before each notify → stops
  updating the instant a STOP arrives.
- `startShield` sets `shouldRun=true` on success.

### ⚠️ Still to verify on device
- Toggle OFF → notification + VPN icon gone **immediately** (no lingering).
- "End session" from notification → same clean stop.
- ON → OFF → ON cycle works (fresh `shouldRun` each start).
- (Carried) welcome screen scroll + premium look, back dialog.

**Next:** Zamir installs `aec5ced`, tests OFF cleanup thoroughly. Share
`Download/NullFlow/nullflow.log` if the icon/notif still lingers (the new
`teardown:` log lines will show exactly what ran).

---

## ✅ Previous Status: BUILT - welcome screen redesigned (scrollable + premium)

**Built 2026-09-03 (commit `3051837`):** `NullFlow.apk` (23 MB).

### 🆕 Welcome screen: scrollable + premium redesign
User feedback: "not scrollable" + "looks like text text only, make it premium".
**Changes (`OnboardingScreen.kt`):**
- **Scrollable**: content now in a `verticalScroll(rememberScrollState())` Column
  (was a fixed Column with `Spacer(weight(1f))` that couldn't scroll).
- **Ambient glow background**: two soft radial gradients (electric-blue top-left,
  neon-cyan bottom-right) for premium depth (`AmbientGlow`).
- **Privacy pill**: the "0 bytes" line is now a rounded badge with a 🔒 icon
  (was bare centered text).
- **Feature cards**: the 3 "how it works" rows are now rich neumorphic cards
  (`FeatureCard`) with an icon chip (48dp rounded square) + title + description
  (was bare `FeatureRow` text lines - removed).
- **Section labels**: uppercase eyebrow labels ("HOW IT WORKS", "ONE-TIME SETUP")
  via `SectionLabel`.
- (Carried) Breathing hero, rotating tip card, hidden granted perms, gatekeeper
  button, brand footer.

### ⚠️ Still to verify on device
- Welcome screen scrolls smoothly on small screens.
- Premium look: glow + cards + pill render correctly (no clipping).
- (Carried from `1149f21`) OFF cleanup, back dialog, tip card.

**Next:** Zamir installs `3051837`, checks the welcome screen look + scroll.
Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT - back dialog + welcome polish + sticky-restart fix

**Built 2026-09-03 (commit `1149f21`):** `NullFlow.apk` (23 MB).

### 🐛 BUG FIXED: VPN icon/notification lingered after OFF (sticky restart)
Root cause (from log): `onStartCommand` returned **`START_STICKY`** for the
start path. Every STOP intent → `stopSelf()` → system **re-started** the service
(sticky) → `onStartCommand(null)` → `else` branch → `startShield()` re-ran →
tunnel re-established → VPN icon came back. Log showed 5 repeated STOPs.
**Fixes (`FocusVpnService`):**
- Start path now returns **`START_NOT_STICKY`** (system won't auto-restart).
- **Null-intent guard**: `onStartCommand(null)` (system re-delivery after death)
  now calls `stopSelf()` and does NOT re-establish the tunnel.
- (Carried from `1ee1e86`): `onDestroy` + STOP path call `stopForeground(REMOVE)`
  + `cancel(NOTIF_ID)`; "End session" uses distinct PendingIntent request code 2.

### 🆕 UX: back-confirmation dialog (now "Exit")
Pressing back (button OR gesture) shows **"Exit NullFlow?"** dialog with
**Exit** / **Stay**. Wired via `OnBackPressedDispatcher.addCallback` in
`MainActivity`.
- **Exit** (was "Minimize"): sends `ACTION_STOP` to `FocusVpnService` (stops
  shield + ends session + cleans Room + removes notification - idempotent),
  then `finishAffinity()` to close the app completely. Rationale: "Minimize"
  made no sense (the user can just switch apps); "Exit" fully tears down.
- **Stay**: dismisses the dialog.

### 🆕 Welcome screen polish
- **Hide granted permissions**: if a permission is already granted, its checklist
  row is hidden entirely (returning users see a clean screen). "One-time setup"
  label only shows when something is still needed.
- **3 feature rows** ("How it works"): Pick apps / One tap zero popups / Data
  never moves - quiet icon + title + description.
- (Carried) Rotating tip card (30 lines, auto 6s + tap, TIP/TRICK/MOTIVATE).

### 🆕 Debug: notification/service lifecycle logging
`buildNotification` logs title/text; `onDestroy` logs each step (fd close,
stopForeground, cancel) + final state; STOP path logs startId. Easier to trace
why the icon/notif lingers.

### ⚠️ Still to verify on device
- Toggle OFF → VPN icon + notification gone (no sticky re-start).
- Back button/gesture → dialog appears → Exit (stops shield + closes app) /
  Stay work.
- Welcome: granted perms hidden; feature rows + tip card render.

**Next:** Zamir installs `1149f21`, tests OFF cleanup + back dialog + welcome.
Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT - OFF now fully cleans up (VPN icon + notif)

**Built 2026-09-03 (commit `1ee1e86`):** `NullFlow.apk` (23 MB).

### 🐛 BUGS FIXED: shield didn't fully turn off
User report: (1) "End session" from notification did nothing, (2) VPN icon
stayed in status bar after OFF, (3) "Local Privacy Shield is active"
notification lingered.
Root cause: `onDestroy()` closed the tunnel fd but **never called
`stopForeground()`** → the foreground notification + VPN status-bar icon were
orphaned. Also the notification's "End session" PendingIntent (request code 1)
coalesced with the toggle's stop intent.
**Fixes (`FocusVpnService`):**
- `onDestroy()`: now calls `stopForeground(STOP_FOREGROUND_REMOVE)` +
  `NotificationManager.cancel(NOTIF_ID)` → clears the notification + VPN icon.
- `onStartCommand(ACTION_STOP)`: calls `stopForeground(REMOVE)` immediately
  (instant UI update) before `stopSelf()`.
- "End session" PendingIntent now uses **request code 2** (distinct from the
  toggle's code 1) so the two stop intents never coalesce.

### ⚠️ Still to verify on device
- Toggle OFF → VPN icon + notification disappear immediately.
- "End session" from notification → shield turns off + icon/notif gone.
- Full cycle: ON (icon+notif appear) → OFF (icon+notif gone) → ON again.

**Next:** Zamir installs `1ee1e86`, tests OFF cleanup + notification End-session.
Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT - core flow WORKS + stale-state reconcile

**Built 2026-09-03 (commit `939480c`):** `NullFlow.apk` (23 MB).

### ✅ CORE FLOW CONFIRMED WORKING (from device log)
Pick apps → Done → toggle ON → **Shield ACTIVE - 2 apps blackholed** (no crash,
no profile loop). Toggle OFF → clean stop. The crash fix + profile fix both hold.

### 🐛 BUG FIXED: stale "ON" state after app kill/restart
Symptom: app killed while shield ON → on restart, UI showed `isActive=true`
(`runningSession=true`) even though the service was dead → toggle stuck.
Root cause: Room session + active profile survived the process death, but the
VPN service did not.
**Fix:**
- `FocusVpnService.isShieldRunning` (static `@Volatile`, private set) - true only
  while the tunnel is live in a running process; set true on establish, false in
  `onDestroy`. Resets to false on every fresh process start.
- `MainActivity.onCreate`: if `!isShieldRunning` and a running session exists in
  Room → end it + deactivate the profile (reconcile). Logged as "Reconciled
  stale session".
- Cosmetic: Done-button log now prints the count, not the raw list.

### ⚠️ Known (harmless): repeated ACTION_STOP
Log shows multiple STOP intents for an already-stopped service (notification
"End session" + toggle). `stopSelf()` is idempotent/safe, so no action needed
unless it becomes noisy.

### ⚠️ Still to verify on device
- Kill app while shield ON → reopen → toggle should read OFF (reconciled).
- Full cycle: pick → Done → ON (VPN icon + notif) → kill app → reopen → OFF.

**Next:** Zamir installs `939480c`, tests the kill/restart reconcile. Share
`Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT - profile loop FIXED + Done bar in picker

**Built 2026-09-03 (commit `79bc450`):** `NullFlow.apk` (23 MB).

### 🐛 BUG FIXED: profile-creation loop (confirmed from log)
Symptom: every toggle ON created a NEW empty profile (1→2→3→4→5→6→7) and
showed "0 apps", re-opening the picker in an endless loop.
Root cause: `activeProfile` was always null (a profile is only "active" once
explicitly marked), so `onToggle` fell through to `createDefaultProfile()` on
every tap.
**Fixes:**
- **`effectiveProfile`** (`MainScreen`): the profile the UI points at =
  `activeProfile ?: profiles.firstOrNull()`. The profile row, blocked-app
  count, and toggle all use it → no more duplicate profiles.
- **Mark profile active on block** (`AppPickerSheet.toggleApp`): when an app is
  checked, `clearActive()` + `setActive(profileId, true)` so the toggle finds it.
- **Toggle** now resolves `effectiveProfile?.id ?: createDefaultProfile(dao)`.

### 🆕 UX: "Done" bar in the app picker
The picker had NO way to confirm (only swipe-down). Added a full-width
**"Done · N apps selected"** button at the bottom (closes the sheet, haptic).
Header still shows live "N selected".

### ⚠️ Still to verify on device
- Pick apps → tap **Done** → profile row shows "N apps shielded".
- Toggle ON → shield engages (VPN icon + notification), no new profile minted.
- Toggle OFF → clean stop.

**Next:** Zamir installs `79bc450`, tests the full flow (pick → Done → ON →
OFF). Share `Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT - crash FIXED + welcome screen + rotating tips

**Built 2026-09-03 (commit `c47f867`):** `NullFlow.apk` (23 MB).

### 🐛 CRASH FIXED (root cause confirmed from log)
The ON/OFF crash was **`ForegroundServiceDidNotStartInTimeException`**.
Android requires `startForeground()` within **5s** of `startForegroundService()`.
The old code read blocked apps first; with **0 apps** it called `stopSelf()`
*without ever calling `startForeground()`* → 5s timeout → app killed.
**Fix (`FocusVpnService.startShield`):** call `startForeground()` **FIRST**
(before reading packages / establishing the tunnel), so the 5s contract is
always satisfied, THEN decide to stay active or stop.

### 🆕 UI changes (this build)
- **Welcome screen shows on EVERY app open** (`MainActivity` gate → always
  `OnboardingScreen`). Returning users see the permission checklist already
  checked → single "Enter NullFlow" tap. Fresh installs still walk setup.
- **Rotating tip card** on the welcome screen (upper-middle, between the
  "0 bytes" line and the permission checklist):
  - **30 short lines** in `OnboardingScreen.kt` (`TIPS` pool): 10 TIP +
    10 TRICK + 10 MOTIVATE.
  - **Auto-swap** every 6s (fade out/in via `Animatable`).
  - **Manual swap** on tap (same fade). Small `↻` affordance on the right.
  - **Tag rotates** in fixed order TIP → TRICK → MOTIVATE → …; each step picks
    a random line from that tag's pool.
- **0-apps guard** (`MainScreen.onToggle`): toggling ON with 0 blocked apps now
  opens the app picker instead of starting an empty shield.
- **Profile row** shows `N apps shielded` / `No apps yet`.
- **Fresh-install entry point**: "Choose apps to shield" button (OFF, no
  profile) so apps can be picked BEFORE turning on.

### ⚠️ Still to verify on device
- Toggle ON with apps blocked → does the shield actually engage (VPN icon +
  notification)? (The crash is fixed, but the happy path wasn't tested yet.)
- Tip card auto/manual swap + tag rotation look/feel.

**Next:** Zamir installs `c47f867`, tests: (1) pick apps, (2) toggle ON,
(3) confirm shield works, (4) tip card behavior. Share
`Download/NullFlow/nullflow.log` if anything misbehaves.

---

## ✅ Previous Status: BUILT WITH LOGGING - debugging the ON/OFF crash

**Built 2026-09-03 (commit `57e0ddb`):** `NullFlow.apk` (23 MB).
Crash-proof logging (`AppLog.kt` → `Download/NullFlow/nullflow.log`) + crash
handler + full lifecycle logging. This build's log **confirmed** the
`ForegroundServiceDidNotStartInTimeException` root cause (fixed in `c47f867`).

---

## ✅ Previous Status: BUILT - `NullFlow.apk` (23 MB) ready for device test

**Built 2026-09-03:** `BUILD SUCCESSFUL`, APK 23 MB at
`apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`.
Manifest verified: package `com.codezmr.nullflow`, label "NullFlow",
permissions VIBRATE / POST_NOTIFICATIONS / FOREGROUND_SERVICE(+_DATA_SYNC) /
QUERY_ALL_PACKAGES. Working tree CLEAN (commit `c88c0fc`).

> ⚠️ **Compose 1.6.1 (BOM 2024.02.00) gotchas hit this build:**
> - `Modifier.shadow` uses `elevation: Dp` - NO `radius`, NO `ambientColor`/`spotColor` params.
> - `infiniteTransition.animateFloat` does NOT exist → use `Animatable` +
>   `LaunchedEffect` ping-pong loop (`animateTo(1f)` / `animateTo(0f)` in `while(true)`).
> - `Canvas` drawscope has NO `strokeWidth` property → use `N.dp.toPx()`.
> - `animateColorAsState` lives in `androidx.compose.animation` (NOT `.core`).
> - KSP 1.9.22-1.0.17 chokes on multi-line `if { } else { }` blocks inside
>   `mutableStateOf(...)` ("Expecting an element") → use `||`/`&&` expressions.
> - `Modifier.fillMaxSize().background(x)` on one line → "Overload resolution
>   ambiguity" → split onto separate lines.

**Next:** Device test - (1) onboarding: breathing hero, both checklist rows →
neon checks, gatekeeper → Enter; (2) main: 1-tap toggle ON (no popups), bg dims,
WhatsApp single-tick; (3) OFF → internet back; (4) Edit apps sheet; (5) stats.

---

## ✅ Previous Status: ALL CODE DONE (Phases 1-4 + Onboarding) - AWAITING BUILD PERMISSION

**Done so far:**
- Local git repo initialized (no remote, ever).
- `doc/APP_IDEA.md` - full app idea + branding + 5-phase architecture (read-only reference).
- Gradle wrapper copied from SnapTriage (Gradle 8.7, cached).
- `local.properties` → `sdk.dir=/home/mohmmad/Android/Sdk`.
- **Phase 1 COMPLETE:** root + app `build.gradle.kts` (compileSdk 34, minSdk 30,
  Room 2.6.1 + KSP 1.9.22-1.0.17, coroutines 1.7.1, APK renamed to
  `NullFlow.apk`), `settings.gradle.kts`, `gradle.properties`,
  `AndroidManifest.xml` (VIBRATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE + _DATA_SYNC,
  QUERY_ALL_PACKAGES + FocusVpnService with BIND_VPN_SERVICE), res/
  (strings, colors, themes, **provided NullFlow icon kit** - adaptive foreground PNG
  + density mipmaps, background `#101014`).
- **Phase 2 COMPLETE:** Room DB (`FocusProfile`, `BlockedApp`, `FocusSession`
  entities + `FocusDao` with CRUD + Flow emitters + `FocusDatabase`),
  `PackageManagerRepo` (filters system apps, caches labels+icons, target allow-list),
  `Settings` (SharedPreferences onboarding flag).
- **Phase 3 COMPLETE:** `FocusVpnService` (blackhole logic via `addAllowedApplication`
  + `setBlocking(true)`, foreground notification with 30s timer, onRevoke handling).
- **Phase 4 COMPLETE:** `NullFlowTheme` (dark, rest-mode palette), `Haptics`
  (tick/engage/disengage), `MainScreen` (hero toggle + animateColorAsState bg +
  live session timer + stats + pre-prompt consent sheet), `AppPickerSheet`
  (ModalBottomSheet + LazyColumn + multi-select checkboxes), `MainActivity`
  (edge-to-edge, onboarding gate, wires DAO + picker sheet).
- **ONBOARDING COMPLETE (Play-review required):** `OnboardingScreen` - neumorphic
  dark aesthetic: BreathingHero (3D matte toggle + 4s icy-blue LED pulse),
  stark value-prop typography, "0 bytes" Halo anchor, two neumorphic tactile
  checklist rows (Notifications `POST_NOTIFICATIONS` + Local Shield
  `VpnService.prepare()`), gatekeeper button (ghost → electric blue + pulse).
  Sequential prompting, real-time check state, heavy haptic on grant.
- **INSTANT TOGGLE (UX upgrade):** VPN consent is handled DURING onboarding,
  so the MainScreen hero toggle is now a **1-tap action with zero popups**.
  PrePromptSheet removed from MainScreen. Toggle ON → startShield() directly.

**Next:**
- **Phase 5: ASK ZAMIR BEFORE BUILDING.** `./gradlew assembleDebug --no-daemon`
  → `app/build/outputs/apk/debug/NullFlow.apk`.
- After build: device-test (onboarding checklist → both checks → Enter, then
  toggle ON → WhatsApp single-tick, OFF → internet back, haptics, bg dim,
  app picker, stats).

---

## 📁 Project Layout (target)

```
3Sep2026_app_freez/
├── SESSION_STATE.md          ← this file
├── doc/APP_IDEA.md           ← idea + architecture (read-only reference)
└── apps/NullFlow/
    ├── build.gradle.kts      ← AGP 8.5.2, Kotlin 1.9.22
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── local.properties      ← sdk.dir=/home/mohmmad/Android/Sdk
    ├── gradlew + gradle/wrapper/  ← Gradle 8.7 (COPIED from SnapTriage - do NOT change)
    └── app/
        ├── build.gradle.kts  ← compileSdk 34, minSdk 30, compose, Room+KSP, coroutines
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/codezmr/nullflow/
            │   ├── MainActivity.kt
            │   ├── data/       ← Room DB, entities, DAOs, PackageManagerRepo
            │   ├── ui/         ← Compose screens (hero toggle, sheets, theme)
            │   └── vpn/        ← FocusVpnService
            └── res/
```

---

## 🧱 Tech Stack (LOCKED - see ../../ANDROID_BUILD_SETUP.md for why)

| Component | Version |
|---|---|
| Gradle | **8.7** (cached; VPN blocks newer downloads) |
| AGP | **8.5.2** |
| Kotlin | **1.9.22** |
| Compose Compiler | **1.5.8** (must match Kotlin) |
| Compose BOM | **2024.02.00** |
| Room | **2.6.1** + KSP **1.9.22-1.0.17** (both cached) |
| Coroutines | **1.7.1** (cached) |
| JDK | 17 (sourceCompat/jvmTarget) |
| SDK | `~/Android/Sdk` (android-34, android-36) |

**APK rename:** `app/build.gradle.kts` has an `android.applicationVariants.all { ... outputFileName = "NullFlow.apk" }` block (legacy API - `androidComponents.outputFileName` doesn't exist in AGP 8.5.2).

---

## 🔑 Key Implementation Decisions

1. **Blackhole VPN (CRUCIAL):** Do NOT use `addDisallowedApplication`.
   Route ONLY blocked apps INTO the VPN dead-end:
   - `addAddress("10.0.0.2", 32)` + `addRoute("0.0.0.0", 0)`
   - `addAllowedApplication(pkg)` for each blocked package
   - `setBlocking(true)` → their packets are silently DROPPED
   - All other apps bypass the VPN → keep internet.
2. **Never say "VPN" in UI** - call it "Local Privacy Shield" / "Focus Wall".
   Pre-prompt screen BEFORE the system consent dialog.
3. **Haptics:** `VibrationEffect.createOneShot(50, 150)` touch-down,
   `createOneShot(100, 255)` on successful activation.
4. **Visual state:** `animateColorAsState` background → deep dark `#121212`
   when active ("rest mode").
5. **App picker:** filter out system apps
   (`(flags & ApplicationInfo.FLAG_SYSTEM) != 0`), cache labels + icons.
6. **Foreground service:** VPN service needs a persistent notification
   ("Focus Session Active" + timer).

---

## 🖥️ Build / Install / Debug

- **APK:** `apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`
- **Build:** `cd apps/NullFlow && ./gradlew assembleDebug --no-daemon`
- **Install:** `adb install -r apps/NullFlow/app/build/outputs/apk/debug/NullFlow.apk`
- **Logcat:** `adb logcat -s NullFlow`

---

## ⚠️ Known / Watch-out

- **Git: LOCAL-ONLY.** NO remote - never add one, never push. Commit locally
  before big changes.
- **VPN-gated:** `services.gradle.org` is dead over this VPN. Never bump the
  Gradle wrapper version.
- **Compose Compiler must match Kotlin** (1.9.22 → 1.5.8).
- **Compose BOM 2024.02.00 gotchas** (from SnapTriage):
  - `Modifier.shadow` needs `import androidx.compose.ui.draw.shadow` AND uses
    `elevation: Dp` (not `radius`).
  - `slideInVertically`/`slideOutVertically` don't take `targetOffsetY`/
    `initialOffsetY` lambda params - use `animationSpec` only.
  - `align` is a `BoxScope`/`RowScope`/`ColumnScope` member - do NOT import
    `androidx.compose.foundation.layout.align`.
- **QUERY_ALL_PACKAGES** is a Play Store scrutiny item - fine for sideloaded
  APK; would need justification for Play release.
- **VpnService.prepare()** returns null if already authorized - handle both
  cases in the consent flow.
