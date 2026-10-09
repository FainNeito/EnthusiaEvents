# Isolated TEST acceptance

Status on 2026-10-09: player/client testing is on hold at the user's request. Do not start sessions, invite testers, change TEST fixtures or restart servers while this hold is in effect. The earlier startup and empty-session smoke evidence is historical; it does not mark any acceptance case below as passed. See release-readiness.md for the source and artifact state.

This is an unmerged test build. It is not a production release. Record the exact source commit and SHA-256 before upload, and verify the running plugin after a controlled TEST restart. Do not copy production databases or enable Discord integrations for this exercise.

## Preparation and rollback

Inventory TEST player count, runtime build, current event plugin JARs, event config, maps and session restoration files. Stop an active event normally and verify there are no pending player/arena restores before replacing a build. If TEST contains unresolved inventory snapshots or reset work, pause installation until they are resolved. Stop the backend with no players, take a verified recovery snapshot, retain the previous plugin JAR outside `plugins`, and replace only EnthusiaEvents. Preserve the existing configuration/maps and keep `schedule.enabled: false`. Check other TEST plugins for active Discord or shared economy/database writes before bringing up the test build.

For rollback, gracefully stop TEST, preserve new restoration files/logs, restore the previous JAR/config and compare checksums. Do not overwrite pending snapshots with a backup without reconciling them. Start and verify the previous version only after the retained recovery state is safe.

## Acceptance cases

Use disposable test accounts with known inventory, armor, offhand, effects, health, XP, flight and return location. Record those before joining and compare after every leave, quit, spectate and forced stop. Use existing saved arenas first; staff must confirm `/ee readiness <EVENT>` before starting.

| Case | Action | Required observation |
| --- | --- | --- |
| Capture targeting | Enemy Java and Bedrock clients strike carrier through captive; also attempt friendly fire, jailed attacks, out-of-reach and blocked line of sight | Carrier receives normal allowed combat; captive stays immune; forbidden attacks stay blocked |
| Capture cleanup | Play four rounds; leave/disconnect carrier and middle captive; rescue, then forced stop | No canceled cleanup dismount or stale carry owner; all snapshots restore; clients confirm armor/shields visible |
| Bracket withdrawal | Solo and team Sumo/Fight: typed leave, spectate, GUI/API withdrawal and normal disconnect; repeat with command canceled by another plugin | Advance once; correct opponent/queue; canceled command preserves contestant |
| Arena reset | Spleef/SkyWars/Runner edit floor/chests, force stop and attempt a queued/manual start during batched reset | Complete blocks/container restoration; new starts blocked until reset completes |
| Reset failure | Use only a disposable test-world fixture to cause a failed block update; retry `/ee resetarena` | Failed/unattempted states retained; all start paths blocked; successful retry clears gate. Do not corrupt a shared world to manufacture failure |
| Menus | Open browser, schedule, missing/valid map readiness and paged loot; try shift click, drag, number keys and permission revocation | No item extraction or unauthorized navigation; private identity hidden; disabled voting shown honestly |
| Loot persistence | Change a disposable table entry with enchantments/custom keys; invalid min/max/chance; configure/reset caps; compare sample rolls | Metadata preserved; invalid writes rejected; edits blocked mid-event/reset; zero caps retain legacy behavior |
| Diagnostics | Query `/ee trace`, rotate logs, stop TEST normally | Session IDs/phases and restore outcomes recorded; bounded files; no inventory data; orderly flush |
| Runner | Add RUNNER to TEST-only enabled list, reload with no active event, create isolated region/spawns/spectator/breakable-area; private match only | Only floor/region intersection collapses after four ticks; no PvP; elimination/timeout works; late callbacks cannot alter next match; floor/inventory restored |

Runner and loot caps stay out of public rotation until accepted. A process crash does not preserve the in-memory arena reset queue. Paper startup/console checks do not prove Java/Bedrock hitboxes, equipment visibility or player acceptance.

## Evidence receipt

For each case record date, TEST server ID, runtime build, source SHA, artifact SHA-256, session UUID, client edition/version, tester, result, relevant log lines and restoration comparison. Leave unexecuted cases as `pending`, never `passed`. Local Mockito regression results are tracked separately in verification.md.
