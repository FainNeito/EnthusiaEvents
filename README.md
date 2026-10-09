# EnthusiaEvents

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/1ad33581df904ce7a288cfcf06d98e38)](https://app.codacy.com?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

EnthusiaEvents is a Paper/Leaf event plugin for running scheduled, voted, private, and staff-started minigame events on Enthusia Network. It manages event joining, map setup, player snapshots, custom scoreboards, map exports, runtime cleanup, and event-specific gameplay logic.

## Documentation

- [`docs/requirements.md`](docs/requirements.md), [`docs/implementation.md`](docs/implementation.md), [`docs/tasks.md`](docs/tasks.md), and [`docs/verification.md`](docs/verification.md) — proposed recovery, preflight, Speedbuilders and seasonal roadmap. These additions are not implemented; testing remains on hold.
- [`PLAYER_GUIDE.md`](PLAYER_GUIDE.md) — current player-facing mechanics and live Enthusia SMP deployment behavior. Use this as the primary source for future wiki/player documentation.
- [`Specifications.md`](Specifications.md) — original/development specification; some deployment values and older design statements may no longer match current code/config.
- [`TESTING.md`](TESTING.md) — implementation/testing coverage.

## Supported Events

- BedWars
- Block Party
- Boat Race
- Capture Players
- Capture the Flag
- Elytra Race
- Fight, Sumo, and Knockback FFA variants
- Horse Race
- One in the Chamber
- Parkour
- Quake
- Red Light Green Light
- SkyWars
- Spleef and Splegg

## Core Features

- Public event votes and staff-forced starts.
- Private staff-only event sessions with invited players.
- Per-event map setup and validation.
- Dedicated map/world export tools.
- Player snapshot restore for inventory, armor, health, max health, XP, flight, gamemode, potion effects, and location.
- Safe restore retry and emergency restore commands for stuck players.
- Event scoreboard override while players are inside events.
- Runtime cleanup for vehicles, projectiles, shops, generators, flags, checkpoints, particles, temporary blocks, and event items.
- Disabled-event controls and autostart controls for release safety.

## Main Commands

Player commands:

- `/event join`
- `/event leave`
- `/event spectate`
- `/event vote`
- `/event start`
- `/event stats`
- `/event next`

Admin commands:

- `/ee autostart <on|off|status>`
- `/ee disable <event>`
- `/ee enable <event>`
- `/ee enabled`
- `/ee disabled`
- `/ee status`
- `/ee private <event> [players...]`
- `/ee invite <player>`
- `/ee forcestart <event>`
- `/ee advance`
- `/ee forcestop`
- `/ee eventtp <event> [mapId]`
- `/ee restore <player>`
- `/ee retryrestores`
- `/ee stuckcheck <player>`
- `/ee emergencyrestore <player>`
- `/ee setup <event> <mapId>`
- `/ee setup save`
- `/ee map <create|list|tp|status|export|exportall|transfer|retarget|...>`
- `/ee map clear <event> <mapId> <section> [confirm]`

The standalone `/setup` command is intentionally not registered. Use `/ee setup` so setup access stays inside the admin command surface.

## Setup Data

Map setup and runtime configuration are stored under the plugin data folder. Map data should be treated as release data, not throwaway config, because it contains regions, spawns, checkpoints, generators, finish lines, release walls, shops, and other event-specific points.

Use `map clear` to redo one part of a configured map. Supported sections include `region`, `spectator`, `spawns`, `finishes`, `checkpoints`, `checkpoint-blocks`, `points`, `beds`, `shops`, `areas`, `chests`, `generators`, and `all`. Clearing `all` requires the final `confirm` argument and keeps the map ID and target world intact.

BedWars beds are configured from the team-tools page by clicking the actual placed bed. Its material and facing are saved and the complete bed is rebuilt before each match. Legacy marker-only bed entries must be configured again.

To reconfigure an existing map, use `/ee eventtp <event> <mapId>`, clear only the affected saved data, then reopen the palette with `/ee setup <event> <mapId>`. Save and exit with `/ee setup save`. For example:

- BedWars beds: `/ee map clear BEDWARS <mapId> beds`, then `/ee setup BEDWARS <mapId>`.
- Horse Race checkpoints: `/ee map clear HORSE_RACE <mapId> checkpoints`, then `/ee setup HORSE_RACE <mapId>`.
- Capture the Flag spawns: `/ee map clear CAPTURE_THE_FLAG <mapId> spawns`, then `/ee setup CAPTURE_THE_FLAG <mapId>`.
- Fight 1v1 complete setup reset: `/ee eventtp FIGHT_1V1 <mapId>`, `/ee map clear FIGHT_1V1 <mapId> all confirm`, then `/ee setup FIGHT_1V1 <mapId>`.

Before release:

- Confirm `/ee autostart status` is `off`.
- Disable any unfinished events with `/ee disable <event>`.
- Run private event tests with `/ee private <event>`.
- Use `/ee map status` to confirm event maps are in the intended worlds.
- Keep backups of worlds and the plugin data folder before copying to the live server.

## Restore Safety

Player snapshots are kept until they are successfully restored. Unrestored snapshots are not purged automatically. If a player is stuck or has a pending restore:

- Use `/ee stuckcheck <player>` to inspect event markers, metadata, snapshot state, max health, gamemode, vehicle state, and potion effects.
- Use `/ee emergencyrestore <player>` to clean event state and restore the saved snapshot if possible.
- Use `/ee retryrestores` after loading missing worlds or fixing teleport issues.

## Building

Requirements:

- Java 26
- Maven
- Paper/Leaf-compatible server API for Minecraft 1.21+

Build:

```bash
mvn test
mvn package
```

The compiled plugin jar is written to `target/`.

## Release Notes

This plugin touches inventories, worlds, vehicles, scoreboards, combat behavior, economy rewards, and player restore state. Always test event join, leave, forced stop, relog restore, plugin restart, and emergency restore before deploying a new jar to the live server.
