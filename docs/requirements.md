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

Scope decisions: preserve hourly voting; no arbitrary weekly calendar or new payout policy. Runner is the recommended first new game; Speedbuilders/Paint Panic remain later alternatives rather than simultaneous replacements. No direct Surf inventory-restore transplant, dummy-entity dependency, automatic Discord webhook or guild-XP policy is introduced.

Evidence: task-local Discord playtest audit and static SurfEvents inspection, retained outside this repository. Public source findings and regression proof are recorded in verification.md.
