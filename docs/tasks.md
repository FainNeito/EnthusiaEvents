# Tasks

SPEAR order: spec -> prove -> engine -> arch -> refine. No project-local EARS/state tooling exists; requirement/state evidence is maintained manually and no automated SPEAR pass is claimed.

- [x] Fetch authoritative main and preserve clean audit checkout in isolated worktree.
- [x] Read source, player guide, original specification and acceptance checklists.
- [x] Specify behavior/compatibility and live boundaries.
- [x] Prove applicable failure paths using source analysis and meaningful regression tests.
- [x] Implement EVT-001 through EVT-009.
- [x] Review platform boundaries, permissions, persistence/failure handling and private-session visibility.
- [x] Canonical clean verify, configured API checks, interactive GUI preview and self-review.
- [x] Push reviewable PR; inspect exact-head hosted checks and review findings.

PR #15 is open and ready for review in wsg138/EnthusiaEvents. Initial head `a4fc9b3` had passing local checks and an approval-blocked upstream workflow; CodeRabbit initially skipped the draft. Those are historical states. Current evidence and remaining gates are in release-readiness.md.

Runtime acceptance: carrier targeting/armor rendering on Java and Bedrock, full donor matches and staging reset cost remain pending. An unmerged candidate was activated on TEST before the testing hold. Production has not changed.

## Follow-up 2026-10-09

- [x] Review all 39 Codacy findings and correct applicable maintainability issues; document narrowly scoped false-positive/test-fixture exceptions.
- [x] Enable ready-for-review status and inspect the resulting CodeRabbit review.
- [x] Re-run canonical verification after refinement and inspect hosted checks for the refined head.
- [x] Prepare an isolated TEST playtest plan with rollback, fixture setup and explicit client acceptance evidence.

CodeRabbit's first completed review identified three applicable findings: spectate withdrawal misses evidence/last-survivor completion; reset status should say `none`; the trace writer should recover after transient file errors. Focused regressions were established before correcting those paths; all three review threads are resolved.

All three findings are corrected; three focused assertions failed on the reviewed implementation and passed after correction. Canonical and TEST-runtime API clean verification each passed 31 tests on `fca7f5a`. Codacy and exact-source fork CI passed on that commit; upstream workflow approval remains unavailable to this account. Java/Bedrock acceptance remains pending.

## Testing hold and non-runtime work

- [x] Re-fetch current main and preserve the clean PR source base.
- [x] Reconcile task/verification state with completed review and historical TEST activation.
- [x] Confirm all three review threads remain resolved.
- [x] Review current network build ownership; no EnthusiaEvents entry exists in its current main tree.
- [x] Add source-status matrix, standalone release/rollback gates and unreleased notes.
- [x] Include new commands in existing usage text; preserve handlers, aliases and permissions.
- [x] Mark testing on hold without changing server files or claiming new acceptance.
- [ ] Obtain human review and upstream workflow approval.
- [ ] Resume client/playtest cases only when the user lifts the hold.
- [ ] Merge and produce a canonical release only after the remaining gates and explicit authorization.

## Draft roadmap source implementation

- [x] Create a separate current-main-based branch and explicitly include unmerged PR #15 dependency.
- [x] Add loaded-world preflight, confirmed snapshot capture/archive, ordinary-block arena recovery, private Speedbuilders and season-count source slices.
- [x] Inspect domain/platform boundaries, opt-in defaults, failure retention, start fences and legacy reset ordering in source.
- [x] Deliver fixture-only interactive states; saved Page readback and local phone-width layout inspection are distinct from client acceptance.
- [x] Compile, focused failure regressions and supported API verification after the hold was lifted.
- [x] Supported container codecs, historical apply/reconciliation, retention and seasonal personal-best/cosmetic slices implemented below.
- [ ] Exact-head CI, code/human review, disposable crash proof, Java/Bedrock and donor acceptance.

See roadmap-work.md for actual implementation scope and remaining boundaries. Historical passing results above apply only to PR #15, not this new source.

This follow-up changes documentation and command help only. Spec/source inspection and text consistency checks apply; behavioral proof and engine implementation do not apply because no gameplay or persistence behavior changes. No local test/build or server action is performed during this follow-up. Existing PR workflows may run automatically when the branch is updated; their results are recorded separately.

## Completion pass

- [x] Supported container codec and automated replay verification.
- [x] Historical preview/confirm, undo and durable uncertainty reconciliation; resolved-only retention.
- [x] Seasonal participation/grace, personal bests, owned cosmetic selection.
- [x] Regression proof and canonical/API matrix: 56 tests and PMD on each profile.
- [x] Inspect exact-head static analysis and fork hosted CI; engine fe00627 passes with zero new issues.
- [ ] Obtain maintainer review/upstream workflow approval and merge authorized source; account is read-only.
- [x] Refresh interactive preview and confirm saved Page readback.

Local testing hold lifted by renewed request; no production action authorized.

# Tasks

### TASK-SOUND-001 — Exact custom event support
**References:** REQ-SOUND-001
**Evidence:** Existing enum-only paths reject reviewed enthusia_theme events. Behavioral routing test added; red/green results pending. Preserve installed TEST feature commits in a separate combined TEST branch; source PR remains focused.
