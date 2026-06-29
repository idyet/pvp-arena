# Match loadouts by total item possession, ignoring worn-vs-inventory placement

A saved Loadout stores worn equipment and inventory supplies **distinctly** (so a
snapshot is faithful), yet the load-diff **flattens** both into a single
`itemId → quantity` bag and compares that against the same flattening of the live
Setup (worn + inventory combined). Spellbook is compared separately.

We chose this "total-possession" model over per-ledger matching (worn diffed against
worn, inventory against inventory) deliberately: the player should be free to arrange
where an item sits. If the Loadout wants a Dragon dagger worn but the player keeps it
in inventory, that is a **match**, not a prompt to remove-and-re-add. The diff only
answers "do you possess the right items in the right quantities?" — placement is the
player's call.

## Consequences

- `need(item) = max(0, wantTotal − haveTotal)` drives catalog "to-add" highlights;
  `excess(item) = max(0, haveTotal − wantTotal)` drives the setup slashed-circle marks.
- The stored worn/inventory distinction is currently used only for snapshot fidelity
  and display, not for matching. A future reader should not "fix" the diff to honour
  placement — that is intentionally out of scope.

## Considered and rejected

- **Independent ledgers** (worn-vs-worn, inv-vs-inv): would flag an item sitting in the
  wrong place, but produces confusing "remove it then re-add it" guidance the UI can't
  express well, for a case (manual mis-placement) that rarely occurs since players
  usually wipe-and-rebuild.
