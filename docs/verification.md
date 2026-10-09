# Verification

## Baseline/source proof

Current main: `58d4a6dea746c4a51dc4999230d6107b93ed672f`, fetched again before delivery. Existing main suite: 11 tests passed. Regression harness uses Mockito 5.20.0, added only as a test dependency.
Capture damage handler rejects carried targets; direct mounting can intercept client targeting of carrier. Equipment resets occur before async teleport completion and repeated-round cleanup can leave reverse carry ownership for offline players. ArenaResetService.reset returns void and pending work has no start gate. These are source-supported failure paths; invisible equipment/client hitbox symptoms are not independently reproduced.

Existing source already addresses jail containment, equipment durability, guild aliases, bracket quits, flight and race recovery. Preserve those defenses and add targeted checks rather than rewriting based on old reports.

## Results

Three regression tests were separately executed against main's unchanged implementation and failed with assertions, without compilation/fixture errors: cleanup dismount cancellation, reverse carry ownership after offline round reset, and direct manager bracket withdrawal. The same tests pass with these changes. The bracket test exercises the manager API; main already handled typed commands via a preprocess hook. Withdrawal now happens in the actual operation and the early hook is removed, preserving cancellation by other plugins.

Additional coverage exercises intercepted captive attacks and explicit cancellation, spectator denial, block update false/throw/retry and all start gates, loot cap validation, metadata-preserving atomic writes and failed replacement, bounded trace flush/file failure, Runner opt-in and record-before-removal/session-end fences. Mockito tests exercise adapter paths; they are not a real Paper server or client simulation.

## Local checks

- Canonical `mvn --batch-mode --no-transfer-progress clean verify`: 28 tests, zero failures/errors/skips; compile, package and configured command PMD checks passed.
- JDK 25.0.3, Maven 3.9.11, repository's configured Paper API `26.2.build.39-alpha`.
- `git diff --check` clean. One API profile is declared; no additional profile is claimed.
- Manual review checked reset queue retention, default balance preservation, command permissions, deferred inventory navigation, private-session identity exposure, existing snapshot behavior and optional Runner wiring. No new companion-plugin API dependency.
- No project-local EARS validator/state helpers were present. Requirements/task/evidence records are manual; no automated SPEAR tooling pass is claimed.

## Interactive preview

Saved and read back: https://chatgpt.com/space/page_ec6b2bdcbb588191886cfbdb36dbff6d . This private Page requires the owner's ChatGPT account. Source: `docs/events-preview.html`.

The locally rendered sandbox preview was exercised through browser navigation and 360 px iframe layout. Readiness controls measured approximately 108 by 58 px; content width matched viewport without horizontal overflow. Remote Page/embed save and owner access were confirmed through Pages. The browser was logged out of ChatGPT, so authenticated remote rendering and actual phone acceptance remain unverified. Fixture maps/rolls/settings are simulated; responsive slot reflow is not a Minecraft screenshot.

## Hosted and runtime gates

Hosted PR checks and review status are recorded after PR creation. A local passing build is not hosted CI or review approval. No server files, production settings, restarts or running versions were changed. The local test JAR is an unmerged review artifact, not a production release.

Before release, verify merge/commit, canonical clean build, monorepo dependency pin where applicable, version/hash and activation evidence. Stage and playtest:

1. Java and Bedrock: attack carrier through captive, friendly fire, jailed attackers, rescue carriers and reach/visibility. Play at least four capture rounds; inspect armor/shield visibility, jail/free actions, middle-of-chain leave, carrier disconnect and forced stop. Client targeting/rendering is still unproven.
2. Sumo/Fight solo and team brackets: direct manager and typed leave/spectate, spectator placement, normal quit and commands cancelled by another plugin. Advance only once; retain winner/queue.
3. Spleef/SkyWars/Runner: track floor/chest edits, batched reset, block-update failure/retry, start rejection and delayed queue resume. Verify complete world state restoration. Retained memory does not survive a process crash.
4. Staff tooling: revoked permission, full/missing-map pages, loot metadata, invalid numbers, edits blocked mid-event, sample counts and real probability/cap balance. Configure caps deliberately; zero retains legacy unlimited behavior.
5. Runner private match: floor bounds, jump/standstill behavior, last survivor, timeout, late tasks, block restoration, original inventories and restart/stop recovery. Keep it out of public rotation until accepted.
6. Logs: session IDs/phases, failed and successful restores, reset status, unwritable/rotated files and shutdown flush. No inventory contents or secrets.
