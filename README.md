# PvP Arena

Quality-of-life utilities for dueling on PvP Arena (Emir's Arena) worlds. The
plugin does nothing outside PvP Arena worlds.

## Features

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

- **Shift-click discard** — toggle Feature 1 (default on).
- **Highlight spellbook mismatch** — toggle Feature 2 (default on).
