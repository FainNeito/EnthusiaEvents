# Proposed implementation architecture

This design covers crash-safe arena recovery, versioned player recovery, stronger preflight, Speedbuilders and seasonal progression. All names, commands, settings and screens here are proposals; no runtime classes or active configuration are added. PR #15 remains a separate dependency for its existing reset gates, tools and Runner improvements.

## Current source contracts

`reset/ArenaResetService` holds Bukkit `BlockState` objects in memory. PR #15 retains failed work and gates starts but does not persist that queue. Serialized baselines must replace assumptions that a live BlockState can be written to disk or touched asynchronously.

`event/PlayerSnapshotService` preserves pending snapshots, retries teleport and marks restore success only after application. Its `capture()` currently calls a void `save()` that logs an IOException and still returns true. A future persistence-result contract must close this source-supported durability gap; no runtime reproduction or fix is claimed here. Current pending captures and success semantics must survive migration.

`event/MapSetupService` validates map structure. The proposed preflight extends it with bounded inspections of actual world state, without invoking `LocationCodec.decode` paths that can create worlds during a read-only check.

`event/EventOutcomeService` coordinates rankings, stats and Vault deposits; `stats/EventStatsService` maintains lifetime stats. Seasonal credit must consume an immutable completion result, avoid double-accounting existing rewards, and keep its own season-qualified ledger. A Vault deposit does not provide a cross-process idempotency key; exactly-once external currency payment cannot be promised by merely writing a local flag.

## Layer boundaries

| Layer | Proposed contracts | Responsibility |
| --- | --- | --- |
| Domain | ArenaIdentity, RecoveryState, SnapshotVersion, PreflightFinding, BuildTemplate, BuildScore, SeasonDefinition, OutcomeCreditKey | Immutable values, transitions, scoring and eligibility; no Bukkit/file/GUI imports |
| Application | PrepareArena, RecoverArena, CapturePlayerState, InspectRecovery, ApplyHistoricalRecovery, RunPreflight, SpeedbuildersRound, CreditSeasonOutcome | Orchestrate ports, leases, version checks and durable acknowledgements |
| Infrastructure | PaperWorldCodec, PaperSnapshotCodec, FileRecoveryRepository, FileSnapshotArchive, FileSeasonLedger, StaffRecoveryView, SeasonView | Main-thread Bukkit adapters, bounded file IO, commands, permissions and menus |

Use additive adapters around existing manager/service entry points first. Preserve public compatibility constructors and command/API semantics. Do not combine this work with a wholesale EventManager rewrite. Persistent records use explicit schemas and migrations; a schema mismatch quarantines data rather than attempting unsafe coercion.

## Arena recovery protocol

Prefer a complete baseline of the permitted mutation region before ACTIVE. Persisting only MONITOR event observations cannot establish a durable before-mutation guarantee for every automatic/world change. Capture on the server thread in bounded batches while the map is leased and gameplay is blocked. Convert supported state to immutable bytes, write a temporary baseline with integrity metadata, flush the file and commit its manifest using a verified storage contract. If required atomic/durability semantics are unavailable, fail closed rather than silently weakening the guarantee.

Manifest identity: schema, server namespace, session UUID, map ID/version, world UUID/name, bounds, permitted mutation region, baseline checksum, entry count, timestamps and recovery generation. Paths derive from validated internal IDs, never raw map names or staff input. Baselines include block data and supported inventories/tile metadata; unsupported tile classes fail preparation until a lossless adapter exists. Entity/vehicle cleanup remains separately owned: a block baseline is not a decorative-entity backup.

State sequence: PREPARING -> PREPARED -> ACTIVE -> RESTORING -> VERIFIED -> COMPLETE. Any failure becomes QUARANTINED while retaining the last durable generation. On restart, unfinished records are fenced before schedule/queue/private/manual/API starts become available. The first slice retains the global reset gate; per-arena concurrency is a later change.

Recovery checks identity/integrity, loads only required chunks through managed tickets, applies batches on the server thread and records replay progress. Replaying original state is repeatable under an exclusive map lease. Verify supported state after replay before committing COMPLETE; only then release the gate and prune resolved baselines. Missing worlds remain unavailable; recovery must not silently generate a replacement world with the same name. Release tickets in both success and failure paths.

Proposed staff operations: `/ee recovery list`, `/ee recovery inspect <session>`, `/ee recovery retry <session>`. No ordinary discard/unlock shortcut. A later emergency operator resolution must require a reason, preserve original bytes, and leave a reviewed audit trail. Existing `/ee resetarena` remains compatible for the existing in-memory queue.

## Player archive and recovery protocol

Keep active pending restoration separate from read-only history. New captures create immutable archive bytes plus an active pointer only after persistence is confirmed. Changing capture success reporting requires caller rollback: fee, membership, temporary inventory and teleport changes must not proceed when persistence fails. Legacy snapshots.yml is read and retained during migration; unsupported/corrupt entries remain inspectable without overwriting original bytes.

Proposed history retention: five resolved versions per player and 30 days, configurable and disabled until the new storage path is accepted. Existing active pending retention is unchanged; pending/ambiguous versions never expire automatically. Directory permissions restrict raw inventory bytes to server operators. Routine traces store IDs and hashes, never inventory contents.

Historical recovery states: INSPECTED -> CONFIRMED -> APPLYING -> APPLIED or RECONCILE_REQUIRED. Confirmation binds target version, current-player-state generation, operator and reason, then rechecks them at apply time under a per-player lease. Capture current state durably as a recovery undo record before application. Prevent active event participation, external inventory transfers and concurrent restores for the operation. Successful teleport and item/state application must precede the durable APPLIED result. A crash between application and acknowledgement is ambiguous; lock out automatic replay and require reconciliation.

A history record is not a promise that its items are safe to grant: the player may already have spent/transferred them. Show that risk and require staff reconciliation. Initial slice provides archival inspection only; applying old versions is a separate later slice.

Proposed commands: `/ee recovery player <uuid>` and `/ee recovery inspect-player <capture-id>`. Separate operator-default permissions for inspect and apply are proposed. Existing admin restoration commands and permissions remain intact; any GUI rechecks permissions when opened, navigated and confirmed.

## Preflight protocol

Run a cheap structural check first, then bounded world inspection. Report BLOCKER, WARNING, UNKNOWN and READY; READY requires all mandatory checks for the game's rules. Cache only by map/world/recovery/config generation and bounded age, and invalidate on setup changes. Starts must recheck authoritative guards; a green cached menu never grants permission to start through an outstanding reset.

Checks include world availability/UUID, consistent region/world, game-specific spawn containment, passable player space, appropriate support for that game, spectator space and recovery state. Do not require a solid floor for flight/racing spectator positions without a game-specific rule. Beds must have valid paired parts and team ownership; chests must be usable containers with valid tiers; Runner's floor must lie inside its region. Speedbuilders requires equivalent non-overlapping plots and an allowed template palette.

Do not silently repair coordinates or replace invalid beds. Optional chunk loading has a configurable budget and explicit CHECKING status; unloaded checks otherwise show UNKNOWN. Proposed view extends `/ee readiness <EVENT>` and adds `/ee preflight <EVENT> <map>` for text results, using the same findings model.

## Speedbuilders protocol

First release is private/opt-in and depends on durable arena/player recovery plus preflight. Proposed configurable pacing: five rounds, 30 seconds to memorize and 90 seconds to build, followed by bounded scoring/reset. Values are design defaults, not approved live settings. Each plot receives the same relative template orientation/material palette and equal dimensions.

Templates allow ordinary supported blocks only initially: no containers, block entities, fluids, explosives, command blocks, gravity-sensitive arrangements or interactive redstone. Reject duplicate/out-of-bounds positions and unsupported state properties. Blocks placed in a plot are event-only; prevent drops, crafting, external inventory commands, cross-plot edits and block escape. Survival/adventure controls and event items must be specified against actual Java/Bedrock input behavior before engine implementation.

Let N be non-air target cells (N > 0), C exact canonical-state matches, and E placed non-air blocks where the template is air. Proposed score is `max(0, C - E) / N`, shown with matched/missing/wrong/extra counts. Wrong orientation counts as wrong; decorative entity data is excluded. Use integer numerators for ties, rank equal totals equally, and award no speed bonus initially. A tie policy must integrate with existing rankings and reward semantics before enabling rewards.

Phase operations carry session UUID, round generation and plot identity. Delayed scoring cannot read/mutate the next round; leave/disconnect forfeits that round without duplicating credit. Stops restore plots and players through shared recovery services, never a separate snapshot implementation. Text/timer cues supplement colored blocks and support texture-pack variations.

## Seasonal progression protocol

Use immutable season IDs and explicit UTC start/end instants in storage; display configured timezone labels to players. Reject overlapping definitions. Do not infer current-season history from lifetime stats or reset lifetime wins. Pin an eligible session to its season when it becomes active; a late completion retains that identity. Define closing grace periods before live scheduling.

Credit key: server namespace + season ID + session UUID + player UUID + credit type. Persist each eligibility decision and completion outcome before exposing credit. Private, TEST, cancelled/incomplete sessions earn no credit by default; eligibility requires joining before ACTIVE and meeting a configurable active-participation threshold. Anti-AFK/abuse rules and exact thresholds remain policy decisions, not silently implemented rules.

First slice tracks participation, wins and supported personal bests only. Proposed milestone examples (three completed public events, one win) illustrate the preview and are not live grants. Cosmetic catalogue IDs remain unconfigured; no Nexo/companion calls, guild XP or currency changes occur until rewards and runtime adapters are approved. Own-ledger unlock ownership can be transactional; external grants require provider idempotency or an UNCERTAIN state with manual reconciliation. Cancellation never grants a win.

Proposed `/event season` view shows season/timezone, progress, personal bests and locked/unlocked cosmetic requirements without exposing private rosters. Proposed staff inspection provides credit/grant IDs and retry status, not unrestricted arbitrary command rewards.

## Delivery order and open decisions

1. Preflight finding model and read-only diagnostics; regression proof after testing resumes.
2. Arena codec/manifest and quarantine gates; unsupported state and durability capability proof before destructive activation.
3. Snapshot persistence acknowledgement and read-only archive; historical application follows separately.
4. Private Speedbuilders; scoring/pacing/tie policy and full restoration acceptance.
5. Read-only season credit/progress; cosmetic catalogue and grant adapters follow separately.

User decisions remaining before live policy: season dates/timezone/grace, participation thresholds, cosmetic catalogue, Speedbuilders pacing/tie prizes and retention/storage budgets. Reasonable defaults above permit design work, but no live economy, guild or inventory grant policy is inferred.
