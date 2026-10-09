# Source status and deferred acceptance

Current PR #17 implements recovery/progression and includes unmerged PR #15. Canonical and explicit stable API clean verification each passed 56 tests and PMD. Local testing/builds resumed; reviewed merging is authorized, while deployment/client acceptance remain separate. See [roadmap-work.md](roadmap-work.md) for current behavior and limits. Upstream workflow approval and merge require write access unavailable to the connected account.

The dated evidence below describes the earlier PR #15 delivery; its testing-hold statements are historical.

This matrix reconciles the prior playtest audit, authoritative main and PR #15. Original private Discord evidence remains task-local. Source protections and automated regression evidence are distinct from deployment and player acceptance. Testing is currently on hold.

| Concern or recommendation | Source status | Remaining boundary |
| --- | --- | --- |
| Guild-vault duplication / event item escape | Existing `EventDurabilityListener` and `EventGameplaySafetyListener` cover tracked participants/spectators, aliases and persistent inventories; snapshot restoration is preserved | Real guild aliases, world transfers, inventory paths and restore acceptance |
| Captive intercepts carrier attacks | PR adds eligible-carrier forwarding through normal combat listeners in `CaptureAttackListener` | Java/Bedrock hitboxes, reach/visibility, immunity and friendly-fire acceptance |
| Stale captive ownership / repeated-round equipment | PR clears reverse ownership before dismount, handles offline round cleanup and fences post-teleport inventory synchronization | Four-round client equipment rendering, carrier/middle-captive departures and restoration |
| Solo/team bracket withdrawal | PR centralizes leave/spectate withdrawal and active completion in `EventManager`; quit protections already exist | Multiplayer queue/forfeit transitions and commands cancelled by other plugins |
| Failed arena restoration / reuse | PR retains failed/unattempted block states and gates all starts in `ArenaResetService` and manager entry points | Real tile/container/fluids restoration, reset cost and failure/retry acceptance; no crash journal |
| Map readiness / setup warnings | PR provides a read-only paged readiness view with existing validation in `MapSetupService` | Actual arena geometry and beds must be configured by staff; reported TEST BedWars/Hot Potato/Spleef setup remains incomplete |
| BedWars hunger/durability/crafting/team expectations | Existing gameplay guards and setup requirements remain; no team-mode or resource-sharing policy is introduced | Bed destruction, shop/generator gameplay and inventory acceptance; confirm intended solo/team policy before extending |
| Elytra/Horse/Boat Race and spawn orientation | Existing checkpoint, simulated death, spawn and recovery paths preserved | Actual yaw/pitch, course restrictions, collisions/sinking and full matches |
| Loot editing, samples and optional caps | PR adds atomic metadata-preserving writes, validation and event/reset edit gates; zero caps retain existing balance | Client/menu permissions, extraction attempts and measured balance; no speculative default caps |
| Player browsing and schedule visibility | PR adds current configuration-derived information and hides private-session identity | Java/Bedrock menu rendering/navigation; schedule indicates voting availability rather than guaranteed event starts |
| Diagnostics | PR adds bounded memory/file trace; reviewed IO recovery continues after failed records with counted drops | Real shutdown/rotation behavior under load; trace cannot recover inventories or arena state |
| Runner | PR adds opt-in tracked floor collapse, session-fenced callbacks and existing last-survivor restoration path; absent from default enabled list | Real movement/PvP/timeout/elimination/reset acceptance; TEST's isolated setup and empty session are only smoke evidence |
| TEST bounty/Discord spam | Existing integration hooks loaded in prior TEST startup; no new webhook added | Real kill/outcome cross-plugin acceptance and confirmed TEST destination isolation |
| Speedbuilders, Paint Panic, durable recovery and typed rewards | Deferred; not implemented in this PR | Dedicated requirements, policy decisions and meaningful regression/runtime evidence |

No unresolved review finding currently supplies a supported additional gameplay change that can be shipped without its own proof. During the hold, preserve the verified implementation and finish documentation/release preparation. Remaining runtime-dependent work stays explicitly pending.
