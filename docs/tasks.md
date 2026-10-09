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

- [ ] Review all 39 Codacy findings and correct applicable maintainability issues; document narrowly scoped false-positive/test-fixture exceptions.
- [ ] Enable ready-for-review status and inspect the resulting CodeRabbit review.
- [ ] Re-run canonical verification after refinement and inspect exact-head checks.
- [ ] Prepare an isolated TEST playtest plan with rollback, fixture setup and explicit client acceptance evidence.
