# Events playtest improvements

Base: authoritative main `58d4a6dea746c4a51dc4999230d6107b93ed672f`. User authorizes implementing applicable fixes and recommended features; production remains read-only. SurfEvents 3.2.4 is static reference only, not copied source or runtime proof.

- EVT-001: When a player attacks a carried captive, the system shall target the active carrier using normal combat rules and preserve prisoner immunity. Friendly fire, jailed attackers and spectators shall remain blocked.
- EVT-002: When a Capture Players round ends or a participant leaves, the system shall detach stale passenger state before teleport/equipment reset and remove all carry ownership for departing players.
- EVT-003: While an arena reset is pending, the system shall reject starts and expose reset completion/failure without discarding recoverable work.
- EVT-004: When staff inspect readiness, the system shall show saved map data, validation failures and relevant repair instructions without mutating map data.
- EVT-005: When staff edit loot, the system shall validate chances/quantities, preserve existing metadata, prevent edits during events and persist changes before reporting success. A sample preview shall show actual rolls and configurable equipment limits.
- EVT-006: When a session changes state, the system shall record a bounded trace with session ID, event/map, phase, participants, restoration outcomes and reset status. Routine logs shall not contain secrets or inventory contents.
- EVT-007: When players browse events, the system shall show current availability, descriptions and configured settings, and show scheduled votes honestly without revealing private sessions.
- EVT-008: The system shall preserve existing commands, permissions, economy amounts, config semantics and success-aware player restoration.
- EVT-009: The system shall encapsulate reusable game/cleanup rules outside the monolithic listener and provide a disabled-by-default Runner mode with tracked block removal and complete restoration.
- EVT-010: Every new GUI shall have an accurate interactive preview with clickable mobile-accessible delivery or an explicitly reported delivery limitation. Client rendering and gameplay acceptance shall remain separate from automated checks.
- EVT-011: Command usage and delivery records shall describe the implemented commands and current evidence accurately, distinguish historical checks from current checks, and honor an explicit hold on testing.

Scope decisions: preserve hourly voting; no arbitrary weekly calendar or new payout policy. Runner is the recommended first new game; Speedbuilders/Paint Panic remain later alternatives rather than simultaneous replacements. No direct Surf inventory-restore transplant, dummy-entity dependency, automatic Discord webhook or guild-XP policy is introduced.

## Roadmap implementation follow-up

The renewed request authorizes draft source implementation; the earlier testing hold remains active. [PR #16](https://github.com/wsg138/EnthusiaEvents/pull/16) defines the full REC/SNAP/PREF/BUILD/SEAS roadmap. This branch implements the first bounded slices below and does not claim the full roadmap or its behavioral proof is complete.

- ROAD-001: Before confirmed event capture succeeds, the system shall acknowledge atomic snapshot persistence; on failure it shall restore the prior in-memory record and reject entry before temporary inventory changes.
- ROAD-002: When history is enabled, the system shall write immutable per-player capture versions with identity/integrity metadata and expose inspection only; unreadable legacy storage shall remain intact and block new capture.
- ROAD-003: When durable arena recovery is enabled, the system shall capture ordinary block data before PRESTART mutation, retain a global lease until complete verified replay, quarantine unfinished startup records, and reject every unsupported tile entity.
- ROAD-004: When staff run preflight, the system shall inspect bounded loaded-world points without generating chunks/worlds or repairing geometry. Opt-in activation enforcement shall recheck before ACTIVE; recovery and Speedbuilders require these checks.
- ROAD-005: When an explicitly enabled private Speedbuilders session starts, the system shall validate equivalent isolated plots and a supported template, run bounded reset/memory/build/scoring phases, share tied results and grant no currency.
- ROAD-006: When explicitly configured production season progress is enabled, the system shall pin an eligible public session to its ACTIVE-time season, persist a unique completion ledger, exclude private/test/cancelled outcomes, preserve lifetime stats and expose read-only counts.
- ROAD-007: While testing is held, this source shall remain an uncompiled, unverified draft without a JAR or server activation; future behavioral proof, client acceptance and release gates remain open.

Evidence: task-local Discord playtest audit and static SurfEvents inspection, retained outside this repository. Public source findings and regression proof are recorded in verification.md.
