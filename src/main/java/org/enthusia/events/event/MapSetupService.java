package org.enthusia.events.event;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.World;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.LocationCodec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings({"PMD.UseConcurrentHashMap", "PMD.UnusedPrivateMethod"})
public final class MapSetupService {

    private final EnthusiaEventsPlugin plugin;
    private final File file;
    private final Map<EventType, Map<String, EventMap>> maps = new EnumMap<>(EventType.class);

    public MapSetupService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        File directory = new File(plugin.getDataFolder(), "maps");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create maps setup folder.");
        }
        this.file = new File(directory, "maps.yml");
        migrateLegacyMapsFile();
        ensureMapsFile();
        load();
    }

    public void reload() {
        load();
    }

    public EventMap create(EventType eventType, String id, String worldName) {
        Map<String, EventMap> eventMaps = maps.computeIfAbsent(eventType, ignored -> new java.util.LinkedHashMap<>());
        EventMap map = eventMaps.computeIfAbsent(id.toLowerCase(Locale.ROOT), ignored -> new EventMap(eventType, id));
        map.worldName(worldName);
        save();
        return map;
    }

    public Optional<EventMap> find(EventType eventType, String id) {
        return Optional.ofNullable(maps.getOrDefault(eventType, Map.of()).get(id.toLowerCase(Locale.ROOT)));
    }

    public List<EventMap> mapsFor(EventType eventType) {
        return maps.getOrDefault(eventType, Map.of()).values().stream()
                .sorted(Comparator.comparing(EventMap::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean isInsideAnyConfiguredRegion(Location location) {
        if (location == null) {
            return false;
        }
        return maps.values().stream()
                .flatMap(eventMaps -> eventMaps.values().stream())
                .map(EventMap::region)
                .anyMatch(region -> region != null && region.contains(location));
    }

    public List<EventMap> usableMapsFor(EventType eventType) {
        return mapsFor(eventType).stream()
                .filter(map -> validate(map).isEmpty())
                .toList();
    }

    public List<String> validate(EventMap map) {
        List<String> errors = new ArrayList<>();
        if (map.worldName() == null || map.worldName().isBlank()) {
            errors.add("world is not set");
        }
        if (map.region() == null) {
            errors.add("region is not set");
        }
        if (map.spawns().isEmpty()) {
            errors.add("at least one spawn point is required");
        }
        if (map.spectatorSpawn() == null) {
            errors.add("spectator spawn is required");
        }
        if (requiresFinish(map.eventType()) && !hasFinishPoint(map)) {
            errors.add(map.eventType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " finish point is required");
        }
        if (map.eventType() == EventType.BOAT_RACE && !map.areas().containsKey("release-wall")) {
            errors.add("boat race release wall area is required");
        }
        if (map.eventType() == EventType.SKYWARS && map.chests().values().stream().allMatch(List::isEmpty)) {
            errors.add("at least one SkyWars chest is required");
        }
        if (map.eventType() == EventType.BEDWARS && map.generators().values().stream().allMatch(List::isEmpty)) {
            errors.add("at least one BedWars generator is required");
        }
        if (map.eventType() == EventType.BEDWARS) {
            if (map.generators().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase("solo") || entry.getKey().startsWith("solo-"))
                    .allMatch(entry -> entry.getValue().isEmpty())) {
                errors.add("at least one BedWars island generator is required");
            }
            if (map.generators().getOrDefault("diamond", List.of()).isEmpty()) {
                errors.add("at least one BedWars diamond generator is required");
            }
            if (map.generators().getOrDefault("emerald", List.of()).isEmpty()) {
                errors.add("at least one BedWars emerald generator is required");
            }
            if (map.points().keySet().stream().noneMatch(key -> key.startsWith("bed-")
                    && map.pointBlockData().containsKey(key))) {
                errors.add("at least one BedWars bed is required");
            }
            map.spawns().keySet().stream()
                    .filter(key -> key.startsWith("team-") && key.endsWith("-spawn"))
                    .map(key -> key.substring("team-".length(), key.length() - "-spawn".length()))
                    .filter(team -> !map.points().containsKey("bed-" + team)
                            || !map.pointBlockData().containsKey("bed-" + team))
                    .forEach(team -> errors.add("BedWars team " + team + " bed is required"));
            if (map.points().keySet().stream().noneMatch(key -> key.startsWith("item-shop"))) {
                errors.add("at least one BedWars item shop is required");
            }
            if (map.points().keySet().stream().noneMatch(key -> key.startsWith("upgrade-shop"))) {
                errors.add("at least one BedWars upgrade shop is required");
            }
        }
        if (map.eventType() == EventType.RUNNER && !map.areas().containsKey("breakable-area"))
            errors.add("Runner breakable-area is required; use /ee setup <EVENT> <mapId> area breakable-area");
        if (map.eventType() == EventType.BLOCK_PARTY && !map.areas().containsKey("color-floor")) {
            errors.add("block party color floor area is required");
        }
        if (map.eventType() == EventType.RED_LIGHT_GREEN_LIGHT && !map.areas().containsKey("light-display")) {
            errors.add("red light green light display area is required");
        }
        if (map.eventType() == EventType.RED_LIGHT_GREEN_LIGHT && !map.areas().containsKey("finish-line")) {
            errors.add("red light green light finish line area is required");
        }
        if (map.eventType() == EventType.CAPTURE_THE_FLAG
                && map.points().keySet().stream().filter(key -> key.startsWith("flag-")).count() < 2) {
            errors.add("capture the flag requires at least two team flag points");
        }
        if (map.eventType() == EventType.CAPTURE_PLAYERS) {
            List<String> teams = map.spawns().keySet().stream()
                    .filter(key -> key.startsWith("team-") && key.endsWith("-spawn"))
                    .map(key -> key.substring("team-".length(), key.length() - "-spawn".length()))
                    .distinct()
                    .toList();
            for (String team : teams) {
                if (!map.areas().containsKey("capture-zone-" + team)) {
                    errors.add("capture players " + team + " capture zone is required");
                }
                if (!map.areas().containsKey("jail-zone-" + team)) {
                    errors.add("capture players " + team + " jail zone is required");
                }
                if (!map.areas().containsKey("free-zone-" + team)) {
                    errors.add("capture players " + team + " free zone is required");
                }
            }
        }
        if (map.eventType() == EventType.ELYTRA_RACE
                && map.checkpoints().keySet().stream().noneMatch(this::isRaceCheckpointKey)
                && map.checkpointBlocks().values().stream().allMatch(List::isEmpty)
                && map.areas().keySet().stream().noneMatch(this::isRaceCheckpointKey)) {
            errors.add("at least one elytra checkpoint is required");
        }
        if (map.eventType() == EventType.ELYTRA_RACE) {
            map.checkpointBlocks().entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .filter(entry -> !map.points().containsKey("checkpoint-spawn-" + entry.getKey()))
                    .map(Map.Entry::getKey)
                    .forEach(key -> errors.add(key + " respawn point is required"));
        }
        return errors;
    }

    public Optional<EventMap> firstUsableMap(EventType eventType) {
        return mapsFor(eventType).stream().findFirst();
    }

    public List<EventMap> allMaps() {
        return maps.values().stream()
                .flatMap(eventMaps -> eventMaps.values().stream())
                .sorted(Comparator.comparing(EventMap::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean isConfiguredMapWorld(String worldName) {
        return worldName != null && maps.values().stream()
                .flatMap(eventMaps -> eventMaps.values().stream())
                .map(EventMap::worldName)
                .filter(java.util.Objects::nonNull)
                .anyMatch(worldName::equalsIgnoreCase);
    }

    public int clearMapSection(EventMap map, String requestedSection) {
        if (map == null || requestedSection == null) {
            return 0;
        }
        String section = requestedSection.toLowerCase(Locale.ROOT).replace('_', '-');
        int removed = switch (section) {
            case "all" -> clearAllSetup(map);
            case "region", "borders", "border" -> clearRegion(map);
            case "spectator", "spectator-spawn" -> clearSpectator(map);
            case "spawn", "spawns", "start", "starts", "start-spots" -> clearMap(map.spawns());
            case "finish", "finishes", "finish-spots" -> clearFinishes(map);
            case "checkpoint", "checkpoints" -> clearCheckpoints(map);
            case "checkpoint-blocks" -> clearNestedMap(map.checkpointBlocks());
            case "point", "points" -> clearPoints(map);
            case "bed", "beds", "bed-spawns" -> clearPointsByPrefix(map, List.of("bed-"));
            case "shop", "shops" -> clearPointsByPrefix(map, List.of("item-shop", "upgrade-shop", "team-shop"));
            case "area", "areas" -> clearMap(map.areas());
            case "chest", "chests" -> clearNestedMap(map.chests());
            case "generator", "generators", "gens" -> clearNestedMap(map.generators());
            default -> -1;
        };
        if (removed >= 0) {
            save();
        }
        return removed;
    }

    private int clearAllSetup(EventMap map) {
        int removed = clearRegion(map) + clearSpectator(map);
        removed += clearMap(map.spawns());
        removed += clearMap(map.checkpoints());
        removed += clearNestedMap(map.checkpointBlocks());
        removed += clearPoints(map);
        removed += clearMap(map.areas());
        removed += clearNestedMap(map.chests());
        removed += clearNestedMap(map.generators());
        return removed;
    }

    private int clearRegion(EventMap map) {
        if (map.region() == null) {
            return 0;
        }
        map.region(null);
        return 1;
    }

    private int clearSpectator(EventMap map) {
        if (map.spectatorSpawn() == null) {
            return 0;
        }
        map.spectatorSpawn(null);
        return 1;
    }

    private int clearFinishes(EventMap map) {
        int removed = removeKeys(map.checkpoints(), key -> key.startsWith("finish"));
        removed += removeKeys(map.areas(), key -> key.startsWith("finish"));
        return removed;
    }

    private int clearCheckpoints(EventMap map) {
        int removed = removeKeys(map.checkpoints(), key -> !key.startsWith("finish"));
        removed += clearNestedMap(map.checkpointBlocks());
        removed += removeKeys(map.points(), key -> key.startsWith("checkpoint-spawn-"));
        return removed;
    }

    private int clearPoints(EventMap map) {
        int removed = map.points().size();
        map.points().clear();
        map.pointBlockData().clear();
        return removed;
    }

    private int clearPointsByPrefix(EventMap map, List<String> prefixes) {
        List<String> keys = map.points().keySet().stream()
                .filter(key -> prefixes.stream().anyMatch(key.toLowerCase(Locale.ROOT)::startsWith))
                .toList();
        keys.forEach(key -> {
            map.points().remove(key);
            map.pointBlockData().remove(key);
        });
        return keys.size();
    }

    private <K, V> int clearMap(Map<K, V> values) {
        int removed = values.size();
        values.clear();
        return removed;
    }

    private <K, V> int clearNestedMap(Map<K, List<V>> values) {
        int removed = values.values().stream().mapToInt(List::size).sum();
        values.clear();
        return removed;
    }

    private <V> int removeKeys(Map<String, V> values, java.util.function.Predicate<String> predicate) {
        List<String> keys = values.keySet().stream()
                .filter(key -> predicate.test(key.toLowerCase(Locale.ROOT)))
                .toList();
        keys.forEach(values::remove);
        return keys.size();
    }

    public EventMap quickSetup(EventType eventType, String id, Location center, int radius) {
        EventMap map = create(eventType, id, center.getWorld().getName());
        map.region(new CuboidRegion(
                center.getWorld().getName(),
                center.getBlockX() - radius,
                Math.max(center.getBlockY() - 64, -64),
                center.getBlockZ() - radius,
                center.getBlockX() + radius,
                Math.min(center.getBlockY() + 64, 320),
                center.getBlockZ() + radius
        ));
        map.spawns().put("start", center.clone());
        map.spectatorSpawn(center.clone());
        save();
        return map;
    }

    public void retargetWorld(EventMap map, World targetWorld) {
        map.worldName(targetWorld.getName());
        if (map.region() != null) {
            CuboidRegion region = map.region();
            map.region(new CuboidRegion(
                    targetWorld.getName(),
                    region.minX(),
                    region.minY(),
                    region.minZ(),
                    region.maxX(),
                    region.maxY(),
                    region.maxZ()
            ));
        }
        map.spectatorSpawn(reworld(map.spectatorSpawn(), targetWorld));
        map.spawns().replaceAll((key, location) -> reworld(location, targetWorld));
        map.checkpoints().replaceAll((key, location) -> reworld(location, targetWorld));
        map.checkpointBlocks().replaceAll((key, locations) -> locations.stream()
                .map(location -> reworld(location, targetWorld))
                .collect(Collectors.toCollection(ArrayList::new)));
        map.points().replaceAll((key, location) -> reworld(location, targetWorld));
        map.areas().replaceAll((key, area) -> reworld(area, targetWorld));
        map.chests().replaceAll((tier, locations) -> locations.stream()
                .map(location -> reworld(location, targetWorld))
                .collect(Collectors.toCollection(ArrayList::new)));
        map.generators().replaceAll((type, locations) -> locations.stream()
                .map(location -> reworld(location, targetWorld))
                .collect(Collectors.toCollection(ArrayList::new)));
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<EventType, Map<String, EventMap>> entry : maps.entrySet()) {
            String base = "events." + entry.getKey().name();
            for (EventMap map : entry.getValue().values()) {
                String path = base + "." + map.id();
                yaml.set(path + ".world", map.worldName());
                if (map.region() != null) {
                    yaml.set(path + ".region.world", map.region().worldName());
                    yaml.set(path + ".region.min-x", map.region().minX());
                    yaml.set(path + ".region.min-y", map.region().minY());
                    yaml.set(path + ".region.min-z", map.region().minZ());
                    yaml.set(path + ".region.max-x", map.region().maxX());
                    yaml.set(path + ".region.max-y", map.region().maxY());
                    yaml.set(path + ".region.max-z", map.region().maxZ());
                }
                yaml.set(path + ".spectator-spawn", LocationCodec.encode(map.spectatorSpawn()));
                for (Map.Entry<String, Location> spawn : map.spawns().entrySet()) {
                    yaml.set(path + ".spawns." + spawn.getKey(), LocationCodec.encode(spawn.getValue()));
                }
                for (Map.Entry<String, Location> checkpoint : map.checkpoints().entrySet()) {
                    yaml.set(path + ".checkpoints." + checkpoint.getKey(), LocationCodec.encode(checkpoint.getValue()));
                }
                for (Map.Entry<String, List<Location>> checkpointEntry : map.checkpointBlocks().entrySet()) {
                    int index = 0;
                    for (Location location : checkpointEntry.getValue()) {
                        yaml.set(path + ".checkpoint-blocks." + checkpointEntry.getKey() + "." + index++,
                                LocationCodec.encode(location));
                    }
                }
                for (Map.Entry<String, Location> point : map.points().entrySet()) {
                    yaml.set(path + ".points." + point.getKey(), LocationCodec.encode(point.getValue()));
                }
                for (Map.Entry<String, String> blockData : map.pointBlockData().entrySet()) {
                    yaml.set(path + ".point-block-data." + blockData.getKey(), blockData.getValue());
                }
                for (Map.Entry<String, CuboidRegion> area : map.areas().entrySet()) {
                    yaml.set(path + ".areas." + area.getKey() + ".world", area.getValue().worldName());
                    yaml.set(path + ".areas." + area.getKey() + ".min-x", area.getValue().minX());
                    yaml.set(path + ".areas." + area.getKey() + ".min-y", area.getValue().minY());
                    yaml.set(path + ".areas." + area.getKey() + ".min-z", area.getValue().minZ());
                    yaml.set(path + ".areas." + area.getKey() + ".max-x", area.getValue().maxX());
                    yaml.set(path + ".areas." + area.getKey() + ".max-y", area.getValue().maxY());
                    yaml.set(path + ".areas." + area.getKey() + ".max-z", area.getValue().maxZ());
                }
                for (Map.Entry<Integer, List<Location>> chestEntry : map.chests().entrySet()) {
                    int index = 0;
                    for (Location location : chestEntry.getValue()) {
                        yaml.set(path + ".chests.tier" + chestEntry.getKey() + "." + index++, LocationCodec.encode(location));
                    }
                }
                for (Map.Entry<String, List<Location>> generatorEntry : map.generators().entrySet()) {
                    int index = 0;
                    for (Location location : generatorEntry.getValue()) {
                        yaml.set(path + ".generators." + generatorEntry.getKey() + "." + index++, LocationCodec.encode(location));
                    }
                }
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save maps/maps.yml: " + e.getMessage());
        }
    }

    private void migrateLegacyMapsFile() {
        File legacy = new File(plugin.getDataFolder(), "maps.yml");
        if (file.exists() || !legacy.exists()) {
            return;
        }
        try {
            Files.copy(legacy.toPath(), file.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Migrated map setup data to maps/maps.yml. The old maps.yml was left in place as a backup.");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to migrate maps.yml to maps/maps.yml: " + e.getMessage());
        }
    }

    private void ensureMapsFile() {
        if (file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("events", new java.util.LinkedHashMap<>());
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create maps/maps.yml: " + e.getMessage());
        }
    }

    private void load() {
        maps.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection events = yaml.getConfigurationSection("events");
        if (events == null) {
            return;
        }
        for (String eventKey : events.getKeys(false)) {
            EventType type = EventType.parse(eventKey);
            ConfigurationSection eventSection = events.getConfigurationSection(eventKey);
            if (eventSection == null) {
                continue;
            }
            Map<String, EventMap> eventMaps = maps.computeIfAbsent(type, ignored -> new java.util.LinkedHashMap<>());
            for (String mapId : eventSection.getKeys(false)) {
                ConfigurationSection mapSection = eventSection.getConfigurationSection(mapId);
                if (mapSection == null) {
                    continue;
                }
                EventMap map = new EventMap(type, mapId);
                map.worldName(mapSection.getString("world"));
                ConfigurationSection regionSection = mapSection.getConfigurationSection("region");
                if (regionSection != null) {
                    map.region(new CuboidRegion(
                            regionSection.getString("world", map.worldName()),
                            regionSection.getDouble("min-x"),
                            regionSection.getDouble("min-y"),
                            regionSection.getDouble("min-z"),
                            regionSection.getDouble("max-x"),
                            regionSection.getDouble("max-y"),
                            regionSection.getDouble("max-z")
                    ));
                }
                map.spectatorSpawn(LocationCodec.decode(mapSection.getString("spectator-spawn", "")));
                loadLocations(mapSection.getConfigurationSection("spawns"), map.spawns());
                loadLocations(mapSection.getConfigurationSection("checkpoints"), map.checkpoints());
                loadCheckpointBlocks(mapSection.getConfigurationSection("checkpoint-blocks"), map);
                loadLocations(mapSection.getConfigurationSection("points"), map.points());
                loadStrings(mapSection.getConfigurationSection("point-block-data"), map.pointBlockData());
                removeLegacyBedPoints(map);
                loadAreas(mapSection.getConfigurationSection("areas"), map);
                loadChestLocations(mapSection.getConfigurationSection("chests"), map);
                loadGeneratorLocations(mapSection.getConfigurationSection("generators"), map);
                eventMaps.put(mapId.toLowerCase(Locale.ROOT), map);
            }
        }
    }

    private void loadLocations(ConfigurationSection section, Map<String, Location> target) {
        if (section == null) {
            return;
        }
        for (String key : new ArrayList<>(section.getKeys(false))) {
            target.put(key, LocationCodec.decode(section.getString(key, "")));
        }
    }

    private void loadStrings(ConfigurationSection section, Map<String, String> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                target.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
    }

    private void removeLegacyBedPoints(EventMap map) {
        if (map.eventType() != EventType.BEDWARS) {
            return;
        }
        List<String> legacyKeys = map.points().keySet().stream()
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith("bed-"))
                .filter(key -> !map.pointBlockData().containsKey(key.toLowerCase(Locale.ROOT)))
                .toList();
        if (legacyKeys.isEmpty()) {
            return;
        }
        legacyKeys.forEach(map.points()::remove);
        plugin.getLogger().warning("Cleared " + legacyKeys.size() + " legacy BedWars bed marker(s) from map "
                + map.id() + ". Reconfigure them by clicking the actual beds in /ee setup.");
    }

    private void loadChestLocations(ConfigurationSection section, EventMap map) {
        if (section == null) {
            return;
        }
        for (String tierKey : section.getKeys(false)) {
            int tier = Integer.parseInt(tierKey.replaceAll("\\D+", ""));
            ConfigurationSection tierSection = section.getConfigurationSection(tierKey);
            if (tierSection == null) {
                continue;
            }
            for (String key : tierSection.getKeys(false)) {
                Location location = LocationCodec.decode(tierSection.getString(key, ""));
                if (location != null) {
                    map.addChest(tier, location);
                }
            }
        }
    }

    private void loadCheckpointBlocks(ConfigurationSection section, EventMap map) {
        if (section == null) {
            return;
        }
        for (String checkpointId : section.getKeys(false)) {
            ConfigurationSection checkpointSection = section.getConfigurationSection(checkpointId);
            if (checkpointSection == null) {
                continue;
            }
            for (String key : checkpointSection.getKeys(false)) {
                Location location = LocationCodec.decode(checkpointSection.getString(key, ""));
                if (location != null) {
                    map.addCheckpointBlock(checkpointId.toLowerCase(Locale.ROOT), location);
                }
            }
        }
    }

    private void loadGeneratorLocations(ConfigurationSection section, EventMap map) {
        if (section == null) {
            return;
        }
        for (String type : section.getKeys(false)) {
            ConfigurationSection typeSection = section.getConfigurationSection(type);
            if (typeSection == null) {
                continue;
            }
            for (String key : typeSection.getKeys(false)) {
                Location location = LocationCodec.decode(typeSection.getString(key, ""));
                if (location != null) {
                    map.addGenerator(type, location);
                }
            }
        }
    }

    private void loadAreas(ConfigurationSection section, EventMap map) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection area = section.getConfigurationSection(key);
            if (area == null) {
                continue;
            }
            map.areas().put(key.toLowerCase(Locale.ROOT), new CuboidRegion(
                    area.getString("world", map.worldName()),
                    area.getDouble("min-x"),
                    area.getDouble("min-y"),
                    area.getDouble("min-z"),
                    area.getDouble("max-x"),
                    area.getDouble("max-y"),
                    area.getDouble("max-z")
            ));
        }
    }

    private Location reworld(Location location, World world) {
        if (location == null) {
            return null;
        }
        return new Location(
                world,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    private CuboidRegion reworld(CuboidRegion region, World world) {
        if (region == null) {
            return null;
        }
        return new CuboidRegion(
                world.getName(),
                region.minX(),
                region.minY(),
                region.minZ(),
                region.maxX(),
                region.maxY(),
                region.maxZ()
        );
    }

    private boolean requiresFinish(EventType type) {
        return type == EventType.PARKOUR
                || type == EventType.BOAT_RACE
                || type == EventType.HORSE_RACE
                || type == EventType.ELYTRA_RACE;
    }

    private boolean hasFinishPoint(EventMap map) {
        return map.checkpoints().keySet().stream()
                .anyMatch(key -> key.toLowerCase(Locale.ROOT).startsWith("finish"))
                || map.areas().keySet().stream()
                .anyMatch(key -> key.toLowerCase(Locale.ROOT).startsWith("finish"));
    }

    private boolean isRaceCheckpointKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("ring")
                || lower.startsWith("cp")
                || lower.startsWith("checkpoint");
    }
}
