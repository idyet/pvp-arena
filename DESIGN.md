# PvP Arena — Design Spec

RuneLite Plugin Hub plugin. Quality-of-life utilities for players dueling on PvP
Arena (Emir's Arena) worlds. The plugin is **inert outside that context** — every
feature is gated on the world type *and* on its target interface being loaded.

See [CONTEXT.md](./CONTEXT.md) for the domain glossary.

## Scope

Three features, each independently toggleable and structured so more PvP Arena
utilities can be added later without reworking the core:

1. **Shift-click discard** — prevent accidental left-click deletion of setup
   items; require Shift for intentional discard.
2. **Spellbook mismatch** — make a spellbook mismatch obvious on the unranked duel
   screen before the player commits.
3. **Loadouts** — save named snapshots of a [[Setup]] (worn + inventory + spellbook),
   grouped by [[Build]], and recall one to drive add/remove highlights that guide the
   player to rebuild it.

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

## Feature 3 — Loadouts

Save named snapshots of a setup and recall one to drive add/remove highlights that
guide the player to rebuild it in the arena builder. Terms (see CONTEXT.md): a
**Loadout** is the saved snapshot; the **Setup** is the live in-builder state; the
**Catalog** is the on-screen list you add items from; the **Active loadout** is the
one currently loaded for comparison.

### Data model

A **Loadout** is `{ id, name, build, spellbook, worn[], inventory[], savedAt }`:

- `id` — stable generated id (for rename/delete/future reorder).
- `name` — custom, player-editable. Duplicates allowed (just a label).
- `build` — the build value (`0`/`1`/`2`) it was saved from. Stored as the **value**,
  not the label; labels come from a hardcoded value→label table (Max/Med, Zerker,
  Pure). Grouping/display only — **not** a lock (see "Build context").
- `spellbook` — the spellbook display label text (e.g. `"Ancient Magicks"`).
- `worn[]` — `{slot, itemId}` per occupied equipment slot (incl. ammo/quiver quantity).
- `inventory[]` — `{itemId, qty}` per supply stack.

Worn and inventory are stored **distinctly** for snapshot fidelity, but matching
flattens them (see ADR-0002). Items in the arena are never noted/placeholdered, so
matching is direct item-id equality — no canonicalization.

### Reading the Setup (ADR-0001)

Read from **builder widgets**, not `PVPA_LOADOUT_*` varbits:

- inventory: each `_NITEMS_LIST` child → `getItemId()` + `getItemQuantity()`.
- worn: each `_NSLOTx` → item id (and quantity for ammo).
- spellbook: display label text (reuse `SpellbookMismatchOverlay.spellbookLabel()`).

The varbits carry **no quantity** (28 inv varbits, no quantity fields) and the arena
varbits already proved unreliable (Feature 2). Consequence: a builder must be **open**
(showing the desired build) to save, and the load-diff only runs while a builder is
open.

### Matching / diff (ADR-0002)

Flatten worn + inventory of both sides into `itemId → totalQty` bags; compare spellbook
separately.

- **To add**: `need(item) = max(0, wantTotal − haveTotal)` → highlight in the Catalog
  with a count.
- **Excess / wrong**: `excess(item) = max(0, haveTotal − wantTotal)` → mark in the
  Setup (covers both over-quantity and fully-unwanted items).
- **Spellbook**: differs → flag.
- **Full match** ⇔ no `need`, no `excess`, **and** spellbook matches.

Re-evaluated **live** (on the relevant item/varbit/widget changes), so counts tick down
and marks clear as the player edits — same reactive pattern as Feature 2. (Compute the
diff once per change and cache the to-add/excess maps; render by lookup, don't rescan
the whole catalog each frame.)

### Build context (not locked)

A Loadout is **not** locked to its saved build. The diff always runs against the
**currently-shown** build panel using the Active loadout's items. Loading a Pure
loadout while a Zerker panel is shown highlights against the Zerker panel anyway. No
"switch build" hint.

### Load lifecycle

Loading arms an **Active loadout** (exactly one; loading another replaces it).
Highlights render **only while a Setup builder is open**; the panel selection just arms
the target. Active loadout is **ephemeral** (in-memory). Cleared when:

1. The player hits **Stop** in the panel (manual).
2. The Setup **fully matches** (auto-clear + brief confirmation).
3. A **different** Loadout is loaded (replace).
4. The player **leaves the PvP Arena world** or disables the plugin/feature.
5. A **duel begins** (drops the armed target so it can't reappear if a builder reopens).

If a needed item isn't in the current catalog (possible since builds aren't locked),
highlight everything locatable and show a **"N items not in this catalog"** note on the
active loadout in the panel; full-match (and thus auto-clear) then won't fire — Stop
manually.

### Save flow

- **Trigger**: a **"Save current setup"** button at the top of the sidebar panel,
  enabled only while a builder is open (greyed + tooltip otherwise). Snapshots the
  currently-shown build panel.
- **Name**: Swing input dialog, pre-filled with an editable default (`<Build label>
  loadout`).
- **Build**: auto-tagged from the currently-shown build.
- **New vs update**: Save always creates a *new* Loadout; overwriting an existing one is
  a per-entry **"Update from current setup"** action (builder-open-gated).

### Storage (ADR-0003)

JSON (Gson) under the `pvparena` config group, one key (e.g. `loadouts`), **global**
(not RSProfile-scoped). No hard cap on count.

### Sidebar panel

`PluginPanel` behind a `NavigationButton` added on entering a PvP Arena world and
removed on leaving / logout (and when the feature is off). Layout:

- **"Save current setup"** button (builder-open-gated).
- Three fixed, collapsible build sections (Max/Med, Zerker, Pure) in build order,
  **always shown even when empty** so saves have an obvious home.
- Each entry: name + a small spellbook tag (e.g. "Ancient"). **Row click = Load.**
  A `⋯` menu offers **Rename**, **Update from current setup**, **Delete** (with confirm).
- The Active loadout is highlighted and exposes a **Stop** control.
- Order within a group = save order (newest at bottom). Drag-reorder deferred past v1.

### Visual treatment

Overlay-drawn on widget bounds (no widget mutation, fully reversible), static, fixed
colors (no color config), matching the plugin convention.

| State | Surface | Treatment |
|---|---|---|
| To add | Catalog option | Green outline over the **whole option** (icon + name text), clipped to the catalog viewport, plus a green count **only when more than one** is needed |
| Excess / wrong | Setup inventory & worn item | Red outline over the item |
| Spellbook differs | Spellbook display | Red outline (same look as Feature 2, distinct trigger: loadout vs setup) |

The diff re-evaluates every frame, so as the player adds items the count falls and the
green outline clears once an item is satisfied; red outlines clear as excess is discarded.
Synergy with Feature 1: removing excess is a Shift-click discard.

> The catalog and the owned inventory are separate widgets. The catalog is
> `_NITEMS_LIST` (confirmed: highlights land on it); the owned inventory is `_NINVENTORY`.
> Both are read by DFS started directly at their grid widget. (An earlier read-by-exclusion
> DFS from `universe` never reached `_NINVENTORY` — a sibling, not a descendant — so the
> inventory always read empty.) The full catalog option bounds are the item icon unioned
> with its same-row text sibling(s).

### Catalog filter (hide + repack)

An opt-in per-loadout control that collapses the catalog to **only the active loadout's
items**, so the player sees just what to add. Unlike everything else in Feature 3 this
**mutates widgets**, so it is built to be fully reversible.

- **Control**: a funnel icon button on the Active loadout's panel row (alongside Stop).
  **Engages automatically** when a loadout is loaded; the button toggles it. **Auto-clears**
  when the player clicks the in-game catalog search button (`_NSEEK`, detected via
  `MenuOptionClicked`) so the native search isn't fought.
- **Filter set**: the still-needed items (`diff.toAdd().keySet()`) — exactly the rows the
  green to-add outline highlights. The catalog shrinks as the player adds items and empties
  once nothing is left to add (the remaining excess/spellbook work shows on the setup side).
- **Order** (top to bottom): equipable items first by equipment slot (head to ammo), then
  the rest by target quantity ascending. "Equipable" is decided by `ItemManager` item stats,
  so equipable switches stored in the loadout's inventory sort with the gear, not the
  consumables. `PvpArenaPlugin.filterOrder` builds the ordered id list; `CatalogFilter`
  repacks the kept rows into that order (index 0 topmost — smaller `originalY` is nearer the
  top). Order is keyed off the stored loadout (stable), so rows don't re-sort as items are
  added — only drop out.
- **Mechanism** (`CatalogFilter`): the catalog (`_NITEMS_LIST`) is a flat scroll list —
  ~460 rows, ~4 widgets each, all direct dynamic-children, every widget's `originalY ==
  rowIndex * 32` (verified in-game 2026-06). Group children by that 32px band, hide
  non-loadout rows, repack survivors into a gap-free `0..n` run, shrink `scrollHeight`.
- **Re-apply**: the game rebuilds the list on its own (item add/remove, build switch),
  resetting the repack, so `PvpArenaPlugin.onClientTick` polls `maintain()`. It
  short-circuits via a `scrollHeight` check when already filtered (≈free steady state) and
  re-applies after a rebuild.
- **Reversibility**: `clear()` restores every touched widget's hidden flag + `originalY`
  and the list's `scrollHeight` from a saved snapshot; `restore()` no-ops safely if the
  game rebuilt the list since (detected by a `scrollHeight` mismatch). Cleared on stop,
  search, feature/plugin off, and leaving the arena.

> Trade-off: this is the **only** widget mutation in the plugin (the rest is overlay-only,
> per "Visual treatment"). Justified by being self-contained and fully restored; the one
> visible artifact is a possible 1-frame flicker when the game rebuilds before the next
> `onClientTick` re-applies.

### Config

One master toggle `loadouts` (default **true**) gating the whole feature (panel, save,
highlights, catalog filter). When off, fully inert. No sub-toggles — the filter is
controlled per-loadout from the panel, not config.

### Architecture

- `LoadoutPanel` (+ `NavigationButton` wiring in `PvpArenaPlugin`) — the sidebar UI.
- `LoadoutManager` — load/save/delete/rename, JSON persistence via `ConfigManager`,
  holds the Active loadout state.
- `Loadout` — the data model (above).
- `SetupReader` — reads the live Setup (worn/inventory/spellbook) from builder widgets.
- `LoadoutDiff` — pure diff: two bags → to-add / excess maps + spellbook flag.
- `LoadoutOverlay` — paints catalog to-add highlights, setup slashed-circles, spellbook
  outline while a builder is open and a loadout is active.
- `CatalogFilter` — hides + repacks the catalog to the active loadout's items (see "Catalog
  filter"); the only widget-mutating piece, fully reversible.

Each piece is guarded by `inPvpArena()` + the builder interface check + the `loadouts`
config flag, like the existing features.

### Testing

Pure, client-free logic (mirrors the existing tests):

- `LoadoutDiff` — bags in → to-add / excess / spellbook-flag / full-match out, across
  the Q4 scenarios (need-more, excess, unwanted, total-possession placement, spellbook).
- Build value→label mapping.

Anything client-coupled (widget reads, overlay bounds, panel, persistence) is covered by
the manual checklist.

## Config (`PvpArenaConfig`)

Three booleans, all default **true**:

- `shiftClickDelete` — "Shift-click discard"
- `spellbookMismatch` — "Highlight spellbook mismatch"
- `loadouts` — "Loadouts" (master toggle for Feature 3)

No color option.

## Architecture

- `PvpArenaPlugin` — wiring, shared `inPvpArena()` helper, registers/unregisters
  the overlays, event subscribers, and the loadouts nav button.
- `ShiftClickDeleteHandler` — `onPostMenuSort` subscriber for Feature 1.
- `SpellbookMismatchOverlay` — Feature 2 (reads display label text each render).
- Feature 3 (Loadouts) — `LoadoutPanel`, `LoadoutManager`, `Loadout`, `SetupReader`,
  `LoadoutDiff`, `LoadoutOverlay` (see "Feature 3 → Architecture").

Each feature is independent and guarded by `inPvpArena()` + its interface check +
its config flag. Adding another feature is a new class wired the same way.

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

Loadouts (Feature 3):

- [ ] Confirm the build value→label order (which of `0`/`1`/`2` is Max/Med, Zerker, Pure).
- [ ] Confirm how to read the **currently-shown build** on the Staging-area Supplies
      screen (`PVPA_TRANSMIT_BUILD` vs the `TAB_EQUIPMENT0/1/2` tab state).
- [ ] Confirm the **duel-begin** signal for auto-clear (candidate:
      `PVPA_UNRANKEDDUEL_TRANSMIT_PROGRESS` terminal value; or builder close into the fight).
- [ ] Confirm exact widget identities: Catalog vs current inventory vs worn — and that
      `_NITEMS_LIST` children expose `getItemId()` + `getItemQuantity()`.
- [ ] Confirm worn ammo/quiver quantity is readable from its slot widget.
- [ ] Confirm catalog item ids equal worn/inventory item ids (no variant surprises).
- [ ] Confirm catalog highlight badges clip to the scroll viewport at various scales.

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
| Setup inventory (per build) | `PvpArena{Unrankedduel,StagingareaSupplies}._NITEMS_LIST` |
| Setup worn slots (per build) | `..._NSLOT0..13` (head/cape/ammy/wep/body/shield/legs/hands/feet/ring/ammo) |
| Catalog search / wipe (per build) | `..._NSEEK` / `..._NWIPE` |
| Staging build tabs | `PvpArenaStagingareaSupplies.TAB_EQUIPMENT0/1/2` |
| Duel-begin signal (candidate) | `VarbitID.PVPA_UNRANKEDDUEL_TRANSMIT_PROGRESS` (14028) |
| Loadout state varbits (unused — see ADR-0001) | `PVPA_LOADOUT_A/B/C_INV_00..27`, `_WORN_*`, `_SPELLBOOK` |

> Spellbook comparison is on display label text, not varbits. The loadout varbit
> `PVPA_LOADOUT_A_SPELLBOOK` (14035) encodes `Ancient=0, Standard=1, Lunar=2` (NOT
> the `VarbitID.SPELLBOOK` order), and `PVPA_UNRANKEDDUEL_TRANSMIT_OPPONENTSPELLBOOK`
> (14031) is unreliable — both avoided. See "Feature 2 → Data source".
