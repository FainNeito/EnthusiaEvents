package org.enthusia.events.infrastructure;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.config.EventConfigService;
import org.enthusia.events.event.*;
import org.enthusia.events.loot.LootTableService;
import java.util.*;

/** Read-only inventory views. Persistent edits use explicit validated staff commands. */
public final class EventToolsGui implements Listener {
    private final EnthusiaEventsPlugin plugin;
    private final EventManager manager;
    private final MapSetupService maps;
    private final EventConfigService settings;
    private final LootTableService loot;
    public EventToolsGui(EnthusiaEventsPlugin plugin, EventManager manager, MapSetupService maps,
                         EventConfigService settings, LootTableService loot) {
        this.plugin = plugin; this.manager = manager; this.maps = maps; this.settings = settings; this.loot = loot;
    }
    private record View(String kind, EventType type, int tier, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName("§6" + name);
        meta.setLore(lore.stream().map(line -> "§7" + line).toList());
        item.setItemMeta(meta); return item;
    }
    private Inventory inventory(View view, String title) {
        return Bukkit.createInventory(view, 54, title);
    }
    public void browse(Player player) {
        Inventory inv = inventory(new View("browse", null, 0, 0), "Events • Browse");
        int slot = 0;
        for (EventType type : EventType.values()) {
            EventDefinition definition = plugin.eventRegistry().definition(type);
            boolean enabled = definition != null && !manager.isEventDisabled(type);
            String availability = !enabled ? "Disabled" : maps.usableMapsFor(type).isEmpty() ? "Needs map setup"
                    : manager.isArenaResetting() ? "Arena resetting" : manager.hasSession() ? "Starts temporarily unavailable" : "Available";
            inv.setItem(slot++, item(settings.icon(type), type.name().replace('_', ' '), List.of(
                    settings.description(type), availability,
                    "Minimum / ready: " + settings.minPlayers(type, 2) + " / " + settings.readyPlayers(type, 2),
                    "Duration: " + settings.activePhaseSeconds(type, 300) + " seconds",
                    "Winner reward: " + settings.winnerReward(type, 100), "Click for details")));
        }
        inv.setItem(49, item(Material.CLOCK, "Schedule", List.of(scheduleLabel(), "Click to inspect schedule")));
        player.openInventory(inv);
    }
    public String scheduleLabel() {
        if (!plugin.getConfig().getBoolean("schedule.enabled", false)) return "Automatic voting is off";
        return "Next hourly vote: " + manager.nextHourlyVoteLabel() + " (server time)";
    }
    public void schedule(Player player) {
        Inventory inv = inventory(new View("schedule", null, 0, 0), "Events • Schedule");
        inv.setItem(22, item(Material.CLOCK, "Hourly vote", List.of(scheduleLabel(),
                "A vote chooses an event; this is not a guaranteed start.", "Minimum players and map readiness still apply.")));
        back(inv); player.openInventory(inv);
    }
    private void back(Inventory inv) { inv.setItem(49, item(Material.ARROW, "Browse events", List.of("Click to return"))); }
    public void details(Player player, EventType type) {
        Inventory inv = inventory(new View("details", type, 0, 0), "Events • " + type.name());
        inv.setItem(22, item(settings.icon(type), type.name(), List.of(settings.description(type),
                "Configured maps: " + maps.mapsFor(type).size(), "Ready maps: " + maps.usableMapsFor(type).size(),
                "Minimum / ready: " + settings.minPlayers(type, 2) + " / " + settings.readyPlayers(type, 2),
                "Use /event start " + type + " to request a start.")));
        if (player.hasPermission("enthusia.events.admin"))
            inv.setItem(31, item(Material.COMPARATOR, "Staff readiness", List.of("Click to inspect saved map validation")));
        back(inv); player.openInventory(inv);
    }
    public void readiness(Player player, EventType type, int page) {
        if (!player.hasPermission("enthusia.events.admin")) return;
        List<EventMap> list = maps.mapsFor(type);
        int maxPage = Math.max(0, (list.size() - 1) / 45);
        page = Math.max(0, Math.min(page, maxPage));
        Inventory inv = inventory(new View("readiness", type, 0, page), "Readiness • " + type.name());
        for (int i = page * 45; i < Math.min(list.size(), page * 45 + 45); i++) {
            EventMap map = list.get(i);
            List<String> errors = maps.validate(map);
            var lines = new ArrayList<String>();
            lines.add(errors.isEmpty() ? "Ready" : "Needs setup");
            lines.add("World: " + map.worldName());
            lines.add("Spawns: " + String.join(", ", map.spawns().keySet()));
            lines.add("Areas: " + String.join(", ", map.areas().keySet()));
            lines.add("Points: " + String.join(", ", map.points().keySet()));
            lines.add("Chests by tier: " + map.chests().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().size()).toList());
            lines.add("Generators: " + map.generators().keySet());
            lines.add("Checkpoints: " + map.checkpoints().keySet());
            lines.add("Region: " + (map.region() == null ? "missing" : map.region().minX() + "," + map.region().minY() + "," + map.region().minZ()
                    + " to " + map.region().maxX() + "," + map.region().maxY() + "," + map.region().maxZ()));
            lines.addAll(errors);
            lines.add("Repair: /ee setup " + type + " " + map.id());
            lines.add("Inspect commands: /ee map help");
            inv.setItem(i % 45, item(errors.isEmpty() ? Material.LIME_DYE : Material.RED_DYE, map.id(), lines));
        }
        if (list.isEmpty()) inv.setItem(22, item(Material.BARRIER, "No saved maps", List.of("Create with /ee map create " + type + " <mapId>.")));
        pagination(inv, page, maxPage); player.openInventory(inv);
    }
    public void loot(Player player, EventType type, int tier, int page, boolean sample) {
        if (!player.hasPermission("enthusia.events.admin")) return;
        var entries = loot.inspect(type, tier);
        int maxPage = sample ? 0 : Math.max(0, (entries.size() - 1) / 45);
        page = Math.max(0, Math.min(page, maxPage));
        Inventory inv = inventory(new View(sample ? "sample" : "loot", type, tier, page),
                (sample ? "Sample • " : "Loot • ") + type + " " + tier);
        if (sample) {
            var rolled = loot.roll(type, tier);
            for (int i = 0; i < Math.min(45, rolled.size()); i++) inv.setItem(i, rolled.get(i));
            inv.setItem(47, item(Material.PAPER, "Rolled entries: " + rolled.size(),
                    List.of("Showing the first " + Math.min(45, rolled.size()) + " entries.", "Sample only; no inventory transfer.")));
        } else for (int i = page * 45; i < Math.min(entries.size(), page * 45 + 45); i++) {
            var entry = entries.get(i);
            inv.setItem(i % 45, item(entry.material(), entry.id(), List.of(
                    "Amount: " + entry.min() + "–" + entry.max(), "Chance: " + Math.round(entry.chance() * 100) + "%",
                    "Edit: /ee loot " + type + " " + tier + " edit " + entry.id() + " <min> <max> <chance>")));
        }
        var caps = loot.limits(type, tier);
        inv.setItem(48, item(Material.COMPARATOR, "Limits (0 = unlimited)", List.of("Armor: " + caps.maxArmor(),
                "Weapons: " + caps.maxWeapons(), "Total entries: " + caps.maxTotal(), "Edits blocked during events.")));
        inv.setItem(49, item(Material.CHEST, sample ? "Back to table" : "Sample roll", List.of("Preview only; items cannot be taken.")));
        pagination(inv, page, maxPage); player.openInventory(inv);
    }
    private void pagination(Inventory inv, int page, int max) {
        if (page > 0) inv.setItem(45, item(Material.ARROW, "Previous", List.of("Click")));
        if (page < max) inv.setItem(53, item(Material.ARROW, "Next", List.of("Click")));
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;
        int slot = event.getRawSlot();
        // Never open inventories during InventoryClickEvent's transaction.
        Bukkit.getScheduler().runTask(plugin, () -> navigate(player, view, slot));
    }
    private void navigate(Player player, View view, int slot) {
        if (!player.isOnline()) return;
        if (view.kind.equals("browse")) {
            if (slot < EventType.values().length) details(player, EventType.values()[slot]);
            else if (slot == 49) schedule(player);
        } else if (view.kind.equals("loot") || view.kind.equals("sample")) {
            if (slot == 49) loot(player, view.type, view.tier, 0, view.kind.equals("loot"));
            else if (slot == 45 || slot == 53) loot(player, view.type, view.tier, view.page + (slot == 45 ? -1 : 1), false);
        } else if (view.kind.equals("readiness")) {
            if (slot == 45 || slot == 53) readiness(player, view.type, view.page + (slot == 45 ? -1 : 1));
        } else if (slot == 31 && view.kind.equals("details")) readiness(player, view.type, 0);
        else if (slot == 49) browse(player);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }
}
