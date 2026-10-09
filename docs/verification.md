# Verification

## Baseline/source proof

Current main: `58d4a6dea746c4a51dc4999230d6107b93ed672f`, fetched again before delivery. Existing main suite: 11 tests passed. Regression harness uses Mockito 5.20.0, added only as a test dependency.
Capture damage handler rejects carried targets; direct mounting can intercept client targeting of carrier. Equipment resets occur before async teleport completion and repeated-round cleanup can leave reverse carry ownership for offline players. ArenaResetService.reset returns void and pending work has no start gate. These are source-supported failure paths; invisible equipment/client hitbox symptoms are not independently reproduced.

Existing source already addresses jail containment, equipment durability, guild aliases, bracket quits, flight and race recovery. Preserve those defenses and add targeted checks rather than rewriting based on old reports.

## Results

Three regression tests were separately executed against main's unchanged implementation and failed with assertions, without compilation/fixture errors: cleanup dismount cancellation, reverse carry ownership after offline round reset, and direct manager bracket withdrawal. The same tests pass with these changes. The bracket test exercises the manager API; main already handled typed commands via a preprocess hook. Withdrawal now happens in the actual operation and the early hook is removed, preserving cancellation by other plugins.

Additional coverage exercises intercepted captive attacks and explicit cancellation, spectator denial, block update false/throw/retry and all start gates, loot cap validation, metadata-preserving atomic writes and failed replacement, bounded trace flush/file failure, Runner opt-in and record-before-removal/session-end fences. Mockito tests exercise adapter paths; they are not a real Paper server or client simulation.

## Local checks

- Earlier canonical `mvn --batch-mode --no-transfer-progress clean verify`: 28 tests, zero failures/errors/skips. The corrected implementation `fca7f5a` subsequently passed 31 tests; see refinement below.
- JDK 25.0.3, Maven 3.9.11, repository's configured Paper API `26.2.build.39-alpha`.
- `git diff --check` clean. One API profile is declared; an additional explicit TEST runtime API override was verified later, as recorded below.
- Manual review checked reset queue retention, default balance preservation, command permissions, deferred inventory navigation, private-session identity exposure, existing snapshot behavior and optional Runner wiring. No new companion-plugin API dependency.
- No project-local EARS validator/state helpers were present. Requirements/task/evidence records are manual; no automated SPEAR tooling pass is claimed.

## Interactive preview

Saved and read back: https://chatgpt.com/space/page_ec6b2bdcbb588191886cfbdb36dbff6d . This private Page requires the owner's ChatGPT account. Source: `docs/events-preview.html`.

The locally rendered sandbox preview was exercised through browser navigation and 360 px iframe layout. Readiness controls measured approximately 108 by 58 px; content width matched viewport without horizontal overflow. Remote Page/embed save and owner access were confirmed through Pages. The browser was logged out of ChatGPT, so authenticated remote rendering and actual phone acceptance remain unverified. Fixture maps/rolls/settings are simulated; responsive slot reflow is not a Minecraft screenshot.

## Hosted and runtime gates

PR: https://github.com/wsg138/EnthusiaEvents/pull/15 . Initial head `a4fc9b3c7e5d868e8b4ed21bc0bb4ba3c4a8bc02` had upstream Quality run `37880807816`, completed as `action_required`, and a skipped draft review. The PR is now ready for review; CodeRabbit's three later findings were corrected and resolved. A resolved automated review is separate from human approval.

A local passing build is not hosted CI or review approval. The initial local artifact had Maven version `1.0.0-SNAPSHOT` and SHA-256 `f3ef5bf2b8a08fb27d081fd92f601dae210dcb13379b82aad39aae2cef0b4b5e`; it is historical. The corrected unmerged candidate was later activated on isolated TEST. Production was not changed. Exact current TEST artifact provenance is in release-readiness.md.

Before release, verify merge/commit, canonical clean build, monorepo dependency pin where applicable, version/hash and activation evidence. Stage and playtest:

1. Java and Bedrock: attack carrier through captive, friendly fire, jailed attackers, rescue carriers and reach/visibility. Play at least four capture rounds; inspect armor/shield visibility, jail/free actions, middle-of-chain leave, carrier disconnect and forced stop. Client targeting/rendering is still unproven.
2. Sumo/Fight solo and team brackets: direct manager and typed leave/spectate, spectator placement, normal quit and commands cancelled by another plugin. Advance only once; retain winner/queue.
3. Spleef/SkyWars/Runner: track floor/chest edits, batched reset, block-update failure/retry, start rejection and delayed queue resume. Verify complete world state restoration. Retained memory does not survive a process crash.
4. Staff tooling: revoked permission, full/missing-map pages, loot metadata, invalid numbers, edits blocked mid-event, sample counts and real probability/cap balance. Configure caps deliberately; zero retains legacy unlimited behavior.
5. Runner private match: floor bounds, jump/standstill behavior, last survivor, timeout, late tasks, block restoration, original inventories and restart/stop recovery. Keep it out of public rotation until accepted.
6. Logs: session IDs/phases, failed and successful restores, reset status, unwritable/rotated files and shutdown flush. No inventory contents or secrets.

## Refinement 2026-10-09

Fetched authoritative main again; base is unchanged. PR #15 is now ready for review. Codacy reported 39 issues: eight complexity findings, naming/literal/parameter style findings, six logger/per-loop heuristics, six test reflection findings and two J2EE thread findings. Split capture eligibility/reach, Runner floor eligibility/scheduling, command dispatch/editing and inventory navigation/rendering into smaller methods. Named slot/argument constants and renamed backing fields; page/tier parameters are no longer reassigned.

The trace API is named `recordSessionEvidence`, accurately describing the bounded persistent diagnostics rather than a severity-controlled logger. No log-level guard may discard this evidence. Test fixture reflection has a rule-specific exception with its rationale; widening legacy private production state for tests would weaken encapsulation. The daemon file writer has a rule-specific J2EE exception: this application is a Paper plugin, not a managed web application, and the worker handles strings/file IO only. No global quality threshold was relaxed.

Canonical clean verify after refinement passed all 28 tests and configured PMD. Interface behavior/menu appearance is unchanged; the existing interactive preview remains applicable. Current GitHub credential permissions were checked: upstream push/maintain/admin are false, so workflow approval cannot be performed by this account. Hosted checks and code review remain separate from the passing local build. The detailed isolated TEST procedure is in playtest.md; its runtime cases remain pending until actually executed.

Codacy subsequently passed with zero new issues at `b973a48`. The existing Quality workflow also passed on the fork (run `37882723803`, validation mirror PR FainNeito/EnthusiaEvents#1); upstream run `37882349592` still needs maintainer approval. The fork result does not fulfill upstream branch protection.

CodeRabbit completed its review of `b973a48` with three applicable findings. Added two spectate-withdrawal tests and a transient trace-file recovery test first: all three failed with assertions against the unchanged reviewed implementation (zero fixture/compile errors). Corrected shared leave/spectate completion/evidence, reset status wording and per-record IO recovery with one warning and counted drops. Canonical clean verify now passes 31 tests and configured PMD. TEST Paper API `26.2.build.128-stable` was also checked with 28 tests before this correction; the corrected head receives a fresh runtime-profile check before installation. Remaining player/client cases are explicitly pending.

The corrected source also passed a fresh clean verify with `-Dpaper.api.version=26.2.build.128-stable`: 31 tests, zero failures/errors/skips, configured PMD passed. TEST runtime is Paper 26.2 build 128; startup and real client acceptance remain distinct from this API check.

## Corrected source and TEST smoke evidence

Implementation commit `fca7f5a3960783a5d96f624f9518de9c9f1cced8` passed Codacy with zero new issues and fork Quality run `37883242465`. Upstream run `37883240850` is `action_required`. All three review threads were confirmed resolved on the follow-up inspection.

Before the testing hold, a checksum-verified candidate enabled on TEST with CombatLogX and NotBounties integrations. An empty private Runner session selected its isolated map, stopped normally and persisted two trace records with zero drops and reset pending=0. No players participated. Existing incomplete map setup was reported separately; no full game, inventory restoration, hitbox or rendering acceptance is claimed.

The user then put testing on hold. This follow-up reconciles documentation, release boundaries and command usage only. Current main was fetched and remained `58d4a6d`. No gameplay code, tests, dependencies, workflow, reward policy or default configuration changed; no local tests/builds or server actions were performed. Text review and `git diff --check` are appropriate to this scope. Existing automatic hosted checks on a documentation/help commit do not extend runtime acceptance.

## Roadmap source draft

The subsequent implementation request adds ROAD-001 through ROAD-007 source on a separate branch. No compilation, plugin tests, packaging or server/client actions were performed. The prior passing suite/API evidence does not apply to this new head. Source inspection and `git diff --check` are the only code checks claimed during the hold. Future failure/regression and runtime gates are in roadmap-work.md.

The [implementation-state preview](https://chatgpt.com/space/page_ccbed6c3c180819187bfc13e2457aabc) was saved and read back. Local fixture controls rendered at 360px, with preview root clientWidth=scrollWidth=328px. This is presentation inspection only. Owner login is required; authenticated phone access and real Minecraft rendering remain unverified.

## Completion verification 2026-10-09

Renewed request lifts local testing/build hold and authorizes reviewed merge, without server deployment. Java 25.0.3 and Maven 3.9.11 canonical clean verify passed 55 tests, zero failures/errors/skips, and PMD. Separate clean verify with Paper 26.2.build.128-stable passed the same 55 tests and PMD. Canonical declared profile remains 26.2.build.39-alpha.

Coverage: capture publication failure; post-application acknowledgement failure and restart isolation; malformed-history rejection; state/permission-bound historical application with persisted undo/no second application; lease generation/corruption/failed acknowledgement; archive tamper detection and resolved-only retention; supported container boundaries; arena disk reconstruction, identity/corruption/unloaded retry/replay verification; preflight budgets; scoring; seasonal idempotency/restart/exclusions/grace/policy corruption and cosmetic ownership. Paper serialization is mocked; native NBT round trips remain pending. Service/disk reconstruction is not real server process-kill proof.

Three assertion regressions were observed before correction: malformed historical health accepted (events-completion-malformed-red.log, 1 assertion failure), external builders blocked and stale round callback cancelling replacement (events-speed-red.log, 2 assertion failures). Task-local logs live under operations outside source. Fixture registry initialization errors are not product regression evidence. Current canonical/API logs: events-completion-canonical.log and events-completion-runtime-api.log. No server operations occurred.

No EARS/state helpers exist; manual requirement/task evidence applies. Exact-head CI/review, upstream merge and Paper/Java/Bedrock acceptance remain separate. Connected upstream account has pull-only permissions and cannot approve workflows or merge.

### Hosted analysis refinement

Head 8a817f5 passed fork Quality run 37899190229. Upstream Quality run 37898964358 requires maintainer approval. Codacy reported 121 additions; GitHub published only its first 50 annotations. CodeRabbit reports automatic review disabled for repositories below 10 stars; no completed review is claimed. Refined validators, confirmation/reconciliation paths, preflight traversal, capture/replay batches, season parsing and plot setup into smaller methods, named protocol constants and used narrow PMD exceptions only for required Bukkit/YAML null deletion or retired task references and independent mutable item stacks. No complexity threshold was waived.

Review also found initial idle-grace credit for a stationary online player. A focused regression failed with one assertion and zero errors (events-season-idle-red.log); participation now requires observed movement/rotation from the ACTIVE baseline. Recovery interaction isolation now caches journal state instead of rereading disk on every event; publication/acknowledgement preserves failure isolation. Canonical and explicit stable API clean verification passed 56 tests and PMD after refinement (events-completion-refinement.log and events-completion-refinement-runtime-api.log). Remaining static findings are rechecked on the new head before merging.

BadgersMC/enthusia-network current main was fetched read-only; its .gitmodules contains no EnthusiaEvents pin. This plugin currently owns its standalone build. Local 360px recovery-confirmation and victory-title fixtures were inspected and controls exercised; no clipped text was observed. Page storage/access readback is confirmed, but authenticated phone and Minecraft rendering remain unverified.

Hosted analysis on bd89149 fell to 67 additions; fork Quality 37900474095 passed. On 70b9a12 it fell to 26 additions. Final cleanup names remaining limits/fixture constants, makes fixture helpers private and imports Jupiter annotations explicitly, scopes lifecycle-null exceptions to retired references, and separates bounded plot writes. Main-thread-owned game maps have a specific UseConcurrentHashMap exception: changing their map implementation cannot make Paper API access thread safe. Source SuppressWarnings annotations do not alter runtime bytecode. All 56 tests and PMD passed on both local profiles after the gameplay/fixture refinement; the last change only removes a redundant source-only suppression. Exact-head hosted verification remains separately checked.
