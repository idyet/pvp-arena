# PvP Arena

Glossary for the PvP Arena RuneLite plugin: utility features for players dueling on
PvP Arena (Emir's Arena) worlds. The plugin is inert outside that context.

## Language

**PvP Arena world**:
A game world whose world-types include `WorldType.PVP_ARENA`. The authoritative
signal for "the plugin may act." Every feature additionally checks that its target
interface is loaded.
_Avoid_: duel world, arena world

**Loadout**:
The equipment, inventory items, and spellbook a player assembles for a duel using
the arena's build interfaces.
_Avoid_: setup, kit, gear

**Loadout builder**:
An arena interface where a player assembles a loadout. Two exist: the Staging-area
Supplies screen (`PvpArenaStagingareaSupplies`) and the Unranked-duel screen
(`PvpArenaUnrankedduel`).

**Unranked duel screen**:
The `PvpArenaUnrankedduel` interface. The only screen showing both the local
player's spellbook selector and the opponent's chosen spellbook, plus the confirm
button. Where [[Spellbook mismatch]] surfacing applies.

**Spellbook mismatch**:
The condition where the local player's active-loadout spellbook label differs from
the opponent's spellbook label on the [[Unranked duel screen]]. Determined by
comparing the **display label text** (e.g. "Ancient Magicks" vs "Standard
Spellbook"), case-insensitively — not varbit values, whose encodings differ and are
unreliable. While true (and the screen is open on a [[PvP Arena world]]), a static
red outline marks the player's spellbook display, the opponent's spellbook display,
and the confirm button, and a `PVP_WARNING_ICON` is drawn on the confirm button.
Fails quiet when either label is unavailable.

**Build**:
The agreed stat/equipment template for a duel (`PVPA_TRANSMIT_BUILD`). Selects
which of the player's three loadout panels (`_0`/`_1`/`_2`) is active, and therefore
which player spellbook label is read for [[Spellbook mismatch]]. Verified in-game:
build 0 → panel `_0`.

**Discard**:
The destructive menu option on a [[Loadout]] item that removes it from the
loadout. Default left-click on items that have it. The action Feature 1
("shift-click delete") gates behind Shift.
_Avoid_: Delete, Remove

**Examine**:
The benign, non-destructive menu option on a loadout item. Promoted to default
left-click when Shift is not held, so a misclick during drag-rearrange examines
rather than discards.
