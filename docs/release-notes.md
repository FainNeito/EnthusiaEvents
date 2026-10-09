# Unreleased notes — PR #15

Prepared for review only. The PR is unmerged, player acceptance is on hold, and production has not changed. Do not publish this draft as a live changelog.

## Fixes

- Capture carrier targeting now handles captive-intercepted attacks while retaining combat restrictions.
- Capture cleanup clears stale ownership before dismount and handles offline participants; post-teleport inventory refresh is fenced to the current session. Client equipment visibility still needs acceptance.
- Leave/spectate bracket withdrawal shares the manager path. Spectating also records departure and completes eligible empty/last-survivor sessions.
- Failed arena resets retain pending work, block new starts and expose a staff retry. Successful status displays `failure: none`.
- Transient trace-file errors count dropped records without permanently terminating the writer.

## Tools and optional gameplay

- `/event browse` and `/event schedule` show configured availability and voting state.
- `/ee readiness <EVENT>` shows saved map requirements and repair guidance.
- `/ee loot` inspects/samples tables and permits validated staff edits and optional stack-entry caps. Existing metadata and zero-cap balance are preserved.
- `/ee trace [session-uuid]` exposes bounded recent diagnostics; `/ee resetarena` retries retained arena work.
- Opt-in Runner collapses tracked floor blocks beneath moving participants and uses the existing elimination/restoration lifecycle. It remains absent from the default enabled list.

Staff commands keep `enthusia.events.admin`; existing aliases, economy amounts and restoration semantics remain. Command usage text includes the new tools. No extra library/runtime dependency or automatic Discord announcement is introduced.

## Acceptance and limitations

The corrected implementation previously passed 31 tests on the declared and TEST API profiles, Codacy, and the existing workflow on a fork. Upstream CI still requires maintainer approval. TEST startup and an empty private Runner session passed smoke checks before the hold; full matches, menus, inventory restoration and Java/Bedrock targeting/rendering remain pending. Arena reset retention does not survive an abrupt process crash.

See [release-readiness.md](release-readiness.md) for provenance and release gates, [source-status.md](source-status.md) for remaining concerns, and [implementation.md](implementation.md) for commands/configuration.
