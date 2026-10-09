# Source evidence and future proof plan

This planning branch is based on main `58d4a6d`; implementation context from PR #15 (`f1ab881`, code artifact `fca7f5a`) is explicitly separate. No tests/builds or server/player actions are run during the hold. These are required future cases, not passed tests or historical red/green results.

## Source evidence

- ArenaResetService stores `originalStates` and `pendingRestore` as in-memory Bukkit objects. PR #15's completion/failure gates do not serialize them.
- PlayerSnapshotService refuses to overwrite pending snapshots and retains teleport failures. Its capture-success result is not coupled to save IOException acknowledgement. This is a static failure path, not a reproduced loss report.
- MapSetupService checks required map structure; actual world geometry inspection needs game-specific rules and read-only decoding.
- EventOutcomeService includes Vault deposits and session accounting; EventStatsService saves lifetime statistics. Neither provides a proposed season-qualified grant ledger. A local ledger cannot alone guarantee exactly-once Vault/external grants across an uncertain crash window.

## Required failure and regression proof

| Requirement group | Future proof cases | Runtime/client boundary |
| --- | --- | --- |
| REC | Restart at each manifest transition; torn/truncated/corrupt baseline; unsupported tile; wrong world UUID; missing world; disk full/permission failure; atomic commit unavailable; false/throwing replay update; repeat replay and resume; every start path quarantined; chunk tickets released | Representative supported containers/tile state/fluids, forced process loss in disposable environment and measured main-thread budget |
| SNAP | Capture write fails before fee/membership/temp state; no overwrite of pending; corrupt legacy migration preserved; resolved-only pruning; stale confirmation; concurrent restore; disconnect/teleport failure; crash after apply before acknowledgement; ambiguous state forbids auto replay | Actual metadata/equipment/location restoration; staff recovery after reconciled external transfers; Java/Bedrock inventory visibility |
| PREF | Missing world without creation; wrong region/world; game-specific air/support; paired bed/tier container validity; empty/unsupported/overlapping plots; unloaded unknown; permission revoke; cache invalidation and final start recheck | Existing arena geometry and acceptable tick/chunk budgets; readable staff repair workflow |
| BUILD | Exact/rotated/wrong/missing/extra build scores; N=0 rejection; tie ranking; plot isolation; stale session/round callback; leave/quit/stop; palette exploit/drop escape; deterministic round reset | Java/Bedrock placing, memory readability, fair equivalent plots, timing, full player/world restore and donor feedback |
| SEAS | Duplicate/late outcome; cross-server namespace; season boundary/timezone; invalid overlap; private/TEST/cancel exclusion; no lifetime reset; failed/uncertain external grant; no duplicate reward on retry; out-of-order events | Actual eligible public play, AFK policy, real companion API and mobile/client progress view |

## Planning verification

Requirements and architecture are reviewed against current source and the testing hold. `git diff --check` and documentation/link consistency review apply. No behavioral proof/engine checks apply to unimplemented proposals. Interactive states use fixture data and do not call a server or mutate inventory. Saved remote Page access and local preview inspection are distinct from authenticated phone and real-client acceptance.

Final PR and preview receipts are recorded in the task-local delivery record; no private Discord logs, raw inventory archives or server identifiers are committed.

The [interactive proposal](https://chatgpt.com/space/page_fd2e3c0578fc819181aa6cd11de4267e) is saved as a private Page. Its visualization insertion receipt and Page readback confirm delivery. Local browser inspection at 360px covered feature/scenario selection and normal/uncertain lifecycle navigation, with no horizontal overflow in the preview root. This is presentation inspection only; no plugin tests ran. Authenticated phone access and actual in-game behavior remain unverified. The checked-in HTML contains fixture data and can also be rendered through the Visualize skill.
