# NullFlow — Architecture Polish & UX Improvements (Feature Spec)

> **Status:** APPROVED — all cross-questions answered "Option A / defaults".
> **Package:** `com.codezmr.nullflow`
> **Companion decisions:** see inline notes; all Q1–Q6 = Option A.
>
> **⚠️ SUPERSEDED ITEMS (see §4 "Post-Polish Additions"):**
> - §2.1 "One-Tap Preset Chips" — **REMOVED**. Users build their own modes by
>   picking individual apps (no opaque categories). `PackageManagerRepo.presets`
>   deleted.
> - §2.3 "Checkbox Accent" — **N/A** (checkboxes were replaced by Tactile App
>   Cards in §2.1b).
> - §1.2 "Frozen Notification Timer" — **UPGRADED** to a full custom
>   `RemoteViews` HUD with a live "Pings Deflected" counter (see §4.1).

---

## 1. Critical State & Service Fixes

### 1.1 QS Onboarding Bypass (Q3 = A)
**Problem:** Activating the shield via the QS tile bypasses the mandatory
Welcome/Consent screen.
**Fix:** In `FocusTileService.onClick()`, check `Settings.hasOnboarded`
(SharedPreferences) BEFORE showing the panel. If `false` → do NOT show the
panel; launch `MainActivity` (which shows Welcome) and collapse the shade.
- Use `Settings.get(context).hasOnboarded`.
- Launch via `startActivityAndCollapse` with a **PendingIntent** (see 1.4 note —
  `startActivityAndCollapse(Intent)` is disallowed on Android 15).

### 1.2 Frozen Notification Timer (Q1 = A)
**Problem:** The foreground notification timer only updates every 30s
(`delay(30_000)`), so it appears static between jumps.
**Fix:** Change `startTimerUpdates()` to tick **every second**
(`delay(1_000)`). Reliable across OEMs (vs. `Chronometer` in `RemoteViews`,
which breaks on skinned OS).

### 1.3 "End session" State Desync (Q2 = A)
**Problem:** The notification's "End session" sends only `ACTION_STOP` to the
service → tunnel stops + notification removed, but the **Room session is never
ended** and the profile is never deactivated. Result: tile (reads in-memory
`isShieldRunning`) shows OFF, but the panel (reads Room `runningSession` +
`activeProfile`) shows ON.
**Fix:** Make "End session" go through the SAME full stop path as the app
toggle — it must also end the Room session + deactivate the profile.
- Cleanest approach: the notification "End session" action should trigger the
  full stop (service teardown **and** Room cleanup). Since a `PendingIntent`
  from a notification can only target a service/broadcast/activity, the
  service's `ACTION_STOP`/`ACTION_STOP_SHIELD` handler will also perform the
  Room session-end + profile-deactivation (idempotent, guarded by
  "only if a running session exists").

### 1.4 `startActivityAndCollapse` PendingIntent (found in logs)
**Problem:** `startActivityAndCollapse(Intent)` throws
`UnsupportedOperationException` on Android 15 — it requires a **PendingIntent**.
**Fix:** Build a `PendingIntent.getActivity(...)` and pass that to
`startActivityAndCollapse(pendingIntent)`. Applies to both the onboarding gate
(1.1) and the "+ Create / Edit Modes" action.

---

## 2. UI & Aesthetic Polish

### 2.1 App Picker → "The Focus Matrix" (Q4 upgraded — NO checkboxes)
**File:** `ui/AppPickerSheet.kt` (full overhaul)
Replaces the standard Material checkboxes with a high-end, futuristic
"Focus Matrix". Two pillars (presets removed — see §4.2):

**(a) Sticky search (top)**
- **Sticky search:** `OutlinedTextField`, dark glass (`#141820` surface,
  `#00E5FF` cursor/focus border). Filters the ENTIRE unified list by label
  (case-insensitive).
- ~~**Preset chips**~~ — **REMOVED** (users build their own modes by picking
  individual apps; no opaque categories). See §4.2.

**(b) Tactile App Card (replaces the checkbox)**
- **Unselected:** dark charcoal `#12151C`, muted grey outline `#222733`,
  desaturated icon, `+ ADD` badge (grey).
- **Selected:** pops forward, neon-cyan glow `#00E5FF` border, full-color
  vibrant icon with a radial light halo behind it, `🔒 SHIELDED` cyan pill
  (black text).
- **Micro-spring:** tap scales to 0.96× then springs back
  (`spring(dampingRatio = MediumBouncy)`). Heavy thud haptic on tap
  (`VibrationEffect.createOneShot(40, DEFAULT_AMPLITUDE)`).
- Icons rendered via `rememberAppIconPainter` (async, in-memory cached,
  `BitmapPainter`) — the SAME loader the QS tile panel uses. **NOT**
  Coil/Accompanist (not available offline). Replaced the deprecated
  `Image(bitmap = …)` overload.

**(c) Layout**
- `LazyColumn` with `verticalArrangement = spacedBy(10.dp)`.
- Preset chips + search are sticky above the scrolling card list.
- Keep the existing "Done · N apps selected" bar at the bottom.

> NOTE: The earlier "Suggested cluster" idea is superseded by the Preset Chips
> (same intent — quick access to high-distraction apps — but as one-tap
> category toggles instead of a static group).

### 2.2 Neumorphic Hero Switch 3D (Q5 = A)
**File:** `ui/MainScreen.kt` (`HeroToggle` only — NOT the onboarding
`BreathingHero`, which stays flat/ethereal).
- Add a dark, offset drop-shadow to the **bottom-right** and a subtle
  semi-transparent white highlight to the **top-left** for a 3D extruded
  hardware feel.
- Implement via layered `Modifier.shadow` / gradient overlays on the existing
  circular switch (keep current size + colors).

### 2.3 Brand Consistency — Checkbox Accent
**File:** `ui/AppPickerSheet.kt`
- Override the default Material 3 blue on the app-picker checkboxes with the
  app's electric-cyan `#00E5FF` via `CheckboxDefaults.colors(checkedColor = …)`.

---

## 3. Quick Settings Panel Upgrade (Q6 = A)
**File:** `ui/tile/TileFocusPanel.kt`
- **Visual mode selector:** replace the "N apps shielded" text under each mode
  with a horizontal row of the target app icons.
- **Data:** add a `ProfileWithApps` Room query (profile + its blocked package
  names). Render a `LazyRow` of app icons (24dp, `CircleShape`, 8dp spacing).
- **Icon loading:** helper composable that loads `Drawable`/`Bitmap` icons via
  `PackageManager` from the saved package names; cache in memory.
- **Scroll hint:** `horizontalScroll` + a right-edge gradient fade so users
  know the list extends off-screen. Show ALL icons (scrollable), no hard cap.

---

## 4. Post-Polish Additions (implemented after the original Q1–Q6)

### 4.1 "Pings Deflected" Notification HUD + Packet Counter
**Files:** `vpn/FocusVpnService.kt`, `res/layout/notification_focus_hud.xml`,
`res/drawable/ic_shield_hud.xml`

The blackhole tunnel drops packets silently, but the OS still hands us the byte
stream on the interface fd. By actively READING that stream we count every
connection attempt a blocked app makes ("pings deflected") and discard the
payload (strict zero-data privacy — never inspected/logged/stored).

- **Counter:** `deflectedPings` (`AtomicInteger`), reset to 0 per session.
- **Reader:** `startPacketReader(fd)` — dedicated IO coroutine reads the tunnel
  `FileInputStream` in a `while(shouldRun)` loop (32 KB buffer). Each successful
  read = one deflected attempt → increment. Runs on its own `readerScope` so
  hot-swaps restart it without killing the ticker. Cancelled on teardown +
  onDestroy.
- **HUD layout:** OEM-safe `LinearLayout` (no ConstraintLayout), fixed padding,
  `singleLine` + `ellipsize` on all TextViews. Dark `#0A0C10` bg, shield icon,
  title, timer (`tv_timer`), cyan `tv_pings` ("X Pings Deflected"), cyan "End"
  button (`btn_end_session`).
- **Wiring:** `buildNotification()` builds `RemoteViews`, sets `tv_timer` +
  `tv_pings`, binds `btn_end_session` → stop PendingIntent, body → MainActivity.
  Uses `setCustomContentView` + `setCustomBigContentView` (plain-text fallback
  for OEMs that ignore RemoteViews). The 1s ticker re-notifies every second →
  live ping count.

> **Caveat:** the reader counts I/O *reads*, not individual packets (a single
> `read()` can return multiple packets). This is the privacy-correct tradeoff —
> precise per-packet counting would require parsing IP headers (inspecting
> payloads), which violates zero-data privacy.

### 4.2 Dropped Predefined Presets
**File:** `ui/AppPickerSheet.kt`, `data/PackageManagerRepo.kt`
- Removed the "ONE-TAP PRESETS" section, `PresetChip` composable, and
  `togglePreset()`. Deleted `PackageManagerRepo.presets` + `Preset` data class.
- Rationale: users don't trust opaque categories (they can't see what's in
  each). Users now build their own modes by picking individual apps. The
  home-screen + tile-panel icon rows make the blocked set fully transparent.

### 4.3 Home-Screen Blocked-App Icon Row
**File:** `ui/MainScreen.kt`
- Beneath the active profile name, a scrollable `LazyRow` of the blocked apps'
  icons (24dp circles, 8dp spacing) + right-edge gradient fade — mirrors the QS
  tile panel so the user sees exactly what's shielded. New `BlockedAppIconRow`
  composable.

### 4.4 1-Tap Quick Settings Tile Pinning (Onboarding)
**Files:** `MainActivity.kt`, `ui/OnboardingScreen.kt`
- **`MainActivity.requestAddQsTile(onResult)`:** gated behind
  `Build.VERSION.SDK_INT >= TIRAMISU` (33). Uses
  `StatusBarManager.requestAddTileService(ComponentName, "GhostShield", Icon,
  mainExecutor, Consumer<Int>)`. Callback result code `0` = added, non-zero =
  dismissed. Dependency-free main-thread `Executor` (Handler on main looper).
  Pre-33 → reports `false`.
- **`OnboardingScreen.QsTilePinSection`:** injected ABOVE the gatekeeper button.
  Header: "Highly Recommended for Seamless Use".
  - **API 33+:** electric-cyan (`#00E5FF`) outlined button
    "[ ⚡ Pin to Quick Settings ]". On tap → haptic tick + `requestAddQsTile`.
    On success → flips to dimmed "✓ Added to Quick Settings" + haptic engage.
  - **API 30-32 fallback:** muted glassmorphic card with manual drag-and-drop
    instructions.
  - **Optional** — never blocks onboarding.

 ### 4.5 Zero-Warning Cleanup
**File:** `ui/MainScreen.kt`
- Replaced the two `effectiveProfile!!` non-null assertions with a safe local
  `val profile = effectiveProfile` inside the `if` block. Both compiler
  warnings eliminated.

---

## 5. Tactical HUD + Exit + Icon Fixes (latest round)

### 5.1 Tactical HUD Header (MainScreen)
**File:** `ui/MainScreen.kt`
Replaced the centered "NullFlow" + tagline with a top-left asymmetrical HUD
(mimics command-line / aviation HUD / security-software aesthetics):
- **"NULLFLOW"** all-caps, `FontWeight.Black`, `letterSpacing = 2.sp`, 22sp.
- **Status line:** 6dp circular node + monospace `Text` (`FontFamily.Monospace`,
  11sp). State-driven by `isShieldActive`:
  - **ON:** cyan `#00E5FF` node with `shadow(blur=8dp)` glow + `SYS.STATUS: SECURE`.
  - **OFF:** muted grey `#4A4E58` node (no glow) + `SYS.STATUS: STANDBY` (`#8A8F99`).
- `statusBarsPadding()` for notch/cutout safety. Animated color/glow (400ms).
- 40dp guaranteed min gap to the Hero Switch.

### 5.2 Exit Dialog (was "Minimize")
**File:** `MainActivity.kt`
- "Minimize" → **"Exit"**. Rationale: "Minimize" made no sense (the user can
  just switch apps); "Exit" fully tears down.
- On Exit: sends `ACTION_STOP` to `FocusVpnService` (stops shield + ends session
  + cleans Room + removes notification — idempotent), then `finishAffinity()` to
  close the app completely.
- Dialog text: "This stops the shield and closes the app completely. Your focus
  data is saved on this phone."

### 5.3 App Picker Icon Placeholder
**File:** `ui/AppPickerSheet.kt` (`TactileAppCard`)
- **Root cause:** `rememberAppIconPainter` returns a transparent `ColorPainter`
  until the bitmap loads on `Dispatchers.IO`, so the icon slot appeared empty
  until the user tapped the card (recomposition after load).
- **Fix:** added a visible 40dp placeholder circle BEHIND the async-loaded icon.
  The slot always shows a dark circle while the bitmap loads; the icon fades in
  on top when ready.

---

## Verification
- `./gradlew assembleDebug` compiles clean (zero warnings on touched files).
- All Room interactions off the main thread (`Dispatchers.IO`).
- No UI leaks when the tile panel is dismissed.
- Device checks:
  - Notification shows the custom HUD (shield + timer + cyan "X Pings
    Deflected" + "End" button) — NOT the default text layout.
  - Ping count increments in real time when a blocked app tries to connect.
  - "End session" (notification button OR tile OR app toggle) → tile AND panel
    both show OFF (no desync).
  - Tile tap before onboarding → forces Welcome screen.
  - App picker: search filters all; NO preset chips; tactile cards + icons
    render (not grey circles); header shows "N Shielded" (not a raw list).
  - Hero toggle has 3D extruded look.
  - Tile panel shows app-icon rows per mode with right-edge fade.
  - Home screen shows blocked-app icon row under the profile name + fades right.
  - Onboarding (API 33+): cyan "Pin to Quick Settings" button → system dialog →
    tile appears → button flips to "Added". Haptic tick + engage.
  - MainScreen HUD header: top-left "NULLFLOW" + status line. ON → cyan glow +
    "SYS.STATUS: SECURE"; OFF → grey + "SYS.STATUS: STANDBY". No notch/cutout
    overlap.
  - Back button → "Exit NullFlow?" dialog → Exit stops shield + closes app;
    Stay dismisses.
  - App picker: icons show a placeholder circle immediately (not empty) while
    loading; icon fades in on top.
