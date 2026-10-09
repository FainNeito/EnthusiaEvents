package org.enthusia.events.event;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Production guardrails for event transitions that are difficult for individual game
 * modules to own safely: persistent inventory escape, BedWars void
 * deaths, and CTF armor consistency.
 */
public final class EventGameplaySafetyListener implements Listener {

    private static final String BEDWARS_ITEM_SHOP_TITLE = "BedWars Item Shop";
    private static final String BEDWARS_UPGRADE_SHOP_TITLE = "Upgrades & Traps";

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;

    public EventGameplaySafetyListener(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    /**
     * Force BedWars void deaths through the same PlayerDeathEvent path as combat deaths.
     * This preserves the normal respawn delay and pickaxe/axe tier downgrade instead of
     * granting an instant, penalty-free return to base.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedWarsVoid(PlayerMoveEvent event) {
        EventSession session = eventManager.session();
        Location to = event.getTo();
        if (session == null || session.phase() != EventPhase.ACTIVE
                || session.definition().type() != EventType.BEDWARS
                || !session.participants().contains(event.getPlayer().getUniqueId())
                || to == null || event.getPlayer().isDead()) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map == null || map.region() == null || to.getY() > map.region().minY() - 1.0D) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.setFallDistance(0.0F);
        if (player.getHealth() > 0.0D) {
            player.setHealth(0.0D);
        }
    }

    /**
     * Event items may be stored in event-local physical containers, but never in external
     * virtual/persistent inventories such as ender chests, guild vaults, backpacks, mail,
     * auction GUIs, or another plugin's player vault.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !eventManager.isEventPlayer(player.getUniqueId())) {
            return;
        }
        if (isAllowedEventInventory(player, event.getView().getTopInventory(), event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED
                + "External storage is disabled while you are in an event.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !eventManager.isEventPlayer(player.getUniqueId())) {
            return;
        }
        if (!isAllowedEventInventory(player, event.getView().getTopInventory(), event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !eventManager.isEventPlayer(player.getUniqueId())) {
            return;
        }
        if (!isAllowedEventInventory(player, event.getView().getTopInventory(), event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    /**
     * Called by the plugin timer. The original CTF respawn/flag-return helpers use chainmail,
     * while the intended starting kit is diamond chest/boots + iron leggings + team helmet.
     * Keep that armor invariant throughout the match while preserving a carried flag banner.
     */
    public void tickCtfArmor() {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE
                || session.definition().type() != EventType.CAPTURE_THE_FLAG) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            ensureArmorMaterial(player, EquipmentPiece.CHESTPLATE, Material.DIAMOND_CHESTPLATE);
            ensureArmorMaterial(player, EquipmentPiece.LEGGINGS, Material.IRON_LEGGINGS);
            ensureArmorMaterial(player, EquipmentPiece.BOOTS, Material.DIAMOND_BOOTS);

            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet != null && helmet.getType().name().endsWith("_BANNER")) {
                continue;
            }
            Color color = teamColor(eventManager.teamFor(uuid));
            if (!isTeamHelmet(helmet, color)) {
                player.getInventory().setHelmet(coloredHelmet(color));
            }
        }
    }

    private boolean isAllowedEventInventory(Player player, Inventory inventory, String title) {
        InventoryType type = inventory.getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER) {
            return true;
        }

        if (inventory.getHolder() != null
                && inventory.getHolder().getClass().getName().startsWith("org.enthusia.events.")) {
            return true;
        }

        if (BEDWARS_ITEM_SHOP_TITLE.equals(title) || BEDWARS_UPGRADE_SHOP_TITLE.equals(title)) {
            return true;
        }

        Location inventoryLocation = inventory.getLocation();
        EventSession session = eventManager.session();
        EventMap map = session == null ? null : session.selectedMap();
        return session != null
                && session.phase() == EventPhase.ACTIVE
                && map != null
                && map.region() != null
                && inventoryLocation != null
                && map.region().contains(inventoryLocation);
    }

    private void ensureArmorMaterial(Player player, EquipmentPiece piece, Material expected) {
        ItemStack current = switch (piece) {
            case CHESTPLATE -> player.getInventory().getChestplate();
            case LEGGINGS -> player.getInventory().getLeggings();
            case BOOTS -> player.getInventory().getBoots();
        };
        if (current != null && current.getType() == expected) {
            return;
        }
        ItemStack replacement = new ItemStack(expected);
        switch (piece) {
            case CHESTPLATE -> player.getInventory().setChestplate(replacement);
            case LEGGINGS -> player.getInventory().setLeggings(replacement);
            case BOOTS -> player.getInventory().setBoots(replacement);
        }
    }

    private ItemStack coloredHelmet(Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        if (helmet.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            helmet.setItemMeta(meta);
        }
        return helmet;
    }

    private boolean isTeamHelmet(ItemStack helmet, Color color) {
        if (helmet == null || helmet.getType() != Material.LEATHER_HELMET
                || !(helmet.getItemMeta() instanceof LeatherArmorMeta meta)) {
            return false;
        }
        return meta.getColor().equals(color);
    }

    private Color teamColor(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> Color.fromRGB(255, 0, 0);
            case "2", "blue" -> Color.fromRGB(0, 80, 255);
            case "3", "green" -> Color.fromRGB(0, 255, 0);
            case "4", "yellow" -> Color.fromRGB(255, 255, 0);
            case "5", "orange" -> Color.fromRGB(255, 165, 0);
            case "6", "purple" -> Color.fromRGB(128, 0, 128);
            case "7", "cyan" -> Color.fromRGB(0, 255, 255);
            default -> Color.fromRGB(255, 255, 255);
        };
    }

    private enum EquipmentPiece {
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }
}
