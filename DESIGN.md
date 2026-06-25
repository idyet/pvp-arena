# PvP Arena — Design Spec

RuneLite Plugin Hub plugin. Quality-of-life utilities for players dueling on PvP
Arena (Emir's Arena) worlds. The plugin is **inert outside that context** — every
feature is gated on the world type *and* on its target interface being loaded.

See [CONTEXT.md](./CONTEXT.md) for the domain glossary.

## Scope

Two features in v1, each independently toggleable and structured so more PvP Arena
utilities can be added later without reworking the core:

1. **Shift-click discard** — prevent accidental left-click deletion of loadout
   items; require Shift for intentional discard.
2. **Spellbook mismatch** — make a spellbook mismatch obvious on the unranked duel
   screen before the player commits.

## Activation gate

Two layers, both required (decided in grilling — world gate alone is too loose,
interface gate alone risks future interface reuse):

1. **World layer** — `client.getWorldTypes().contains(WorldType.PVP_ARENA)`
   (`WorldType.PVP_ARENA`, bit `1 << 6`). A shared `inPvpArena()` helper.
   `VarbitID.THIS_IS_A_PVP_ARENA_WORLD` (13679) exists as a cross-check if needed.
2. **Interface layer** — each feature additionally checks that its target arena
   interface is loaded/visible before acting.

## Feature 1 — Shift-click discard

### Behavior

For a loadout item whose **default left-click is `Discard`** (right-click menu
contains `Discard`, `Examine`):

| Situation        | Left-click does | Right-click menu      |
|------------------|-----------------|-----------------------|
| Shift **not** held | `Examine`     | `Discard`, `Examine`  |
| Shift **held**     | `Discard`     | `Discard`, `Examine`  |

- It is a **reorder, not a removal**. Nothing is stripped from the right-click
  menu, so a deliberate right-click → `Discard` always works regardless of Shift
  or plugin state.
- Items whose default left-click is **not** `Discard` are left completely
  unchanged (per spec: "items which are not left-click delete should not change
  their left-click option").
- Promote `Examine` to the non-Shift left-click rather than a no-op (user
  preference): a stray click during drag-rearrange examines instead of deleting.

### Scope

- **Screens:** both loadout builders — `PvpArenaStagingareaSupplies` (group
  `0x02f6`) and `PvpArenaUnrankedduel` (group `0x02f5`).
- **Regions:** both the inventory item list (`_N ITEMS_LIST` / items container)
  and the worn-equipment slots (`_N WORN` / `_N SLOTn`), wherever a left-click
  `Discard` is present.

### Mechanism

- Hook `onPostMenuSort` (the hook RuneLite core's shift-click swapper uses). Each
  tick the menu is rebuilt; the entry that is left-click is the leftmost/last in
  the array.
- Read Shift **live** at sort time: `client.isKeyPressed(KeyCode.KC_SHIFT)`
  (`KC_SHIFT = 81`).
- Scope the reorder to entries whose target widget belongs to one of the two
  builder interface groups **and** whose option text is exactly `Discard` /
  `Examine`. Swap the two entries' order so the desired option is leftmost for the
  current Shift state.

### Assumptions to verify in-game

- Exact option strings are `Discard` and `Examine` (from the user; confirm casing
  / any quantity variants like `Discard-5`).
- Every item that has `Discard` also has `Examine` (promotion target exists).

## Feature 2 — Spellbook mismatch

Only on `PvpArenaUnrankedduel` — the one screen showing both the player's spellbook
selector(s) and the opponent's chosen spellbook plus the confirm button.
`PvpArenaLegacyduel*` has no spellbook concept and is out of scope.

### Data source (display label text, NOT varbits)

In-game testing (2026-06) proved the varbit approach unworkable:

- The loadout spellbook varbit (`PVPA_LOADOUT_A_SPELLBOOK`, 14035) uses a
  **non-standard encoding**: `Ancient=0, Standard=1, Lunar=2` (Arceuus=3) — not the
  `VarbitID.SPELLBOOK` (4070) order.
- The opponent varbit (`PVPA_UNRANKEDDUEL_TRANSMIT_OPPONENTSPELLBOOK`, 14031) is
  **unreliable**: it shares varp 3465 with transmit-progress state and was observed
  flickering `0→2→0` while the opponent's displayed book stayed fixed.

Instead, compare the **display label text**, which both sides render identically
(`"Standard Spellbook"`, `"Ancient Magicks"`, `"Lunar Spellbook"`, …). The label
lives on a **child** of the spellbook display widget.

- Active player display: `PvpArenaUnrankedduel._0/_1/_2 SPELLBOOK_DISPLAY`, indexed
  by `VarbitID.PVPA_TRANSMIT_BUILD` (14024) (build 0 → `_0`). Only this varbit is
  still read, and only to pick which player panel is active.
- Opponent display: `PvpArenaUnrankedduel.OPPONENTSPELLBOOK_DISPLAY`.
- Both labels persist in the widget tree even when their tab is hidden, so the
  compare works regardless of which tab (my-equipment / their-equipment) is showing.

### Comparison rule

```
mismatch ⇔ !playerLabel.equalsIgnoreCase(opponentLabel)   // fail quiet if either blank
```

Encoding-proof and matches exactly what the player sees on screen.

### Activation conditions (all required)

1. World is `PVP_ARENA`.
2. `PvpArenaUnrankedduel` is loaded/visible.
3. Opponent spellbook is a known, unambiguous value.
4. Player's agreed-build loadout spellbook is known.
5. The two differ.

Re-evaluated **live** on `VarbitChanged` (for the relevant varbits) and on widget
load, so toggling spellbook on either side updates immediately. Cleared when the
screen closes or the feature/plugin is disabled. Highlights as soon as 1–5 hold —
no "wait until ready/accepted" gate.

**Fail quiet:** if either value is ambiguous/unreadable, show nothing. Better to
miss a flag than to false-alarm.

### Visual treatment

A single `Overlay` (matching the sibling-plugin convention of drawing on widget
bounds rather than mutating widgets — fully reversible, no teardown). While
mismatch is active, draw on three widget bounds:

- **Player's spellbook display** (`_N SPELLBOOK_DISPLAY` of the active build) —
  static red outline box.
- **Opponent's spellbook display** (`OPPONENTSPELLBOOK_DISPLAY`) — static red
  outline box.
- **Confirm button** (`OPPONENT_CONFIRM`) — static red outline box **plus**
  `SpriteID.PVP_WARNING_ICON` (1728), right-aligned with padding and vertically
  centered on the button.

The player and opponent displays live on separate tabs and are never visible
simultaneously; `outline()` skips hidden widgets, so each highlights on its own tab.

Static (no pulse), no text label, red is fixed (no color config) — all decided in
grilling.

### Verified in-game (2026-06)

- World gate passes for unranked challenges too (they run on `PVP_ARENA` worlds).
- `PVPA_TRANSMIT_BUILD` = 0 → player panel `_0`; label-text compare works on both
  the my-equipment and their-equipment tabs.
- Confirm button (`OPPONENT_CONFIRM`) does become visible at the confirm step and
  receives the outline + icon.

## Config (`PvpArenaConfig`)

Two booleans, both default **true**:

- `shiftClickDelete` — "Shift-click discard"
- `spellbookMismatch` — "Highlight spellbook mismatch"

No color option.

## Architecture

- `PvpArenaPlugin` — wiring, shared `inPvpArena()` helper, registers/unregisters
  the overlay and event subscribers.
- `ShiftClickDeleteHandler` — `onPostMenuSort` subscriber for Feature 1.
- `SpellbookMismatchOverlay` — Feature 2 (reads display label text each render).

Each feature is independent and guarded by `inPvpArena()` + its interface check +
its config flag. Adding a third feature is a new class wired the same way.

## Testing

Pure, client-free logic only (mirrors `disable-cancel` / `pvp-overheads`):

- `desiredLeftClick(hasDiscardDefault, hasExamine, shiftHeld) → {DISCARD | EXAMINE | UNCHANGED}`
- `isMismatch(playerLabel, opponentLabel) → boolean` (case-insensitive, fail-quiet on
  null/blank).

JUnit 4, matching the sibling plugins. No mocked-client integration tests. Anything
client-coupled (varbit reads, widget bounds, menu reordering) is covered by the
manual checklist below.

## Manual in-game verification checklist

- [ ] Confirm `Discard` / `Examine` exact option strings and any quantity variants.
- [ ] Confirm every `Discard` item also exposes `Examine`.
- [ ] Confirm Shift swap works in both builders, both regions; right-click `Discard`
      still works with feature on + no Shift.
- [x] Spellbook mismatch: label-text compare, verified in-game (2026-06).
- [ ] Confirm overlay aligns with the widget bounds at various client scales.

## Key constants reference

| Purpose | Constant |
|---|---|
| World gate | `WorldType.PVP_ARENA` (`1<<6`); `VarbitID.THIS_IS_A_PVP_ARENA_WORLD` 13679 |
| Shift detect | `KeyCode.KC_SHIFT` (81), `client.isKeyPressed(...)` |
| Loadout builder (staging) | `InterfaceID.PvpArenaStagingareaSupplies` (`0x02f6`) |
| Loadout builder (duel) | `InterfaceID.PvpArenaUnrankedduel` (`0x02f5`) |
| Opponent spellbook display | `PvpArenaUnrankedduel.OPPONENTSPELLBOOK_DISPLAY` |
| Confirm button | `PvpArenaUnrankedduel.OPPONENT_CONFIRM` |
| Player spellbook displays | `PvpArenaUnrankedduel._0/_1/_2 SPELLBOOK_DISPLAY` |
| Build (picks active player panel) | `VarbitID.PVPA_TRANSMIT_BUILD` (14024) |
| Warning icon | `SpriteID.PVP_WARNING_ICON` (1728) |

> Spellbook comparison is on display label text, not varbits. The loadout varbit
> `PVPA_LOADOUT_A_SPELLBOOK` (14035) encodes `Ancient=0, Standard=1, Lunar=2` (NOT
> the `VarbitID.SPELLBOOK` order), and `PVPA_UNRANKEDDUEL_TRANSMIT_OPPONENTSPELLBOOK`
> (14031) is unreliable — both avoided. See "Feature 2 → Data source".
