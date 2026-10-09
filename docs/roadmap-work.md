# Roadmap implementation evidence

Authoritative main `58d4a6d` fetched on 2026-10-09. This isolated branch includes unmerged PR #15 as an explicit dependency; neither branch is a production release. PR #16 specifies the recommendation families. No local EARS/state tools exist; tracking is manual.

The user requested implementation while preserving the earlier testing hold. Source-derived failure paths establish PROVE preparation, not executed red/green evidence. ENGINE work below is unverified: do not activate or produce a release until focused failure cases, canonical builds/API profiles, hosted review and client acceptance are complete. No server operations are authorized here.

Requirements: retain REC/SNAP/PREF/BUILD/SEAS contracts from PR #16. Initial recovery codec must reject unsupported tile state rather than lose it. Historical player records are inspection-only; applying an older inventory remains separately gated by reconciliation design. Speedbuilders remains private and opt-in with no currency payout. Seasons expose read-only progress and create no external grants.

Source evidence: arena originals exist only in memory; snapshot capture returns true after void save logs an IOException; map validation inspects saved structure; location decoding may generate missing worlds; outcome stats have no season-qualified identity. These findings justify the additive adapters and confirmed persistence contract.

All implementation, architecture inspection and future failure cases will be recorded below. Plugin tests, compilation, runtime cost and Java/Bedrock acceptance remain pending under the hold.

## Source implemented in this draft

| Family | Current slice | Deliberate limits |
| --- | --- | --- |
| PREF | `/ee preflight <EVENT> <map>` reports loaded-world spawn space/support, paired beds, container tiers and region containment; bounded point budget; optional start checks run before preparation and ACTIVE | No chunk loading, geometry repair, cache or new inventory menu. Unloaded/over-budget checks are UNKNOWN and cannot pass enforced activation. Existing map loading behavior is unchanged; the new inspection never invokes world creation. |
| SNAP | Capture acknowledges forced atomic file publication before entry proceeds; failure restores the previous in-memory record. Optional immutable per-player archives include capture/session/time/schema/hash; `/ee recovery player <uuid>` inspects up to 20 versions. Unreadable legacy files block writes/captures. | Existing public `save()` and restoration APIs remain compatible. Archive application, diff/confirmation, concurrent historical leases and resolved-only retention are not implemented. All archives stay on disk, including unlinked captures from failed publication; protect the plugin data folder with operator filesystem permissions. |
| REC | Disabled opt-in captures the whole integer arena region before PRESTART mutation, with bounded main-thread batches and immutable off-thread serialization/file work. SHA-256/schema/session/map/world UUID/bounds/count checks; one pending file; global start gate; explicit restart inspection/retry; repeatable replay followed by a full verification pass; atomic completion archive. Legacy memory reset completes before durable replay. | Ordinary block data only: every TileState is rejected, including containers, signs and other block entities. Not an entity/vehicle backup. No chunk generation/loading/tickets: required chunks must already be loaded. Publication forces the file and requires atomic rename; this targets process loss, not a proven power-loss/filesystem guarantee. Recovery remains unverified and is not a production feature. |
| BUILD | Private, explicitly enabled SPEEDBUILDERS definition excluded from public choices; at most eight equivalent non-overlapping plots; one nonempty ordinary-block template up to 512 cells; bounded plot writes; memory/build/scoring loops; supported palette and restricted commands/crafting/drop/pickup/edit/movement; integer score penalties and tied winners; shared snapshot/arena restoration | Same template repeats each round in this first slice. No speed bonus, prizes or currency. Plot/template setup uses existing map area/spawn tools; floors must sit below build volumes. Java/Bedrock input and exploit resistance remain acceptance gates. |
| SEAS | Explicit production opt-in and namespace, non-overlapping UTC definitions, ACTIVE-time season pin, immutable checksum-qualified per-session completion ledger, private/test/cancel exclusion, minimum session duration, duplicate/conflict checks, lifetime independence and `/event season` counts | Minimum session duration is not per-player AFK proof. Exact active-participation policy, closing grace, personal-best metric adapters, milestone ownership, cosmetics and external grant reconciliation are pending. Closed definitions must remain available for retained ledger reads. |

Pure scoring/findings/season identity live in domain; Paper and file adapters live in infrastructure. Existing manager/service constructors and permissions stay compatible; recovery inspection/retry add operator-default permissions. New engine adapters integrate with existing entry/outcome/cleanup paths rather than replacing the manager. Dedicated application ports for future tile codecs, historical apply and reward integrations remain design work; no new companion plugin calls are added.

## Operator setup after proof and review

All new switches default to false. The unconditional capture-acknowledgement correction also remains unverified. Do not install this branch during the testing hold.

- `preflight.enforce-start`: enables final loaded-world checks for existing games. Inspection is available without enforcing it. Durable recovery and Speedbuilders always require preflight.
- `recovery.arena.enabled`: enables supported destructive-game baselines, with `max-blocks` capped at 50,000 and `blocks-per-tick` capped at 512. Startup pending files block starts even if the switch is later disabled; never delete them to bypass the lease.
- `/ee recovery list` or `inspect`: status for the single global baseline. `/ee recovery retry` requires admin plus recovery permissions and no session; legacy pending blocks must be retried first with `/ee resetarena`. A preparation-only failure with no published baseline can be cleared by explicit retry.
- `recovery.player-history.enabled`: archives new captures. No pruning or historical inventory grants exist. `/ee recovery player <uuid>` reports metadata only.
- Speedbuilders requires `speedbuilders.enabled`, durable recovery, `SPEEDBUILDERS` in `events.enabled`, and a structurally valid map. Set one `build-template` area plus matching `plot-<id>` areas/spawns with equal integer dimensions, separate from the template and each other. Each spawn uses the same clear corner, at plot minimum Y; solid support lies outside the mutable volume. Start only with `/ee private SPEEDBUILDERS ...`.
- Season progress requires `seasons.enabled`, explicit `environment: production`, a stable namespace and immutable `definitions.<id>.start/end` UTC instants. No dates/rewards are chosen here. `/event season` shows only the requesting player's counts, end/timezone and exclusion policy.

## Future proof and open gates

These cases are specified, not executed:

1. PREF: missing/mismatched world, unloaded chunks, budget exhaustion, invalid bed partners, wrong container/tier, invalid floors/areas, revoked permissions and setup change during prestart. Prove inspection creates no worlds/chunks and measure tick cost.
2. SNAP: atomic write/force/rename/permission failures, rollback of prior records, no inventory/membership mutation on rejected capture, metadata fidelity, legacy malformed entry preservation, unlinked archive retention and failed resolution marker writes.
3. REC: process stop during capture/write/ACTIVE/replay/verification/completion; truncated/corrupt/oversized/duplicate-cell/schema records; wrong UUID/bounds; unloaded chunks; unsupported tiles; retry idempotency; stop while write is in flight; disable with pending work; legacy failure/retry ordering; every start entry remains fenced. Measure region-size budgets and verify all original blocks in a disposable world.
4. BUILD: exact/missing/wrong/extra scores and ties; empty/unsupported template; overlapping/wrong-size plots; unobstructed matching spawns; complete block/item/player restoration; cross-plot/admin edits; replenishment/drop/pickup/crafting/command exploits; leave/disconnect/stop; stale round callback and failed teleport/chunk access; Java/Bedrock timing/readability and donor feedback.
5. SEAS: duplicate and conflicting completion IDs; private/test/cancel/empty outcomes; short session and roster bounds; restart checksum/schema/definition validation; late completion across a boundary; timezone/overlap parsing; failed atomic publication, frozen progress and manual reconciliation; lifetime totals unchanged. Resolve AFK/grace/personal-best/cosmetic policy before adding those slices.

No EARS/state helper pass, compile success, new test success, runtime proof, artifact or deployment is claimed. Human review and all deferred checks remain necessary before this draft can be merged/released.

## Interactive delivery

[Draft implementation states](https://chatgpt.com/space/page_ccbed6c3c180819187bfc13e2457aabc) are saved in a private Page. Source is roadmap-preview.html. Page/embed receipt and readback confirmed storage/access. Local simulated controls rendered at 360px without root overflow (328px client/scroll widths). Authenticated phone access and actual Minecraft rendering remain unverified. These fixtures never call a server or grant items.
