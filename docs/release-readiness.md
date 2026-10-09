# Release readiness

Current PR #17 implements recovery/progression and includes unmerged PR #15. Canonical and explicit stable API clean verification each passed 56 tests and PMD. Local testing/builds resumed; reviewed merging is authorized, while deployment/client acceptance remain separate. See [roadmap-work.md](roadmap-work.md) for current behavior and limits. Upstream workflow approval and merge require write access unavailable to the connected account.

The dated evidence below describes the earlier PR #15 delivery; its testing-hold statements are historical.

As of 2026-10-09, PR [#15](https://github.com/wsg138/EnthusiaEvents/pull/15) is open and ready for review. Testing is on hold at the user's request. The paused acceptance checklist is in [playtest.md](playtest.md). This document records existing evidence and release preparation; it does not authorize another test, merge or deployment.

## Source and artifact provenance

| Item | Recorded state |
| --- | --- |
| Authoritative repository | `wsg138/EnthusiaEvents` |
| Main inspected | `58d4a6dea746c4a51dc4999230d6107b93ed672f`; fetched again during documentation follow-up |
| Verified implementation | `fca7f5a3960783a5d96f624f9518de9c9f1cced8` |
| Prior canonical verification | 31 tests and configured PMD passed with Java 25.0.3 / Maven 3.9.11 |
| Prior API verification | Declared `26.2.build.39-alpha` and explicit `26.2.build.128-stable` override |
| Codacy on implementation | Success, zero new issues |
| Fork workflow on implementation | [Quality 37883242465](https://github.com/FainNeito/EnthusiaEvents/actions/runs/37883242465) passed; does not fulfill upstream branch protection |
| Upstream workflow on implementation | [Quality 37883240850](https://github.com/wsg138/EnthusiaEvents/actions/runs/37883240850) requires maintainer approval |
| Review | Three CodeRabbit findings corrected and resolved; human approval pending |
| Unmerged TEST JAR | `EnthusiaEvents-PR15-fca7f5a-UNMERGED-TEST.jar` |
| TEST artifact SHA-256 | `92e4de823940aaa5bb0c04dc31f879ca0ec5b4d0c0e6ab309377d2382161dff4`; uploaded copy downloaded and compared |
| Version distinction | Maven `1.0.0-SNAPSHOT`; plugin descriptor reports `1.0.0`. Neither identifies a commit; use the source/hash receipt |
| TEST evidence | Plugin enabled; empty private Runner start/stop; persisted trace, dropped=0, reset pending=0 |
| Player acceptance | Pending and on hold; no full client match proven |
| Production | Unchanged |

The later documentation/help commit is separate from the already installed TEST artifact. It must not be described as running on TEST. No replacement JAR is produced during the hold.

## Build ownership

Inspected current [BadgersMC/enthusia-network main](https://github.com/BadgersMC/enthusia-network/tree/main), its `.gitmodules`, recursive tracked tree and build entries. EnthusiaEvents has no current submodule or matching build entry. The current release path is this repository's standalone Maven build and `.github/workflows/quality.yml`; there is no existing Events monorepo pin to update. Recheck ownership before a future release because the network build may change.

Do not add a speculative Events submodule or repoint another plugin's entry. If the network later adopts EnthusiaEvents, its module/pin must go through a separate reviewed change and combined-build verification.

## Remaining release gates

1. A maintainer approves the upstream workflow; inspect its result and any new review findings for the exact PR head. Fork CI is supporting evidence only.
2. After the user lifts the hold, execute the relevant Java/Bedrock cases in playtest.md and record actual inventory/location/world comparisons. Resolve map readiness failures before affected matches.
3. Obtain human review and explicit authorization to merge. Keep the fork validation mirror unmerged; only the authoritative repository owns the plugin release.
4. Record the exact merged commit. From a clean checkout of that commit, use Java 25 and `mvn --batch-mode --no-transfer-progress clean verify`. Confirm the target runtime/API and companion integrations, then record build/CI results, descriptor/Maven versions and SHA-256. The existing feature-branch TEST JAR is not a production artifact.
5. Recheck monorepo ownership, if any. Production upload/activation requires its own authorization and receipt proving the running server loaded the intended build.

Release notes are prepared in [release-notes.md](release-notes.md); they are an unpublished draft, not an announcement that fixes are live.

## Recovery boundaries

The earlier TEST installation has a verified stopped-server recovery backup and retained old JAR outside `plugins`. Private panel identifiers and full server paths remain in the task-local receipt rather than public repository documentation. A rollback, if later authorized, must gracefully stop the backend, retain new restoration files/logs, reconcile pending snapshots and reset work, restore the previous JAR/config, compare hashes, then start and verify. Do not overwrite outstanding player snapshots with old backups.

Arena reset retention is in memory. Abrupt process loss does not preserve a reset queue; traces are diagnostics, not a transaction journal. A durable reset journal, versioned inventory recovery, new reward integrations and additional games remain separate design work requiring behavioral proof. They are not silently included during the testing hold.
