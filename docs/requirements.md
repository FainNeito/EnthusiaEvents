# Recovery and gameplay roadmap requirements

Planning baseline: authoritative main `58d4a6dea746c4a51dc4999230d6107b93ed672f`, fetched on 2026-10-09. Existing improvements are in unmerged [PR #15](https://github.com/wsg138/EnthusiaEvents/pull/15). This separate planning change implements no runtime behavior. Testing remains on hold; proposed settings below are not active server policy.

## Crash-safe arena recovery

- REC-001: Before a destructive event becomes active, the system shall durably commit a versioned baseline of its permitted mutation region, including supported container/tile data, and an arena recovery manifest.
- REC-002: While capture, persistence, replay or recovery is incomplete, the system shall block all starts that could reuse the arena; the initial implementation shall retain the existing global reset gate.
- REC-003: When startup finds an unfinished or unreadable recovery record, the system shall quarantine the arena and retain the original record until validated restoration or a separately audited operator resolution succeeds.
- REC-004: When persistence, decoding or a block update fails, the system shall retain recoverable work and report the failure without marking completion or silently applying an incomplete baseline.
- REC-005: While capturing/restoring an arena, the system shall read and mutate Bukkit state on the server thread within bounded batches; off-thread work shall receive only immutable serialized data.
- REC-006: When a baseline contains unsupported tile state, an unavailable world, changed arena identity or an oversized region, the system shall reject destructive activation with a repair reason.

## Versioned player recovery

- SNAP-001: Before changing a player's event state, the system shall obtain a confirmed persistent capture and preserve any existing pending snapshot without overwriting it.
- SNAP-002: When a capture is archived, the system shall retain an immutable version identified by player, session, capture ID, timestamp, schema and integrity data; ordinary event restoration shall retain the current success-aware path.
- SNAP-003: When staff request an older version, the system shall show its identity and differences, require a reason and explicit confirmation, recheck permission/current state, and audit the outcome before any grant is treated as complete.
- SNAP-004: While a player has an active session, pending restoration or an ambiguous previous recovery, the system shall block historical recovery and concurrent changes for that player.
- SNAP-005: When retention limits are reached, the system shall prune only resolved history; pending/ambiguous snapshots shall remain recoverable and trigger a storage warning.
- SNAP-006: When a recovery application has an uncertain outcome, the system shall preserve the record for reconciliation and shall not automatically replay it on restart.

## Stronger arena preflight

- PREF-001: When staff inspect a map, the system shall classify findings as blockers, warnings or unknown checks and show the exact map/world/coordinates and a repair action.
- PREF-002: Before starting a game, the system shall reject missing worlds, mismatched regions, invalid game objects, unavailable spawn space and unresolved recovery work using game-specific rules.
- PREF-003: While validating a large or unloaded region, the system shall bound main-thread work and show checking/unknown status rather than presenting an unfinished scan as ready.
- PREF-004: When staff run preflight, the system shall inspect without replacing blocks, relocating spawns, creating worlds or silently rewriting map data.

## Speedbuilders

- BUILD-001: When a private Speedbuilders round starts, the system shall assign every participant an isolated equivalent plot and a validated template, then progress through memory, build, scoring and reset phases.
- BUILD-002: During scoring, the system shall compare block positions and supported canonical block states, penalize missing/wrong/extra blocks, and rank equal scores equally rather than inventing a single winner.
- BUILD-003: When a player leaves, disconnects or attempts a cross-plot edit, the system shall preserve isolation and restoration and shall fence delayed work to the current session/round.
- BUILD-004: Before a Speedbuilders match becomes active, the system shall validate template materials, plot bounds, spawn safety, durable arena baseline and confirmed player captures.
- BUILD-005: During memory/build/scoring, the system shall communicate phases and time with text as well as color; default production rotation shall remain unchanged until acceptance.

## Seasonal progression

- SEAS-001: When an eligible completed event outcome is recorded, the system shall credit participation, wins and personal bests once per season/session/player/outcome type.
- SEAS-002: When an event is private, TEST-only, cancelled or incomplete, the system shall award no seasonal progression by default.
- SEAS-003: When a season closes, the system shall retain its results and shall not reset lifetime statistics or overwrite a later season's data during late callbacks.
- SEAS-004: When a cosmetic unlock is granted, the system shall use a durable grant identity and distinguish applied, failed and uncertain outcomes; uncertain external grants shall not be blindly retried.
- SEAS-005: Unless a separate economy/guild policy is approved, the system shall preserve existing currency rewards and shall not award guild XP or currency through the season feature.

## Delivery

- DELIVERY-001: Every implemented slice shall have meaningful regression/failure evidence, API/integration checks, reviewed source and distinct client acceptance before release.
- DELIVERY-002: While testing is on hold, the delivered work shall consist of source-grounded design, future proof cases, task records and clearly simulated previews; no gameplay activation or data migration shall occur.
- DELIVERY-003: Proposed previews shall include a clickable remote link and readable touch-friendly controls; authenticated phone/client acceptance shall be stated separately.

No project-local EARS/state helpers exist on this base. These requirements are maintained manually; automated validation is not claimed.
