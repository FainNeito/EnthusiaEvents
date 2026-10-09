package org.enthusia.events.infrastructure;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Bed;
import org.enthusia.events.domain.PreflightFinding;
import org.enthusia.events.domain.PreflightFinding.Severity;
import org.enthusia.events.event.*;
import org.enthusia.events.EnthusiaEventsPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only, loaded-chunk diagnostics. Bounded point count; never generates a world/chunk. */
public final class ArenaPreflight {
    private final EnthusiaEventsPlugin plugin;
    private final MapSetupService maps;
    public ArenaPreflight(EnthusiaEventsPlugin plugin, MapSetupService maps) { this.plugin = plugin; this.maps = maps; }

    public List<PreflightFinding> inspect(EventMap map) {
        List<PreflightFinding> findings = new ArrayList<>();
        for (String error : maps.validate(map)) add(findings, Severity.BLOCKER, map.id(), error, "Use /ee setup to repair saved setup");
        World world = map.worldName() == null ? null : plugin.getServer().getWorld(map.worldName());
        if (world == null) {
            add(findings, Severity.BLOCKER, map.id(), "world unavailable", "Load the original world; this check does not create it");
            return List.copyOf(findings);
        }
        inspectRegion(findings, map, world);
        return inspectLoadedMap(findings, map, world);
    }
    private List<PreflightFinding> inspectLoadedMap(List<PreflightFinding> findings, EventMap map, World world) {
        Budget budget = new Budget(Math.clamp(plugin.getConfig().getInt("preflight.max-points", 128), 1, 512));
        if (!inspectSpawns(findings, map, world, budget)) return budgetExceeded(findings, map);
        spawn(findings, map, "spectator", map.spectatorSpawn(), world, false);
        if (!inspectChests(findings, map, world, budget)) return budgetExceeded(findings, map);
        if (!inspectBeds(findings, map, world, budget)) return budgetExceeded(findings, map);
        if (!inspectAreas(findings, map, world, budget)) return budgetExceeded(findings, map);
        return List.copyOf(findings);
    }
    private static void inspectRegion(List<PreflightFinding> findings, EventMap map, World world) {
        if (map.region() != null && (!Objects.equals(map.region().worldName(), world.getName()) || !validBounds(map.region(), world)))
            add(findings, Severity.BLOCKER, map.id(), "region world/bounds invalid", "Re-select both region corners in the original world");
    }
    private boolean inspectSpawns(List<PreflightFinding> findings, EventMap map, World world, Budget budget) {
        for (var entry : map.spawns().entrySet()) {
            if (!budget.take()) return false;
            spawn(findings, map, entry.getKey(), entry.getValue(), world, needsFloor(map.eventType()));
        }
        return true;
    }
    private boolean inspectChests(List<PreflightFinding> findings, EventMap map, World world, Budget budget) {
        for (var tier : map.chests().entrySet()) for (Location location : tier.getValue()) {
            if (!budget.take()) return false;
            inspectChest(findings, map, world, tier.getKey(), location);
        }
        return true;
    }
    private void inspectChest(List<PreflightFinding> findings, EventMap map, World world, int tier, Location location) {
        String label = "tier " + tier + " " + coordinates(location);
        if (tier < 1 || tier > 3) add(findings, Severity.BLOCKER, label, "invalid tier", "Use tier 1-3");
        if (inspectable(findings, label, location, world) && !(location.getBlock().getState() instanceof Container))
            add(findings, Severity.BLOCKER, label, "not a container", "Re-select the actual loot container");
        if (location != null && map.region() != null && !map.region().contains(location))
            add(findings, Severity.BLOCKER, label, "outside arena", "Move the saved chest point inside the region");
    }
    private boolean inspectBeds(List<PreflightFinding> findings, EventMap map, World world, Budget budget) {
        if (map.eventType() != EventType.BEDWARS) return true;
        for (var entry : map.points().entrySet()) {
            if (!entry.getKey().startsWith("bed-")) continue;
            if (!budget.take()) return false;
            inspectBed(findings, entry.getKey(), entry.getValue(), world);
        }
        return true;
    }
    private void inspectBed(List<PreflightFinding> findings, String label, Location location, World world) {
        if (!inspectable(findings, label, location, world)) return;
        if (!(location.getBlock().getBlockData() instanceof Bed bed)) {
            add(findings, Severity.BLOCKER, label, "saved bed is missing", "Re-select a paired team bed"); return;
        }
        var other = location.getBlock().getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
        if (!world.isChunkLoaded(other.getX() >> 4, other.getZ() >> 4)) {
            add(findings, Severity.UNKNOWN, label, "bed partner chunk unloaded", "Load the existing chunk and inspect again");
        } else if (!matchingBed(other, location, bed)) {
            add(findings, Severity.BLOCKER, label, "bed partner invalid", "Repair both matching bed halves");
        }
    }
    private static boolean matchingBed(org.bukkit.block.Block other, Location location, Bed bed) {
        return other.getBlockData() instanceof Bed partner && partner.getPart() != bed.getPart()
                && partner.getFacing() == bed.getFacing() && other.getType() == location.getBlock().getType();
    }
    private boolean inspectAreas(List<PreflightFinding> findings, EventMap map, World world, Budget budget) {
        for (var entry : map.areas().entrySet()) {
            if (!budget.take()) return false;
            if (map.region() == null) break;
            CuboidRegion area = entry.getValue();
            if (!validBounds(area, world) || !contains(map.region(), area))
                add(findings, Severity.BLOCKER, entry.getKey(), "area outside arena or wrong world", "Re-select area corners inside the arena");
        }
        return true;
    }
    private static List<PreflightFinding> budgetExceeded(List<PreflightFinding> findings, EventMap map) {
        unknownBudget(findings, map);
        return List.copyOf(findings);
    }
    private static final class Budget {
        private int remaining;
        private Budget(int maximum) { remaining = maximum; }
        private boolean take() { remaining--; return remaining >= 0; }
    }

    public static boolean ready(List<PreflightFinding> findings) {
        return findings.stream().noneMatch(f -> f.severity() != Severity.WARNING);
    }
    private void spawn(List<PreflightFinding> findings, EventMap map, String label, Location location, World world, boolean floor) {
        String pointLabel = label + " " + coordinates(location);
        if (!inspectable(findings, pointLabel, location, world)) return;
        if (map.region() != null && !map.region().contains(location)) add(findings, Severity.BLOCKER, label, "outside arena", "Re-select the spawn inside the region");
        if (!location.getBlock().isPassable() || !location.clone().add(0, 1, 0).getBlock().isPassable())
            add(findings, Severity.BLOCKER, label, "player space obstructed", "Provide clear feet/head space");
        if (floor && !location.clone().subtract(0, 1, 0).getBlock().getType().isSolid())
            add(findings, Severity.BLOCKER, label, "support missing", "Provide a safe solid spawn floor");
    }
    private boolean inspectable(List<PreflightFinding> findings, String label, Location location, World world) {
        if (!validLocation(location, world)) {
            add(findings, Severity.BLOCKER, label, "location unavailable/invalid", "Re-select location in the original world"); return false;
        }
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            add(findings, Severity.UNKNOWN, label, "chunk unloaded", "Load the existing chunk and inspect again"); return false;
        }
        return true;
    }
    private static boolean validLocation(Location location, World world) {
        return location != null && location.getWorld() != null && location.getWorld().getUID().equals(world.getUID())
                && finiteLocation(location) && location.getBlockY() > world.getMinHeight() && location.getBlockY() + 1 < world.getMaxHeight();
    }
    private static boolean finiteLocation(Location location) {
        return Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ());
    }
    private static boolean needsFloor(EventType type) { return type != EventType.ELYTRA_RACE && type != EventType.BOAT_RACE; }
    private static boolean validBounds(CuboidRegion region, World world) {
        return region != null && Objects.equals(region.worldName(), world.getName())
                && validAxis(region.minX(), region.maxX()) && validAxis(region.minY(), region.maxY())
                && validAxis(region.minZ(), region.maxZ()) && region.minY() >= world.getMinHeight() && region.maxY() < world.getMaxHeight();
    }
    private static boolean validAxis(double minimum, double maximum) {
        return Double.isFinite(minimum) && Double.isFinite(maximum) && minimum <= maximum;
    }
    public static boolean contains(CuboidRegion outer, CuboidRegion inner) {
        return outer != null && inner != null && Objects.equals(outer.worldName(), inner.worldName())
                && containsAxis(outer.minX(), outer.maxX(), inner.minX(), inner.maxX())
                && containsAxis(outer.minY(), outer.maxY(), inner.minY(), inner.maxY())
                && containsAxis(outer.minZ(), outer.maxZ(), inner.minZ(), inner.maxZ());
    }
    private static boolean containsAxis(double outerMin, double outerMax, double innerMin, double innerMax) {
        return innerMin >= outerMin && innerMax <= outerMax;
    }
    private static String coordinates(Location l) { return l == null ? "unset" : l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }
    private static void unknownBudget(List<PreflightFinding> f, EventMap m) { add(f, Severity.UNKNOWN, m.id(), "point budget exceeded", "Reduce points or raise preflight.max-points (maximum 512)"); }
    private static void add(List<PreflightFinding> f, Severity s, String label, String problem, String repair) { f.add(new PreflightFinding(s, label, problem, repair)); }
}
