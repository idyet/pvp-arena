# Loadout codes are a versioned, opaque clipboard format decoupled from the model

A [[Loadout]] can be shared as a [[Loadout code]]: a text string carried on the system
clipboard, produced by **Export** ("Copy loadout code") and consumed by **Import**. The
wire format is:

```
pvpa-loadout-v1:<Base64(JSON)>
```

where the JSON is a dedicated `LoadoutCode` DTO — **not** the internal `Loadout` object —
carrying only `name`, `build`, `spellbook`, `worn`, and `inventory` (reusing `WornItem` /
`InvItem` as leaf types). Encode/decode live in a single `LoadoutCodec`. Import mints a
fresh `id` and stamps `savedAt = now`, so it reuses the existing `LoadoutManager.add()`
path.

## Decisions and rationale

**Versioned magic prefix.** The `pvpa-loadout-v1:` prefix lets Import (1) reject arbitrary
clipboard content with a clean message instead of a parse-exception, and (2) branch on
version if the schema ever changes. A code from a newer plugin (`pvpa-loadout-v2:`) gets a
distinct "made with a newer version" message rather than a generic "invalid" one. The
prefix — not compression — is the real future-proofing.

**Base64, not raw JSON.** The code is one opaque token: no braces/quotes to be mangled by
Discord markdown or line-wrapping, and it reads as a code rather than inviting hand-editing
of a payload that would then fail validation. The cost — a curious user can't eyeball the
contents — is accepted (arguably a feature: it discourages producing corrupt codes by hand).

**No gzip (for v1).** One loadout is a few dozen item IDs — sub-1KB JSON. Compression saves
little and adds a decode branch. `java.util.zip` is available (no new dependency) if a future
bulk format ever wants it; the version prefix leaves room to add it.

**DTO decoupled from the internal `Loadout`.** Serializing `Loadout` directly would couple
the wire format to the internal model: a field rename or a change to `WornItem`'s shape during
a future refactor would silently change or break codes already in the wild. A dedicated
`LoadoutCode` DTO makes the wire shape explicit and independent, and makes field exclusion
(`id`, `savedAt` are simply absent) fall out for free. Cost: a small amount of mapping
boilerplate, which Lombok makes trivial. Leaf types (`WornItem` / `InvItem`) are reused
because they are already dumb data holders; only the top-level envelope is mirrored.

**No import-time item validation.** Item IDs in a code are trusted, not checked against the
item cache at import. Graceful degradation already exists downstream: when a loadout is
loaded against a [[Setup builder]], `unlocatableCount` / the "N not in catalog" surfacing
handles items that can't be placed. Validating at import would duplicate that logic, couple
import to `ItemManager`, and risk rejecting legitimate items. Bad or unavailable items fail
soft at load time, exactly like a hand-built loadout containing an item the current builder
doesn't offer.

## Consequences

- **The format is sticky.** Once codes are shared externally, changing the wire format
  strands them. Future changes go through a new version tag with a decode path for `v1`,
  never an in-place redefinition.
- Import/export are **inert-safe**: they touch only the clipboard and config, never the
  client thread. Their availability is gated only incidentally, by panel visibility
  (PvP Arena worlds — see the "inert outside" identity), not by any client interaction.
- Import always adds a new entry (duplicates allowed); it never dedupes or overwrites,
  because no stable source identity is carried across the code (by design — `id` is
  regenerated).
