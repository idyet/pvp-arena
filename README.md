# PvP Arena

Quality-of-life utilities for dueling on PvP Arena (Emir's Arena) worlds. The
plugin does nothing outside PvP Arena worlds.

## Features

### Loadouts (default on)

Save your duel setups and rebuild them in seconds. A **Loadouts** panel appears in
the RuneLite sidebar whenever you're on a PvP Arena world.

![Saving, loading, and sharing a loadout](loadout.gif)

- **Save current setup** — with a setup screen open (staging-area supplies or the
  unranked duel screen), snapshot your worn gear, inventory items + quantities, and
  spellbook as a named loadout.
- Loadouts are grouped by build — **Max/Med**, **Zerker**, **Pure** — but not locked
  to it: load any loadout while any build's panel is showing.
- **Click a loadout to load it.** The plugin compares it against your live setup and
  paints the screen so you can rebuild by eye:
  - **Green outline + count** on catalog items you still need to add (the count is how
    many more are required).
  - **Red outline** on inventory/worn items to discard (only the surplus over the
    loadout, not every copy) and on your spellbook selector if it differs.
  - Highlights update as you go; when your setup matches, it shows **"Loadout matched"**
    and stops comparing automatically.
- **Filter** (funnel icon) — narrow the catalog to just the active loadout's items so
  you're not scrolling past everything else.
- **Stop** (square icon) — stop comparing at any time.
- **Options** (⋯ menu) — Rename, Update from current setup, Copy loadout code, Delete.

**Share loadouts.** Export a loadout to your clipboard as a portable **loadout code**
with *Copy loadout code*, and recreate it anywhere with **Import loadout code**. A code
is a single opaque token (safe to paste into Discord) — no files involved.

Loadouts are stored once per RuneLite install and shared across all your accounts, since
PvP Arena normalizes stats to the build preset — your library follows you everywhere.

### Shift-click discard (default on)

When building a loadout, a plain left-click on an item can accidentally **Discard**
it while you are trying to click-drag to rearrange. With this on:

- **Left-click** an item → **Examine** (safe).
- **Shift + left-click** → **Discard** (intentional).
- Right-clicking still shows `Discard` as normal.

Only items whose default left-click is `Discard` are affected; everything else is
left as-is. Works in both the staging-area supplies screen and the unranked duel
loadout screen.

### Spellbook mismatch (default on)

On the unranked duel screen, if your selected spellbook differs from your
opponent's, the plugin highlights it before you commit:

- A red outline on **your** spellbook selector, the **opponent's** spellbook, and
  the **confirm** button.
- A warning icon on the confirm button.

The highlight appears as soon as a mismatch exists and clears the moment either
side switches to matching spellbooks.

## Config

- **Loadouts** — toggle the Loadouts panel and its load highlights (default on).
- **Shift-click discard** — toggle shift-gated discard (default on).
- **Highlight spellbook mismatch** — toggle the spellbook-mismatch warning (default on).
