package org.enthusia.events.infrastructure;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;

/** Optional floor-collapse game, using the existing session/elimination/reset pipeline. */
public final class RunnerListener implements Listener {
    private final EnthusiaEventsPlugin plugin;
    private final EventManager manager;
    private final java.util.Set<String> pending = new java.util.HashSet<>();
    private EventSession tracked;
    public RunnerListener(EnthusiaEventsPlugin plugin, EventManager manager) { this.plugin = plugin; this.manager = manager; }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        EventSession session = manager.session();
        if (tracked != session) { pending.clear(); tracked = session; }
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.RUNNER
                || !session.participants().contains(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        EventMap map = session.selectedMap();
        if (map == null || map.region() == null) return;
        var floor = map.areas().get("breakable-area");
        var block = event.getTo().clone().subtract(0, 1, 0).getBlock();
        if (floor == null || !floor.contains(block.getLocation()) || !map.region().contains(block.getLocation()) || block.isEmpty()) return;
        String key = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (!pending.add(key)) return;
        var data = block.getBlockData().clone();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (tracked != session) return;
            pending.remove(key);
            if (manager.session() != session || session.phase() != EventPhase.ACTIVE || !block.getBlockData().equals(data)) return;
            manager.recordArenaResetBlock(block);
            block.setType(Material.AIR, false);
        }, 4L);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void combat(EntityDamageByEntityEvent event) {
        var session = manager.session();
        if (session != null && session.definition().type() == EventType.RUNNER
                && manager.isEventPlayer(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
}
