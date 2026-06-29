# PvP Arena

Glossary for the PvP Arena RuneLite plugin: utility features for players dueling on
PvP Arena (Emir's Arena) worlds. The plugin is inert outside that context.

## Language

**PvP Arena world**:
A game world whose world-types include `WorldType.PVP_ARENA`. The authoritative
signal for "the plugin may act." Every feature additionally checks that its target
interface is loaded.
_Avoid_: duel world, arena world

**Setup**:
The *live* equipment, inventory items, and spellbook a player has currently
assembled in a [[Setup builder]] for the active [[Build]]. The thing a [[Loadout]]
is compared against and recalled into.
_Avoid_: live loadout, current loadout, kit, gear

**Loadout**:
A *saved, named, persisted* snapshot of a [[Setup]] (worn equipment, inventory
items + quantities, spellbook) that the plugin stores and lets the player recall.
Grouped *for display* by the [[Build]] it was saved from — but **not** locked to it:
any Loadout can be loaded while any build panel is showing, and the diff runs against
whichever panel is currently shown. The artifact this feature manages; the panel that
lists them is titled "Loadouts".
_Avoid_: saved setup, preset, template.

**Setup builder**:
An arena interface where a player assembles a [[Setup]]. Two exist: the Staging-area
Supplies screen (`PvpArenaStagingareaSupplies`) and the Unranked-duel screen
(`PvpArenaUnrankedduel`). Each shows worn equipment, the current inventory, and the
[[Catalog]].
_Avoid_: loadout builder.

**Catalog**:
The persistent, full list of every available supply item inside a [[Setup builder]].
Search only scrolls to an item (it does not filter); a left-click adds one of that
item to the [[Setup]] inventory. The surface where a loaded [[Loadout]]'s "to-add"
highlights and required-quantity counts are drawn.
_Avoid_: selection list, item list, supply list.

**Active loadout**:
The single [[Loadout]] the player has currently "loaded" for comparison. While set,
the plugin diffs it against the live [[Setup]] and paints highlights. Ephemeral
(in-memory): cleared manually, on full match, on loading another, on leaving a
[[PvP Arena world]], and when a duel begins. At most one at a time.
_Avoid_: selected loadout, loaded setup.

**Unranked duel screen**:
The `PvpArenaUnrankedduel` interface. The only screen showing both the local
player's spellbook selector and the opponent's chosen spellbook, plus the confirm
button. Where [[Spellbook mismatch]] surfacing applies.

**Spellbook mismatch**:
The condition where the local player's active-loadout spellbook label differs from
the opponent's spellbook label on the [[Unranked duel screen]]. Determined by
comparing the **display label text** (e.g. "Ancient Magicks" vs "Standard
Spellbook"), case-insensitively — not varbit values, whose encodings differ and are
unreliable. ("Active-loadout spellbook" here means the active [[Setup]]'s spellbook.)
While true (and the screen is open on a [[PvP Arena world]]), a static
red outline marks the player's spellbook display, the opponent's spellbook display,
and the confirm button, and a `PVP_WARNING_ICON` is drawn on the confirm button.
Fails quiet when either label is unavailable.

**Build**:
The account-build template for a duel (`PVPA_TRANSMIT_BUILD`), one of exactly three
fixed values shown to the player as the labels **"Max/Med"**, **"Zerker"**, and
**"Pure"**. Selects which of the player's three setup panels (`_0`/`_1`/`_2`) /
in-game loadout slots (`A`/`B`/`C`) is active, and therefore which player spellbook
label is read for [[Spellbook mismatch]]. Verified in-game: build 0 → panel `_0`.
The grouping axis for [[Loadout]]s.
_Avoid_: Account build, account type, stat build (all mean this).
_To verify in-game_: the exact value→label mapping (which of 0/1/2 is Pure, etc.).

**Discard**:
The destructive menu option on a [[Setup]] item that removes it from the setup.
Default left-click on items that have it. The action Feature 1 ("shift-click delete")
gates behind Shift.
_Avoid_: Delete, Remove

**Examine**:
The benign, non-destructive menu option on a [[Setup]] item. Promoted to default
left-click when Shift is not held, so a misclick during drag-rearrange examines
rather than discards.
