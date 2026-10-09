package org.enthusia.events.infrastructure;

import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.enthusia.events.EnthusiaEventsPlugin;
import java.util.UUID;

/** Retained uncertain inventories stay isolated until explicit reconciliation. */
public final class RecoveryIsolationListener implements Listener {
    private final EnthusiaEventsPlugin plugin;
    public RecoveryIsolationListener(EnthusiaEventsPlugin plugin) { this.plugin = plugin; }
    private boolean blocked(UUID player) { return plugin.snapshots().recoveryBlocked(player); }
    @EventHandler(priority = EventPriority.HIGHEST) public void place(org.bukkit.event.block.BlockPlaceEvent e) { if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void breakBlock(org.bukkit.event.block.BlockBreakEvent e) { if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(org.bukkit.event.entity.EntityDamageEvent e) { if (e.getEntity() instanceof org.bukkit.entity.Player && blocked(e.getEntity().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void death(org.bukkit.event.entity.PlayerDeathEvent e) { if (blocked(e.getEntity().getUniqueId())) { e.setKeepInventory(true); e.getDrops().clear(); e.setKeepLevel(true); e.setDroppedExp(0); } }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent e) { if (blocked(e.getWhoClicked().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if (blocked(e.getWhoClicked().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void open(InventoryOpenEvent e) { if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if (blocked(e.getEntity().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEvent e) { if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void entity(PlayerInteractEntityEvent e) { if (blocked(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void command(PlayerCommandPreprocessEvent e) {
        if (!blocked(e.getPlayer().getUniqueId())) return;
        String message = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        boolean recovery = (message.startsWith("/ee recovery ") || message.startsWith("/event recovery ") || message.startsWith("/events recovery "))
                && e.getPlayer().hasPermission("enthusia.events.admin") && e.getPlayer().hasPermission("enthusia.events.recovery.apply");
        if (!recovery) { e.setCancelled(true); e.getPlayer().sendMessage("Inventory recovery needs staff reconciliation before commands or transfers."); }
    }
}
