# Loadout code v2 compresses the JSON with raw DEFLATE

[[ADR-0004]] established the [[Loadout code]] wire format `pvpa-loadout-v1:<Base64(JSON)>` and
recorded a deliberate "no gzip for v1" decision, on the premise that one loadout is "a few dozen
item IDs, sub-1KB JSON" where compression saves little. Real loadouts are larger than that
premise assumed: a full no-honour setup is 11 worn items plus a ~28-slot inventory, each leaf
carrying full `slot`/`itemId`/`quantity` keys. A measured real "max nh" code was **1252 chars**
even after the inventory-dedup normalization. v2 revisits the compression decision with numbers.

## Decision

v2 is `pvpa-loadout-v2:<Base64(raw-DEFLATE(JSON))>` of the **same `LoadoutCode` envelope** as v1.
Only the byte pipeline changes: v1 is `Base64(JSON)`, v2 wraps a raw DEFLATE layer under the
Base64. The JSON schema is untouched, so v1 and v2 decode through one shared DTO and one Gson
parse; the only fork is an inflate step. `VERSION` is bumped to `2`.

**Encode always emits v2; decode accepts every released version back to v1.** A code shared before
v2 existed must import forever (the sticky-format contract from [[ADR-0004]]). A frozen, verbatim
v1 code is pinned in `LoadoutCodecTest` as a back-compat witness, never regenerated from the
current encoder, so a v1 regression cannot hide once encode stops emitting v1.

## Measured result

The real "max nh" loadout: **1252 -> 360 chars, a 71% reduction.** (For reference, the same code
before the earlier inventory-dedup step was 1768 chars.)

## Rationale

**Raw DEFLATE, not gzip.** `java.util.zip` is in the JDK, so no new dependency (as [[ADR-0004]]
already anticipated). The nowrap (raw) variant omits the zlib header and Adler-32 checksum,
avoiding roughly six bytes of overhead that would otherwise hurt small payloads. `BEST_COMPRESSION`
is deterministic for a fixed input, so the same loadout still produces a byte-identical code (the
`encodeIsDeterministic` guarantee holds).

**Always compress, unconditionally.** A tiny loadout that happens to compress a few bytes larger
than its raw form is still tiny, and compressing unconditionally avoids an "is this payload
compressed?" detection branch in decode. The version tag alone selects the pipeline.

**Schema left alone (the simplest lever won).** Four shapes were measured on the real loadout:

| Lever | Size | vs baseline |
|-------|------|-------------|
| A: Tier-0 JSON + DEFLATE (chosen) | 360 | -71% |
| B: positional arrays, no compression | 484 | -61% |
| C: positional arrays + DEFLATE | 296 | -76% |
| D: merged `bag()` list + DEFLATE | 256 | -80% |

A was chosen despite not being the smallest. The gap from A to D is ~100 chars on a clipboard
token that is already trivially pasteable at either size, so the marginal shrink buys nothing a
user can feel, while B/C/D each cost permanent complexity: a positional codec loses Gson's
tolerance to field reorder/absence forever, and the merged form (D) additionally discards the
worn `slot` and the worn-vs-inventory split, making import lossy on the wire. Those fields are
already dead for matching (everything flows through `Loadout.bag()`, see [[ADR-0002]]) and for
display, but keeping them costs nothing under A and preserves a fully lossless round-trip. Key
shortening and a spellbook enum were considered and dropped as dominated or marginal. If a future
need ever justifies structural shrink, v3 is the escape hatch, exactly as the version prefix
intends.

## Consequences

- **Decode-v1-forever.** The v1 path stays alive indefinitely, guarded by the frozen-literal test.
- **The opaque-token property is preserved.** The payload is still one Base64 blob with no
  braces or quotes to be mangled by chat clients.
- **Corrupt input fails soft.** A truncated or non-DEFLATE v2 payload decodes to a clean
  `INVALID`, never an exception escaping the codec.
- **The format is still sticky.** v2 codes are now in the wild too; any further change is a v3
  with a decode path kept for v1 and v2, never an in-place redefinition.
