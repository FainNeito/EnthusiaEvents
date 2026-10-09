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

Draft PR #15 is open in wsg138/EnthusiaEvents. Initial implementation head `a4fc9b3` has local clean verification. Hosted Quality run `37880807816` is `action_required`; CodeRabbit skipped the draft, and no review submissions/threads were present at inspection. Maintainer CI approval and independent review remain delivery gates, not passing checks.

Runtime acceptance: carrier targeting/armor rendering on Java and Bedrock, full donor matches, staging reset cost and artifact activation are separate remaining gates. No production mutation authorized.

## Follow-up 2026-10-09

- [x] Review all 39 Codacy findings and correct applicable maintainability issues; document narrowly scoped false-positive/test-fixture exceptions.
- [x] Enable ready-for-review status and inspect the resulting CodeRabbit review.
- [x] Re-run canonical verification after refinement and inspect hosted checks for the refined head.
- [x] Prepare an isolated TEST playtest plan with rollback, fixture setup and explicit client acceptance evidence.

CodeRabbit's first completed review identified three applicable findings: spectate withdrawal misses evidence/last-survivor completion; reset status should say `none`; the trace writer should recover after transient file errors. Establish focused withdrawal/file-recovery regressions before correcting those paths. TEST stays stopped until its staged candidate includes these fixes.

All three findings are corrected; three focused assertions fail on the reviewed implementation and pass here. Canonical and TEST-runtime API clean verification each pass 31 tests. Inspect new-head hosted checks after pushing; upstream workflow approval remains unavailable to this account. Java/Bedrock acceptance remains pending.
