# Read arena Setup state from builder widgets, not PVPA_LOADOUT varbits

The Loadouts feature must read the live Setup (worn equipment, inventory supplies +
quantities, spellbook) to save snapshots and to diff them on load. The game exposes
per-build varbits (`PVPA_LOADOUT_A/B/C_INV_00..27`, `_WORN_*`, `_SPELLBOOK`), so
reading those would let us snapshot any build at any time, even with the builder
closed.

We instead read from the **Setup builder widgets** (`_NITEMS_LIST` children for
`getItemId()` + `getItemQuantity()`, `_NSLOTx` for worn items, the spellbook display
label text). Reasons: (1) the inventory varbits carry **no quantity** — there are
exactly 28 inventory varbits (one per slot) and no quantity fields — and quantity is
core to matching; (2) the arena varbits already proved unreliable and oddly-encoded
once (the spellbook-mismatch feature abandoned them for display-label text — see
DESIGN.md "Feature 2 → Data source"); (3) widgets carry itemId **and** quantity and
match exactly what the player sees.

## Consequences

- A Setup builder must be **open** (showing the desired build) to save a Loadout, and
  the live load-diff only runs while a builder is open. Saving/loading is anchored to
  the builder screen, not the sidebar in isolation.
- Reinforces the plugin-wide principle (first set by Feature 2) of trusting rendered
  widget data over arena varbits.

## Considered and rejected

- **Varbit-based reading** — would allow saving any build with the builder closed, but
  carries no quantity data and has a track record of unreliable encoding. May be
  revisited if the `PVPA_LOADOUT_*` encoding (including how quantity is represented) is
  ever reverse-engineered and verified stable.
