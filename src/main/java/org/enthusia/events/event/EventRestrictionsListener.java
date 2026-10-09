package org.enthusia.events.event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.enthusia.events.EnthusiaEventsPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.NPathComplexity"})
public final class EventRestrictionsListener implements Listener {

    private static final long BLOCKED_WARNING_COOLDOWN_MILLIS = 5000L;

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final MapSetupService mapSetupService;
    private final Map<UUID, Long> blockedWarnings = new ConcurrentHashMap<>();

    public EventRestrictionsListener(EnthusiaEventsPlugin plugin, EventManager eventManager, MapSetupService mapSetupService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.mapSetupService = mapSetupService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!eventManager.isEventPlayer(player.getUniqueId())) {
            return;
        }
        if (plugin.getConfig().getBoolean("restrictions.allow-admin-bypass", true)
                && player.hasPermission("enthusia.events.admin")) {
            return;
        }
        String message = event.getMessage();
        if (message.isBlank()) {
            return;
        }
        String command = message.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> blocked = plugin.getConfig().getStringList("restrictions.blocked-commands");
        if (blocked.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(command::equals)) {
            event.setCancelled(true);
            plugin.messages().send(player, "blocked-command");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("restrictions.block-teleports", true)) {
            return;
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        EventSession session = eventManager.session();
        boolean enforceActiveMap = session != null && (session.phase() == EventPhase.PRESTART
                || session.phase() == EventPhase.ACTIVE || session.phase() == EventPhase.TROPHY);
        EventMap activeMap = eventManager.activeMap();

        if (eventManager.isEventPlayer(player.getUniqueId())) {
            if (session != null && session.spectators().contains(player.getUniqueId())
                    && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                event.setCancelled(true);
                return;
            }
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                    && (session == null || !allowsEventPearls(session.definition().type()))) {
                event.setCancelled(true);
                player.getInventory().remove(Material.ENDER_PEARL);
                sendBlockedWarning(player, "blocked-teleport");
                return;
            }
            if (eventManager.canExitByTeleport(player.getUniqueId())) {
                event.setCancelled(false);
                return;
            }
            if (session != null && (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN)) {
                CuboidRegion region = eventManager.waitingHubRegion();
                if (region != null && (to == null || !region.contains(to))) {
                    event.setCancelled(true);
                    sendBlockedWarning(player, "blocked-teleport");
                }
                return;
            }
            if (session != null && session.phase() == EventPhase.TROPHY) {
                CuboidRegion region = eventManager.trophyRoomRegion();
                if (region != null && (to == null || !region.contains(to))) {
                    event.setCancelled(true);
                    sendBlockedWarning(player, "blocked-teleport");
                }
                return;
            }
            if (enforceActiveMap && activeMap != null && activeMap.region() != null && (to == null || !activeMap.region().contains(to))) {
                event.setCancelled(true);
                sendBlockedWarning(player, "blocked-teleport");
            }
            return;
        }

        if (enforceActiveMap && activeMap != null && activeMap.region() != null && to != null && activeMap.region().contains(to)) {
            event.setCancelled(true);
            sendBlockedWarning(player, "blocked-event-area");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (plugin.getConfig().getBoolean("restrictions.block-portals", true)
                && eventManager.isEventPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "blocked-teleport");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location location = event.getLocation();
        CuboidRegion hub = eventManager.waitingHubRegion();
        CuboidRegion trophy = eventManager.trophyRoomRegion();
        boolean protectedEventLocation = mapSetupService.isConfiguredMapWorld(location.getWorld().getName())
                || (hub != null && hub.contains(location))
                || (trophy != null && trophy.contains(location));
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM && protectedEventLocation) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session != null && eventManager.isEventPlayer(player.getUniqueId())
                && session.definition().type() != EventType.SKYWARS) {
            event.setCancelled(true);
            sendBlockedWarning(player, "blocked-crafting");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        EventSession session = eventManager.session();
        if (session == null || session.definition().type() != EventType.BEDWARS) {
            return;
        }
        EventMap activeMap = eventManager.activeMap();
        boolean eventPlayer = eventManager.isEventPlayer(event.getPlayer().getUniqueId());
        boolean activeBed = activeMap != null
                && activeMap.region() != null
                && activeMap.region().contains(event.getBed().getLocation());
        if (eventPlayer || activeBed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null || !eventManager.isEventPlayer(player.getUniqueId()) || allowsEventPearls(session.definition().type())) {
            return;
        }
        event.setCancelled(true);
        player.getInventory().remove(Material.ENDER_PEARL);
        plugin.messages().send(player, "blocked-teleport");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (eventManager.isSpawnLocked(event.getPlayer().getUniqueId())) {
            Location locked = event.getFrom().clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
            return;
        }
        EventMap activeMap = eventManager.activeMap();
        EventSession session = eventManager.session();
        if (session == null) {
            return;
        }
        boolean eventPlayer = eventManager.isEventPlayer(event.getPlayer().getUniqueId());
        if (eventPlayer && (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN)) {
            keepInside(event, eventManager.waitingHubRegion(), eventManager.waitingHubLocation());
            return;
        }
        if (eventPlayer && session.phase() == EventPhase.TROPHY) {
            keepInside(event, eventManager.trophyRoomRegion(), eventManager.trophyRoomLocation());
            return;
        }
        if (session.phase() != EventPhase.ACTIVE || activeMap == null || activeMap.region() == null) {
            return;
        }
        boolean inside = activeMap.region().contains(to);
        if (!eventPlayer && inside) {
            event.setTo(event.getFrom());
            sendBlockedWarning(event.getPlayer(), "blocked-event-area");
            return;
        }
        if (eventPlayer && !inside) {
            if (to.getY() < activeMap.region().minY()) {
                return;
            }
            event.setTo(event.getFrom());
            sendBlockedWarning(event.getPlayer(), "blocked-teleport");
        }
    }

    private void sendBlockedWarning(Player player, String messageKey) {
        long now = System.currentTimeMillis();
        Long previous = blockedWarnings.get(player.getUniqueId());
        if (previous != null && now - previous < BLOCKED_WARNING_COOLDOWN_MILLIS) {
            return;
        }
        blockedWarnings.put(player.getUniqueId(), now);
        plugin.messages().send(player, messageKey);
    }

    private void keepInside(PlayerMoveEvent event, CuboidRegion region, Location fallback) {
        if (region == null || region.contains(event.getTo())) {
            return;
        }
        if (fallback != null) {
            event.setTo(fallback);
        } else {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldBlockMapEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldBlockMapEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldBlockMapEdit(Player player, Location location) {
        if (plugin.arenaRecovery() != null && plugin.arenaRecovery().quarantine() && mapSetupService.isInsideAnyConfiguredRegion(location)) return true;
        EventSession session = eventManager.session();
        if (session != null && eventManager.isEventPlayer(player.getUniqueId()) && session.phase() != EventPhase.ACTIVE) {
            return true;
        }
        if (plugin.getConfig().getBoolean("restrictions.allow-admin-bypass", true)
                && player.hasPermission("enthusia.events.admin")) {
            return false;
        }
        if (session != null && eventManager.isEventPlayer(player.getUniqueId())) {
            EventType type = session.definition().type();
            return type != EventType.SKYWARS
                    && type != EventType.BEDWARS
                    && type != EventType.SPLEEF
                    && type != EventType.SPLEGG
                    && type != EventType.SPEEDBUILDERS;
        }
        CuboidRegion hubRegion = eventManager.waitingHubRegion();
        if (hubRegion != null && hubRegion.contains(location)) {
            return true;
        }
        CuboidRegion trophyRegion = eventManager.trophyRoomRegion();
        if (trophyRegion != null && trophyRegion.contains(location)) {
            return true;
        }
        return mapSetupService.isInsideAnyConfiguredRegion(location);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        EventSession session = eventManager.session();
        Player player = event.getPlayer();
        if (!(plugin.getConfig().getBoolean("restrictions.allow-admin-bypass", true)
                && player.hasPermission("enthusia.events.admin"))) {
            CuboidRegion hubRegion = eventManager.waitingHubRegion();
            CuboidRegion trophyRegion = eventManager.trophyRoomRegion();
            Location location = player.getLocation();
            if ((hubRegion != null && hubRegion.contains(location))
                    || (trophyRegion != null && trophyRegion.contains(location))) {
                event.setCancelled(true);
                return;
            }
        }
        if (session != null && eventManager.isEventPlayer(event.getPlayer().getUniqueId())
                && (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN)) {
            event.setCancelled(true);
            return;
        }
        if (session != null && !allowsEventItemDrops(session.definition().type())
                && session.participants().contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (session != null && session.spectators().contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session != null
                && session.definition().type() == EventType.CAPTURE_THE_FLAG
                && session.participants().contains(player.getUniqueId())) {
            if (isHelmetInteraction(event)) {
                event.setCancelled(true);
            }
            return;
        }
        if (session != null && session.spectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session != null
                && session.definition().type() == EventType.CAPTURE_THE_FLAG
                && session.participants().contains(player.getUniqueId())
                && event.getRawSlots().stream().anyMatch(this::isHelmetRawSlot)) {
            event.setCancelled(true);
        }
    }

    private boolean isHelmetInteraction(InventoryClickEvent event) {
        return event.getSlotType() == InventoryType.SlotType.ARMOR || isHelmetRawSlot(event.getRawSlot()) || event.getSlot() == 39;
    }

    private boolean isHelmetRawSlot(int rawSlot) {
        return rawSlot == 5;
    }

    private boolean allowsEventPearls(EventType type) {
        return type == EventType.SKYWARS || type == EventType.KNOCKBACK_FFA || type == EventType.BEDWARS;
    }

    private boolean allowsEventItemDrops(EventType type) {
        return type == EventType.SKYWARS
                || type == EventType.BEDWARS
                || type == EventType.CAPTURE_THE_FLAG
                || type == EventType.FIGHT_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.FIGHT_FFA;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session != null && session.phase() == EventPhase.ACTIVE && eventManager.isBracketEvent()
                && (event.getDamager() instanceof Player attacker)
                && (!eventManager.isBracketContestant(attacker.getUniqueId())
                || !eventManager.isBracketContestant(victim.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        if (session != null && session.phase() == EventPhase.PRESTART
                && event.getDamager() instanceof Player attacker
                && (eventManager.isEventPlayer(attacker.getUniqueId()) || eventManager.isEventPlayer(victim.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        if ((eventManager.isWaitingLocked() || eventManager.isTrophyLocked())
                && event.getDamager() instanceof Player attacker
                && (eventManager.isEventPlayer(attacker.getUniqueId()) || eventManager.isEventPlayer(victim.getUniqueId()))) {
            event.setCancelled(true);
        }
    }
}
