# GhostShield — Quick Settings Tile & Native Focus Panel (Feature Spec)

> **Status:** IMPLEMENTED ✅ — the tile + panel are built and shipping. The open
> questions in §5 were resolved during development (see inline notes +
> `doc/POLISH_SPEC.md` §4 for post-polish additions).
> **Package:** `com.codezmr.nullflow` · **Depends on:** existing `FocusVpnService`
> (blackhole engine) + Room DB (`FocusProfile` / `BlockedApp` / `FocusSession`).
>
> **Post-polish addition:** onboarding now offers a **1-tap native pin** of this
> tile via `StatusBarManager.requestAddTileService` (Android 13+), with a manual
> drag-and-drop fallback card for API 30-32. See `doc/POLISH_SPEC.md` §4.4.

---

## 1. WHAT we are building

A native Android **Quick Settings Tile** (`FocusTileService`) named
**"GhostShield"** that lives in the phone's swipe-down notification shade.

Tapping it opens a **floating, dark-mode Focus Panel** (Compose `BottomSheetDialog`)
right over whichever app the user is currently using. Inside the panel the user can:

1. Toggle the **master Shield ON / OFF**.
2. View and select from **all custom profiles** in Room DB (Study Mode, Gym, Deep Work…).
3. See **how many apps are blocked** in each mode.
4. Tap **"+ Create / Edit Modes"** → panel closes and the **main NullFlow app** opens.

## 2. WHY we are building it

- **Zero friction:** opening the main app takes 3–4s; the QS panel takes ~0.5s.
- **Native OS feel:** makes NullFlow feel like a built-in system feature
  (like Bluetooth / Battery Saver), not a third-party app.
- **Competitive dominance:** almost no Play Store competitor offers a full
  multi-profile switcher inside a QS tile.

## 3. HOW it works (architecture)

```
User taps QS Tile
        │
        ▼
TileService.showDialog()  ──► reads Profiles reactively from Room DB
        │
        ▼
User picks "Study Mode"   ──► updates Room DB active profile
        │
        ▼
Send Intent to VpnService ──► FocusVpnService receives ACTION_REFRESH_RULES
                              and updates the blackhole tunnel on the fly.
```

### Components

| # | File | Responsibility |
|---|------|----------------|
| 1 | `tile/FocusTileService.kt` | QS tile. `onStartListening()` reflects state; `onClick()` shows the panel. |
| 2 | `ui/tile/TileFocusPanel.kt` | Compose panel: master switch, profile list, "+ Create / Edit Modes". |
| 3 | `vpn/FocusVpnService.kt` | **Modified** to handle `ACTION_REFRESH_RULES` (hot-swap tunnel) + `ACTION_STOP_SHIELD`. |
| 4 | `AndroidManifest.xml` | Register the tile service. |

### Tile state mapping
- **ACTIVE** (blue glow) when an active profile + running session exists.
- **INACTIVE** otherwise.
- Updated in `onStartListening()` and after any state change.

### Panel UI (NullFlow aesthetic)
- Background `#0A0C10` (rest-mode dark), accent `#00E5FF` (neon cyan).
- **Header row:** "Use Focus Shield" title + master `Switch`.
  - ON → `startForegroundService(FocusVpnService)` for the active profile.
  - OFF → send `ACTION_STOP_SHIELD`.
- **Section label:** "SELECT MODE" (muted grey `#808080`, 12sp bold).
- **Profiles list (`LazyColumn`):** each row = `RadioButton` + name +
  "N apps shielded". Tapping sets it active in Room + sends `ACTION_REFRESH_RULES`.
- **Divider** (`#1E222B`).
- **Action row:** "+ Create / Edit Modes" → dismiss dialog +
  `startActivityAndCollapse(Intent(this, MainActivity::class.java))`.

### Service changes
- `ACTION_REFRESH_RULES`: do **NOT** tear down the foreground service /
  notification. Async-query Room for the new active profile's packages and
  re-establish the `VpnService.Builder` with the new `addAllowedApplication()` list.
- `ACTION_STOP_SHIELD`: stop tunnel, `stopForeground(STOP_FOREGROUND_REMOVE)`,
  `stopSelf()`.

## 4. Verification checklist
- `./gradlew assembleDebug` compiles clean.
- All Room interactions off the main thread (`Dispatchers.IO`).
- No UI leaks when the `BottomSheetDialog` is dismissed.

---

## 5. Open questions (BLOCKING — see QUESTIONS_GHOSTSHIELD.md)
See the companion questions file. Key items:
1. **Hot-swap vs restart** for `ACTION_REFRESH_RULES` (tunnel can't be edited in place).
2. **Profile switching while shield is OFF** — just update Room, or also start?
3. **`ic_hero_toggle` icon** does not exist yet — need to create it.
4. **Material `BottomSheetDialog`** requires adding the Material Components dependency.
5. **Session accounting** — does switching profiles mid-session end the old session?
6. **Tile icon** — static vs dynamic (active/inactive variants).
