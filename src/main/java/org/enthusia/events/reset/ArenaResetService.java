package org.enthusia.events.reset;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.CuboidRegion;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class ArenaResetService implements Listener {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final Map<String, BlockState> originalStates = new LinkedHashMap<>();
    private final Deque<BlockState> pendingRestore = new ArrayDeque<>();
    private BukkitTask resetTask;

    public ArenaResetService(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    private String lastFailure = "";

    public boolean isResetting() { return resetTask != null || !pendingRestore.isEmpty()
            || (plugin.arenaRecovery() != null && plugin.arenaRecovery().blocked()); }
    public int pendingBlocks() { return pendingRestore.size(); }
    public String failure() { return lastFailure.isEmpty() ? null : lastFailure; }

    public void reset() {
        if (resetTask != null) return;
        // A retry retains the failed head and every unattempted state.
        if (!originalStates.isEmpty()) {
            pendingRestore.addAll(originalStates.values().stream().toList().reversed());
            originalStates.clear();
        }
        if (pendingRestore.isEmpty()) {
            if (plugin.arenaRecovery() != null) plugin.arenaRecovery().reset();
            return;
        }
        lastFailure = "";
        int synchronousLimit = Math.max(0, plugin.getConfig().getInt("reset.max-blocks-per-session", 50_000));
        if (pendingRestore.size() <= synchronousLimit) {
            restoreBatch(pendingRestore.size());
        } else {
            int perTick = Math.max(100, plugin.getConfig().getInt("reset.max-blocks-per-tick", 2_000));
            resetTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> restoreBatch(perTick), 1L, 1L);
        }
    }

    private void restoreBatch(int perTick) {
        for (int i = 0; i < perTick && !pendingRestore.isEmpty(); i++) {
            BlockState state = pendingRestore.peekFirst();
            try {
                if (!state.update(true, false)) throw new IllegalStateException("BlockState.update returned false");
                pendingRestore.removeFirst();
            } catch (RuntimeException exception) {
                lastFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                cancelResetTask();
                plugin.getLogger().severe("Arena reset paused with " + pendingRestore.size()
                        + " blocks retained. New events are blocked. Retry with /ee resetarena. " + lastFailure);
                return;
            }
        }
        if (pendingRestore.isEmpty()) {
            cancelResetTask();
            if (plugin.arenaRecovery() != null && plugin.isEnabled()) plugin.arenaRecovery().reset();
            plugin.recordSessionEvidence(eventManager.session(), "arena-reset-complete", null);
        }
    }

    @SuppressWarnings("PMD.NullAssignment") // Retire the cancelled Bukkit task reference.
    private void cancelResetTask() {
        if (resetTask != null) { resetTask.cancel(); resetTask = null; }
    }

    /** Finish before Bukkit cancels tasks. Failed states remain retained in memory;
     * this is not a crash-recovery journal. */
    public void finishPendingSynchronously() {
        // Durable work remains on disk for explicit restart reconciliation; never discard it on shutdown.
        if (plugin.arenaRecovery() != null) plugin.arenaRecovery().shutdown();
        cancelResetTask();
        pendingRestore.addAll(originalStates.values().stream().toList().reversed());
        originalStates.clear();
        lastFailure = "";
        restoreBatch(pendingRestore.size());
    }

    public void clear() {
        if (isResetting() || !originalStates.isEmpty())
            throw new IllegalStateException("Cannot discard unrestored arena states");
        lastFailure = "";
    }

    public void recordBlock(Block block) {
        record(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (trackCurrentSession(event.getBlockPlaced().getLocation())) {
            remember(event.getBlockReplacedState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            record(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        record(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        record(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!plugin.getConfig().getBoolean("reset.track-physics", false)) {
            return;
        }
        record(event.getBlock());
    }

    private void record(Block block) {
        if (trackCurrentSession(block.getLocation())) {
            remember(block.getState());
        }
    }

    private void remember(BlockState state) {
        originalStates.putIfAbsent(key(state.getLocation()), state);
    }

    private boolean trackCurrentSession(Location location) {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE || !isResetTracked(session.definition().type())) {
            return false;
        }
        EventMap map = session.selectedMap();
        CuboidRegion region = map == null ? null : map.region();
        return region != null && region.contains(location);
    }

    private boolean isResetTracked(EventType type) {
        return type == EventType.RUNNER
                || type == EventType.SPLEEF
                || type == EventType.SPLEGG
                || type == EventType.BEDWARS
                || type == EventType.SKYWARS
                || type == EventType.BLOCK_PARTY;
    }

    private String key(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":"
                + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
