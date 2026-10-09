package org.enthusia.events.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventType;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventConfigService {

    private final EnthusiaEventsPlugin plugin;
    private final File directory;
    private final Map<EventType, EventSettings> settings = new EnumMap<>(EventType.class);

    public EventConfigService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "events");
        reload();
    }

    public void reload() {
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create events config folder.");
        }
        settings.clear();
        for (EventType type : EventType.values()) {
            settings.put(type, load(type));
        }
    }

    public Material icon(EventType type) {
        return settings.getOrDefault(type, defaults(type)).icon();
    }

    public String description(EventType type) {
        return settings.getOrDefault(type, defaults(type)).description();
    }

    public int minPlayers(EventType type, int fallback) {
        return settings.getOrDefault(type, defaults(type)).minPlayers();
    }

    public int readyPlayers(EventType type, int fallback) {
        return settings.getOrDefault(type, defaults(type)).readyPlayers();
    }

    public double winnerReward(EventType type, double fallback) {
        return settings.getOrDefault(type, defaults(type)).winnerReward();
    }

    public long activePhaseSeconds(EventType type, long fallback) {
        return settings.getOrDefault(type, defaults(type)).activePhaseSeconds();
    }

    private EventSettings load(EventType type) {
        File file = new File(directory, type.name().toLowerCase(Locale.ROOT) + ".yml");
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        EventSettings defaults = defaults(type);
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("events.per-event." + type.name());

        boolean changed = false;
        int configVersion = yaml.getInt("config-version", 1);
        if (type == EventType.BEDWARS && configVersion < 2
                && yaml.getLong("active-phase-seconds", 900L) == 900L) {
            yaml.set("active-phase-seconds", 1200L);
            changed = true;
        }
        if (configVersion < 2) {
            yaml.set("config-version", 2);
            changed = true;
        }
        changed |= setMissing(yaml, "icon", legacy == null ? defaults.icon().name() : legacy.getString("icon", defaults.icon().name()));
        changed |= setMissing(yaml, "description", legacy == null ? defaults.description() : legacy.getString("description", defaults.description()));
        changed |= setMissing(yaml, "min-players", legacy == null ? defaults.minPlayers() : legacy.getInt("min-players", defaults.minPlayers()));
        changed |= setMissing(yaml, "ready-players", legacy == null ? defaults.readyPlayers() : legacy.getInt("ready-players", defaults.readyPlayers()));
        changed |= setMissing(yaml, "winner-reward", legacy == null ? defaults.winnerReward() : legacy.getDouble("winner-reward", defaults.winnerReward()));
        changed |= setMissing(yaml, "active-phase-seconds", legacy == null ? defaults.activePhaseSeconds() : legacy.getLong("active-phase-seconds", defaults.activePhaseSeconds()));

        if (changed || !file.exists()) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save event config " + file.getName() + ": " + e.getMessage());
            }
        }

        Material icon = Material.matchMaterial(yaml.getString("icon", defaults.icon().name()));
        if (icon == null || !icon.isItem()) {
            plugin.getLogger().warning("Invalid icon in events/" + file.getName() + ". Using " + defaults.icon().name() + ".");
            icon = defaults.icon();
        }
        return new EventSettings(
                icon,
                yaml.getString("description", defaults.description()),
                Math.max(1, yaml.getInt("min-players", defaults.minPlayers())),
                Math.max(1, yaml.getInt("ready-players", defaults.readyPlayers())),
                Math.max(0.0D, yaml.getDouble("winner-reward", defaults.winnerReward())),
                Math.max(0L, yaml.getLong("active-phase-seconds", defaults.activePhaseSeconds()))
        );
    }

    private boolean setMissing(YamlConfiguration yaml, String key, Object value) {
        if (yaml.contains(key)) {
            return false;
        }
        yaml.set(key, value);
        return true;
    }

    private EventSettings defaults(EventType type) {
        return switch (type) {
            case SKYWARS -> new EventSettings(Material.FEATHER, "Loot island chests, bridge carefully, and be the last player alive.", 2, 2, 100.0D, 600L);
            case BEDWARS -> new EventSettings(Material.RED_BED, "Solo BedWars. Protect your bed, use island generators, and eliminate every other player.", 2, 2, 100.0D, 1200L);
            case FIGHT_1V1 -> new EventSettings(Material.IRON_SWORD, "A straight kit fight. Last player standing wins.", 2, 2, 100.0D, 300L);
            case FIGHT_2V2 -> new EventSettings(Material.DIAMOND_SWORD, "Small-team kit combat. Survive the fight to win.", 4, 4, 100.0D, 420L);
            case FIGHT_FFA -> new EventSettings(Material.NETHERITE_SWORD, "Free-for-all kit combat. Last player alive wins.", 2, 4, 100.0D, 600L);
            case SUMO_1V1 -> new EventSettings(Material.SLIME_BALL, "Knock your opponent out of the arena without weapons or armor.", 2, 2, 100.0D, 180L);
            case SUMO_2V2 -> new EventSettings(Material.LEAD, "Team sumo. Stay on the platform and knock everyone else off.", 4, 4, 100.0D, 240L);
            case SUMO_FFA -> new EventSettings(Material.HONEY_BLOCK, "Free-for-all sumo. Last player on the platform wins.", 2, 4, 100.0D, 300L);
            case KNOCKBACK_FFA -> new EventSettings(Material.STICK, "Free-for-all knockback fight. Hit players out of the arena to win.", 2, 4, 100.0D, 300L);
            case QUAKE -> new EventSettings(Material.GOLDEN_HOE, "Railgun free-for-all. Hit players with quake shots and be the last alive.", 2, 4, 100.0D, 240L);
            case ONE_IN_THE_CHAMBER -> new EventSettings(Material.BOW, "One arrow can end a fight. Earn arrows by eliminating players.", 2, 4, 100.0D, 300L);
            case CAPTURE_THE_FLAG -> new EventSettings(Material.WHITE_BANNER, "Steal the enemy flag and return to your own flag to capture it.", 2, 4, 100.0D, 600L);
            case CAPTURE_PLAYERS -> new EventSettings(Material.IRON_CHAIN, "Capture enemy players, defend your jail, and free your teammates.", 2, 4, 100.0D, 600L);
            case BLOCK_PARTY -> new EventSettings(Material.NOTE_BLOCK, "Stand on the called color before the timer locks each round.", 2, 4, 100.0D, 420L);
            case HOT_POTATO -> new EventSettings(Material.BAKED_POTATO, "Pass the hot potato before it explodes. Last player safe wins.", 2, 4, 100.0D, 420L);
            case RUNNER -> new EventSettings(Material.TNT, "Keep running as the floor disappears. Last player standing wins.", 2, 4, 100.0D, 420L);
            case SPLEEF -> new EventSettings(Material.DIAMOND_SHOVEL, "Break the floor under other players and be the last one standing.", 2, 4, 100.0D, 420L);
            case SPLEGG -> new EventSettings(Material.SNOWBALL, "Use snowballs and shovels to break blocks and drop opponents.", 2, 4, 100.0D, 420L);
            case RED_LIGHT_GREEN_LIGHT -> new EventSettings(Material.REDSTONE_TORCH, "Move on green, freeze on red, and survive until the end.", 2, 4, 100.0D, 300L);
            case BOAT_RACE -> new EventSettings(Material.OAK_BOAT, "Race the course by boat and cross the finish first.", 2, 2, 100.0D, 420L);
            case HORSE_RACE -> new EventSettings(Material.SADDLE, "Ride the course and cross the finish first.", 2, 2, 100.0D, 420L);
            case ELYTRA_RACE -> new EventSettings(Material.ELYTRA, "Fly through the route and be the first to reach the finish.", 2, 2, 100.0D, 420L);
            case PARKOUR -> new EventSettings(Material.RABBIT_FOOT, "Race through the course, hit checkpoints, and finish first to win.", 2, 2, 100.0D, 300L);
        };
    }

    private record EventSettings(Material icon, String description, int minPlayers, int readyPlayers,
                                 double winnerReward, long activePhaseSeconds) {
    }
}
