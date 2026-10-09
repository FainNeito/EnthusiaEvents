package org.enthusia.events.audit;

import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventRegistry;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapSetupService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@SuppressWarnings({"PMD.UseConcurrentHashMap", "PMD.AvoidDuplicateLiterals"})
public final class EventSpecAuditRegistry {

    private final EnthusiaEventsPlugin plugin;
    private final EventRegistry eventRegistry;
    private final MapSetupService mapSetupService;
    private final Map<EventType, EventSpec> specs = new EnumMap<>(EventType.class);

    public EventSpecAuditRegistry(EnthusiaEventsPlugin plugin, EventRegistry eventRegistry, MapSetupService mapSetupService) {
        this.plugin = plugin;
        this.eventRegistry = eventRegistry;
        this.mapSetupService = mapSetupService;
        registerDefaults();
    }

    public void logStartupWarnings() {
        for (EventType type : eventRegistry.all().stream().map(definition -> definition.type()).toList()) {
            EventSpec spec = specs.get(type);
            if (spec == null) {
                plugin.getLogger().warning(type.name() + " has no event spec audit entry.");
                continue;
            }
            List<EventMap> maps = mapSetupService.mapsFor(type);
            if (maps.isEmpty()) {
                plugin.getLogger().warning(type.name() + " is enabled but has no map setup.");
                continue;
            }
            boolean usable = false;
            for (EventMap map : maps) {
                List<String> errors = mapSetupService.validate(map);
                if (errors.isEmpty()) {
                    usable = true;
                    continue;
                }
                plugin.getLogger().warning(type.name() + " map '" + map.id() + "' is incomplete: " + String.join(", ", errors));
            }
            if (!usable) {
                plugin.getLogger().warning(type.name() + " is enabled but no configured map passes validation. Required: " + spec.summary());
            }
        }
    }

    public EventSpec spec(EventType type) {
        return specs.get(type);
    }

    private void registerDefaults() {
        put(EventType.SPEEDBUILDERS, "private opt-in, plot-<id> areas/spawns, build-template area, durable ordinary-block recovery", false, false, "highest build score; tied winners", "durable baseline");
        put(EventType.SKYWARS, "spawn, spectator, region, tiered chests", true, true, "last player standing", "tracked blocks");
        put(EventType.BEDWARS, "team spawns, spectator, region, beds, shops, generators", true, true, "last team alive", "tracked blocks");
        put(EventType.FIGHT_1V1, "spawns, spectator, region", true, false, "last player standing", "restore players");
        put(EventType.FIGHT_2V2, "team spawns, spectator, region", true, false, "last team standing", "restore players");
        put(EventType.FIGHT_FFA, "spawns, spectator, region", true, false, "last player standing", "restore players");
        put(EventType.SUMO_1V1, "spawns, spectator, region", false, false, "last player on platform", "restore players");
        put(EventType.SUMO_2V2, "team spawns, spectator, region", false, false, "last team on platform", "restore players");
        put(EventType.SUMO_FFA, "spawns, spectator, region", false, false, "last player on platform", "restore players");
        put(EventType.KNOCKBACK_FFA, "spawns, spectator, region", false, false, "last player standing", "restore players");
        put(EventType.QUAKE, "spawns, spectator, region", false, false, "last player standing", "restore players");
        put(EventType.ONE_IN_THE_CHAMBER, "spawns, spectator, region", false, false, "last player standing", "restore players");
        put(EventType.CAPTURE_THE_FLAG, "team spawns, flags, spectator, region", true, false, "three captures", "restore players");
        put(EventType.CAPTURE_PLAYERS, "team spawns, jail zone, free zone, capture zone", true, false, "capture score", "restore players");
        put(EventType.BLOCK_PARTY, "spawns, spectator, region, color floor", false, false, "last player standing", "tracked floor");
        put(EventType.HOT_POTATO, "spawns, spectator, region", false, false, "last player standing", "restore players");
        put(EventType.SPLEEF, "spawns, spectator, region, breakable area", false, false, "last player standing", "tracked blocks");
        put(EventType.RUNNER, "spawns, spectator, region, breakable area", false, false, "last player standing", "tracked blocks");
        put(EventType.SPLEGG, "spawns, spectator, region, breakable area", false, false, "last player standing", "tracked blocks");
        put(EventType.RED_LIGHT_GREEN_LIGHT, "spawns, finish, spectator, region, light display", false, false, "survive to finish", "restore displays");
        put(EventType.BOAT_RACE, "spawns, finish, spectator, region", false, false, "finish order", "restore players");
        put(EventType.HORSE_RACE, "spawns, finish, spectator, region", false, false, "finish order", "restore players");
        put(EventType.ELYTRA_RACE, "spawns, finish, rings, spectator, region", false, false, "finish order", "restore players");
        put(EventType.PARKOUR, "spawns, checkpoints, finish, spectator, region", false, false, "finish order", "restore players");
    }

    private void put(EventType type, String setup, boolean kits, boolean loot, String winCondition, String resetBehavior) {
        specs.put(type, new EventSpec(setup, kits, loot, winCondition, resetBehavior));
    }

    public record EventSpec(String setup, boolean usesKits, boolean usesLoot, String winCondition, String resetBehavior) {
        String summary() {
            StringJoiner joiner = new StringJoiner("; ");
            joiner.add("setup=" + setup);
            joiner.add("kits=" + usesKits);
            joiner.add("loot=" + usesLoot);
            joiner.add("win=" + winCondition);
            joiner.add("reset=" + resetBehavior);
            return joiner.toString();
        }
    }
}
