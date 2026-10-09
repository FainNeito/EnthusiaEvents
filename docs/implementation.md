# Implementation and operating guide

Changes are independent implementations informed by SurfEvents concepts. No decompiled third-party implementation or runtime dependency is included.

## Fixes

- Capture attacks intercepted by an invulnerable captive target the carrier through `Player.attack`, retaining normal damage listeners. Paper initially cancels invulnerable targets, so the listener intentionally handles those events; it respects explicit cancellation of otherwise valid attacks. Participation, spectator, distance and visibility checks apply.
- Carry ownership is cleared before cleanup emits dismount events. Offline ownership is removed on round reset. Departures detach captive chains before player snapshot restoration. Round resets respawn each participant once. Inventory synchronization is refreshed after successful teleport with a session/participation fence; invisible armor remains a client retest item.
- Leave/spectate resolve bracket withdrawal in the manager. Main already covered normal typed commands with a preprocess hook. That hook is removed to avoid withdrawing a contestant before another plugin cancels a command; API and GUI callers now use the same operation.
- Arena reset retains a failed head and unattempted states on false/throwing block updates. All entry points reject pending resets, queue starts wait, and `/ee resetarena` retries when no session is active. This is memory retention and orderly-disable completion, not crash recovery across process loss.

## New tools

| Command | Behavior |
| --- | --- |
| `/event browse` | Read-only event descriptions, availability, minimum/ready players, duration and configured winner reward. Click for details. |
| `/event schedule` | Hourly vote information; explicitly shows when automatic votes are off and never promises an event start. |
| `/ee readiness <EVENT>` | Read-only saved map summary, validation errors, repair commands and pagination. |
| `/ee loot <SKYWARS\|BEDWARS> <1-3>` | Read-only paged table; actual sample rolls include original item metadata and cannot be taken. |
| `/ee loot SKYWARS 3 edit <id> <min> <max> <chance>` | Persist validated quantities/chance before reloading; preserve other keys, effects and enchantments. Chance is 0–1, amount 1–64. |
| `/ee loot SKYWARS 3 limits <armor> <weapons> <total>` | Per-roll entry limits. Zero means unlimited. Example: `2 2 12`. |
| `/ee trace [session-uuid]` | Last 12 records from bounded in-memory diagnostics, with full current/previous trace files under `logs/`. |
| `/ee resetarena` | Retry retained arena work and show pending count/failure. |

Staff tools require `enthusia.events.admin`, including permission rechecks when navigating. Loot writes are blocked during any event or pending reset. Samples show the first 45 entries and disclose the total if a custom table rolls more. Caps count entry stacks, not armor slots or item quantity. Loot tier fallback uses tier 1's limits. Caps are opt-in, preserving existing server balance until staff configure them. Atomic replacement failure leaves the old configuration active and reports failure.

Traces store session/event/map/phase, lifecycle actions and player UUIDs; they contain no inventory contents or secrets. The file queue is bounded to 2,048 and recent memory to 256 records. Current and previous logs are each approximately 2 MB. File errors leave recent memory available and are reported; logs are diagnostics, not an authoritative transaction journal. Public browser/schedule views never disclose private-session identity, invitees or its phase.

## Optional Runner

Runner is absent from the default `events.enabled` list. To test it, staff must add `RUNNER`, reload while no event is active, and create a map with region, spawns, spectator and a `breakable-area` covering floor blocks. Use existing `/ee map create`, `/ee setup RUNNER <mapId>`, area tools, save and `/ee readiness RUNNER`.

Floor blocks underneath moving participants disappear after four ticks. Only the intersection of the floor area and arena region may change. Original block state is recorded before removal; delayed tasks are fenced to the same active session. PvP is blocked, hunger is locked, falling below the region eliminates players, and the existing last-player-standing/outcome/snapshot path applies. Private Runner matches and restoration must pass before adding it to public rotation. Speedbuilders now has an unverified private source implementation described in roadmap-work.md; Paint Panic remains a future option.

## Architecture and preview

Pure limits/validation live in `domain`. Paper event listeners, file diagnostics, inventory views and command tooling live in `infrastructure`; existing manager/service constructors remain compatible. Snapshot capture now requires acknowledged persistence, as described in roadmap-work.md. Existing payout values are retained; private Speedbuilders has no currency payout. No new external companion-plugin API calls are introduced.

The interactive preview source is `docs/events-preview.html`. Its remote Page link is https://chatgpt.com/space/page_ec6b2bdcbb588191886cfbdb36dbff6d (owner ChatGPT login required). It uses sample maps, menu state and rolls; it does not run a server. Responsive slot reflow represents the same actions on narrow displays rather than claiming Minecraft pixel geometry. Java/Bedrock rendering and gameplay acceptance remain separate.
