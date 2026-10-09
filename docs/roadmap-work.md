# Recovery and progression implementation

Authoritative main `58d4a6d` was fetched on 2026-10-09. PR #17 includes PR #15's unmerged playtest fixes and implements recommendation families from planning PR #16. The renewed request lifts the local build/testing hold and authorizes reviewed merging, but no deployment or server action. Project-local EARS/state helpers are absent; SPEAR requirements, tasks and evidence are maintained manually.

## Implemented behavior and boundaries

| Family | Implemented | Acceptance limits |
| --- | --- | --- |
| PREF | Bounded loaded-world spawn/support, bed, container and region inspection; UNKNOWN for unloaded/over-budget checks; optional enforcement before preparation and ACTIVE | Inspection creates no worlds/chunks. Real geometry and tick cost need server acceptance. Existing legacy location loading is unchanged. |
| SNAP | Durable capture acknowledgement and rollback; immutable checksum archives; actor/state/version-bound 90-second historical confirmation; durable undo before mutation; application journal and isolation across restart; audited reconciliation; resolved-only retention | Old versions without world UUID are inspection-only. Staff must reconcile items spent or transferred outside the plugin. No distributed inventory/provider exactly-once guarantee. Native Paper metadata round trips remain pending. |
| REC | Bounded pre-mutation baseline, checksum/schema/world/bounds identity, global pending lease, startup quarantine, replay and second verification; EE-ARENA-3 supports ordinary cells plus unlocked Chest/Barrel inventories, item metadata, PDC and names; EE-ARENA-2 remains readable | Locks, loot tables/refill containers, signs, other tiles, entities and vehicles fail closed. Chunks must already be loaded. Forced file publication plus atomic rename targets process loss; real process-kill/power-loss acceptance remains pending. |
| BUILD | Private opt-in Speedbuilders, up to eight equivalent isolated plots and 512-cell ordinary template; memory/build/scoring cues, penalties and ties; shared restoration; edits scoped to participants/arena; stale callbacks fenced | One template repeats. No speed bonus or currency. Floors sit below mutable plots. Multiplayer exploits, timing, restoration and Java/Bedrock input remain acceptance gates. |
| SEAS | Production namespace/UTC season pinned at ACTIVE; unique durable completion ledger; private/test/cancel exclusions; sampled participation and closing grace; course/setup-qualified race personal bests; policy seal; own-ledger cosmetics, ownership checks, selection and victory title | No external grants/provider calls. Movement/rotation sampling is a participation heuristic. Retained definitions/policy must stay stable. Catalogue and dates start empty. |

Domain owns pure score/finding/season values; infrastructure owns Paper listeners and filesystem adapters. Existing constructors, commands, permissions and lifetime semantics remain compatible. New application permission defaults to op. No companion-plugin integration contract changed.

## Operator configuration and commands

All new switches default false. Pending arena baselines and uncertain player journals continue fencing unsafe reuse even if disabled. Never delete recovery files to bypass these protections.

- `preflight.enforce-start`: final loaded-world checks; `/ee preflight <EVENT> <map>` inspects. Recovery and Speedbuilders require preflight.
- `recovery.arena.enabled`: supported destructive-game baselines; maximum 50,000 cells, 512 per tick, 16 MiB record and 2 MiB tile. `/ee recovery list|inspect|retry` inspects/retries with no session. Retry legacy memory reset first with `/ee resetarena` when required.
- `recovery.player-history.enabled`: archives captures. `/ee recovery player <uuid>` reports journal/history. `/ee recovery preview <uuid> <capture-uuid> <reason...>` shows differences and issues a token; `/ee recovery confirm <token>` rechecks actor, permission, state and integrity. Target must be online outside events. Application persists undo and APPLYING lease before mutation; uncertainty blocks automatic replay and transfers.
- `/ee recovery reconcile <uuid> <reason...>` previews explicit no-replay reconciliation. Confirm only after staff resolve the uncertain outcome; acknowledgement records actor/reason without restoring inventory again.
- `recovery.player-history.retention.enabled`: guarded explicit `/ee recovery prune <uuid>`. Only verified resolved versions beyond count/age are eligible; current pointers, confirmations, pending/corrupt records and uncertain leases remain protected. No scheduled deletion.
- Speedbuilders needs `speedbuilders.enabled`, durable recovery, `SPEEDBUILDERS` in `events.enabled`, valid template and matching non-overlapping `plot-<id>` areas/spawns. Start with `/ee private SPEEDBUILDERS ...`; public choices exclude it.
- Seasons need `seasons.enabled`, `environment: production`, stable namespace and UTC `definitions.<id>.start/end`. Participation uses `minimum-active-seconds`, `idle-seconds` and `closing-grace-seconds`. Changed sealed policy freezes progress. `/event season` shows counts/bests; `/event season equip <id|none>` selects owned titles. Optional `cosmetics.<id>.title/completed/wins` catalogue defaults empty.

## Verification and gates

Java 25.0.3 / Maven 3.9.11 canonical `clean verify` and explicit Paper `26.2.build.128-stable` `clean verify` both passed **55 tests**, zero failures/errors/skips, plus configured PMD. Declared profile is `26.2.build.39-alpha`.

Three new assertion failures established regressions before fixes: malformed historical health accepted; Speedbuilders cancelling unrelated builders outside the arena; stale callbacks stopping replacement tasks. New-feature coverage includes failed captures/acknowledgements, restart lease isolation, confirmed undo/state/permission binding, corrupt archive retention, arena disk reconstruction/replay/identity/unloaded paths, supported containers, score arithmetic, preflight budgets and seasonal duplicate/restart/exclusion/grace/policy/ownership rules. Mocked serialization is not native NBT proof; service/disk reconstruction is not a real server process-kill test.

Exact-head hosted CI/review and canonical merge remain gates. Connected upstream account has pull access only and cannot approve workflows or merge. Fork checks do not fulfill upstream protection. Native Paper failure/restart, region tick cost, container/inventory fidelity and full donor Java/Bedrock matches remain activation gates. No server upload, restart or production release occurred.

## Interactive delivery

[Recovery and progression states](https://chatgpt.com/space/page_ccbed6c3c180819187bfc13e2457aabc) are saved in a private Page; source is `roadmap-preview.html`. Save receipt/readback confirms the current embed. Controls show fixtures, not a Minecraft client/live grants. Owner login is required; authenticated phone access and actual client rendering remain unverified.
