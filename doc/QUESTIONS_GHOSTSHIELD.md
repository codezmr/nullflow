# GhostShield - Open Questions (answer these before we build)

Zamir, I verified the codebase before asking. Here's what I found and what I
need you to decide. **Short answers are fine** (e.g. "Q1: yes, restart").

---

## 🔴 Blocking (must answer)

### Q1. Hot-swapping the tunnel is NOT possible in place - how do you want it?
Android's `VpnService` tunnel **cannot be edited after `establish()`**. To change
which apps are blocked, the only correct way is: **close the old tunnel fd →
re-`establish()` a new one**. The foreground service + notification stay up the
whole time (no flicker), but there is a brief moment where the tunnel is
re-created.

- **Option A (recommended):** On `ACTION_REFRESH_RULES`, close old fd +
  re-establish with the new profile's apps. Service/notification stay alive.
  This is the only technically correct "on the fly" update.
- **Option B:** Tear down fully and re-run `startShield()` (simpler, but the
  notification briefly disappears and re-appears).

**Which one?** (I'll go with A unless you say otherwise.)

### Q2. What should happen when the user picks a DIFFERENT profile while the shield is OFF?
Right now the shield is OFF (no tunnel). User taps "Gym Mode" in the panel.

- **Option A (recommended):** Just mark "Gym Mode" active in Room. Do **NOT**
  start the shield - the user still has to flip the master switch ON.
- **Option B:** Mark it active **and** auto-start the shield immediately.

**Which one?**

### Q3. The tile icon `@drawable/ic_hero_toggle` does not exist yet.
Your manifest snippet references `android:icon="@drawable/ic_hero_toggle"`, but
that drawable isn't in the project (only `ic_launcher_foreground.png` exists).

- **Option A (recommended):** I create a simple vector drawable
  (`ic_hero_toggle.xml`) - a circle + slash (matches the "null-ring" brand mark).
- **Option B:** You'll provide the icon file.
- **Option C:** Reuse the existing `ic_launcher_foreground`.

**Which one?** (A is fastest and on-brand.)

### Q4. The panel needs the Material Components library (new dependency).
`com.google.android.material.bottomsheet.BottomSheetDialog` (the one that
`TileService.showDialog()` requires) lives in the **Material Components**
library, which is **not currently in `build.gradle.kts`** (we only have Compose
Material3). I'll need to add:
```
implementation("com.google.android.material:material:1.11.0")
```
This is a safe, standard addition. **OK to add it?** (I assume yes.)

---

## 🟡 Should confirm (I'll use the default if you don't answer)

### Q5. Session accounting when switching profiles mid-session
If the shield is ON for "Deep Work" and the user switches to "Gym Mode" via the
tile, do we:
- **Default (recommended):** End the "Deep Work" session in Room, start a new
  "Gym Mode" session. (Keeps stats accurate per profile.)
- **Alternative:** Keep one continuous session, just change which apps are blocked.

### Q6. Tile icon - static or dynamic?
- **Default (recommended):** One static icon; the tile just changes color
  (blue glow) when active. Simpler.
- **Alternative:** Two icon variants (off = grey, on = blue). Looks nicer but
  needs two drawables.

### Q7. Should the tile also show up / behave if the app was never opened?
The tile is always available once the app is installed (that's how QS tiles
work). No action needed - just confirming you're aware the tile appears in the
"edit tiles" list automatically.

### Q8. Panel master switch vs. main app hero toggle - same source of truth?
**Default (recommended):** Both read the same Room state (`activeProfile` +
`runningSession`) and both drive the same `FocusVpnService`. They stay in sync
automatically. Confirming this is what you want.

---

## ✅ My proposed defaults (if you just say "go with defaults")
- Q1 → **A** (close + re-establish, keep service alive)
- Q2 → **A** (just mark active, don't auto-start)
- Q3 → **A** (I create `ic_hero_toggle.xml` vector)
- Q4 → **yes** (add Material Components 1.11.0)
- Q5 → **default** (end old session, start new)
- Q6 → **default** (static icon, color changes)
- Q7 → aware
- Q8 → **yes** (shared source of truth)
