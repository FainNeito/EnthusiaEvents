package org.enthusia.events.infrastructure;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.block.TileState;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.domain.BuildScore;
import org.enthusia.events.event.*;
import java.util.*;
import java.util.function.Consumer;

/** Private opt-in game, with bounded plot work and session/round callback fences. */
public final class Speedbuilders implements Listener {
    private static final String PLOT_PREFIX = "plot-";
    private static final String TEMPLATE_AREA = "build-template";
    private static final String AIR_BLOCK_DATA = "minecraft:air";
    private static final String BUILD_PHASE = "build";
    private final EnthusiaEventsPlugin plugin;
    private final EventManager manager;
    private EventSession tracked;
    private final Map<UUID, CuboidRegion> plots = new LinkedHashMap<>();
    private final Map<UUID, Integer> totals = new LinkedHashMap<>();
    private Map<Offset, String> template = Map.of();
    private List<Material> palette = List.of();
    private BukkitTask task;
    private String phase = "idle";
    private int round;
    private long generation;
    public Speedbuilders(EnthusiaEventsPlugin plugin, EventManager manager) { this.plugin = plugin; this.manager = manager; }

    public List<String> validate(EventSession session) {
        if (!session.privateSession() || !plugin.getConfig().getBoolean("speedbuilders.enabled", false)) return List.of("Speedbuilders requires an enabled private session");
        if (!plugin.arenaRecovery().enabled(EventType.SPEEDBUILDERS)) return List.of("Speedbuilders requires durable arena recovery");
        EventMap map = session.selectedMap();
        if (map == null || map.region() == null) return List.of("Arena region missing");
        List<String> errors = new ArrayList<>();
        List<Map.Entry<String, CuboidRegion>> candidates = map.areas().entrySet().stream().filter(e -> e.getKey().startsWith(PLOT_PREFIX)).sorted(Map.Entry.comparingByKey()).toList();
        CuboidRegion source = map.areas().get(TEMPLATE_AREA);
        if (!hasRequiredPlots(source, candidates.size(), session.participants().size())) return List.of("Set build-template and one plot-<id> area/spawn per player");
        if (!validTemplateBounds(source, map.region())) errors.add("Template must be 1-512 cells inside the arena");
        if (candidates.size() > 8) errors.add("Initial release supports at most eight plots");
        validatePlots(map, source, candidates, errors);
        if (!errors.isEmpty()) return List.copyOf(errors);
        World world = plugin.getServer().getWorld(map.worldName());
        if (world == null) return List.of("Original world is unavailable");
        validateTemplate(source, candidates, map, world, errors);
        return List.copyOf(errors);
    }
    private static boolean hasRequiredPlots(CuboidRegion source, int plots, int players) { return source != null && plots >= players && plots > 0; }
    private static boolean validTemplateBounds(CuboidRegion source, CuboidRegion arena) {
        return integerBounds(source) && volume(source) >= 1 && volume(source) <= 512 && ArenaPreflight.contains(arena, source);
    }
    private void validatePlots(EventMap map, CuboidRegion source, List<Map.Entry<String, CuboidRegion>> candidates, List<String> errors) {
        for (int i = 0; i < candidates.size(); i++) {
            var entry = candidates.get(i);
            validatePlot(map, source, entry, errors);
            for (int j = 0; j < i; j++) if (overlaps(entry.getValue(), candidates.get(j).getValue())) errors.add(entry.getKey() + " overlaps another plot");
        }
    }
    private void validatePlot(EventMap map, CuboidRegion source, Map.Entry<String, CuboidRegion> entry, List<String> errors) {
        CuboidRegion plot = entry.getValue();
        if (!integerBounds(plot) || !sameSize(source, plot) || !ArenaPreflight.contains(map.region(), plot)) errors.add(entry.getKey() + " must have template dimensions and be inside the arena");
        validatePlotSpawn(map.spawns().get(entry.getKey()), plot, entry.getKey(), errors);
        if (overlaps(source, plot)) errors.add(entry.getKey() + " overlaps the source template");
    }
    private static void validatePlotSpawn(Location spawn, CuboidRegion plot, String label, List<String> errors) {
        if (!plot.contains(spawn)) errors.add(label + " requires a matching safe spawn inside its build volume");
        if (spawn != null && (spawn.getBlockY() != plot.minY() || plot.maxY() - plot.minY() < 1)) errors.add(label + " spawn must be on the bottom layer of a build volume at least two blocks high; its solid floor stays outside the build volume");
    }
    private void validateTemplate(CuboidRegion source, List<Map.Entry<String, CuboidRegion>> candidates, EventMap map, World world, List<String> errors) {
        try {
            Map<Offset, String> decoded = read(source, world);
            if (decoded.isEmpty()) errors.add("Template cannot be empty");
            for (String state : decoded.values()) if (!allowed(plugin.getServer().createBlockData(state).getMaterial())) { errors.add("Template contains unsupported material"); break; }
            if (decoded.values().stream().map(s -> plugin.getServer().createBlockData(s).getMaterial()).distinct().count() > 12) errors.add("Template palette exceeds twelve materials");
            validateViewingCorners(decoded, candidates, map, errors);
        } catch (RuntimeException failure) { errors.add("Template unavailable: " + failure.getMessage()); }
    }
    private static void validateViewingCorners(Map<Offset, String> decoded, List<Map.Entry<String, CuboidRegion>> candidates, EventMap map, List<String> errors) {
            for (var entry : candidates) {
                Location spawn = map.spawns().get(entry.getKey());
                Offset feet = new Offset(spawn.getBlockX() - (int) entry.getValue().minX(), spawn.getBlockY() - (int) entry.getValue().minY(), spawn.getBlockZ() - (int) entry.getValue().minZ());
                if (decoded.containsKey(feet) || decoded.containsKey(new Offset(feet.x(), feet.y() + 1, feet.z()))) errors.add(entry.getKey() + " template obstructs the matching spawn; use the same clear viewing corner in every plot");
            }
    }
    public void start(EventSession session) {
        stop();
        List<String> errors = validate(session);
        if (!errors.isEmpty()) { manager.stop("Speedbuilders preparation failed: " + String.join("; ", errors)); return; }
        tracked = session;
        List<CuboidRegion> available = session.selectedMap().areas().entrySet().stream().filter(e -> e.getKey().startsWith(PLOT_PREFIX)).sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
        int i = 0;
        List<String> names = session.selectedMap().areas().keySet().stream().filter(key -> key.startsWith(PLOT_PREFIX)).sorted().toList();
        for (UUID player : session.participants()) {
            plots.put(player, available.get(i)); totals.put(player, 0);
            Player online = plugin.getServer().getPlayer(player);
            if (online == null || !manager.teleportGameParticipant(online, session.selectedMap().spawns().get(names.get(i++)))) {
                manager.stop("Speedbuilders plot teleport failed"); stop(); return;
            }
        }
        template = read(session.selectedMap().areas().get(TEMPLATE_AREA), plugin.getServer().getWorld(session.selectedMap().worldName()));
        palette = template.values().stream().map(s -> plugin.getServer().createBlockData(s).getMaterial()).distinct().toList();
        round = 0;
        nextRound();
    }
    private void nextRound() {
        if (!current()) { stop(); return; }
        round++;
        phase = "preparing";
        mutatePlots(true, () -> {
            phase = "memory";
            players(p -> { p.setGameMode(GameMode.ADVENTURE); p.getInventory().clear(); p.sendMessage("Speedbuilders round " + round + ": memorize the build."); });
            countdown(Math.clamp(plugin.getConfig().getInt("speedbuilders.memory-seconds", 30), 5, 120), this::build);
        });
    }
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // Distinct inventory stacks are required per player and stack.
    private void build() {
        phase = "clearing";
        mutatePlots(false, () -> {
            phase = BUILD_PHASE;
            players(p -> {
                p.setGameMode(GameMode.SURVIVAL); p.getInventory().clear();
                for (Material material : palette) {
                    long required = template.values().stream().filter(s -> plugin.getServer().createBlockData(s).getMaterial() == material).count();
                    for (long count = 0; count < required; count += 64) p.getInventory().addItem(new ItemStack(material, 64));
                }
                p.sendMessage("Build the remembered template in your plot. Extra blocks reduce your score.");
            });
            countdown(Math.clamp(plugin.getConfig().getInt("speedbuilders.build-seconds", 90), 10, 300), this::score);
        });
    }
    private void score() {
        phase = "scoring";
        players(p -> { p.setGameMode(GameMode.ADVENTURE); p.getInventory().clear(); });
        try {
            World world = plugin.getServer().getWorld(tracked.selectedMap().worldName());
            for (var entry : plots.entrySet()) {
                if (!tracked.participants().contains(entry.getKey())) continue;
                BuildScore score = BuildScore.compare(template, read(entry.getValue(), world));
                totals.merge(entry.getKey(), score.points(), Integer::sum);
                Player p = plugin.getServer().getPlayer(entry.getKey());
                if (p != null) p.sendMessage("Score " + score.points() + "/" + score.target() + "; matched=" + score.matched() + " missing=" + score.missing() + " wrong=" + score.wrong() + " extra=" + score.extra());
            }
            if (round < Math.clamp(plugin.getConfig().getInt("speedbuilders.rounds", 5), 1, 10)) nextRound(); else finish();
        } catch (RuntimeException failure) { manager.stop("Speedbuilders scoring failed; arena recovery retained"); stop(); }
    }
    private void finish() {
        phase = "complete";
        int best = totals.entrySet().stream().filter(e -> tracked.participants().contains(e.getKey())).mapToInt(Map.Entry::getValue).max().orElse(0);
        List<UUID> winners = totals.entrySet().stream().filter(e -> tracked.participants().contains(e.getKey()) && e.getValue() == best).map(Map.Entry::getKey).toList();
        String names = winners.stream().map(id -> { Player p = plugin.getServer().getPlayer(id); return p == null ? id.toString() : p.getName(); }).collect(java.util.stream.Collectors.joining(", "));
        manager.messageEventPlayers("Speedbuilders complete: " + names + " (" + best + " points). Equal top scores share the result; currency rewards are disabled.");
        manager.endActiveEvent(winners);
        stop();
    }
    @SuppressWarnings("PMD.NullAssignment") // Null retires task/session references after cancellation.
    private void countdown(int seconds, Runnable next) {
        if (task != null) task.cancel();
        long token = ++generation;
        int[] remaining = {seconds};
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (token != generation) return;
            if (!current()) { stop(); return; }
            players(p -> p.sendActionBar(net.kyori.adventure.text.Component.text("Round " + round + " · " + phase + " · " + remaining[0] + "s")));
            if (remaining[0]-- <= 0) { task.cancel(); task = null; next.run(); }
        }, 0L, 20L);
    }
    @SuppressWarnings("PMD.NullAssignment") // Null retires task/session references after cancellation.
    private void mutatePlots(boolean show, Runnable finished) {
        long token = ++generation;
        List<Map.Entry<CuboidRegion, Offset>> cells = new ArrayList<>();
        for (CuboidRegion plot : plots.values()) for (Offset offset : offsets(plot)) cells.add(Map.entry(plot, offset));
        int[] cursor = {0};
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (token != generation) return;
            if (!current()) { stop(); return; }
            try {
                World world = plugin.getServer().getWorld(tracked.selectedMap().worldName());
                for (int n = 0; n < 128 && cursor[0] < cells.size(); n++, cursor[0]++) {
                    var entry = cells.get(cursor[0]); var offset = entry.getValue(); var plot = entry.getKey();
                    int x = (int) plot.minX() + offset.x(), y = (int) plot.minY() + offset.y(), z = (int) plot.minZ() + offset.z();
                    if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) throw new IllegalStateException("Plot chunk unavailable");
                    var block = world.getBlockAt(x, y, z);
                    if (block.getState() instanceof TileState) throw new IllegalStateException("Unexpected plot tile");
                    block.setBlockData(plugin.getServer().createBlockData(show ? template.getOrDefault(offset, AIR_BLOCK_DATA) : AIR_BLOCK_DATA), false);
                }
                if (cursor[0] == cells.size()) { task.cancel(); task = null; finished.run(); }
            } catch (RuntimeException failure) { manager.stop("Speedbuilders plot update failed; arena recovery retained"); stop(); }
        }, 1L, 1L);
    }
    private Map<Offset, String> read(CuboidRegion region, World world) {
        if (world == null) throw new IllegalStateException("Original world missing");
        Map<Offset, String> result = new LinkedHashMap<>();
        for (Offset o : offsets(region)) {
            int x = (int) region.minX() + o.x(), y = (int) region.minY() + o.y(), z = (int) region.minZ() + o.z();
            if (!world.isChunkLoaded(x >> 4, z >> 4)) throw new IllegalStateException("Chunk unloaded");
            var block = world.getBlockAt(x, y, z);
            if (block.getState() instanceof TileState) throw new IllegalStateException("Tile state unsupported");
            if (!block.isEmpty()) result.put(o, block.getBlockData().getAsString());
        }
        return Map.copyOf(result);
    }
    @SuppressWarnings("PMD.NullAssignment") // Null retires task/session references after cancellation.
    public void stop() { generation++; if (task != null) { task.cancel(); task = null; } tracked = null; plots.clear(); totals.clear(); phase = "idle"; }
    private boolean current() { return tracked != null && manager.session() == tracked && tracked.phase() == EventPhase.ACTIVE; }
    private void players(Consumer<Player> action) { for (UUID uuid : plots.keySet()) { Player p = plugin.getServer().getPlayer(uuid); if (p != null && tracked.participants().contains(uuid)) action.accept(p); } }
    private boolean game() { return manager.session() != null && manager.session().definition().type() == EventType.SPEEDBUILDERS; }
    private boolean mayEdit(Player p, Location location) { return current() && phase.equals(BUILD_PHASE) && tracked.participants().contains(p.getUniqueId()) && plots.containsKey(p.getUniqueId()) && plots.get(p.getUniqueId()).contains(location); }
    private boolean protectedEdit(Player player, Location location) {
        if (!game()) return false;
        EventMap map = manager.session().selectedMap();
        return manager.isEventPlayer(player.getUniqueId()) || map != null && map.region() != null && map.region().contains(location);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void place(BlockPlaceEvent e) { if (protectedEdit(e.getPlayer(), e.getBlock().getLocation()) && (!mayEdit(e.getPlayer(), e.getBlock().getLocation()) || !palette.contains(e.getBlock().getType()))) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void breakBlock(BlockBreakEvent e) { if (protectedEdit(e.getPlayer(), e.getBlock().getLocation())) { if (!mayEdit(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); else { e.setDropItems(false); e.setExpToDrop(0); } } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void replenish(BlockBreakEvent e) {
        if (game() && mayEdit(e.getPlayer(), e.getBlock().getLocation()) && palette.contains(e.getBlock().getType())) e.getPlayer().getInventory().addItem(new ItemStack(e.getBlock().getType(), 1));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void pickup(EntityPickupItemEvent e) { if (game() && manager.isEventPlayer(e.getEntity().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void open(InventoryOpenEvent e) {
        if (game() && manager.isEventPlayer(e.getPlayer().getUniqueId()) && e.getInventory().getType() != InventoryType.CRAFTING && e.getInventory().getType() != InventoryType.PLAYER) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void damage(EntityDamageEvent e) { if (game() && manager.isEventPlayer(e.getEntity().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void drop(PlayerDropItemEvent e) { if (game() && manager.isEventPlayer(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void craft(CraftItemEvent e) { if (game() && manager.isEventPlayer(e.getWhoClicked().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false) public void command(PlayerCommandPreprocessEvent e) {
        if (!game() || !manager.isEventPlayer(e.getPlayer().getUniqueId())) return;
        String command = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("/event leave", "/ee leave", "/events leave").contains(command)) { e.setCancelled(true); e.getPlayer().sendMessage("Leave Speedbuilders before using other commands."); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void move(PlayerMoveEvent e) {
        if (!current() || !tracked.participants().contains(e.getPlayer().getUniqueId()) || e.getTo() == null) return;
        CuboidRegion plot = plots.get(e.getPlayer().getUniqueId());
        if (plot != null && !plot.contains(e.getTo())) e.setTo(e.getFrom());
    }
    private static final Set<Material> BASE_MATERIALS = Set.of(Material.STONE, Material.COBBLESTONE, Material.OAK_PLANKS, Material.BRICKS, Material.GLASS, Material.TERRACOTTA);
    private static boolean allowed(Material material) { return BASE_MATERIALS.contains(material) || material.name().endsWith("_WOOL") || material.name().endsWith("_TERRACOTTA"); }

    private static boolean integerBounds(CuboidRegion r) {
        for (double value : new double[]{r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()}) {
            if (!Double.isFinite(value) || value != Math.rint(value) || Math.abs(value) > 30_000_000) return false;
        }
        return r.minX() <= r.maxX() && r.minY() <= r.maxY() && r.minZ() <= r.maxZ();
    }
    private static long volume(CuboidRegion r) { return ((long) r.maxX() - (long) r.minX() + 1) * ((long) r.maxY() - (long) r.minY() + 1) * ((long) r.maxZ() - (long) r.minZ() + 1); }
    private static boolean sameSize(CuboidRegion a, CuboidRegion b) { return a.maxX() - a.minX() == b.maxX() - b.minX() && a.maxY() - a.minY() == b.maxY() - b.minY() && a.maxZ() - a.minZ() == b.maxZ() - b.minZ(); }
    private static boolean overlaps(CuboidRegion a, CuboidRegion b) { return Objects.equals(a.worldName(), b.worldName()) && a.minX() <= b.maxX() && b.minX() <= a.maxX() && a.minY() <= b.maxY() && b.minY() <= a.maxY() && a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ(); }
    private static List<Offset> offsets(CuboidRegion r) {
        if (!integerBounds(r) || volume(r) < 1 || volume(r) > 512) throw new IllegalArgumentException("Plot/template size invalid");
        List<Offset> result = new ArrayList<>();
        for (int x = 0; x <= r.maxX() - r.minX(); x++) for (int y = 0; y <= r.maxY() - r.minY(); y++) for (int z = 0; z <= r.maxZ() - r.minZ(); z++) result.add(new Offset(x, y, z));
        return result;
    }
    private record Offset(int x, int y, int z) { }
}
