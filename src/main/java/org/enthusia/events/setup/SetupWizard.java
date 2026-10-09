package org.enthusia.events.setup;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.CuboidRegion;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapSetupService;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings({
        "PMD.UseConcurrentHashMap",
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.NPathComplexity"
})
public final class SetupWizard {

    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService mapSetupService;
    private final NamespacedKey toolKey;
    private final NamespacedKey valueKey;
    private final Map<UUID, SetupSession> sessions = new HashMap<>();
    private final Map<UUID, SetupInventorySnapshot> inventorySnapshots = new HashMap<>();
    private final Map<UUID, Location> pos1Selections = new HashMap<>();
    private final Map<UUID, Location> pos2Selections = new HashMap<>();
    private final Map<UUID, Location> areaPos1Selections = new HashMap<>();
    private final Map<UUID, Location> areaPos2Selections = new HashMap<>();
    private final Map<UUID, String> selectedTeams = new HashMap<>();
    private final Map<UUID, String> selectedElytraCheckpoints = new HashMap<>();
    private final Map<UUID, String> palettePages = new HashMap<>();
    private final Map<UUID, Long> paletteSwitchCooldowns = new HashMap<>();
    private final Map<String, BlockData> originalBlocks = new HashMap<>();
    private final Map<String, MarkerState> markerStates = new HashMap<>();
    private final Map<UUID, List<SetupAction>> actionHistory = new HashMap<>();
    private final Map<UUID, List<Location>> visualOnlyMarkers = new HashMap<>();

    public SetupWizard(EnthusiaEventsPlugin plugin, MapSetupService mapSetupService) {
        this.plugin = plugin;
        this.mapSetupService = mapSetupService;
        this.toolKey = new NamespacedKey(plugin, "setup-tool");
        this.valueKey = new NamespacedKey(plugin, "setup-value");
    }

    public void openPalette(Player player, EventType eventType, String mapId) {
        mapSetupService.create(eventType, mapId, player.getWorld().getName());
        captureInventory(player);
        sessions.put(player.getUniqueId(), new SetupSession(eventType, mapId, null, ""));
        selectedTeams.put(player.getUniqueId(), "1");
        selectedElytraCheckpoints.remove(player.getUniqueId());
        palettePages.put(player.getUniqueId(), "main");
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        givePalette(player, eventType);
        mapSetupService.find(eventType, mapId).ifPresent(map -> markExistingSetup(player, map));
        player.updateInventory();
        plugin.messages().send(player, "setup-wizard-opened", Map.of(
                "event", eventType.name(),
                "map", mapId
        ));
    }

    public void begin(Player player, SetupSession session) {
        sessions.put(player.getUniqueId(), session);
        selectedTeams.putIfAbsent(player.getUniqueId(), "1");
        plugin.messages().send(player, "setup-mode-started", Map.of(
                "event", session.eventType().name(),
                "map", session.mapId(),
                "mode", describe(session)
        ));
    }

    public boolean cancel(Player player) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            mapSetupService.find(session.eventType(), session.mapId())
                    .ifPresent(map -> revertSession(player, map));
        }
        boolean hadSession = sessions.remove(player.getUniqueId()) != null;
        selectedTeams.remove(player.getUniqueId());
        selectedElytraCheckpoints.remove(player.getUniqueId());
        palettePages.remove(player.getUniqueId());
        paletteSwitchCooldowns.remove(player.getUniqueId());
        revertVisualsOnlyLocations(visualOnlyMarkers.remove(player.getUniqueId()));
        restoreInventory(player);
        return hadSession;
    }

    public boolean saveAndClose(Player player) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            Optional<EventMap> map = mapSetupService.find(session.eventType(), session.mapId());
            if (map.isEmpty()) {
                plugin.messages().send(player, "setup-validation-failed", Map.of("reason", "map not found"));
                return false;
            }
            saveDefaultPendingArea(player, map.get());
            List<String> errors = mapSetupService.validate(map.get());
            if (!errors.isEmpty()) {
                plugin.messages().send(player, "setup-validation-failed", Map.of("reason", String.join(", ", errors)));
                return false;
            }
        }
        boolean hadSession = sessions.remove(player.getUniqueId()) != null;
        selectedTeams.remove(player.getUniqueId());
        selectedElytraCheckpoints.remove(player.getUniqueId());
        palettePages.remove(player.getUniqueId());
        paletteSwitchCooldowns.remove(player.getUniqueId());
        revertVisualsOnly(actionHistory.remove(player.getUniqueId()));
        revertVisualsOnlyLocations(visualOnlyMarkers.remove(player.getUniqueId()));
        restoreInventory(player);
        return hadSession;
    }

    public Optional<SetupSession> session(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public boolean isAirSelectionTool(Player player) {
        Optional<ToolSelection> selection = selectionFromHeldItem(player);
        return selection.isPresent() && (selection.get().tool() == SetupTool.POS1
                || selection.get().tool() == SetupTool.POS2
                || selection.get().tool() == SetupTool.SPECTATOR
                || selection.get().tool() == SetupTool.AREA_POS1
                || selection.get().tool() == SetupTool.AREA_POS2
                || selection.get().tool() == SetupTool.AREA
                || selection.get().tool() == SetupTool.CHECKPOINT
                || selection.get().tool() == SetupTool.TEAM
                || selection.get().tool() == SetupTool.PAGE
                || selection.get().tool() == SetupTool.REMOVE);
    }

    public boolean usesRelativePlacementTarget(Player player) {
        return false;
    }

    public boolean hasHeldSetupTool(Player player) {
        return selectionFromHeldItem(player).isPresent();
    }

    public void handleClick(Player player, Block clickedBlock) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        ToolSelection selection = selectionFromHeldItem(player).orElseGet(() -> {
            if (session.tool() == null) {
                return null;
            }
            return new ToolSelection(session.tool(), session.value());
        });
        if (selection == null) {
            plugin.messages().send(player, "setup-tool-needed");
            return;
        }

        Optional<EventMap> mapOptional = mapSetupService.find(session.eventType(), session.mapId());
        if (mapOptional.isEmpty()) {
            plugin.messages().send(player, "setup-failed", Map.of("reason", "map not found"));
            return;
        }
        applySelection(player, mapOptional.get(), clickedBlock, selection);
    }

    public void handleAirClick(Player player) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Optional<ToolSelection> selection = selectionFromHeldItem(player);
        if (selection.isEmpty() || (selection.get().tool() != SetupTool.POS1
                && selection.get().tool() != SetupTool.POS2
                && selection.get().tool() != SetupTool.SPECTATOR
                && selection.get().tool() != SetupTool.AREA_POS1
                && selection.get().tool() != SetupTool.AREA_POS2
                && selection.get().tool() != SetupTool.AREA
                && selection.get().tool() != SetupTool.CHECKPOINT
                && selection.get().tool() != SetupTool.TEAM
                && selection.get().tool() != SetupTool.PAGE
                && selection.get().tool() != SetupTool.REMOVE)) {
            plugin.messages().send(player, "setup-tool-needed");
            return;
        }
        Optional<EventMap> mapOptional = mapSetupService.find(session.eventType(), session.mapId());
        if (mapOptional.isEmpty()) {
            plugin.messages().send(player, "setup-failed", Map.of("reason", "map not found"));
            return;
        }
        EventMap map = mapOptional.get();
        if (selection.get().tool() == SetupTool.CHECKPOINT && map.eventType() == EventType.ELYTRA_RACE) {
            selectElytraCheckpoint(player, map, selection.get().value());
            return;
        }
        if (selection.get().tool() == SetupTool.TEAM) {
            selectTeam(player, selection.get().value());
            return;
        }
        if (selection.get().tool() == SetupTool.PAGE) {
            switchPalettePage(player, selection.get().value());
            return;
        }
        if (selection.get().tool() == SetupTool.REMOVE) {
            removeLatest(player, map);
            return;
        }
        Location playerBlock = player.getLocation().getBlock().getLocation();
        if (selection.get().tool() == SetupTool.SPECTATOR) {
            Location previous = map.spectatorSpawn();
            map.spectatorSpawn(player.getLocation().clone());
            mapSetupService.save();
            record(player, SetupAction.mapLocation(SetupTool.SPECTATOR, "spectator", previous, player.getLocation().clone(), player.getLocation().getBlock().getLocation()));
            plugin.messages().send(player, "setup-click-saved", Map.of("target", "spectator spawn"));
            return;
        }
        if (selection.get().tool() == SetupTool.AREA_POS1) {
            Location previous = areaPos1Selections.put(player.getUniqueId(), playerBlock.clone());
            record(player, SetupAction.pos(SetupTool.AREA_POS1, previous, playerBlock.clone(), null));
            plugin.messages().send(player, "setup-click-saved", Map.of("target", "special area pos1 at your location"));
            return;
        }
        if (selection.get().tool() == SetupTool.AREA_POS2) {
            Location previous = areaPos2Selections.put(player.getUniqueId(), playerBlock.clone());
            record(player, SetupAction.pos(SetupTool.AREA_POS2, previous, playerBlock.clone(), null));
            plugin.messages().send(player, "setup-click-saved", Map.of("target", "special area pos2 at your location"));
            return;
        }
        if (selection.get().tool() == SetupTool.AREA) {
            saveSpecialArea(player, map, areaId(player, selection.get().value()), null);
            return;
        }
        Location previous = selection.get().tool() == SetupTool.POS1
                ? pos1Selections.get(player.getUniqueId())
                : pos2Selections.get(player.getUniqueId());
        applyRegionPosition(player, map, playerBlock, selection.get().tool(), "your location");
        record(player, SetupAction.pos(selection.get().tool(), previous, playerBlock.clone(), playerBlock.clone()));
    }

    public void tickVisuals() {
        for (UUID uuid : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            SetupSession session = sessions.get(uuid);
            if (session == null) {
                continue;
            }
            mapSetupService.find(session.eventType(), session.mapId())
                    .map(EventMap::region)
                    .ifPresent(region -> drawRegion(player, region));
            mapSetupService.find(session.eventType(), session.mapId())
                    .ifPresent(map -> map.areas().values().forEach(area -> drawRegion(player, area)));
        }
    }

    public void handleBlockPlacement(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null || session.eventType() != EventType.ELYTRA_RACE
                || event.getBlockPlaced().getType() != Material.GOLD_BLOCK) {
            return;
        }
        Optional<EventMap> mapOptional = mapSetupService.find(session.eventType(), session.mapId());
        if (mapOptional.isEmpty()) {
            return;
        }
        String checkpointId = selectedElytraCheckpoints.get(player.getUniqueId());
        if (checkpointId == null) {
            event.setCancelled(true);
            plugin.messages().send(player, "setup-failed", Map.of(
                    "reason", "use Start Next Checkpoint before placing checkpoint blocks"
            ));
            return;
        }
        EventMap map = mapOptional.get();
        Location location = event.getBlockPlaced().getLocation();
        List<Location> blocks = map.checkpointBlocks().computeIfAbsent(checkpointId, ignored -> new ArrayList<>());
        if (blocks.stream().anyMatch(existing -> sameBlock(existing, location))) {
            return;
        }
        blocks.add(location.clone());
        mapSetupService.save();

        String key = blockKey(location);
        originalBlocks.putIfAbsent(key, event.getBlockReplacedState().getBlockData().clone());
        markerStates.put(key, new MarkerState(85, Material.GOLD_BLOCK));
        visualOnlyMarkers.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(location.clone());
        record(player, SetupAction.listLocation(SetupTool.CHECKPOINT_BLOCK, checkpointId, location.clone(), location.clone()));

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.containsKey(player.getUniqueId())) {
                replenishElytraCheckpointBlocks(player);
                player.updateInventory();
            }
        });
    }

    private void applySelection(Player player, EventMap map, Block clickedBlock, ToolSelection selection) {
        Location blockLocation = clickedBlock.getLocation();
        Location topCenter = orientedTopCenter(player, blockLocation);
        switch (selection.tool()) {
            case POS1 -> {
                Location previous = pos1Selections.get(player.getUniqueId());
                applyRegionPosition(player, map, blockLocation, SetupTool.POS1, "pos1");
                record(player, SetupAction.pos(SetupTool.POS1, previous, blockLocation.clone(), blockLocation.clone()));
                mark(clickedBlock, Material.LIME_WOOL, 10);
            }
            case POS2 -> {
                Location previous = pos2Selections.get(player.getUniqueId());
                applyRegionPosition(player, map, blockLocation, SetupTool.POS2, "pos2");
                record(player, SetupAction.pos(SetupTool.POS2, previous, blockLocation.clone(), blockLocation.clone()));
                mark(clickedBlock, Material.RED_WOOL, 10);
            }
            case AREA_POS1 -> {
                Location previous = areaPos1Selections.put(player.getUniqueId(), blockLocation.clone());
                record(player, SetupAction.pos(SetupTool.AREA_POS1, previous, blockLocation.clone(), blockLocation.clone()));
                mark(clickedBlock, Material.LIGHT_BLUE_WOOL, 20);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "special area pos1"));
            }
            case AREA_POS2 -> {
                Location previous = areaPos2Selections.put(player.getUniqueId(), blockLocation.clone());
                record(player, SetupAction.pos(SetupTool.AREA_POS2, previous, blockLocation.clone(), blockLocation.clone()));
                mark(clickedBlock, Material.BLUE_WOOL, 20);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "special area pos2"));
            }
            case SPAWN -> {
                String id = selection.value().equalsIgnoreCase("team-spawn")
                        ? "team-" + selectedTeam(player) + "-spawn"
                        : selection.value().isBlank()
                        ? existingLocationKey(map.spawns(), blockLocation).orElseGet(() -> nextId("spawn", map.spawns().size() + 1))
                        : selection.value();
                Location previous = map.spawns().get(id.toLowerCase(Locale.ROOT));
                map.spawns().put(id.toLowerCase(Locale.ROOT), topCenter);
                mapSetupService.save();
                record(player, SetupAction.mapLocation(SetupTool.SPAWN, id.toLowerCase(Locale.ROOT), previous, topCenter.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, Material.DIAMOND_BLOCK, 100);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "spawn " + id));
            }
            case SPECTATOR -> {
                Location previous = map.spectatorSpawn();
                map.spectatorSpawn(topCenter);
                mapSetupService.save();
                record(player, SetupAction.mapLocation(SetupTool.SPECTATOR, "spectator", previous, topCenter.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, Material.ENDER_CHEST, 60);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "spectator spawn"));
            }
            case CHECKPOINT -> {
                if (map.eventType() == EventType.ELYTRA_RACE) {
                    selectElytraCheckpoint(player, map, selection.value());
                    return;
                }
                String id = checkpointId(map, blockLocation, selection.value());
                Location previous = map.checkpoints().get(id.toLowerCase(Locale.ROOT));
                Location stored = map.eventType() == EventType.ELYTRA_RACE
                        ? centeredBlock(player, blockLocation)
                        : topCenter;
                map.checkpoints().put(id.toLowerCase(Locale.ROOT), stored);
                mapSetupService.save();
                record(player, SetupAction.mapLocation(SetupTool.CHECKPOINT, id.toLowerCase(Locale.ROOT), previous, stored.clone(), clickedBlock.getLocation().clone()));
                if (map.eventType() == EventType.ELYTRA_RACE) {
                    clickedBlock.setType(Material.GOLD_BLOCK, false);
                } else {
                    mark(clickedBlock, Material.GOLD_BLOCK, 80);
                }
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "checkpoint " + id));
            }
            case CHECKPOINT_SPAWN -> {
                String id = map.eventType() == EventType.ELYTRA_RACE
                        ? selectedElytraCheckpoints.get(player.getUniqueId())
                        : checkpointSpawnId(map, selection.value());
                if (id == null) {
                    plugin.messages().send(player, "setup-failed", Map.of(
                            "reason", "use Start Next Checkpoint before setting its respawn point"
                    ));
                    return;
                }
                String key = "checkpoint-spawn-" + id;
                Location previous = map.points().get(key);
                map.points().put(key, topCenter);
                mapSetupService.save();
                record(player, SetupAction.mapLocation(SetupTool.CHECKPOINT_SPAWN, key, previous, topCenter.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, Material.RESPAWN_ANCHOR, 80);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "checkpoint " + id + " respawn"));
            }
            case FINISH -> {
                String id = finishId(map, map.eventType(), blockLocation);
                Location previous = map.checkpoints().get(id);
                map.checkpoints().put(id, topCenter);
                mapSetupService.save();
                record(player, SetupAction.mapLocation(SetupTool.FINISH, id, previous, topCenter.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, Material.EMERALD_BLOCK, 90);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", id));
            }
            case CHEST -> {
                int tier = Integer.parseInt(selection.value().isBlank() ? "1" : selection.value());
                map.addChest(tier, blockLocation.clone());
                mapSetupService.save();
                record(player, SetupAction.listLocation(SetupTool.CHEST, String.valueOf(tier), blockLocation.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, chestMarker(tier), 70 + tier);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", "tier " + tier + " chest"));
            }
            case GENERATOR -> {
                String type = generatorId(player, selection.value());
                map.addGenerator(type, blockLocation.clone());
                mapSetupService.save();
                record(player, SetupAction.listLocation(SetupTool.GENERATOR, type, blockLocation.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, generatorMarker(type), 50);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", type + " generator"));
            }
            case POINT -> {
                String key = pointId(player, map, blockLocation, selection.value());
                if (map.eventType() == EventType.BEDWARS && key.startsWith("bed-")) {
                    saveBedPoint(player, map, clickedBlock, key);
                    return;
                }
                Location previous = map.points().get(key);
                Location stored = key.startsWith("flag-") ? flagBlockLocation(player, blockLocation) : topCenter;
                map.points().put(key, stored);
                mapSetupService.save();
                record(player, SetupAction.mapLocation(SetupTool.POINT, key, previous, stored.clone(), clickedBlock.getLocation().clone()));
                mark(clickedBlock, pointMarker(key), 75);
                plugin.messages().send(player, "setup-click-saved", Map.of("target", friendlyKey(key) + " point"));
            }
            case AREA -> {
                String key = areaId(player, selection.value());
                if (saveSpecialArea(player, map, key, clickedBlock.getLocation().clone())) {
                    mark(clickedBlock, areaMarker(key), 65);
                }
            }
            case TEAM -> selectTeam(player, selection.value());
            case PAGE -> switchPalettePage(player, selection.value());
            case REMOVE -> removeAt(player, map, clickedBlock);
        }
    }

    private void applyRegionPosition(Player player, EventMap map, Location location, SetupTool tool, String targetName) {
        if (tool == SetupTool.POS1) {
            pos1Selections.put(player.getUniqueId(), location.clone());
        } else {
            pos2Selections.put(player.getUniqueId(), location.clone());
        }
        saveRegionIfReady(player, map);
        plugin.messages().send(player, "setup-click-saved", Map.of("target", targetName));
    }

    private boolean saveSpecialArea(Player player, EventMap map, String key, Location markerBlock) {
        Location pos1 = areaPos1Selections.get(player.getUniqueId());
        Location pos2 = areaPos2Selections.get(player.getUniqueId());
        if (pos1 == null || pos2 == null) {
            plugin.messages().send(player, "setup-failed", Map.of("reason", "set special area pos1 and pos2 first"));
            return false;
        }
        CuboidRegion previous = map.areas().get(key);
        CuboidRegion area = CuboidRegion.fromCorners(pos1, pos2);
        if (map.eventType() == EventType.CAPTURE_PLAYERS
                && (key.startsWith("capture-zone-") || key.startsWith("jail-zone-") || key.startsWith("free-zone-"))) {
            World world = Bukkit.getWorld(area.worldName());
            if (world != null) {
                area = CuboidRegion.fromCorners(
                        new Location(world, area.minX(), area.minY(), area.minZ()),
                        new Location(world, area.maxX(), area.maxY() + 3, area.maxZ()));
            }
        }
        map.areas().put(key, area);
        mapSetupService.save();
        record(player, SetupAction.area(key, previous, area, markerBlock));
        plugin.messages().send(player, "setup-click-saved", Map.of("target", friendlyKey(key) + " area"));
        return true;
    }

    private void saveDefaultPendingArea(Player player, EventMap map) {
        if (areaPos1Selections.get(player.getUniqueId()) == null || areaPos2Selections.get(player.getUniqueId()) == null) {
            return;
        }
        if (map.eventType() == EventType.PARKOUR && !map.areas().containsKey("release-wall")) {
            saveSpecialArea(player, map, "release-wall", null);
        }
    }

    private void givePalette(Player player, EventType eventType) {
        if (eventType == EventType.BEDWARS) {
            giveBedWarsPalette(player, palettePages.getOrDefault(player.getUniqueId(), "main"));
            return;
        }
        if (eventType == EventType.ELYTRA_RACE) {
            giveElytraPalette(player, palettePages.getOrDefault(player.getUniqueId(), "main"));
            return;
        }
        player.getInventory().setItem(0, tool(Material.LIME_WOOL, SetupTool.POS1, "", "Region Pos 1"));
        player.getInventory().setItem(1, tool(Material.RED_WOOL, SetupTool.POS2, "", "Region Pos 2"));
        player.getInventory().setItem(2, tool(Material.DIAMOND_BLOCK, SetupTool.SPAWN, "", "Spawn Point"));
        player.getInventory().setItem(3, tool(Material.ENDER_EYE, SetupTool.SPECTATOR, "", "Spectator Spawn"));
        int slot = 4;
        switch (eventType) {
            case ELYTRA_RACE -> {
            }
            case PARKOUR -> {
                player.getInventory().setItem(slot++, tool(Material.GOLD_BLOCK, SetupTool.CHECKPOINT, "", "Checkpoint"));
                player.getInventory().setItem(slot++, tool(Material.EMERALD_BLOCK, SetupTool.FINISH, "", "Finish"));
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Release Wall Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Release Wall Pos 2"));
                player.getInventory().setItem(17, tool(Material.GLASS, SetupTool.AREA, "release-wall", "Save Release Wall"));
            }
            case BOAT_RACE -> {
                player.getInventory().setItem(slot++, tool(Material.EMERALD_BLOCK, SetupTool.FINISH, "", "Finish Point"));
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Release Wall Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Release Wall Pos 2"));
                player.getInventory().setItem(slot++, tool(Material.GLASS, SetupTool.AREA, "release-wall", "Save Release Wall"));
            }
            case HORSE_RACE -> {
                player.getInventory().setItem(slot++, tool(Material.EMERALD_BLOCK, SetupTool.FINISH, "", "Finish"));
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Area Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Area Pos 2"));
                player.getInventory().setItem(slot++, tool(Material.LIGHT, SetupTool.AREA, "checkpoint", "Save Checkpoint Area"));
                player.getInventory().setItem(17, tool(Material.GLASS, SetupTool.AREA, "release-wall", "Save Release Wall"));
            }
            case SKYWARS -> {
                player.getInventory().setItem(slot++, tool(Material.CHEST, SetupTool.CHEST, "1", "Tier 1 Chest"));
                player.getInventory().setItem(slot++, tool(Material.TRAPPED_CHEST, SetupTool.CHEST, "2", "Tier 2 Chest"));
                player.getInventory().setItem(slot++, tool(Material.BARREL, SetupTool.CHEST, "3", "Tier 3 Chest"));
            }
            case BEDWARS -> {
            }
            case BLOCK_PARTY -> {
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Color Floor Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Color Floor Pos 2"));
                player.getInventory().setItem(slot++, tool(Material.NOTE_BLOCK, SetupTool.AREA, "color-floor", "Save Color Floor"));
            }
            case RED_LIGHT_GREEN_LIGHT -> {
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Display Area Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Display Area Pos 2"));
                player.getInventory().setItem(slot++, tool(Material.REDSTONE_LAMP, SetupTool.AREA, "light-display", "Save Light Display"));
                player.getInventory().setItem(9, tool(Material.LIME_WOOL, SetupTool.AREA_POS1, "", "Finish Line Pos 1"));
                player.getInventory().setItem(10, tool(Material.GREEN_WOOL, SetupTool.AREA_POS2, "", "Finish Line Pos 2"));
                player.getInventory().setItem(11, tool(Material.EMERALD_BLOCK, SetupTool.AREA, "finish-line", "Save Finish Line"));
            }
            case CAPTURE_THE_FLAG -> {
                player.getInventory().setItem(2, tool(Material.DIAMOND_BLOCK, SetupTool.SPAWN, "team-spawn", "Team Spawn"));
                player.getInventory().setItem(slot++, tool(Material.WHITE_BANNER, SetupTool.POINT, "team-flag", "Team Flag / Capture Point"));
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Area Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Area Pos 2"));
                slot = 9;
                player.getInventory().setItem(slot++, tool(Material.WHITE_WOOL, SetupTool.AREA, "team-base", "Team Base Area"));
                giveTeamSelectors(player);
            }
            case CAPTURE_PLAYERS -> {
                player.getInventory().setItem(2, tool(Material.DIAMOND_BLOCK, SetupTool.SPAWN, "team-spawn", "Team Spawn"));
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Area Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Area Pos 2"));
                player.getInventory().setItem(slot++, tool(Material.IRON_CHAIN, SetupTool.AREA, "capture-zone", "Capture Zone"));
                slot = 9;
                player.getInventory().setItem(slot++, tool(Material.IRON_BARS, SetupTool.AREA, "jail-zone", "Jail Zone"));
                player.getInventory().setItem(slot++, tool(Material.LIME_BANNER, SetupTool.AREA, "free-zone", "Free Zone"));
                giveTeamSelectors(player);
            }
            case FIGHT_1V1, FIGHT_2V2, SUMO_1V1, SUMO_2V2 -> {
                player.getInventory().setItem(2, tool(Material.DIAMOND_BLOCK, SetupTool.SPAWN, "team-spawn", "Team Spawn"));
                giveTeamSelectors(player);
            }
            case SPLEEF, SPLEGG, RUNNER -> {
                player.getInventory().setItem(slot++, tool(Material.LIGHT_BLUE_WOOL, SetupTool.AREA_POS1, "", "Breakable Area Pos 1"));
                player.getInventory().setItem(slot++, tool(Material.BLUE_WOOL, SetupTool.AREA_POS2, "", "Breakable Area Pos 2"));
                player.getInventory().setItem(slot++, tool(Material.DIAMOND_SHOVEL, SetupTool.AREA, "breakable-area", "Save Breakable Area"));
                if (eventType == EventType.SPLEEF) {
                    player.getInventory().setItem(slot++, tool(Material.SNOW_BLOCK, SetupTool.POINT, "breakable-block", "Breakable Block Type"));
                }
            }
            default -> {
            }
        }
        player.getInventory().setItem(8, tool(Material.BARRIER, SetupTool.REMOVE, "", "Remove/Revert"));
    }

    private void giveTeamSelectors(Player player) {
        if (player.getInventory().getItem(7) == null) {
            player.getInventory().setItem(7, tool(Material.NAME_TAG, SetupTool.TEAM, "cycle", "Switch Team (Current Red)"));
        }
        Material[] materials = {
                Material.RED_WOOL,
                Material.BLUE_WOOL,
                Material.GREEN_WOOL,
                Material.YELLOW_WOOL,
                Material.ORANGE_WOOL,
                Material.PURPLE_WOOL,
                Material.CYAN_WOOL,
                Material.WHITE_WOOL
        };
        for (int i = 0; i < materials.length; i++) {
            int team = i + 1;
            player.getInventory().setItem(27 + i, tool(materials[i], SetupTool.TEAM, String.valueOf(team), "Select " + displayTeam(String.valueOf(team))));
        }
    }

    private void giveElytraPalette(Player player, String page) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        if ("checkpoints".equalsIgnoreCase(page)) {
            String selected = selectedElytraCheckpoints.get(player.getUniqueId());
            String current = selected == null ? "None" : selected;
            player.getInventory().setItem(0, tool(Material.IRON_NUGGET, SetupTool.CHECKPOINT, "previous",
                    "Previous Checkpoint (Current: " + current + ")"));
            player.getInventory().setItem(1, new ItemStack(Material.GOLD_BLOCK, 64));
            player.getInventory().setItem(2, tool(Material.GOLD_NUGGET, SetupTool.CHECKPOINT, "next",
                    "Next / New Checkpoint (Current: " + current + ")"));
            player.getInventory().setItem(3, tool(Material.RESPAWN_ANCHOR, SetupTool.CHECKPOINT_SPAWN, "current", "Set Current Respawn Point"));
            player.getInventory().setItem(7, tool(Material.ARROW, SetupTool.PAGE, "elytra-main", "Back to Main Tools"));
        } else {
            player.getInventory().setItem(0, tool(Material.LIME_WOOL, SetupTool.POS1, "", "Region Pos 1"));
            player.getInventory().setItem(1, tool(Material.RED_WOOL, SetupTool.POS2, "", "Region Pos 2"));
            player.getInventory().setItem(2, tool(Material.DIAMOND_BLOCK, SetupTool.SPAWN, "", "Spawn Point"));
            player.getInventory().setItem(3, tool(Material.ENDER_EYE, SetupTool.SPECTATOR, "", "Spectator Spawn"));
            player.getInventory().setItem(4, tool(Material.EMERALD_BLOCK, SetupTool.FINISH, "", "Finish"));
            player.getInventory().setItem(7, tool(Material.ARROW, SetupTool.PAGE, "elytra-checkpoints", "Checkpoint Tools"));
        }
        player.getInventory().setItem(8, tool(Material.BARRIER, SetupTool.REMOVE, "", "Remove/Revert"));
    }

    private void giveBedWarsPalette(Player player, String page) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        if ("team".equalsIgnoreCase(page)) {
            player.getInventory().setItem(0, tool(Material.DIAMOND_BLOCK, SetupTool.SPAWN, "team-spawn", "Team Spawn"));
            player.getInventory().setItem(1, tool(Material.FURNACE, SetupTool.GENERATOR, "team-generator", "Team Generator"));
            player.getInventory().setItem(2, tool(Material.VILLAGER_SPAWN_EGG, SetupTool.POINT, "team-item-shop", "Team Item Shop"));
            player.getInventory().setItem(3, tool(Material.CHEST, SetupTool.POINT, "team-upgrade-shop", "Team Upgrade Shop"));
            player.getInventory().setItem(4, tool(Material.RED_BED, SetupTool.POINT, "team-bed", "Team Bed"));
            player.getInventory().setItem(5, tool(Material.BEACON, SetupTool.POINT, "team-heal-pool", "Team Heal Pool"));
            player.getInventory().setItem(6, tool(Material.NAME_TAG, SetupTool.TEAM, "cycle", "Switch Team (Current " + displayTeam(selectedTeam(player)) + ")"));
            player.getInventory().setItem(7, tool(Material.ARROW, SetupTool.PAGE, "bedwars-main", "Universal Tools"));
            giveTeamSelectors(player);
        } else {
            player.getInventory().setItem(0, tool(Material.LIME_WOOL, SetupTool.POS1, "", "Region Pos 1"));
            player.getInventory().setItem(1, tool(Material.RED_WOOL, SetupTool.POS2, "", "Region Pos 2"));
            player.getInventory().setItem(2, tool(Material.ENDER_EYE, SetupTool.SPECTATOR, "", "Spectator Spawn"));
            player.getInventory().setItem(3, tool(Material.DIAMOND, SetupTool.GENERATOR, "diamond", "Diamond Generator"));
            player.getInventory().setItem(4, tool(Material.EMERALD, SetupTool.GENERATOR, "emerald", "Emerald Generator"));
            player.getInventory().setItem(7, tool(Material.RED_BED, SetupTool.PAGE, "bedwars-team", "Team Tools"));
        }
        player.getInventory().setItem(8, tool(Material.BARRIER, SetupTool.REMOVE, "", "Remove/Revert"));
    }

    private void switchPalettePage(Player player, String value) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long nextAllowed = paletteSwitchCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAllowed) {
            return;
        }
        paletteSwitchCooldowns.put(player.getUniqueId(), now + 500L);
        String page = "main";
        if ("bedwars-team".equalsIgnoreCase(value)) {
            page = "team";
        } else if ("elytra-checkpoints".equalsIgnoreCase(value) || "elytra-areas".equalsIgnoreCase(value)) {
            page = "checkpoints";
        }
        palettePages.put(player.getUniqueId(), page);
        givePalette(player, session.eventType());
        player.updateInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            SetupSession current = sessions.get(player.getUniqueId());
            if (current != null && current.eventType() == session.eventType()) {
                givePalette(player, current.eventType());
                player.updateInventory();
            }
        });
    }

    private ItemStack tool(Material material, SetupTool tool, String value, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(toolKey, PersistentDataType.STRING, tool.name());
            pdc.set(valueKey, PersistentDataType.STRING, value == null ? "" : value);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void saveBedPoint(Player player, EventMap map, Block clickedBlock, String key) {
        if (!(clickedBlock.getBlockData() instanceof Bed clickedBed)) {
            plugin.messages().send(player, "setup-failed", Map.of(
                    "reason", "click an actual placed bed; its material and direction will be saved"
            ));
            return;
        }
        Block footBlock = clickedBed.getPart() == Bed.Part.FOOT
                ? clickedBlock
                : clickedBlock.getRelative(clickedBed.getFacing().getOppositeFace());
        if (!(footBlock.getBlockData() instanceof Bed footBed)) {
            plugin.messages().send(player, "setup-failed", Map.of("reason", "the bed foot block could not be found"));
            return;
        }
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        Location previous = map.points().get(normalizedKey);
        String previousData = map.pointBlockData().get(normalizedKey);
        Location stored = footBlock.getLocation().clone();
        stored.setYaw(player.getLocation().getYaw());
        stored.setPitch(0.0F);
        String serialized = footBed.getAsString();
        map.points().put(normalizedKey, stored);
        map.pointBlockData().put(normalizedKey, serialized);
        mapSetupService.save();
        record(player, SetupAction.mapLocationWithBlockData(
                SetupTool.POINT, normalizedKey, previous, stored.clone(), previousData));
        plugin.messages().send(player, "setup-click-saved", Map.of(
                "target", friendlyKey(normalizedKey) + " (" + footBlock.getType().name().toLowerCase(Locale.ROOT)
                        + ", facing " + footBed.getFacing().name().toLowerCase(Locale.ROOT) + ")"
        ));
    }

    private Optional<ToolSelection> selectionFromHeldItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String toolName = pdc.get(toolKey, PersistentDataType.STRING);
        if (toolName == null) {
            return Optional.empty();
        }
        String value = pdc.get(valueKey, PersistentDataType.STRING);
        try {
            return Optional.of(new ToolSelection(SetupTool.valueOf(toolName), value == null ? "" : value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void removeAt(Player player, EventMap map, Block block) {
        Location location = block.getLocation();
        List<String> removedNames = new ArrayList<>();
        removeLocation(map.spawns(), location, "spawn", removedNames);
        removeLocation(map.checkpoints(), location, "checkpoint", removedNames);
        removeLocationList(map.checkpointBlocks(), location, "checkpoint {key} block", removedNames);
        List<String> removedPointKeys = map.points().entrySet().stream()
                .filter(entry -> matchesClickedBlock(entry.getValue(), location))
                .map(Map.Entry::getKey)
                .toList();
        removeLocation(map.points(), location, "point", removedNames);
        removedPointKeys.forEach(map.pointBlockData()::remove);
        removeArea(map.areas(), location, removedNames);
        removeLocationList(map.chests(), location, "tier {key} chest", removedNames);
        removeLocationList(map.generators(), location, "{key} generator", removedNames);
        if (map.spectatorSpawn() != null && matchesClickedBlock(map.spectatorSpawn(), location)) {
            map.spectatorSpawn(null);
            removedNames.add("spectator spawn");
        }
        String key = blockKey(location);
        BlockData original = originalBlocks.remove(key);
        if (original != null) {
            block.setBlockData(original, false);
            markerStates.remove(key);
            removedNames.add("visual marker");
        }
        if (!removedNames.isEmpty()) {
            mapSetupService.save();
            plugin.messages().send(player, "setup-click-removed", Map.of("removed", String.join(", ", removedNames)));
        } else {
            plugin.messages().send(player, "setup-nothing-removed");
        }
    }

    private void removeLatest(Player player, EventMap map) {
        List<SetupAction> history = actionHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            plugin.messages().send(player, "setup-nothing-removed");
            return;
        }
        SetupAction action = history.remove(history.size() - 1);
        revertAction(player, map, action);
        mapSetupService.save();
        plugin.messages().send(player, "setup-latest-removed", Map.of("removed", action.describe()));
    }

    private void revertSession(Player player, EventMap map) {
        List<SetupAction> history = actionHistory.remove(player.getUniqueId());
        if (history == null) {
            return;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            revertAction(player, map, history.get(i));
        }
        mapSetupService.save();
    }

    private void revertVisualsOnly(List<SetupAction> history) {
        if (history == null) {
            return;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            revertVisual(history.get(i).markerBlock());
        }
    }

    private void revertVisualsOnlyLocations(List<Location> locations) {
        if (locations == null) {
            return;
        }
        for (int i = locations.size() - 1; i >= 0; i--) {
            revertVisual(locations.get(i));
        }
    }

    private void revertAction(Player player, EventMap map, SetupAction action) {
        switch (action.tool()) {
            case POS1 -> {
                if (action.previous() == null) {
                    pos1Selections.remove(player.getUniqueId());
                } else {
                    pos1Selections.put(player.getUniqueId(), action.previous());
                }
            }
            case POS2 -> {
                if (action.previous() == null) {
                    pos2Selections.remove(player.getUniqueId());
                } else {
                    pos2Selections.put(player.getUniqueId(), action.previous());
                }
            }
            case AREA_POS1 -> {
                if (action.previous() == null) {
                    areaPos1Selections.remove(player.getUniqueId());
                } else {
                    areaPos1Selections.put(player.getUniqueId(), action.previous());
                }
            }
            case AREA_POS2 -> {
                if (action.previous() == null) {
                    areaPos2Selections.remove(player.getUniqueId());
                } else {
                    areaPos2Selections.put(player.getUniqueId(), action.previous());
                }
            }
            case SPAWN -> restoreMapLocation(map.spawns(), action);
            case CHECKPOINT, FINISH -> restoreMapLocation(map.checkpoints(), action);
            case CHECKPOINT_BLOCK -> map.checkpointBlocks().getOrDefault(action.key(), new ArrayList<>())
                    .removeIf(location -> sameBlock(location, action.current()));
            case CHECKPOINT_SPAWN -> restoreMapLocation(map.points(), action);
            case POINT -> {
                restoreMapLocation(map.points(), action);
                if (action.previousBlockData() == null) {
                    map.pointBlockData().remove(action.key());
                } else {
                    map.pointBlockData().put(action.key(), action.previousBlockData());
                }
            }
            case AREA -> restoreMapArea(map.areas(), action);
            case SPECTATOR -> map.spectatorSpawn(action.previous());
            case CHEST -> map.chests().getOrDefault(Integer.parseInt(action.key()), new ArrayList<>()).removeIf(location -> sameBlock(location, action.current()));
            case GENERATOR -> map.generators().getOrDefault(action.key(), new ArrayList<>()).removeIf(location -> sameBlock(location, action.current()));
            case REMOVE -> {
            }
        }
        revertVisual(action.markerBlock());
    }

    private void restoreMapLocation(Map<String, Location> locations, SetupAction action) {
        if (action.previous() == null) {
            locations.remove(action.key());
        } else {
            locations.put(action.key(), action.previous());
        }
    }

    private void restoreMapArea(Map<String, CuboidRegion> areas, SetupAction action) {
        if (action.previousArea() == null) {
            areas.remove(action.key());
        } else {
            areas.put(action.key(), action.previousArea());
        }
    }

    private void revertVisual(Location markerBlock) {
        if (markerBlock == null || markerBlock.getWorld() == null) {
            return;
        }
        String key = blockKey(markerBlock);
        BlockData original = originalBlocks.remove(key);
        markerStates.remove(key);
        if (original != null) {
            markerBlock.getBlock().setBlockData(original, false);
        }
    }

    private void record(Player player, SetupAction action) {
        actionHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(action);
    }

    private void removeLocation(Map<String, Location> locations, Location blockLocation, String label, List<String> removedNames) {
        Iterator<Map.Entry<String, Location>> iterator = locations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Location> entry = iterator.next();
            if (matchesClickedBlock(entry.getValue(), blockLocation)) {
                iterator.remove();
                removedNames.add(label + " " + entry.getKey());
            }
        }
    }

    private <T> void removeLocationList(Map<T, List<Location>> locations, Location blockLocation, String label, List<String> removedNames) {
        for (Map.Entry<T, List<Location>> entry : locations.entrySet()) {
            int before = entry.getValue().size();
            entry.getValue().removeIf(location -> matchesClickedBlock(location, blockLocation));
            int removed = before - entry.getValue().size();
            for (int i = 0; i < removed; i++) {
                removedNames.add(label.replace("{key}", String.valueOf(entry.getKey())));
            }
        }
    }

    private void removeArea(Map<String, CuboidRegion> areas, Location blockLocation, List<String> removedNames) {
        Iterator<Map.Entry<String, CuboidRegion>> iterator = areas.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CuboidRegion> entry = iterator.next();
            if (entry.getValue().contains(blockLocation)) {
                iterator.remove();
                removedNames.add(friendlyKey(entry.getKey()) + " area");
            }
        }
    }

    private void saveRegionIfReady(Player player, EventMap map) {
        Location pos1 = pos1Selections.get(player.getUniqueId());
        Location pos2 = pos2Selections.get(player.getUniqueId());
        if (pos1 == null || pos2 == null) {
            return;
        }
        map.region(CuboidRegion.fromCorners(pos1, pos2));
        mapSetupService.save();
        plugin.messages().send(player, "setup-region-saved", Map.of("map", map.id()));
    }

    private void drawRegion(Player player, CuboidRegion region) {
        World world = player.getWorld();
        if (!world.getName().equals(region.worldName())) {
            return;
        }
        double minX = region.minX();
        double minY = region.minY();
        double minZ = region.minZ();
        double maxX = region.maxX() + 1.0D;
        double maxY = region.maxY() + 1.0D;
        double maxZ = region.maxZ() + 1.0D;
        double step = Math.max(1.0D, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) / 28.0D);

        drawLine(player, minX, minY, minZ, maxX, minY, minZ, step);
        drawLine(player, minX, minY, maxZ, maxX, minY, maxZ, step);
        drawLine(player, minX, maxY, minZ, maxX, maxY, minZ, step);
        drawLine(player, minX, maxY, maxZ, maxX, maxY, maxZ, step);

        drawLine(player, minX, minY, minZ, minX, minY, maxZ, step);
        drawLine(player, maxX, minY, minZ, maxX, minY, maxZ, step);
        drawLine(player, minX, maxY, minZ, minX, maxY, maxZ, step);
        drawLine(player, maxX, maxY, minZ, maxX, maxY, maxZ, step);

        drawLine(player, minX, minY, minZ, minX, maxY, minZ, step);
        drawLine(player, maxX, minY, minZ, maxX, maxY, minZ, step);
        drawLine(player, minX, minY, maxZ, minX, maxY, maxZ, step);
        drawLine(player, maxX, minY, maxZ, maxX, maxY, maxZ, step);

        drawNearbyWall(player, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void drawLine(Player player, double x1, double y1, double z1, double x2, double y2, double z2, double step) {
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int points = Math.max(1, (int) Math.ceil(distance / step));
        for (int i = 0; i <= points; i++) {
            double ratio = (double) i / points;
            player.spawnParticle(
                    Particle.FLAME,
                    x1 + (x2 - x1) * ratio,
                    y1 + (y2 - y1) * ratio,
                    z1 + (z2 - z1) * ratio,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private void drawNearbyWall(Player player, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Location location = player.getLocation();
        if (!inside(location, minX, minY, minZ, maxX, maxY, maxZ)) {
            return;
        }
        double threshold = 4.0D;
        if (Math.abs(location.getX() - minX) <= threshold) {
            drawWall(player, minX, minY, minZ, minX, maxY, maxZ);
        }
        if (Math.abs(location.getX() - maxX) <= threshold) {
            drawWall(player, maxX, minY, minZ, maxX, maxY, maxZ);
        }
        if (Math.abs(location.getZ() - minZ) <= threshold) {
            drawWall(player, minX, minY, minZ, maxX, maxY, minZ);
        }
        if (Math.abs(location.getZ() - maxZ) <= threshold) {
            drawWall(player, minX, minY, maxZ, maxX, maxY, maxZ);
        }
        if (Math.abs(location.getY() - minY) <= threshold) {
            drawHorizontalSurface(player, minX, minY, minZ, maxX, maxZ);
        }
        if (Math.abs(location.getY() - maxY) <= threshold) {
            drawHorizontalSurface(player, minX, maxY, minZ, maxX, maxZ);
        }
    }

    private void drawWall(Player player, double x1, double y1, double z1, double x2, double y2, double z2) {
        double xStep = x1 == x2 ? 0.0D : Math.signum(x2 - x1) * 2.0D;
        double zStep = z1 == z2 ? 0.0D : Math.signum(z2 - z1) * 2.0D;
        for (double y = y1; y <= y2; y += 2.0D) {
            if (xStep != 0.0D) {
                for (double x = x1; xStep > 0 ? x <= x2 : x >= x2; x += xStep) {
                    player.spawnParticle(Particle.CLOUD, x, y, z1, 1, 0, 0, 0, 0);
                }
            } else {
                for (double z = z1; zStep > 0 ? z <= z2 : z >= z2; z += zStep) {
                    player.spawnParticle(Particle.CLOUD, x1, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void drawHorizontalSurface(Player player, double minX, double y, double minZ, double maxX, double maxZ) {
        double step = Math.max(2.0D, Math.max(maxX - minX, maxZ - minZ) / 32.0D);
        for (double x = minX; x <= maxX; x += step) {
            for (double z = minZ; z <= maxZ; z += step) {
                player.spawnParticle(Particle.CLOUD, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private boolean inside(Location location, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    private void captureInventory(Player player) {
        inventorySnapshots.putIfAbsent(player.getUniqueId(), new SetupInventorySnapshot(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand().clone()
        ));
    }

    private void restoreInventory(Player player) {
        SetupInventorySnapshot snapshot = inventorySnapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        player.getInventory().setContents(snapshot.contents());
        player.getInventory().setArmorContents(snapshot.armor());
        player.getInventory().setItemInOffHand(snapshot.offhand());
        player.updateInventory();
        plugin.messages().send(player, "setup-inventory-restored");
    }

    private Location orientedTopCenter(Player player, Location blockLocation) {
        Location location = blockLocation.clone().add(0.5D, 1.0D, 0.5D);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        return location;
    }

    private Location centeredBlock(Player player, Location blockLocation) {
        Location location = blockLocation.clone().add(0.5D, 0.5D, 0.5D);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        return location;
    }

    private Location flagBlockLocation(Player player, Location blockLocation) {
        Location location = blockLocation.clone();
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0.0F);
        return location;
    }

    private void mark(Block block, Material material, int priority) {
        String key = blockKey(block.getLocation());
        MarkerState current = markerStates.get(key);
        if (current != null && current.priority() > priority) {
            return;
        }
        originalBlocks.putIfAbsent(key, block.getBlockData().clone());
        markerStates.put(key, new MarkerState(priority, material));
        block.setType(material, false);
    }

    private void markVisualOnly(Player player, Location location, Material material, int priority) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Block block = location.getBlock();
        mark(block, material, priority);
        visualOnlyMarkers.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(block.getLocation());
    }

    private void markExistingSetup(Player player, EventMap map) {
        visualOnlyMarkers.remove(player.getUniqueId());
        if (map.region() != null) {
            markVisualOnly(player, new Location(Bukkit.getWorld(map.region().worldName()), map.region().minX(), map.region().minY(), map.region().minZ()), Material.LIME_WOOL, 5);
            markVisualOnly(player, new Location(Bukkit.getWorld(map.region().worldName()), map.region().maxX(), map.region().maxY(), map.region().maxZ()), Material.RED_WOOL, 5);
        }
        map.spawns().values().forEach(location -> markVisualOnly(player, markerBlockBelow(location), Material.DIAMOND_BLOCK, 5));
        markVisualOnly(player, markerBlockBelow(map.spectatorSpawn()), Material.ENDER_CHEST, 5);
        map.checkpoints().forEach((key, location) -> markVisualOnly(player, markerBlockBelow(location),
                key.toLowerCase(Locale.ROOT).startsWith("finish") ? Material.EMERALD_BLOCK : Material.GOLD_BLOCK, 5));
        map.checkpointBlocks().values().forEach(locations ->
                locations.forEach(location -> markVisualOnly(player, location, Material.GOLD_BLOCK, 5)));
        map.points().forEach((key, location) -> {
            if (!key.toLowerCase(Locale.ROOT).startsWith("bed-")) {
                markVisualOnly(player, location, pointMarker(key), 5);
            }
        });
        map.chests().forEach((tier, locations) -> locations.forEach(location -> markVisualOnly(player, location, chestMarker(tier), 5)));
        map.generators().forEach((type, locations) -> locations.forEach(location -> markVisualOnly(player, location, generatorMarker(type), 5)));
        map.areas().forEach((key, area) -> {
            World world = Bukkit.getWorld(area.worldName());
            if (world == null) {
                return;
            }
            Material marker = areaMarker(key);
            markVisualOnly(player, new Location(world, area.minX(), area.minY(), area.minZ()), marker, 5);
            markVisualOnly(player, new Location(world, area.maxX(), area.maxY(), area.maxZ()), marker, 5);
        });
    }

    private Location markerBlockBelow(Location location) {
        if (location == null) {
            return null;
        }
        return location.clone().subtract(0.0D, 1.0D, 0.0D);
    }

    private Material chestMarker(int tier) {
        return switch (tier) {
            case 2 -> Material.TRAPPED_CHEST;
            case 3 -> Material.BARREL;
            default -> Material.CHEST;
        };
    }

    private Material generatorMarker(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.equals("diamond")) {
            return Material.LIGHT_BLUE_CONCRETE;
        }
        if (lower.equals("emerald")) {
            return Material.LIME_CONCRETE;
        }
        if (lower.equals("team") || lower.startsWith("team-") || lower.startsWith("solo-")) {
            return Material.ORANGE_CONCRETE;
        }
        return Material.GRAY_CONCRETE;
    }

    private Material pointMarker(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("flag")) {
            return Material.WHITE_BANNER;
        }
        if (lower.startsWith("item-shop")) {
            return Material.EMERALD_BLOCK;
        }
        if (lower.startsWith("upgrade-shop") || lower.startsWith("team-shop")) {
            return Material.CHEST;
        }
        if (lower.startsWith("bed")) {
            return Material.RED_BED;
        }
        if (lower.equals("breakable-block")) {
            return Material.SNOW_BLOCK;
        }
        return Material.BEACON;
    }

    private Material areaMarker(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("capture-zone-")) {
            return Material.IRON_CHAIN;
        }
        if (lower.startsWith("jail-zone-")) {
            return Material.IRON_BARS;
        }
        if (lower.startsWith("free-zone-")) {
            return Material.LIME_BANNER;
        }
        return switch (lower) {
            case "color-floor" -> Material.NOTE_BLOCK;
            case "release-wall" -> Material.GLASS;
            case "light-display" -> Material.REDSTONE_LAMP;
            case "finish-line" -> Material.EMERALD_BLOCK;
            case "capture-zone" -> Material.IRON_CHAIN;
            case "base-zone", "base-1", "base-2", "base-3", "base-4", "base-5", "base-6", "base-7", "base-8" -> Material.WHITE_WOOL;
            case "jail-zone" -> Material.IRON_BARS;
            case "free-zone" -> Material.LIME_BANNER;
            case "breakable-area" -> Material.LIGHT_BLUE_CONCRETE;
            default -> Material.LODESTONE;
        };
    }

    private boolean sameBlock(Location left, Location right) {
        return left != null && right != null
                && left.getWorld() != null
                && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private boolean matchesClickedBlock(Location stored, Location clickedBlock) {
        if (sameBlock(stored, clickedBlock)) {
            return true;
        }
        return stored != null && (sameBlock(stored.clone().subtract(0.0D, 1.0D, 0.0D), clickedBlock)
                || sameBlock(stored.clone().subtract(0.5D, 1.0D, 0.5D), clickedBlock));
    }

    private String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private String nextId(String prefix, int start) {
        return prefix + start;
    }

    private String checkpointId(EventMap map, Location blockLocation, String value) {
        if (value == null || value.isBlank()) {
            return existingLocationKey(map.checkpoints(), blockLocation).orElseGet(() -> nextId("cp", map.checkpoints().size() + 1));
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("ring") || lower.equals("cp")) {
            return existingLocationKey(map.checkpoints(), blockLocation).orElseGet(() ->
                    nextId(lower, (int) map.checkpoints().keySet().stream().filter(key -> key.startsWith(lower)).count() + 1));
        }
        return lower;
    }

    private String checkpointSpawnId(EventMap map, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("checkpoint-spawn")) {
            return nextId("cp", (int) map.points().keySet().stream()
                    .filter(key -> key.startsWith("checkpoint-spawn-"))
                    .count() + 1);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("checkpoint-spawn-")) {
            return lower.substring("checkpoint-spawn-".length());
        }
        return lower;
    }

    private void selectElytraCheckpoint(Player player, EventMap map, String direction) {
        int highest = highestElytraCheckpoint(map);
        String current = selectedElytraCheckpoints.get(player.getUniqueId());
        int currentNumber = checkpointNumberValue(current);
        int selectedNumber;
        if ("previous".equalsIgnoreCase(direction)) {
            selectedNumber = currentNumber <= 1 ? Math.max(1, highest) : currentNumber - 1;
        } else if (currentNumber <= 0) {
            selectedNumber = 1;
        } else {
            selectedNumber = currentNumber + 1;
        }
        String checkpointId = "cp" + selectedNumber;
        selectedElytraCheckpoints.put(player.getUniqueId(), checkpointId);
        map.checkpointBlocks().computeIfAbsent(checkpointId, ignored -> new ArrayList<>());
        mapSetupService.save();
        giveElytraPalette(player, "checkpoints");
        player.updateInventory();
        plugin.messages().send(player, "setup-click-saved", Map.of(
                "target", checkpointId + " selected; place gold blocks to define its exact checkpoint area"
        ));
    }

    private int highestElytraCheckpoint(EventMap map) {
        return java.util.stream.Stream.of(
                        map.checkpointBlocks().keySet(),
                        map.checkpoints().keySet(),
                        map.areas().keySet()
                )
                .flatMap(java.util.Collection::stream)
                .mapToInt(this::checkpointNumberValue)
                .max()
                .orElse(0);
    }

    private int checkpointNumberValue(String checkpointId) {
        if (checkpointId == null) {
            return 0;
        }
        String digits = checkpointId.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private void replenishElytraCheckpointBlocks(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.GOLD_BLOCK && !item.hasItemMeta()) {
                item.setAmount(64);
                return;
            }
        }
        player.getInventory().setItem(1, new ItemStack(Material.GOLD_BLOCK, 64));
    }

    private String finishId(EventMap map, EventType eventType, Location blockLocation) {
        if (eventType != EventType.PARKOUR && eventType != EventType.BOAT_RACE
                && eventType != EventType.HORSE_RACE && eventType != EventType.ELYTRA_RACE) {
            return "finish";
        }
        return existingLocationKey(map.checkpoints(), blockLocation).orElseGet(() ->
                nextId("finish-", (int) map.checkpoints().keySet().stream()
                        .filter(key -> key.toLowerCase(Locale.ROOT).startsWith("finish"))
                        .count() + 1));
    }

    private String pointId(Player player, EventMap map, Location blockLocation, String value) {
        if (value == null || value.isBlank()) {
            return existingLocationKey(map.points(), blockLocation).orElseGet(() -> nextId("point", map.points().size() + 1));
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("team-flag")) {
            return "flag-" + selectedTeam(player);
        }
        if (lower.equals("team-bed")) {
            return "bed-" + selectedTeam(player);
        }
        if (lower.equals("team-item-shop")) {
            return "item-shop-" + selectedTeam(player);
        }
        if (lower.equals("team-upgrade-shop")) {
            return "upgrade-shop-" + selectedTeam(player);
        }
        if (lower.equals("bed") || lower.equals("item-shop") || lower.equals("upgrade-shop") || lower.equals("team-shop")) {
            return existingLocationKey(map.points(), blockLocation).orElseGet(() ->
                    nextId(lower + "-", (int) map.points().keySet().stream().filter(key -> key.startsWith(lower)).count() + 1));
        }
        return lower;
    }

    private String generatorId(Player player, String value) {
        if (value == null || value.isBlank()) {
            return "solo";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("team-generator")) {
            return "solo-" + selectedTeam(player);
        }
        return lower;
    }

    private String areaId(Player player, String value) {
        if (value == null || value.isBlank()) {
            return "area";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        SetupSession session = sessions.get(player.getUniqueId());
        if (lower.equals("team-base")) {
            return "base-" + selectedTeam(player);
        }
        if (session != null && session.eventType() == EventType.CAPTURE_PLAYERS
                && (lower.equals("capture-zone") || lower.equals("jail-zone") || lower.equals("free-zone"))) {
            return lower + "-" + selectedTeam(player);
        }
        if (lower.equals("checkpoint")) {
            return nextId("checkpoint-", (int) eventAreaCount(player, "checkpoint-") + 1);
        }
        return lower;
    }

    private long eventAreaCount(Player player, String prefix) {
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return 0;
        }
        return mapSetupService.find(session.eventType(), session.mapId())
                .map(map -> map.areas().keySet().stream().filter(key -> key.startsWith(prefix)).count())
                .orElse(0L);
    }

    private void selectTeam(Player player, String value) {
        String team;
        if (value != null && value.equalsIgnoreCase("cycle")) {
            int current = Integer.parseInt(selectedTeams.getOrDefault(player.getUniqueId(), "1"));
            team = String.valueOf(current >= 8 ? 1 : current + 1);
            updateHeldTeamSelector(player, team);
        } else {
            team = value == null || value.isBlank() ? "1" : value;
        }
        selectedTeams.put(player.getUniqueId(), team);
        plugin.messages().send(player, "setup-click-saved", Map.of("target", displayTeam(team) + " selected"));
    }

    private String selectedTeam(Player player) {
        return selectedTeams.getOrDefault(player.getUniqueId(), "1");
    }

    private void updateHeldTeamSelector(Player player, String team) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Switch Team (Current " + displayTeam(team) + ")");
        item.setItemMeta(meta);
    }

    private String displayTeam(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> "Red";
            case "2", "blue" -> "Blue";
            case "3", "green" -> "Green";
            case "4", "yellow" -> "Yellow";
            case "5", "orange" -> "Orange";
            case "6", "purple" -> "Purple";
            case "7", "cyan" -> "Cyan";
            case "8", "white" -> "White";
            default -> "Team " + team;
        };
    }

    private Optional<String> existingLocationKey(Map<String, Location> locations, Location blockLocation) {
        return locations.entrySet().stream()
                .filter(entry -> matchesClickedBlock(entry.getValue(), blockLocation))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private String friendlyKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
    }

    private String describe(SetupSession session) {
        return session.value() == null || session.value().isBlank()
                ? session.tool().name().toLowerCase(Locale.ROOT)
                : session.tool().name().toLowerCase(Locale.ROOT) + " " + session.value();
    }

    private record ToolSelection(SetupTool tool, String value) {
    }

    private record MarkerState(int priority, Material material) {
    }

    private record SetupAction(SetupTool tool, String key, Location previous, Location current, Location markerBlock,
                               CuboidRegion previousArea, CuboidRegion currentArea, String previousBlockData) {
        static SetupAction pos(SetupTool tool, Location previous, Location current, Location markerBlock) {
            return new SetupAction(tool, tool.name().toLowerCase(Locale.ROOT), previous, current, markerBlock, null, null, null);
        }

        static SetupAction mapLocation(SetupTool tool, String key, Location previous, Location current, Location markerBlock) {
            return new SetupAction(tool, key, previous, current, markerBlock, null, null, null);
        }

        static SetupAction mapLocationWithBlockData(SetupTool tool, String key, Location previous, Location current,
                                                    String previousBlockData) {
            return new SetupAction(tool, key, previous, current, null, null, null, previousBlockData);
        }

        static SetupAction listLocation(SetupTool tool, String key, Location current, Location markerBlock) {
            return new SetupAction(tool, key, null, current, markerBlock, null, null, null);
        }

        static SetupAction area(String key, CuboidRegion previous, CuboidRegion current, Location markerBlock) {
            return new SetupAction(SetupTool.AREA, key, null, null, markerBlock, previous, current, null);
        }

        String describe() {
            return switch (tool) {
                case SPAWN -> "spawn " + key;
                case CHECKPOINT -> "checkpoint " + key;
                case CHECKPOINT_BLOCK -> "checkpoint block for " + key;
                case CHECKPOINT_SPAWN -> key + " point";
                case FINISH -> "finish";
                case SPECTATOR -> "spectator spawn";
                case CHEST -> "tier " + key + " chest";
                case GENERATOR -> key + " generator";
                case POINT -> key + " point";
                case AREA_POS1 -> "special area pos1";
                case AREA_POS2 -> "special area pos2";
                case AREA -> key + " area";
                case TEAM -> "team " + key;
                case PAGE -> "tool page";
                case POS1 -> "region pos1";
                case POS2 -> "region pos2";
                case REMOVE -> "remove";
            };
        }
    }
}
