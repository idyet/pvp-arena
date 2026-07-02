# Loadouts are stored globally, not per-account

Loadouts are persisted to a single `pvparena` config key shared across every account
logged into this RuneLite install — **not** RSProfile-scoped (per-account), which is
the more common RuneLite pattern for per-character data.

Rationale: PvP Arena normalizes a duelist's stats to the chosen [[Build]] preset, so a
"Pure", "Zerker", or "Max/Med" duel is playable from **any** account regardless of its
real stats. A Loadout is therefore a build-template, not character-specific data. Global
storage lets a player build their library once and have it available everywhere, instead
of rebuilding or duplicating templates per account.

## Consequences

- All three build groups are relevant on any account, so the panel always shows them.
- Switching to per-account storage later would strand the existing global library
  (would require a migration), so this is treated as a deliberate, sticky choice.
