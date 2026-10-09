package org.enthusia.events.event;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.BoundingBox;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.TeleportService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings({
        "PMD.UseConcurrentHashMap",
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidFieldNameMatchingMethodName",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.NPathComplexity",
        "PMD.NullAssignment"
})
public final class EventGameplayListener implements Listener {

    private static final List<Material> BLOCK_PARTY_COLORS = List.of(
            Material.WHITE_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE,
            Material.PINK_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.RED_CONCRETE,
            Material.BLACK_CONCRETE
    );
    private static final String BEDWARS_ITEM_SHOP_TAG = "enthusia_bedwars_item_shop";
    private static final String BEDWARS_UPGRADE_SHOP_TAG = "enthusia_bedwars_upgrade_shop";
    private static final String BEDWARS_ITEM_SHOP_TITLE = "BedWars Item Shop";
    private static final String BEDWARS_UPGRADE_SHOP_TITLE = "Upgrades & Traps";
    private static final List<Material> BEDWARS_LOOT_MATERIALS = List.of(
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD
    );

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final NamespacedKey shopRewardKey;
    private final NamespacedKey shopCostKey;
    private final NamespacedKey shopCurrencyKey;
    private final NamespacedKey shopActionKey;
    private final Map<UUID, String> raceCheckpoint = new HashMap<>();
    private final Map<UUID, Integer> raceCheckpointOrder = new HashMap<>();
    private final Map<UUID, Location> raceSafeLocation = new HashMap<>();
    private final Map<UUID, Long> elytraFinishWarningCooldowns = new HashMap<>();
    private final Set<String> elytraCheckpointSurfaceBlocks = new HashSet<>();
    private final Map<UUID, String> ctfCarriers = new HashMap<>();
    private final Map<String, Integer> ctfScores = new HashMap<>();
    private final Map<String, BlockState> ctfOriginalFlagBlocks = new HashMap<>();
    private final Map<String, Location> ctfRenderedFlagBlocks = new HashMap<>();
    private final Map<String, Location> ctfDroppedFlags = new HashMap<>();
    private final Map<String, Integer> ctfCaptureProgress = new HashMap<>();
    private final Map<UUID, String> capturePlayerJails = new HashMap<>();
    private final Map<UUID, List<CapturePlayersCarryEntry>> capturePlayerStacks = new HashMap<>();
    private final Map<UUID, UUID> capturePlayerCarrierByPrisoner = new HashMap<>();
    private final Map<String, Integer> capturePlayerRoundWins = new HashMap<>();
    private final Map<String, Integer> capturePlayerProgress = new HashMap<>();
    private final Map<UUID, Integer> quakeScores = new HashMap<>();
    private final Map<UUID, BukkitTask> quakeRespawns = new HashMap<>();
    private final Map<UUID, Integer> oneInTheChamberScores = new HashMap<>();
    private final Map<UUID, BukkitTask> oneInTheChamberRespawns = new HashMap<>();
    private final Map<UUID, EventInventoryLayout> eventInventoryLayouts = new HashMap<>();
    private final Map<UUID, Integer> spleggLastShotTicks = new HashMap<>();
    private final Map<UUID, BukkitTask> ctfRespawns = new HashMap<>();
    private final Map<UUID, CtfInventoryLayout> ctfInventoryLayouts = new HashMap<>();
    private final List<UUID> bedWarsShopEntities = new ArrayList<>();
    private final Set<String> bedWarsPlacedBlocks = new HashSet<>();
    private final Map<UUID, String> bedWarsShopPage = new HashMap<>();
    private final Map<UUID, String> pendingQuickBuyItem = new HashMap<>();
    private final Map<UUID, List<String>> quickBuySlots = new HashMap<>();
    private final Map<UUID, Integer> bedWarsPickaxeTiers = new HashMap<>();
    private final Map<UUID, Integer> bedWarsAxeTiers = new HashMap<>();
    private final Map<UUID, Boolean> bedWarsHasShears = new HashMap<>();
    private final Map<UUID, Map<Material, Integer>> bedWarsPendingResources = new HashMap<>();
    private final Map<UUID, BukkitTask> bedWarsRespawns = new HashMap<>();
    private final List<String> brokenBedTeams = new ArrayList<>();
    private final Map<String, BlockState> bedWarsTimedBedStates = new HashMap<>();
    private final Map<UUID, Integer> bedWarsArmorTiers = new HashMap<>();
    private final Set<String> bedWarsSharpnessTeams = new HashSet<>();
    private final Map<String, Integer> bedWarsProtectionLevels = new HashMap<>();
    private final Map<String, Integer> bedWarsHasteLevels = new HashMap<>();
    private final Map<String, Integer> bedWarsForgeLevels = new HashMap<>();
    private final Map<String, List<String>> bedWarsTraps = new HashMap<>();
    private final Set<String> bedWarsHealPoolTeams = new HashSet<>();
    private final Set<String> bedWarsTriggeredTraps = new HashSet<>();
    private final Map<String, Integer> bedWarsFeatherFallingLevels = new HashMap<>();
    private final List<UUID> finishOrder = new ArrayList<>();
    private final Map<UUID, Location> redLightLastSafeLocation = new HashMap<>();
    private EventSession trackedSession;
    private BukkitTask tickTask;
    private BukkitTask blockPartyTask;
    private BukkitTask ctfParticleTask;
    private BukkitTask rlglFastTask;
    private UUID hotPotatoHolder;
    private int hotPotatoSeconds;
    private boolean greenLight;
    private boolean yellowLight;
    private int redLightSeconds;
    private Material blockPartyTarget;
    private int blockPartySeconds;
    private int blockPartyTicksRemaining;
    private int blockPartyRoundTicks;
    private int blockPartySoundStage;
    private int blockPartyRound;
    private int blockPartyClearDelay;
    private int skyWarsActiveSeconds;
    private boolean skyWarsGlowApplied;
    private int bedWarsGeneratorTicks;
    private boolean bedWarsBedsDestroyedByTimer;
    private final List<UUID> bedWarsGolems = new ArrayList<>();
    private final List<UUID> bedWarsSilverfish = new ArrayList<>();
    private final Map<UUID, UUID> bedWarsProjectiles = new HashMap<>(); // projectile UUID → owner UUID
    private final Map<UUID, String> bedWarsProjectileType = new HashMap<>(); // projectile UUID → "BED_BUG"|"BRIDGE_EGG"
    private final Map<UUID, BukkitTask> bridgeEggTasks = new HashMap<>();
    private final Map<UUID, Long> quakeShotCooldowns = new HashMap<>();
    private final Map<UUID, Long> quakeLaunchCooldowns = new HashMap<>();

    private enum CapturePlayersCarryMode {
        CAPTURE,
        RESCUE
    }

    private record CapturePlayersCarryEntry(UUID prisonerId, CapturePlayersCarryMode mode, String jailTeam) {
    }

    private record EventInventoryLayout(Map<Material, Integer> preferredSlots) {
        private static final int OFFHAND_SLOT = -1;
    }

    public EventGameplayListener(EnthusiaEventsPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.shopRewardKey = new NamespacedKey(plugin, "bedwars-shop-reward");
        this.shopCostKey = new NamespacedKey(plugin, "bedwars-shop-cost");
        this.shopCurrencyKey = new NamespacedKey(plugin, "bedwars-shop-currency");
        this.shopActionKey = new NamespacedKey(plugin, "bedwars-shop-action");
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActiveEvent, 20L, 20L);
        this.ctfParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCaptureTheFlagParticles, 1L, 1L);
        this.rlglFastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRlglFast, 0L, 1L);
        loadQuickBuySlots();
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (ctfParticleTask != null) {
            ctfParticleTask.cancel();
            ctfParticleTask = null;
        }
        if (blockPartyTask != null) {
            blockPartyTask.cancel();
            blockPartyTask = null;
        }
        quakeRespawns.values().forEach(BukkitTask::cancel);
        quakeRespawns.clear();
        ctfRespawns.values().forEach(BukkitTask::cancel);
        ctfRespawns.clear();
        removeBedWarsShops();
        eventInventoryLayouts.clear();
        ctfInventoryLayouts.clear();
        bedWarsPlacedBlocks.clear();
        restoreTimedBedWarsBeds();
    }

    public void prepareSession(EventSession session) {
        ensureSession(session);
        if (session == null) {
            return;
        }
        clearDroppedEventItems(session.selectedMap());
        clearAmbientMobs(session.selectedMap());
        elytraCheckpointSurfaceBlocks.clear();
        if (session.definition().type() == EventType.ELYTRA_RACE && session.selectedMap() != null) {
            session.selectedMap().checkpointBlocks().values().stream()
                    .flatMap(List::stream)
                    .filter(location -> location != null && location.getWorld() != null)
                    .map(this::locationKey)
                    .forEach(elytraCheckpointSurfaceBlocks::add);
        }
        if (session.definition().type() == EventType.BEDWARS) {
            spawnConfiguredBedWarsBeds(session.selectedMap());
        }
    }

    private void clearAmbientMobs(EventMap map) {
        if (map == null || map.region() == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(map.region().worldName());
        if (world == null) {
            return;
        }
        CuboidRegion region = map.region();
        BoundingBox bounds = new BoundingBox(
                region.minX(), region.minY(), region.minZ(),
                region.maxX() + 1.0D, region.maxY() + 1.0D, region.maxZ() + 1.0D);
        world.getNearbyEntities(bounds, entity -> entity instanceof Mob)
                .forEach(Entity::remove);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || !session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        if (session.phase() == EventPhase.PRESTART && type == EventType.HORSE_RACE) {
            handleHorseRacePreStartFall(session, event.getPlayer(), event.getTo());
            return;
        }
        if (session.phase() != EventPhase.ACTIVE) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map == null || map.region() == null) {
            return;
        }
        if (eventManager.isBracketEvent() && !eventManager.isBracketContestant(event.getPlayer().getUniqueId())) {
            return;
        }

        if (type == EventType.CAPTURE_THE_FLAG) {
            handleCaptureTheFlagMove(session, map, event.getPlayer(), event.getTo());
            return;
        }
        if (type == EventType.CAPTURE_PLAYERS) {
            handleCapturePlayersMove(event, map);
            return;
        }
        if (type == EventType.RED_LIGHT_GREEN_LIGHT) {
            handleRedLightGreenLightMove(event.getPlayer(), map, event.getTo());
            return;
        }
        if (type == EventType.BOAT_RACE) {
            return;
        }
        if (isRaceType(type)) {
            handleRaceMove(session, map, event.getPlayer(), event.getTo());
            if (type == EventType.ELYTRA_RACE && touchesForbiddenElytraSurface(map, event.getPlayer(), event.getTo())) {
                respawnRace(event.getPlayer(), map);
            }
            return;
        }

        double eliminationY = switch (type) {
            case BLOCK_PARTY -> 65.0D;
            case SPLEGG -> -5.0D;
            default -> map.region().minY() - 1.0D;
        };
        if (usesFallElimination(type) && event.getTo() != null && event.getTo().getY() <= eliminationY) {
            if (type == EventType.BEDWARS) {
                String team = session.teams().get(event.getPlayer().getUniqueId());
                if (team != null && !brokenBedTeams.contains(team)) {
                    respawnBedWarsPlayer(session, map, event.getPlayer());
                    return;
                }
            }
            if (type == EventType.KNOCKBACK_FFA) {
                rewardKnockbackKill(event.getPlayer().getKiller());
            }
            String reason = type == EventType.BLOCK_PARTY
                    ? "after " + Math.max(0, blockPartyRound) + " rounds"
                    : "fell out of the arena";
            eventManager.eliminateParticipant(event.getPlayer(), reason);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.definition().type() != EventType.HORSE_RACE
                || (session.phase() != EventPhase.ACTIVE && session.phase() != EventPhase.PRESTART)) {
            return;
        }
        Player player = event.getVehicle().getPassengers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
        if (player == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map != null) {
            if (session.phase() == EventPhase.PRESTART) {
                handleHorseRacePreStartFall(session, player, event.getTo());
            } else {
                handleRaceMove(session, map, player, event.getTo());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        EventMap map = session.selectedMap();
        if (map != null && map.region() != null && map.region().contains(event.getBlock().getLocation())
                && (type == EventType.SKYWARS || type == EventType.BEDWARS)) {
            if (type == EventType.BEDWARS) {
                if (isBedBlock(event.getBlock().getType())) {
                    handleBedWarsBedBreak(session, map, event);
                    return;
                }
                if (!bedWarsPlacedBlocks.contains(locationKey(event.getBlock().getLocation()))) {
                    event.setCancelled(true);
                    return;
                }
                bedWarsPlacedBlocks.remove(locationKey(event.getBlock().getLocation()));
            }
            return;
        }
        if (map != null && map.region() != null && map.region().contains(event.getBlock().getLocation())
                && type == EventType.SPLEEF
                && canBreakSpleefBlock(map, event.getBlock())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        EventMap map = session.selectedMap();
        if (map != null && map.region() != null && map.region().contains(event.getBlockPlaced().getLocation())
                && (type == EventType.SKYWARS || type == EventType.BEDWARS)) {
            if (type == EventType.BEDWARS) {
                if (event.getBlockPlaced().getType() == Material.TNT) {
                    Location location = event.getBlockPlaced().getLocation().add(0.5D, 0.0D, 0.5D);
                    event.getBlockPlaced().setType(Material.AIR, false);
                    TNTPrimed primed = location.getWorld().spawn(location, TNTPrimed.class);
                    primed.setSource(event.getPlayer());
                    primed.setFuseTicks(80);
                    event.getPlayer().playSound(location, Sound.ENTITY_TNT_PRIMED, 1.0F, 1.0F);
                    return;
                }
                bedWarsPlacedBlocks.add(locationKey(event.getBlockPlaced().getLocation()));
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.BEDWARS) {
            return;
        }
        if (!(event.getEntity() instanceof TNTPrimed) && !(event.getEntity() instanceof Fireball)) {
            return;
        }
        event.blockList().removeIf(this::isProtectedBedWarsExplosionBlock);
        event.blockList().forEach(block -> bedWarsPlacedBlocks.remove(locationKey(block.getLocation())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!session.participants().contains(player.getUniqueId()) || !isHungerLockedEvent(session.definition().type())) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.BEDWARS) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getRightClicked().getScoreboardTags().contains(BEDWARS_ITEM_SHOP_TAG)) {
            event.setCancelled(true);
            event.getPlayer().openInventory(createBedWarsItemShop(event.getPlayer()));
            return;
        }
        if (event.getRightClicked().getScoreboardTags().contains(BEDWARS_UPGRADE_SHOP_TAG)) {
            event.setCancelled(true);
            event.getPlayer().openInventory(createBedWarsUpgradeShop(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        EventSession session = eventManager.session();
        if (session == null || session.definition().type() != EventType.BEDWARS) return;
        if (!session.participants().contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String title = event.getView().getTitle();
        if (!BEDWARS_ITEM_SHOP_TITLE.equals(title) && !BEDWARS_UPGRADE_SHOP_TITLE.equals(title)) {
            return;
        }
        event.setCancelled(true);
        EventSession session = eventManager.session();
        if (session == null || session.definition().type() != EventType.BEDWARS || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer().get(shopActionKey, PersistentDataType.STRING);
        if (BEDWARS_ITEM_SHOP_TITLE.equals(title)) {
            if (action != null && action.startsWith("PAGE:")) {
                String targetPage = action.substring(5);
                if (event.isShiftClick() && !"QUICK_BUY".equals(targetPage)) {
                    // Shift-clicking a category tab doesn't do anything special
                    bedWarsShopPage.put(player.getUniqueId(), targetPage);
                    player.openInventory(createBedWarsItemShop(player));
                    return;
                }
                bedWarsShopPage.put(player.getUniqueId(), targetPage);
                player.openInventory(createBedWarsItemShop(player));
                return;
            }
            if ("QUICK_BUY".equals(bedWarsShopPage.getOrDefault(player.getUniqueId(), "QUICK_BUY"))) {
                handleQuickBuyClick(player, clicked, event.isShiftClick());
                return;
            }
            if (event.isShiftClick() && action != null) {
                // Shift-click on category page item: start Quick Buy placement
                pendingQuickBuyItem.put(player.getUniqueId(), action);
                bedWarsShopPage.put(player.getUniqueId(), "QUICK_BUY");
                player.openInventory(createBedWarsItemShop(player));
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Click a Quick Buy slot to place this item.");
                return;
            }
            buyBedWarsShopItem(player, clicked);
            return;
        }
        if (BEDWARS_UPGRADE_SHOP_TITLE.equals(title)) {
            if (action != null && action.startsWith("TRAP_REMOVE:")) {
                String team = session.teams().get(player.getUniqueId());
                if (team != null) {
                    String[] parts = action.split(":");
                    int index = Integer.parseInt(parts[2]);
                    List<String> traps = bedWarsTraps.get(team);
                    if (traps != null && index < traps.size()) {
                        traps.remove(index);
                        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Trap removed from queue.");
                        player.openInventory(createBedWarsUpgradeShop(player));
                    }
                }
                return;
            }
            buyBedWarsUpgrade(player, clicked);
        }
    }

    private void handleQuickBuyClick(Player player, ItemStack clicked, boolean isShiftClick) {
        String action = clicked.getItemMeta().getPersistentDataContainer().get(shopActionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        String pending = pendingQuickBuyItem.remove(player.getUniqueId());
        if (pending != null) {
            // We're in placement mode: place the pending item into this slot
            List<String> slots = quickBuySlots.computeIfAbsent(player.getUniqueId(), uuid -> defaultQuickBuyLayout());
            // Find which slot this is
            int slot = findClickSlot(clicked);
            if (slot >= 0 && slot < 21) {
                while (slots.size() <= slot) {
                    slots.add("EMPTY");
                }
                slots.set(slot, pending);
                saveQuickBuySlots();
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Item added to Quick Buy slot " + (slot + 1) + ".");
                player.openInventory(createBedWarsItemShop(player));
                return;
            }
        }
        if (isShiftClick) {
            // Shift-click on Quick Buy slot: remove item
            List<String> slots = quickBuySlots.computeIfAbsent(player.getUniqueId(), uuid -> defaultQuickBuyLayout());
            int slot = findClickSlot(clicked);
            if (slot >= 0 && slot < slots.size()) {
                slots.set(slot, "EMPTY");
                saveQuickBuySlots();
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Item removed from Quick Buy slot " + (slot + 1) + ".");
                player.openInventory(createBedWarsItemShop(player));
            }
            return;
        }
        // Normal click: buy item
        if (!"EMPTY".equals(action)) {
            buyBedWarsShopItem(player, clicked);
        }
    }

    private int findClickSlot(ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return -1;
        Integer slot = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "qb-slot"), PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    private void saveQuickBuySlots() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "bedwars_quickbuy.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, List<String>> entry : quickBuySlots.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to save BedWars Quick Buy data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadQuickBuySlots() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "bedwars_quickbuy.yml");
        if (!file.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<String> slots = yaml.getStringList(key);
                if (slots != null && !slots.isEmpty()) {
                    quickBuySlots.put(uuid, new ArrayList<>(slots));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        if (!session.participants().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        if (type == EventType.BEDWARS
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && event.getItem().getType() == Material.FIRE_CHARGE) {
            event.setCancelled(true);
            launchBedWarsFireball(event.getPlayer(), event.getItem(), event.getHand());
            return;
        }
        if (type == EventType.BEDWARS
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && event.getItem().getType() == Material.IRON_GOLEM_SPAWN_EGG) {
            event.setCancelled(true);
            spawnDreamDefender(event.getPlayer(), event.getItem(), event.getHand());
            return;
        }
        // Track Bed Bug / Bridge Egg projectiles
        if (type == EventType.BEDWARS
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null) {
            String trackType = null;
            if (event.getItem().getType() == Material.SNOWBALL) {
                trackType = "BED_BUG";
            } else if (event.getItem().getType() == Material.EGG) {
                trackType = "BRIDGE_EGG";
            }
            if (trackType != null) {
                // Let vanilla launch happen, but schedule tracking on next tick
                Player p = event.getPlayer();
                String finalType = trackType;
                Bukkit.getScheduler().runTaskLater(plugin, () -> trackThrownProjectile(p, finalType), 0L);
            }
        }
        if (type == EventType.ELYTRA_RACE
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && event.getItem().getType() == Material.RECOVERY_COMPASS) {
            event.setCancelled(true);
            EventMap map = session.selectedMap();
            if (map != null) {
                respawnRace(event.getPlayer(), map);
            }
            return;
        }
        if (type == EventType.SPLEGG && isSpleggLaunchAction(event)) {
            event.setCancelled(true);
            launchSpleggSnowball(event.getPlayer());
            return;
        }
        if (type == EventType.ONE_IN_THE_CHAMBER
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && event.getItem().getType() == Material.BOW) {
            rememberEventInventoryLayout(event.getPlayer());
            return;
        }
        if (type == EventType.KNOCKBACK_FFA
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && event.getItem().getType() == Material.ENDER_PEARL) {
            rememberEventInventoryLayout(event.getPlayer());
            return;
        }
        if (type == EventType.QUAKE && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.GOLDEN_HOE) {
                return;
            }
            fireQuake(event.getPlayer(), session);
            return;
        }
        if (type == EventType.QUAKE && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.GOLDEN_HOE) {
                return;
            }
            launchQuake(event.getPlayer());
            return;
        }
        if (type == EventType.PARKOUR || type == EventType.BLOCK_PARTY || type == EventType.RED_LIGHT_GREEN_LIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.PRESTART || session.definition().type() != EventType.SKYWARS) {
            return;
        }
        if (!session.participants().contains(player.getUniqueId())) {
            return;
        }
        Location location = event.getInventory().getLocation();
        EventMap map = session.selectedMap();
        if (location != null && map != null && map.region() != null && map.region().contains(location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Chests unlock when the event starts.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        if (type == EventType.QUAKE) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.CAPTURE_THE_FLAG) {
            if (event instanceof EntityDamageByEntityEvent byEntity
                    && damagingPlayer(byEntity.getDamager()) != null) {
                return;
            }
            if (event instanceof EntityDamageByEntityEvent) {
                event.setCancelled(true);
            }
            if (isLethalDamage(player, event)) {
                event.setCancelled(true);
                EventMap map = session.selectedMap();
                if (map != null) {
                    dropCarriedFlag(player, groundFlagLocation(player.getLocation()));
                    scheduleCaptureTheFlagRespawn(session, map, player, "You will respawn in 5 seconds.");
                }
            }
            return;
        }
        if (type == EventType.ONE_IN_THE_CHAMBER) {
            if (event instanceof EntityDamageByEntityEvent byEntity
                    && damagingPlayer(byEntity.getDamager()) != null
                    && (byEntity.getDamager() instanceof Arrow
                    || playerHoldingAxe(damagingPlayer(byEntity.getDamager())))) {
                return;
            }
            event.setCancelled(true);
            return;
        }
        // BedWars: intercept lethal damage, prevent vanilla death screen
        if (type == EventType.BEDWARS && player.getHealth() - event.getFinalDamage() <= 0.0D) {
            event.setCancelled(true);
            player.setHealth(player.getMaxHealth());
            String team = session.teams().get(player.getUniqueId());
            if (team != null && !brokenBedTeams.contains(team)) {
                // Bed alive: schedule 5-second respawn
                bedWarsPickaxeTiers.computeIfPresent(player.getUniqueId(), (uuid, tier) -> Math.max(0, tier - 1));
                bedWarsAxeTiers.computeIfPresent(player.getUniqueId(), (uuid, tier) -> Math.max(0, tier - 1));
                Player killer = event instanceof EntityDamageByEntityEvent byEntity ? damagingPlayer(byEntity.getDamager()) : null;
                if (killer != null && session.participants().contains(killer.getUniqueId())) {
                    transferBedWarsResources(player, killer);
                    eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName() + " was killed by " + killer.getName() + ".");
                } else {
                    eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName() + " died.");
                }
                scheduleBedWarsRespawn(session, player);
            } else {
                // No bed: eliminate
                eventManager.eliminateParticipant(player, "final death");
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName() + " was eliminated.");
            }
            return;
        }
        if (type == EventType.PARKOUR || type == EventType.BLOCK_PARTY || type == EventType.RED_LIGHT_GREEN_LIGHT
                || type == EventType.BOAT_RACE || type == EventType.HORSE_RACE
                || (type == EventType.CAPTURE_PLAYERS && (!(event instanceof EntityDamageByEntityEvent byEntity)
                || damagingPlayer(byEntity.getDamager()) == null))) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.SUMO_1V1 || type == EventType.SUMO_2V2 || type == EventType.SUMO_FFA
                || type == EventType.KNOCKBACK_FFA) {
            event.setDamage(type == EventType.KNOCKBACK_FFA ? 0.01D : 0.0D);
            return;
        }
        if (shouldSimulateLethalDamage(type, event) && isLethalDamage(player, event)) {
            event.setCancelled(true);
            simulateEventDeath(session, player, type);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        EventType type = session.definition().type();
        Player damagePlayer = damagingPlayer(event.getDamager());
        if (eventManager.isBracketEvent()
                && (damagePlayer == null
                || !(event.getEntity() instanceof Player target)
                || !eventManager.isBracketContestant(damagePlayer.getUniqueId())
                || !eventManager.isBracketContestant(target.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        if (eventManager.isTeamBracketEvent()
                && damagePlayer != null
                && event.getEntity() instanceof Player target
                && sameTeam(session, target, damagePlayer)) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.QUAKE && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.SPLEGG && event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        Player damager = damagePlayer;
        if (type == EventType.BEDWARS && event.getEntity() instanceof Player target && damager != null
                && sameTeam(session, target, damager)) {
            event.setCancelled(true);
            return;
        }
        if (type == EventType.CAPTURE_THE_FLAG && event.getEntity() instanceof Player target && damager != null) {
            handleCaptureTheFlagDamage(session, target, damager, event);
            return;
        }
        if (type == EventType.CAPTURE_PLAYERS && event.getEntity() instanceof Player target && damager != null) {
            handleCapturePlayersDamage(session, target, damager, event);
            return;
        }
        if (type == EventType.ONE_IN_THE_CHAMBER && damager != null && event.getEntity() instanceof Player target
                && event.getDamager() instanceof Arrow) {
            event.setCancelled(true);
            handleOneInTheChamberKill(session, damager, target);
            return;
        }
        if (type == EventType.ONE_IN_THE_CHAMBER && damager != null && event.getEntity() instanceof Player target
                && damager.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) {
            event.setCancelled(true);
            Material axe = damager.getInventory().getItemInMainHand().getType();
            if (damager.hasCooldown(axe)) {
                return;
            }
            damager.setCooldown(axe, plugin.getConfig().getInt("one-in-the-chamber.axe-cooldown-ticks", 20));
            handleOneInTheChamberKill(session, damager, target);
            return;
        }
        // BedWars: prevent owner/allies from damaging their own Iron Golems
        if (type == EventType.BEDWARS && event.getEntity() instanceof IronGolem golem && damager != null) {
            String golemTeam = golem.getScoreboardTags().stream()
                    .filter(t -> t.startsWith("bw_owner_"))
                    .findFirst().orElse(null);
            String damagerTeam = session.teams().get(damager.getUniqueId());
            if (golemTeam != null && damagerTeam != null && golemTeam.equals("bw_owner_" + damagerTeam)) {
                event.setCancelled(true);
                return;
            }
        }
        // BedWars: cap fireball direct/splash damage to ~3.0 health
        if (type == EventType.BEDWARS && event.getDamager() instanceof Fireball fireball
                && fireball.getShooter() instanceof Player
                && event.getEntity() instanceof Player target) {
            double cap = plugin.getConfig().getDouble("bedwars.fireball-max-damage", 3.0D);
            if (event.getFinalDamage() > cap) {
                event.setDamage(cap);
            }
            // Fireball damage goes through normal BedWars death/respawn handling
            return;
        }
        // BedWars: Knockback Stick pushes Iron Golems
        if (type == EventType.BEDWARS && damager != null && event.getEntity() instanceof IronGolem golem
                && damager.getInventory().getItemInMainHand().getType() == Material.STICK
                && damager.getInventory().getItemInMainHand().containsEnchantment(Enchantment.KNOCKBACK)) {
            Vector knockback = golem.getLocation().toVector().subtract(damager.getLocation().toVector()).normalize().multiply(2.0D).setY(0.6D);
            golem.setVelocity(knockback);
            event.setDamage(0.5D); // Minimal damage, mostly knockback
            return;
        }
        if (type == EventType.HOT_POTATO && damager != null && event.getEntity() instanceof Player target
                && session.participants().contains(damager.getUniqueId())
                && session.participants().contains(target.getUniqueId())
                && damager.getUniqueId().equals(hotPotatoHolder)) {
            hotPotatoHolder = target.getUniqueId();
            hotPotatoSeconds = 12;
            damager.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You passed the hot potato.");
            target.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You have the hot potato.");
            event.setDamage(0.0D);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        undoEventDeathStatistic(player);
        if (session.definition().type() != EventType.SKYWARS) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        Player killer = player.getKiller();
        if (session.definition().type() == EventType.ELYTRA_RACE) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Respawning at your last checkpoint.");
            return;
        }
        if (session.definition().type() == EventType.ONE_IN_THE_CHAMBER) {
            rememberEventInventoryLayout(player);
            if (killer != null && session.participants().contains(killer.getUniqueId())) {
                addOneInTheChamberScore(killer, 1);
                refillOneInTheChamberArrow(killer);
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + killer.getName()
                        + " eliminated " + player.getName() + ".");
            }
            return;
        }
        if (session.definition().type() == EventType.KNOCKBACK_FFA) {
            rewardKnockbackKill(killer);
        }
        if (session.definition().type() == EventType.BEDWARS) {
            transferBedWarsResources(player, killer);
            String team = session.teams().get(player.getUniqueId());
            if (team != null && !brokenBedTeams.contains(team)) {
                // Bed is alive: schedule 5-second respawn
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName() + " died. Respawning in 5 seconds.");

                // Downgrade tools
                bedWarsPickaxeTiers.computeIfPresent(player.getUniqueId(), (uuid, tier) -> Math.max(0, tier - 1));
                bedWarsAxeTiers.computeIfPresent(player.getUniqueId(), (uuid, tier) -> Math.max(0, tier - 1));

                // Schedule respawn
                scheduleBedWarsRespawn(session, player);
                return;
            }
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName() + " was eliminated.");
        }
        eventManager.eliminateParticipant(player, "died");
    }

    private boolean shouldSimulateLethalDamage(EventType type, EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent
                && (type == EventType.CAPTURE_PLAYERS || type == EventType.ONE_IN_THE_CHAMBER
                || type == EventType.HOT_POTATO || type == EventType.SPLEGG)) {
            return false;
        }
        return type == EventType.SKYWARS
                || type == EventType.ELYTRA_RACE
                || type == EventType.FIGHT_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.FIGHT_FFA
                || type == EventType.SPLEEF
                || type == EventType.SPLEGG
                || type == EventType.RUNNER
                || type == EventType.HOT_POTATO;
    }

    private boolean playerHoldingAxe(Player player) {
        return player != null && player.getInventory().getItemInMainHand().getType().name().endsWith("_AXE");
    }

    private boolean isLethalDamage(Player player, EntityDamageEvent event) {
        return player.getHealth() - event.getFinalDamage() <= 0.0D;
    }

    private void simulateEventDeath(EventSession session, Player player, EventType type) {
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        if (type == EventType.ELYTRA_RACE) {
            player.setHealth(player.getMaxHealth());
            EventMap map = session.selectedMap();
            if (map != null) {
                respawnRace(player, map);
            }
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Respawning at your last checkpoint.");
            return;
        }
        if (type == EventType.SKYWARS) {
            dropSkyWarsInventory(player);
        }
        player.setHealth(player.getMaxHealth());
        eventManager.eliminateParticipant(player, "died");
    }

    private void dropSkyWarsInventory(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            addSkyWarsDrop(items, item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            addSkyWarsDrop(items, item);
        }
        addSkyWarsDrop(items, player.getInventory().getItemInOffHand());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setLevel(0);
        player.setExp(0.0F);
        for (ItemStack item : items) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private void addSkyWarsDrop(List<ItemStack> drops, ItemStack item) {
        if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
            drops.add(item.clone());
        }
    }

    private void undoEventDeathStatistic(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                int deaths = player.getStatistic(Statistic.DEATHS);
                if (deaths > 0) {
                    player.setStatistic(Statistic.DEATHS, deaths - 1);
                }
            } catch (IllegalArgumentException ignored) {
                // Servers may restrict direct statistic writes; regular event damage is intercepted first.
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        EventSession session = eventManager.session();
        if (session != null && session.definition().type() == EventType.BEDWARS
                && eventManager.isEventPlayer(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EventSession session = eventManager.session();
        if (session != null && session.definition().type() == EventType.CAPTURE_PLAYERS
                && capturePlayerCarrierByPrisoner.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void scheduleBedWarsRespawn(EventSession session, Player player) {
        if (bedWarsRespawns.containsKey(player.getUniqueId())) {
            return;
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.SPECTATOR);
        // Force auto-respawn to dismiss vanilla death screen
        Bukkit.getScheduler().runTask(plugin, () -> player.spigot().respawn());
        final int[] seconds = {5};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EventSession current = eventManager.session();
            if (current == null || current != session || current.phase() != EventPhase.ACTIVE
                    || current.definition().type() != EventType.BEDWARS
                    || !current.participants().contains(player.getUniqueId()) || !player.isOnline()) {
                BukkitTask currentTask = bedWarsRespawns.remove(player.getUniqueId());
                if (currentTask != null) currentTask.cancel();
                return;
            }
            if (seconds[0] > 0) {
                showRespawnCountdown(player, seconds[0], 5, ChatColor.YELLOW);
                seconds[0]--;
                return;
            }
            BukkitTask currentTask = bedWarsRespawns.remove(player.getUniqueId());
            if (currentTask != null) currentTask.cancel();
            EventMap map = current.selectedMap();
            clearRespawnCountdown(player);
            respawnBedWarsPlayer(current, map, player);
            player.setGameMode(GameMode.SURVIVAL);
        }, 0L, 20L);
        bedWarsRespawns.put(player.getUniqueId(), task);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        EventSession session = eventManager.session();
        if (session == null) {
            return;
        }
        EventMap map = session.selectedMap();
        if (session.definition().type() == EventType.BEDWARS && session.participants().contains(event.getPlayer().getUniqueId())) {
            // If the player has a pending respawn timer, the timer handles everything
            if (bedWarsRespawns.containsKey(event.getPlayer().getUniqueId())) {
                return;
            }
            Location spawn = bedWarsRespawn(session, map, event.getPlayer());
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getPlayer().setGameMode(GameMode.SURVIVAL);
                respawnBedWarsPlayer(session, map, event.getPlayer());
            });
            return;
        }
        if (session.definition().type() == EventType.ONE_IN_THE_CHAMBER && session.participants().contains(event.getPlayer().getUniqueId())) {
            Location spawn = randomSpawn(map);
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(10.0F);
                player.setFireTicks(0);
                player.setFallDistance(0.0F);
                applyOneInTheChamberLoadout(player);
            });
            return;
        }
        if (session.definition().type() == EventType.ELYTRA_RACE && session.participants().contains(event.getPlayer().getUniqueId())) {
            Location spawn = map == null ? null : safeLocation(event.getPlayer(), map).clone().add(0.0D, 1.0D, 0.0D);
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.setFireTicks(0);
                player.setFallDistance(0.0F);
            });
            return;
        }
        if (!session.spectators().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (map != null && map.spectatorSpawn() != null) {
            event.setRespawnLocation(map.spectatorSpawn());
        }
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().setGameMode(GameMode.SPECTATOR));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        // BedWars Bed Bug / Bridge Egg handling
        if (session.definition().type() == EventType.BEDWARS) {
            UUID projectileId = event.getEntity().getUniqueId();
            String pType = bedWarsProjectileType.remove(projectileId);
            UUID ownerId = bedWarsProjectiles.remove(projectileId);
            if (pType == null) return;
            Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
            if ("BED_BUG".equals(pType) && event.getEntity() instanceof Snowball snowball) {
                spawnBedBugSilverfish(owner, snowball.getLocation());
            } else if ("BRIDGE_EGG".equals(pType)) {
                BukkitTask task = bridgeEggTasks.remove(projectileId);
                if (task != null) task.cancel();
                placeBridgeEggBlocks(session, owner, event.getEntity().getLocation());
            }
            return;
        }
        if (session.definition().type() != EventType.SPLEGG) {
            return;
        }
        if (!(event.getEntity() instanceof Snowball)) {
            return;
        }
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player player) || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        Block block = event.getHitBlock();
        EventMap map = session.selectedMap();
        if (block != null && map != null && map.region() != null && map.region().contains(block.getLocation())
                && canBreakSpleefBlock(map, block)) {
            eventManager.recordArenaResetBlock(block);
            block.setType(Material.AIR, false);
        }
    }

    public void resetForNewSession() {
        raceCheckpoint.clear();
        raceCheckpointOrder.clear();
        raceSafeLocation.clear();
        elytraFinishWarningCooldowns.clear();
        elytraCheckpointSurfaceBlocks.clear();
        ctfCarriers.clear();
        ctfScores.clear();
        restoreCaptureTheFlagBlocks();
        ctfRenderedFlagBlocks.clear();
        ctfDroppedFlags.clear();
        ctfCaptureProgress.clear();
        quakeScores.clear();
        eventInventoryLayouts.clear();
        spleggLastShotTicks.clear();
        oneInTheChamberRespawns.values().forEach(BukkitTask::cancel);
        oneInTheChamberRespawns.clear();
        quakeRespawns.values().forEach(BukkitTask::cancel);
        quakeRespawns.clear();
        ctfRespawns.values().forEach(BukkitTask::cancel);
        ctfRespawns.clear();
        ctfInventoryLayouts.clear();
        bedWarsRespawns.values().forEach(BukkitTask::cancel);
        bedWarsRespawns.clear();
        removeBedWarsShops();
        clearBedWarsEntities();
        bedWarsPlacedBlocks.clear();
        restoreTimedBedWarsBeds();
        bedWarsArmorTiers.clear();
        bedWarsSharpnessTeams.clear();
        bedWarsProtectionLevels.clear();
        bedWarsHasteLevels.clear();
        bedWarsForgeLevels.clear();
        bedWarsTraps.clear();
        bedWarsHealPoolTeams.clear();
        bedWarsTriggeredTraps.clear();
        bedWarsFeatherFallingLevels.clear();
        bedWarsBedsDestroyedByTimer = false;
        bedWarsShopPage.clear();
        pendingQuickBuyItem.clear();
        bedWarsPickaxeTiers.clear();
        bedWarsAxeTiers.clear();
        bedWarsHasShears.clear();
        bedWarsPendingResources.clear();
        saveQuickBuySlots();
        clearDroppedEventItems(trackedSession == null ? null : trackedSession.selectedMap());
        clearEventContainers(trackedSession == null ? null : trackedSession.selectedMap());
        clearCapturePlayersState();
        brokenBedTeams.clear();
        finishOrder.clear();
        redLightLastSafeLocation.clear();
        hotPotatoHolder = null;
        hotPotatoSeconds = 0;
        greenLight = false;
        yellowLight = false;
        redLightSeconds = 0;
        blockPartyTarget = null;
        blockPartySeconds = 0;
        blockPartyTicksRemaining = 0;
        blockPartyRoundTicks = 0;
        blockPartySoundStage = 0;
        blockPartyRound = 0;
        blockPartyClearDelay = 0;
        skyWarsActiveSeconds = 0;
        skyWarsGlowApplied = false;
        if (blockPartyTask != null) {
            blockPartyTask.cancel();
            blockPartyTask = null;
        }
        bedWarsGeneratorTicks = 0;
        quakeShotCooldowns.clear();
        quakeLaunchCooldowns.clear();
        oneInTheChamberScores.clear();
        trackedSession = null;
    }

    public UUID captureCarrier(UUID captive) { return capturePlayerCarrierByPrisoner.get(captive); }

    public void handleCapturePlayerDeparture(Player player) {
        EventSession session = eventManager.session();
        if (session == null || session.definition().type() != EventType.CAPTURE_PLAYERS) return;
        UUID id = player.getUniqueId();
        clearCapturePlayersProgress(id);
        if (!capturePlayerStacks.containsKey(id) && !capturePlayerCarrierByPrisoner.containsKey(id)
                && !capturePlayerJails.containsKey(id)) return;
        UUID carrier = capturePlayerCarrierByPrisoner.remove(id);
        if (carrier != null) {
            List<CapturePlayersCarryEntry> stack = capturePlayerStacks.get(carrier);
            if (stack != null) stack.removeIf(entry -> entry.prisonerId().equals(id));
            player.leaveVehicle();
            rebuildCapturePlayersStack(carrier);
        }
        if (session.selectedMap() != null) releaseCapturePlayersStack(session, session.selectedMap(), player, true);
        else dismountCapturePlayersStack(id);
        capturePlayerJails.remove(id);
        clearCapturePlayersProgress(id);
        restoreCapturePlayerState(player);
    }

    private void detachAllCapturePlayers() {
        var carriers = List.copyOf(capturePlayerStacks.keySet());
        var prisoners = List.copyOf(capturePlayerCarrierByPrisoner.keySet());
        capturePlayerStacks.clear();
        capturePlayerCarrierByPrisoner.clear(); // Clear ownership before emitting dismount events.
        for (UUID id : carriers) {
            Player carrier = Bukkit.getPlayer(id);
            if (carrier != null) carrier.eject();
        }
        for (UUID id : prisoners) {
            Player prisoner = Bukkit.getPlayer(id);
            if (prisoner != null) {
                prisoner.leaveVehicle();
                restoreCapturePlayerState(prisoner);
            }
        }
    }

    private void clearCapturePlayersState() {
        detachAllCapturePlayers();
        capturePlayerJails.clear();
        capturePlayerStacks.clear();
        capturePlayerCarrierByPrisoner.clear();
        capturePlayerRoundWins.clear();
        capturePlayerProgress.clear();
    }

    private void clearEventContainers(EventMap map) {
        if (map == null || map.region() == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(map.worldName());
        if (world == null) {
            return;
        }
        for (Map.Entry<Integer, List<Location>> entry : map.chests().entrySet()) {
            for (Location location : entry.getValue()) {
                if (location == null || location.getWorld() == null) continue;
                BlockState state = location.getBlock().getState();
                if (state instanceof org.bukkit.block.Container container) {
                    container.getInventory().clear();
                }
            }
        }
    }

    private void tickActiveEvent() {
        EventSession session = eventManager.session();
        ensureSession(session);
        if (session == null || session.phase() != EventPhase.ACTIVE) {
            return;
        }
        switch (session.definition().type()) {
            case HOT_POTATO -> tickHotPotato(session);
            case RED_LIGHT_GREEN_LIGHT -> checkRlglMovement(session);
            case BLOCK_PARTY -> ensureBlockPartyTask();
            case CAPTURE_THE_FLAG -> ensureCaptureTheFlagBlocks(session.selectedMap());
            case CAPTURE_PLAYERS -> tickCapturePlayers(session);
            case SKYWARS -> tickSkyWars(session);
            case BEDWARS -> {
                tickBedWarsGenerators(session);
                ensureBedWarsShops(session);
                tickBedWarsUpgrades(session);
                tickBedWarsShopLookAt(session);
                destroyBedWarsBedsOnTimer(session);
            }
            default -> {
            }
        }
    }

    private void tickSkyWars(EventSession session) {
        skyWarsActiveSeconds++;
        if (skyWarsGlowApplied || skyWarsActiveSeconds < plugin.getConfig().getInt("skywars.glow-after-seconds", 300)) {
            return;
        }
        skyWarsGlowApplied = true;
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "All remaining SkyWars players are now glowing.");
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setGlowing(true);
            }
        }
    }

    private void handleRaceMove(EventSession session, EventMap map, Player player, Location to) {
        if (to == null) {
            return;
        }
        EventType type = session.definition().type();
        Location checkLocation = type == EventType.HORSE_RACE && player.isInsideVehicle() && player.getVehicle() != null
                ? player.getVehicle().getLocation()
                : to;
        if (checkLocation.getWorld() == null || map.worldName() == null || !checkLocation.getWorld().getName().equalsIgnoreCase(map.worldName())) {
            return;
        }
        Location safe = safeLocation(player, map);
        double fallDistance = raceFallDistance(type);
        if (type == EventType.HORSE_RACE && checkLocation.getY() <= 65.0D) {
            respawnRace(player, map);
            return;
        }
        // Elytra race only respawns on death or manual trigger, not on low altitude
        if (type != EventType.ELYTRA_RACE
                && sameWorld(safe, checkLocation)
                && (checkLocation.getY() < safe.getY() - fallDistance || checkLocation.getY() <= player.getWorld().getMinHeight() + 2)) {
            respawnRace(player, map);
            return;
        }

        Map.Entry<String, List<Location>> blockCheckpoint = reachedCheckpointBlocks(map, checkLocation, type);
        if (blockCheckpoint != null) {
            Location checkpointLocation = blockCheckpoint.getValue().isEmpty()
                    ? checkLocation
                    : blockCheckpoint.getValue().getFirst();
            recordRaceCheckpoint(player, map, blockCheckpoint.getKey(), checkpointLocation);
            return;
        }

        Map.Entry<String, CuboidRegion> areaCheckpoint = reachedRaceArea(map, checkLocation, type);
        if (areaCheckpoint != null) {
            if (isFinishCheckpoint(areaCheckpoint.getKey())) {
                finishRace(player);
                return;
            }
            recordRaceCheckpoint(player, map, areaCheckpoint.getKey(), center(areaCheckpoint.getValue()));
            return;
        }

        Material marker = checkLocation.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();
        if ((type == EventType.PARKOUR || type == EventType.ELYTRA_RACE) && marker == Material.EMERALD_BLOCK) {
            finishRace(player);
            return;
        }
        if ((type == EventType.PARKOUR || type == EventType.ELYTRA_RACE) && marker == Material.GOLD_BLOCK) {
            String autoCheckpoint = "auto-" + checkLocation.getBlockX() + "-" + checkLocation.getBlockY() + "-" + checkLocation.getBlockZ();
            Map.Entry<String, Location> nearest = nearestCheckpoint(map, checkLocation);
            int order = nearest == null ? checkpointOrder(autoCheckpoint) : checkpointOrder(nearest.getKey());
            int currentBest = raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0);
            if (order > currentBest) {
                recordRaceCheckpoint(player, map, nearest == null ? autoCheckpoint : nearest.getKey(), nearest == null ? checkLocation : nearest.getValue());
            }
            return;
        }

        for (Map.Entry<String, Location> checkpoint : orderedCheckpoints(map)) {
            String key = checkpoint.getKey().toLowerCase(Locale.ROOT);
            boolean reached = isFinishCheckpoint(key)
                    ? sameBlock(checkpoint.getValue(), checkLocation)
                    : reachesCheckpointPoint(checkpoint.getValue(), checkLocation, type);
            if (reached) {
                if (key.equals(raceCheckpoint.get(player.getUniqueId()))) {
                    continue;
                }
                if (isFinishCheckpoint(key)) {
                    finishRace(player);
                    return;
                }
                int order = checkpointOrder(checkpoint.getKey());
                int currentBest = raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0);
                if (order <= currentBest) {
                    return;
                }
                recordRaceCheckpoint(player, map, checkpoint.getKey(), checkpoint.getValue());
                return;
            }
        }
    }

    private void recordRaceCheckpoint(Player player, EventMap map, String key, Location checkpointLocation) {
        int order = checkpointOrder(key);
        int currentBest = raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0);
        EventSession session = eventManager.session();
        if (session != null && session.definition().type() == EventType.ELYTRA_RACE && order != currentBest + 1) {
            return;
        }
        if (order <= currentBest) {
            return;
        }
        raceCheckpoint.put(player.getUniqueId(), key);
        raceCheckpointOrder.put(player.getUniqueId(), order);
        eventManager.setRuntimeScoreboardValue("checkpoint-" + player.getUniqueId(), checkpointNumber(key).isBlank() ? key : checkpointNumber(key));
        Location respawn = checkpointRespawn(map, key, checkpointLocation);
        if (respawn != null) {
            raceSafeLocation.put(player.getUniqueId(), respawn.clone());
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + checkpointMessage(key));
    }

    private void respawnRace(Player player, EventMap map) {
        Location checkpoint = raceSafeLocation.get(player.getUniqueId());
        String id = raceCheckpoint.get(player.getUniqueId());
        if (checkpoint == null && id != null) {
            checkpoint = checkpointRespawn(map, id, map.checkpoints().get(id));
        }
        if (checkpoint == null && !map.spawns().isEmpty()) {
            checkpoint = map.spawns().values().iterator().next();
        }
        if (checkpoint != null) {
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector(0, 0, 0));
            Location target = checkpoint.clone();
            EventSession session = eventManager.session();
            if (session != null && session.definition().type() == EventType.HORSE_RACE) {
                target.add(0.0D, plugin.getConfig().getDouble("horse-race.respawn-height-offset", 2.5D), 0.0D);
            }
            TeleportService.teleport(plugin, player, target, "race checkpoint respawn").thenAccept(success -> {
                if (success) {
                    eventManager.remountRaceVehicle(player, target);
                }
            });
            String checkpointNumber = checkpointNumber(id);
            if (!checkpointNumber.isBlank()) {
                plugin.messages().send(player, "parkour-respawned", Map.of("checkpoint", checkpointNumber));
            }
        }
    }

    private void handleHorseRacePreStartFall(EventSession session, Player player, Location location) {
        EventMap map = session.selectedMap();
        if (map == null || location == null) {
            return;
        }
        double recoveryY = plugin.getConfig().getDouble("horse-race.recovery-y", 65.0D);
        if (location.getY() <= recoveryY) {
            respawnRace(player, map);
        }
    }

    private boolean reachesCheckpointPoint(Location checkpoint, Location current, EventType type) {
        if (!sameWorld(checkpoint, current)) {
            return false;
        }
        if (type != EventType.HORSE_RACE) {
            return checkpoint.distanceSquared(current) <= 4.0D;
        }
        double dx = checkpoint.getX() - current.getX();
        double dz = checkpoint.getZ() - current.getZ();
        return (dx * dx) + (dz * dz) <= 9.0D
                && current.getY() >= checkpoint.getY() - 2.0D
                && current.getY() <= checkpoint.getY() + 6.0D;
    }

    private double raceFallDistance(EventType type) {
        if (type == EventType.HORSE_RACE) {
            return plugin.getConfig().getDouble("horse-race.fall-distance-below-last-safe-point", 96.0D);
        }
        if (type == EventType.BOAT_RACE) {
            return plugin.getConfig().getDouble("boat-race.fall-distance-below-last-safe-point", 96.0D);
        }
        return plugin.getConfig().getDouble("parkour.fall-distance-below-last-safe-point", 8.0D);
    }

    private boolean sameWorld(Location left, Location right) {
        return left != null && right != null
                && left.getWorld() != null && right.getWorld() != null
                && left.getWorld().getUID().equals(right.getWorld().getUID());
    }

    private void handleCaptureTheFlagMove(EventSession session, EventMap map, Player player, Location to) {
        if (to == null) {
            return;
        }
        if (map.region() != null && to.getY() < map.region().minY() - 1.0D) {
            dropCarriedFlag(player, groundFlagLocation(player.getLocation()));
            scheduleCaptureTheFlagRespawn(session, map, player, "You will respawn in 5 seconds.");
            return;
        }
        String ownTeam = session.teams().get(player.getUniqueId());
        if (ownTeam == null) {
            ownTeam = nearestTeamByFlag(map, to);
            if (ownTeam == null) {
                ownTeam = "1";
            }
            session.teams().put(player.getUniqueId(), ownTeam);
        }

        String carriedTeam = ctfCarriers.get(player.getUniqueId());
        if (carriedTeam != null && reachedFlagTeam(map, to) != null && reachedFlagTeam(map, to).equals(ownTeam)) {
            captureFlag(session, player, ownTeam, carriedTeam);
        }
    }

    private void handleCaptureTheFlagDamage(EventSession session, Player target, Player damager, EntityDamageByEntityEvent event) {
        if (!session.participants().contains(target.getUniqueId()) || !session.participants().contains(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        String targetTeam = session.teams().get(target.getUniqueId());
        String damagerTeam = session.teams().get(damager.getUniqueId());
        if (targetTeam != null && targetTeam.equals(damagerTeam)) {
            event.setCancelled(true);
            return;
        }
        if (target.getHealth() - event.getFinalDamage() > 0.0D) {
            return;
        }
        event.setCancelled(true);
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        String carriedTeam = ctfCarriers.remove(target.getUniqueId());
        if (carriedTeam != null) {
            ctfDroppedFlags.put(carriedTeam, groundFlagLocation(target.getLocation()));
            target.setGlowing(false);
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            restoreCaptureTheFlagArmor(target);
            target.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You dropped the flag.");
            damager.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + target.getName() + " dropped the "
                    + displayTeam(carriedTeam) + " flag.");
        }
        rewardCaptureTheFlagKill(damager);
        scheduleCaptureTheFlagRespawn(session, map, target, "You will respawn in 5 seconds.");
        damager.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You stopped " + target.getName() + ".");
    }

    private void dropCarriedFlag(Player player, Location location) {
        String carriedTeam = ctfCarriers.remove(player.getUniqueId());
        if (carriedTeam == null) {
            return;
        }
        ctfDroppedFlags.put(carriedTeam, location);
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You dropped the flag.");
    }

    private void rewardCaptureTheFlagKill(Player player) {
        player.getInventory().addItem(
                new ItemStack(Material.GOLDEN_APPLE, 1),
                new ItemStack(Material.COOKED_BEEF, 2),
                new ItemStack(Material.ARROW, 8)
        );
    }

    private void applyCaptureTheFlagCarrierArmor(Player player, String flagTeam) {
        player.getInventory().setHelmet(new ItemStack(flagMaterial(flagTeam)));
        player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
    }

    private void restoreCaptureTheFlagArmor(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        player.getInventory().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
    }

    private void captureFlag(EventSession session, Player player, String ownTeam, String carriedTeam) {
        if (ctfCarriers.containsValue(ownTeam) || ctfDroppedFlags.containsKey(ownTeam)) {
            sendActionBar(player, ChatColor.RED + "Your flag has to be at base before you can capture");
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Your flag has to be at base before you can capture.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.8F);
            return;
        }
        ctfCarriers.remove(player.getUniqueId());
        ctfDroppedFlags.remove(carriedTeam);
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        int captures = ctfScores.merge(ownTeam, 1, Integer::sum);
        eventManager.setRuntimeScoreboardValue("ctf-score-" + ownTeam.toLowerCase(Locale.ROOT), String.valueOf(captures));
        playForParticipants(session, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.1F);
        messageTeam(session, carriedTeam, ChatColor.GOLD + "[Events] " + ChatColor.RED + "Your flag was captured by " + player.getName() + ".");
        playForTeam(session, carriedTeam, Sound.ENTITY_WITHER_SPAWN, 0.45F, 1.35F);
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + player.getName()
                + " captured the " + displayTeam(carriedTeam) + " flag for " + displayTeam(ownTeam)
                + " (" + captures + " of 3).");
        if (captures >= 3) {
            List<UUID> winners = session.teams().entrySet().stream()
                    .filter(entry -> ownTeam.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .filter(session.participants()::contains)
                    .toList();
            eventManager.endActiveEventDelayed(winners.isEmpty() ? List.of(player.getUniqueId()) : winners, 80L);
            return;
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Flag captured.");
    }

    private void tickCaptureTheFlag(EventSession session) {
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        ensureCaptureTheFlagBlocks(map);
        for (UUID uuid : List.copyOf(session.participants())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (ctfRespawns.containsKey(player.getUniqueId())) {
                continue;
            }
            String ownTeam = session.teams().getOrDefault(player.getUniqueId(), nearestTeamByFlag(map, player.getLocation()));
            if (ownTeam == null) {
                continue;
            }
            String carriedTeam = ctfCarriers.get(player.getUniqueId());
            if (carriedTeam != null) {
                player.setGlowing(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, true, false, true));
                applyCaptureTheFlagCarrierArmor(player, carriedTeam);
                if (processOwnFlagReturn(session, map, player, ownTeam)) {
                    continue;
                }
                String reached = reachedFlagTeam(map, player.getLocation());
                if (ownTeam.equals(reached)) {
                    captureFlag(session, player, ownTeam, carriedTeam);
                }
                continue;
            }
            processFlagPickup(session, map, player, ownTeam);
        }
    }

    private boolean processOwnFlagReturn(EventSession session, EventMap map, Player player, String ownTeam) {
        Location dropped = ctfDroppedFlags.get(ownTeam);
        if (dropped == null || !insideFlagCaptureBox(dropped, player.getLocation())) {
            clearCtfProgress(player, ownTeam);
            return false;
        }
        if (contestedFlag(session, dropped, ownTeam)) {
            clearCtfProgress(player, ownTeam);
            sendActionBar(player, ChatColor.RED + "Flag area contested");
            return true;
        }
        String key = player.getUniqueId() + ":" + ownTeam;
        int progress = ctfCaptureProgress.merge(key, 1, Integer::sum);
        sendActionBar(player, captureBar("Returning your flag", progress, 60, ChatColor.GREEN));
        if (progress < 60) {
            return true;
        }
        ctfCaptureProgress.keySet().removeIf(value -> value.endsWith(":" + ownTeam));
        ctfDroppedFlags.remove(ownTeam);
        sendActionBar(player, ChatColor.GREEN + "Flag returned");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9F, 1.6F);
        messageTeam(session, ownTeam, ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                + player.getName() + " returned your flag.");
        ensureCaptureTheFlagBlocks(map);
        return true;
    }

    private void processFlagPickup(EventSession session, EventMap map, Player player, String ownTeam) {
        for (String flagTeam : flagTeams(map)) {
            if (ctfCarriers.containsValue(flagTeam)) {
                continue;
            }
            Location location = ctfDroppedFlags.getOrDefault(flagTeam, map.points().get("flag-" + flagTeam));
            if (!insideFlagCaptureBox(location, player.getLocation())) {
                clearCtfProgress(player, flagTeam);
                continue;
            }
            // Only check contesting when flag is dropped, not at original spawn
            if (ctfDroppedFlags.containsKey(flagTeam) && contestedFlag(session, location, ownTeam)) {
                clearCtfProgress(player, flagTeam);
                sendActionBar(player, ChatColor.RED + "Flag area contested");
                continue;
            }
            String key = player.getUniqueId() + ":" + flagTeam;
            int progress = ctfCaptureProgress.merge(key, 1, Integer::sum);
            if (flagTeam.equals(ownTeam) && ctfDroppedFlags.containsKey(flagTeam)) {
                sendActionBar(player, captureBar("Returning " + displayTeam(flagTeam) + " flag", progress, 60, ChatColor.GREEN));
                if (progress >= 60) {
                    ctfCaptureProgress.keySet().removeIf(value -> value.endsWith(":" + flagTeam));
                    ctfDroppedFlags.remove(flagTeam);
                    sendActionBar(player, ChatColor.GREEN + "Flag returned");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9F, 1.6F);
                    player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Your flag was returned.");
                    ensureCaptureTheFlagBlocks(map);
                }
                continue;
            }
            if (flagTeam.equals(ownTeam)) {
                clearCtfProgress(player, flagTeam);
                continue;
            }
            sendActionBar(player, captureBar("Capturing " + displayTeam(flagTeam) + " flag", progress, 60, ChatColor.YELLOW));
            if (progress >= 60) {
                ctfCaptureProgress.keySet().removeIf(value -> value.endsWith(":" + flagTeam));
                ctfDroppedFlags.remove(flagTeam);
                ctfCarriers.put(player.getUniqueId(), flagTeam);
                clearRenderedFlagBlock(flagTeam);
                player.setGlowing(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, true, false, true));
                applyCaptureTheFlagCarrierArmor(player, flagTeam);
                sendActionBar(player, ChatColor.GREEN + "Flag captured");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
                messageTeam(session, flagTeam, ChatColor.GOLD + "[Events] " + ChatColor.RED + player.getName()
                        + " took your flag.");
                playForTeam(session, flagTeam, Sound.BLOCK_BELL_USE, 0.9F, 1.1F);
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                        + "You picked up the " + displayTeam(flagTeam) + " flag. Return to your flag to capture it.");
                return;
            }
        }
    }

    private String captureBar(String label, int progress, int max, ChatColor filledColor) {
        int segments = 18;
        int filled = Math.max(0, Math.min(segments, (int) Math.round(progress / (double) max * segments)));
        StringBuilder builder = new StringBuilder(ChatColor.GOLD + label + " ");
        builder.append(filledColor);
        for (int i = 0; i < filled; i++) {
            builder.append('|');
        }
        builder.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < segments; i++) {
            builder.append('|');
        }
        return builder.toString();
    }

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
    }

    private void ensureCaptureTheFlagBlocks(EventMap map) {
        if (map == null) {
            return;
        }
        for (String team : flagTeams(map)) {
            if (ctfCarriers.containsValue(team)) {
                clearRenderedFlagBlock(team);
                continue;
            }
            Location location = ctfDroppedFlags.getOrDefault(team, map.points().get("flag-" + team));
            if (location == null || location.getWorld() == null) {
                continue;
            }
            renderFlagBlock(team, location);
        }
    }

    private void tickCaptureTheFlagParticles() {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE || session.definition().type() != EventType.CAPTURE_THE_FLAG) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map != null) {
            tickCaptureTheFlag(session);
            drawCaptureTheFlagParticles(map);
        }
    }

    private void drawCaptureTheFlagParticles(EventMap map) {
        for (String team : flagTeams(map)) {
            Location location = ctfDroppedFlags.get(team);
            if (location == null) {
                UUID carrier = ctfCarriers.entrySet().stream()
                        .filter(entry -> team.equals(entry.getValue()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                Player player = carrier == null ? null : Bukkit.getPlayer(carrier);
                location = player == null ? centeredFlagParticleLocation(map.points().get("flag-" + team)) : player.getLocation().clone().add(0.0D, 0.25D, 0.0D);
            } else {
                location = centeredFlagParticleLocation(location);
            }
            if (location == null || location.getWorld() == null) {
                continue;
            }
            Particle.DustOptions dust = new Particle.DustOptions(teamColor(team), 1.1F);
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2.0D * i / 16.0D;
                location.getWorld().spawnParticle(Particle.DUST,
                        location.getX() + Math.cos(angle) * 1.35D,
                        location.getY() + 0.25D,
                        location.getZ() + Math.sin(angle) * 1.35D,
                        1, 0, 0, 0, 0, dust);
            }
        }
    }

    private Location centeredFlagParticleLocation(Location location) {
        if (location == null) {
            return null;
        }
        Location centered = location.clone();
        if (Math.abs(centered.getX() - centered.getBlockX()) < 0.001D) {
            centered.add(0.5D, 0.0D, 0.0D);
        }
        if (Math.abs(centered.getZ() - centered.getBlockZ()) < 0.001D) {
            centered.add(0.0D, 0.0D, 0.5D);
        }
        return centered;
    }

    private boolean contestedFlag(EventSession session, Location location, String actingTeam) {
        boolean ally = false;
        boolean enemy = false;
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !insideFlagCaptureBox(location, player.getLocation())) {
                continue;
            }
            String team = session.teams().get(player.getUniqueId());
            if (actingTeam.equals(team)) {
                ally = true;
            } else {
                enemy = true;
            }
        }
        return ally && enemy;
    }

    private boolean insideFlagCaptureBox(Location flag, Location player) {
        return sameWorld(flag, player)
                && Math.abs(flag.getX() - player.getX()) <= 1.5D
                && Math.abs(flag.getY() - player.getY()) <= 2.5D
                && Math.abs(flag.getZ() - player.getZ()) <= 1.5D;
    }

    private List<String> flagTeams(EventMap map) {
        return map.points().keySet().stream()
                .filter(key -> key.toLowerCase(Locale.ROOT).startsWith("flag-"))
                .map(key -> key.substring("flag-".length()).toLowerCase(Locale.ROOT))
                .toList();
    }

    private Material flagMaterial(String team) {
        return switch (team) {
            case "1", "red" -> Material.RED_BANNER;
            case "2", "blue" -> Material.BLUE_BANNER;
            case "3", "green" -> Material.GREEN_BANNER;
            case "4", "yellow" -> Material.YELLOW_BANNER;
            case "5", "orange" -> Material.ORANGE_BANNER;
            case "6", "purple" -> Material.PURPLE_BANNER;
            case "7", "cyan" -> Material.CYAN_BANNER;
            default -> Material.WHITE_BANNER;
        };
    }

    private void renderFlagBlock(String team, Location location) {
        Location previous = ctfRenderedFlagBlocks.get(team);
        if (previous != null && !sameBlock(previous, location)) {
            clearRenderedFlagBlock(team);
        }
        Block block = location.getBlock();
        ctfOriginalFlagBlocks.putIfAbsent(blockKey(location), block.getState());
        block.setType(flagMaterial(team), false);
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(yawToBlockFace(location.getYaw()));
            block.setBlockData(rotatable, false);
        }
        ctfRenderedFlagBlocks.put(team, block.getLocation());
    }

    private void clearRenderedFlagBlock(String team) {
        Location previous = ctfRenderedFlagBlocks.remove(team);
        if (previous != null && previous.getWorld() != null) {
            previous.getBlock().setType(Material.AIR, false);
        }
    }

    private Location groundFlagLocation(Location source) {
        Location location = source.clone();
        if (location.getWorld() == null) {
            return location;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = Math.min(location.getWorld().getMaxHeight() - 1, location.getBlockY());
        int minY = location.getWorld().getMinHeight();
        for (int y = startY; y >= minY; y--) {
            Block block = location.getWorld().getBlockAt(x, y, z);
            if (!block.getType().isAir()) {
                Location grounded = block.getLocation().add(0.5D, 1.0D, 0.5D);
                grounded.setYaw(source.getYaw());
                grounded.setPitch(0.0F);
                return grounded;
            }
        }
        return location;
    }

    private boolean sameBlock(Location left, Location right) {
        return sameWorld(left, right)
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private String blockKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":"
                + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private BlockFace yawToBlockFace(float yaw) {
        int index = Math.round(((yaw % 360.0F) + 360.0F) % 360.0F / 22.5F) & 0xF;
        return switch (index) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.SOUTH_SOUTH_WEST;
            case 2 -> BlockFace.SOUTH_WEST;
            case 3 -> BlockFace.WEST_SOUTH_WEST;
            case 4 -> BlockFace.WEST;
            case 5 -> BlockFace.WEST_NORTH_WEST;
            case 6 -> BlockFace.NORTH_WEST;
            case 7 -> BlockFace.NORTH_NORTH_WEST;
            case 8 -> BlockFace.NORTH;
            case 9 -> BlockFace.NORTH_NORTH_EAST;
            case 10 -> BlockFace.NORTH_EAST;
            case 11 -> BlockFace.EAST_NORTH_EAST;
            case 12 -> BlockFace.EAST;
            case 13 -> BlockFace.EAST_SOUTH_EAST;
            case 14 -> BlockFace.SOUTH_EAST;
            case 15 -> BlockFace.SOUTH_SOUTH_EAST;
            default -> BlockFace.SOUTH;
        };
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
            default -> "Team " + team;
        };
    }

    private Color teamColor(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> Color.RED;
            case "2", "blue" -> Color.BLUE;
            case "3", "green" -> Color.GREEN;
            case "4", "yellow" -> Color.YELLOW;
            case "5", "orange" -> Color.ORANGE;
            case "6", "purple" -> Color.PURPLE;
            case "7", "cyan" -> Color.AQUA;
            default -> Color.WHITE;
        };
    }

    private void clearCtfProgress(Player player, String flagTeam) {
        ctfCaptureProgress.remove(player.getUniqueId() + ":" + flagTeam);
    }

    private void restoreCaptureTheFlagBlocks() {
        for (BlockState state : ctfOriginalFlagBlocks.values().stream().toList().reversed()) {
            state.update(true, false);
        }
        ctfRenderedFlagBlocks.clear();
        ctfOriginalFlagBlocks.clear();
    }

    private void respawnCaptureTheFlagPlayer(EventSession session, EventMap map, Player player, String message) {
        ctfCarriers.remove(player.getUniqueId());
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        applyCaptureTheFlagLoadout(player, ctfInventoryLayouts.remove(player.getUniqueId()));
        String team = session.teams().getOrDefault(player.getUniqueId(), nearestTeamByFlag(map, player.getLocation()));
        Location spawn = team == null ? null : map.spawns().get("team-" + team + "-spawn");
        if (spawn == null && team != null) {
            spawn = map.points().get("flag-" + team);
        }
        if (spawn == null && !map.spawns().isEmpty()) {
            spawn = map.spawns().values().iterator().next();
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        if (spawn != null) {
            TeleportService.teleport(plugin, player, spawn, "capture the flag base respawn");
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY + message);
    }

    private void scheduleCaptureTheFlagRespawn(EventSession session, EventMap map, Player player, String message) {
        if (ctfRespawns.containsKey(player.getUniqueId())) {
            return;
        }
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        restoreCaptureTheFlagArmor(player);
        ctfInventoryLayouts.put(player.getUniqueId(), captureCtfInventoryLayout(player));
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + message);
        final int[] seconds = {5};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EventSession current = eventManager.session();
            if (current == null || current != session || current.phase() != EventPhase.ACTIVE
                    || !current.participants().contains(player.getUniqueId()) || !player.isOnline()) {
                BukkitTask currentTask = ctfRespawns.remove(player.getUniqueId());
                if (currentTask != null) {
                    currentTask.cancel();
                }
                return;
            }
            if (seconds[0] > 0) {
                showRespawnCountdown(player, seconds[0], 5, ChatColor.RED);
                seconds[0]--;
                return;
            }
            BukkitTask currentTask = ctfRespawns.remove(player.getUniqueId());
            if (currentTask != null) {
                currentTask.cancel();
            }
            player.setGameMode(GameMode.SURVIVAL);
            respawnCaptureTheFlagPlayer(current, map, player, "You respawned.");
            clearRespawnCountdown(player);
        }, 0L, 20L);
        ctfRespawns.put(player.getUniqueId(), task);
    }

    private CtfInventoryLayout captureCtfInventoryLayout(Player player) {
        Map<Material, Integer> preferredSlots = new HashMap<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                preferredSlots.putIfAbsent(item.getType(), slot);
            }
        }
        return new CtfInventoryLayout(preferredSlots);
    }

    private void applyCaptureTheFlagLoadout(Player player, CtfInventoryLayout layout) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        equipCtfArmor(player);
        placeCtfItem(player, layout, namedItem(Material.IRON_SWORD, "CTF Sword"));
        placeCtfItem(player, layout, namedItem(Material.IRON_AXE, "CTF Axe"));
        placeCtfItem(player, layout, namedItem(Material.BOW, "CTF Bow"));
        placeCtfItem(player, layout, new ItemStack(Material.GOLDEN_APPLE, 2));
        placeCtfItem(player, layout, new ItemStack(Material.ARROW, 24));
        placeCtfItem(player, layout, new ItemStack(Material.COOKED_BEEF, 8));
        player.getInventory().setItemInOffHand(namedItem(Material.SHIELD, "CTF Shield"));
        player.updateInventory();
    }

    private void placeCtfItem(Player player, CtfInventoryLayout layout, ItemStack item) {
        Integer preferred = layout == null ? null : layout.preferredSlots().get(item.getType());
        if (preferred != null && preferred >= 0 && preferred < player.getInventory().getStorageContents().length
                && player.getInventory().getItem(preferred) == null) {
            player.getInventory().setItem(preferred, item);
            return;
        }
        player.getInventory().addItem(item);
    }

    private void equipCtfArmor(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isSpleggLaunchAction(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        ItemStack item = event.getItem();
        return item != null && item.getType() == Material.DIAMOND_SHOVEL;
    }

    private void launchSpleggSnowball(Player player) {
        int tick = player.getTicksLived();
        Integer lastTick = spleggLastShotTicks.put(player.getUniqueId(), tick);
        if (lastTick != null && tick - lastTick < 2) {
            return;
        }
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.65D));
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.75F, 1.2F);
    }

    private void rememberEventInventoryLayout(Player player) {
        Map<Material, Integer> currentSlots = new HashMap<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir()) {
                currentSlots.putIfAbsent(item.getType(), slot);
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            currentSlots.put(offhand.getType(), EventInventoryLayout.OFFHAND_SLOT);
        }
        EventInventoryLayout previous = eventInventoryLayouts.get(player.getUniqueId());
        Map<Material, Integer> merged = previous == null ? new HashMap<>() : new HashMap<>(previous.preferredSlots());
        merged.putAll(currentSlots);
        eventInventoryLayouts.put(player.getUniqueId(), new EventInventoryLayout(merged));
    }

    private void placeEventItem(Player player, ItemStack item) {
        EventInventoryLayout layout = eventInventoryLayouts.get(player.getUniqueId());
        Integer preferred = layout == null ? null : layout.preferredSlots().get(item.getType());
        if (preferred != null && preferred == EventInventoryLayout.OFFHAND_SLOT && placeInOffhand(player, item)) {
            return;
        }
        if (preferred != null && preferred >= 0 && preferred < player.getInventory().getStorageContents().length
                && placeInSlot(player, preferred, item)) {
            return;
        }
        player.getInventory().addItem(item);
    }

    private boolean placeInOffhand(Player player, ItemStack item) {
        ItemStack current = player.getInventory().getItemInOffHand();
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItemInOffHand(item);
            return true;
        }
        ItemStack leftover = mergeStack(current, item);
        player.getInventory().setItemInOffHand(current);
        if (leftover == null) {
            return true;
        }
        player.getInventory().addItem(leftover);
        return true;
    }

    private boolean placeInSlot(Player player, int slot, ItemStack item) {
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(slot, item);
            return true;
        }
        ItemStack leftover = mergeStack(current, item);
        player.getInventory().setItem(slot, current);
        if (leftover == null) {
            return true;
        }
        player.getInventory().addItem(leftover);
        return true;
    }

    private ItemStack mergeStack(ItemStack target, ItemStack incoming) {
        if (target.getType() != incoming.getType()) {
            return incoming;
        }
        int space = target.getMaxStackSize() - target.getAmount();
        if (space <= 0) {
            return incoming;
        }
        int moved = Math.min(space, incoming.getAmount());
        target.setAmount(target.getAmount() + moved);
        if (moved >= incoming.getAmount()) {
            return null;
        }
        ItemStack leftover = incoming.clone();
        leftover.setAmount(incoming.getAmount() - moved);
        return leftover;
    }

    private void messageTeam(EventSession session, String team, String message) {
        for (UUID uuid : session.participants()) {
            if (!team.equalsIgnoreCase(session.teams().getOrDefault(uuid, ""))) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void playForTeam(EventSession session, String team, Sound sound, float volume, float pitch) {
        for (UUID uuid : session.participants()) {
            if (!team.equalsIgnoreCase(session.teams().getOrDefault(uuid, ""))) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void handleCapturePlayersMove(PlayerMoveEvent event, EventMap map) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        EventSession session = eventManager.session();
        if (session == null) {
            return;
        }
        if (capturePlayerCarrierByPrisoner.containsKey(player.getUniqueId())) {
            return;
        }
        String playerTeam = session.teams().getOrDefault(player.getUniqueId(), "1");
        String jailTeam = capturePlayerJails.get(player.getUniqueId());
        if (jailTeam != null) {
            player.setInvulnerable(false);
            CuboidRegion jail = teamArea(map, "jail", jailTeam);
            Location jailLocation = jail == null ? center(map.areas().get("jail-zone")) : center(jail);
            if (jailLocation != null && (jail == null || !jail.contains(to))) {
                event.setTo(jailLocation);
                player.setFallDistance(0.0F);
                player.setVelocity(new Vector(0, 0, 0));
            }
            return;
        }
        List<CapturePlayersCarryEntry> carried = capturePlayerStacks.getOrDefault(player.getUniqueId(), List.of());
        if (!carried.isEmpty() && carried.getFirst().mode() == CapturePlayersCarryMode.RESCUE
                && inArea(map, "capture-zone-" + playerTeam, to)) {
            deliverRescuedTeammates(session, map, player, carried);
            return;
        }
        if (!carried.isEmpty() && carried.getFirst().mode() == CapturePlayersCarryMode.CAPTURE
                && inArea(map, "capture-zone-" + playerTeam, to)) {
            jailCarriedEnemies(session, map, player, playerTeam, carried);
        }
    }

    private void handleCapturePlayersDamage(EventSession session, Player target, Player damager, EntityDamageByEntityEvent event) {
        if (!session.participants().contains(target.getUniqueId()) || !session.participants().contains(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        String targetTeam = session.teams().getOrDefault(target.getUniqueId(), "1");
        String damagerTeam = session.teams().getOrDefault(damager.getUniqueId(), "2");
        if (targetTeam.equals(damagerTeam)
                || capturePlayerJails.containsKey(target.getUniqueId())
                || capturePlayerJails.containsKey(damager.getUniqueId())
                || capturePlayerCarrierByPrisoner.containsKey(target.getUniqueId())
                || capturePlayerCarrierByPrisoner.containsKey(damager.getUniqueId())
                || isCapturePlayersRescueCarrier(damager.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (target.getHealth() - event.getFinalDamage() > 0.0D) {
            return;
        }
        event.setCancelled(true);
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        releaseCapturePlayersStack(session, map, target, true);
        attachCapturePlayer(target, damager, CapturePlayersCarryMode.CAPTURE, null);
        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setFireTicks(0);
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + damager.getName()
                + " captured " + target.getName() + ". Bring them to " + displayTeam(damagerTeam) + "'s capture zone.");
    }

    private void respawnCapturePlayer(EventSession session, EventMap map, Player player, String message) {
        String team = session.teams().getOrDefault(player.getUniqueId(), "1");
        Location spawn = map.spawns().get("team-" + team + "-spawn");
        if (spawn == null && !map.spawns().isEmpty()) {
            spawn = map.spawns().values().iterator().next();
        }
        restoreCapturePlayerState(player);
        applyCapturePlayersLoadout(player, team);
        player.updateInventory();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        player.setGameMode(GameMode.SURVIVAL);
        if (spawn != null) {
            TeleportService.teleport(plugin, player, spawn, "capture players respawn").thenAccept(success -> {
                if (success) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (eventManager.session() == session && session.phase() == EventPhase.ACTIVE
                            && session.participants().contains(player.getUniqueId()) && player.isOnline()) player.updateInventory();
                });
            });
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY + message);
    }

    private void tickCapturePlayers(EventSession session) {
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        updateCapturePlayersRoundScoreboard(session);
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            handleCapturePlayersZoneProgress(session, map, player);
        }
        checkCapturePlayersRoundWin(session, map);
    }

    private void handleCapturePlayersZoneProgress(EventSession session, EventMap map, Player player) {
        if (capturePlayerJails.containsKey(player.getUniqueId())
                || capturePlayerCarrierByPrisoner.containsKey(player.getUniqueId())) {
            clearCapturePlayersProgress(player.getUniqueId());
            return;
        }
        String playerTeam = session.teams().getOrDefault(player.getUniqueId(), "1");
        List<CapturePlayersCarryEntry> stack = capturePlayerStacks.getOrDefault(player.getUniqueId(), List.of());
        if (stack.isEmpty()) {
            for (String jailTeam : capturePlayersConfiguredTeams(session, map)) {
                if (jailTeam.equals(playerTeam)) {
                    continue;
                }
                if (!inArea(map, "free-zone-" + jailTeam, player.getLocation())) {
                    continue;
                }
                UUID teammate = jailedTeammateInTeamJail(session, playerTeam, jailTeam);
                if (teammate == null) {
                    continue;
                }
                String key = progressKey(player.getUniqueId(), "rescue-" + jailTeam);
                int progress = capturePlayerProgress.merge(key, 1, Integer::sum);
                player.sendActionBar(ChatColor.GREEN + "Rescuing teammate " + capturePlayersProgressBar(progress, 5));
                if (progress >= 5) {
                    capturePlayerProgress.remove(key);
                    pickUpJailedTeammate(session, map, player, teammate, jailTeam);
                }
                return;
            }
            clearCapturePlayersProgress(player.getUniqueId());
            return;
        }
        clearCapturePlayersProgress(player.getUniqueId());
    }

    private void pickUpJailedTeammate(EventSession session, EventMap map, Player rescuer, UUID teammateId, String jailTeam) {
        Player teammate = Bukkit.getPlayer(teammateId);
        if (teammate == null) {
            return;
        }
        attachCapturePlayer(teammate, rescuer, CapturePlayersCarryMode.RESCUE, jailTeam);
        rescuer.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You picked up " + teammate.getName()
                + ". Bring them to " + displayTeam(session.teams().getOrDefault(rescuer.getUniqueId(), "1")) + "'s capture zone.");
        teammate.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + rescuer.getName()
                + " is carrying you back to safety.");
    }

    private void jailCarriedEnemies(EventSession session, EventMap map, Player carrier, String jailTeam, List<CapturePlayersCarryEntry> carried) {
        clearCapturePlayersProgress(carrier.getUniqueId());
        dismountCapturePlayersStack(carrier.getUniqueId());
        for (CapturePlayersCarryEntry entry : carried) {
            Player prisoner = Bukkit.getPlayer(entry.prisonerId());
            if (prisoner == null) {
                continue;
            }
            capturePlayerJails.put(prisoner.getUniqueId(), jailTeam);
            sendCapturePlayerToJail(map, prisoner, jailTeam);
            prisoner.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You were jailed by " + displayTeam(jailTeam) + ".");
        }
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + carrier.getName()
                + " jailed " + carried.size() + " player" + (carried.size() == 1 ? "" : "s") + " for " + displayTeam(jailTeam) + ".");
        checkCapturePlayersRoundWin(session, map);
    }

    private void deliverRescuedTeammates(EventSession session, EventMap map, Player carrier, List<CapturePlayersCarryEntry> carried) {
        clearCapturePlayersProgress(carrier.getUniqueId());
        dismountCapturePlayersStack(carrier.getUniqueId());
        for (CapturePlayersCarryEntry entry : carried) {
            if (entry.mode() != CapturePlayersCarryMode.RESCUE) {
                continue;
            }
            capturePlayerJails.remove(entry.prisonerId());
            Player prisoner = Bukkit.getPlayer(entry.prisonerId());
            if (prisoner != null) {
                respawnCapturePlayer(session, map, prisoner, "You were rescued by " + carrier.getName() + ".");
            }
        }
        carrier.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Rescue delivered.");
    }

    private void sendCapturePlayerToJail(EventMap map, Player prisoner, String jailTeam) {
        restoreCapturePlayerState(prisoner);
        EventSession session = eventManager.session();
        String prisonerTeam = session == null ? "1" : session.teams().getOrDefault(prisoner.getUniqueId(), "1");
        applyCapturePlayersLoadout(prisoner, prisonerTeam);
        prisoner.setHealth(prisoner.getMaxHealth());
        prisoner.setFoodLevel(20);
        prisoner.setSaturation(10.0F);
        prisoner.setGameMode(GameMode.ADVENTURE);
        CuboidRegion jail = teamArea(map, "jail", jailTeam);
        Location location = jail == null ? center(map.areas().get("jail-zone")) : center(jail);
        if (location != null) {
            TeleportService.teleport(plugin, prisoner, location, "capture players jail");
        }
    }

    private void attachCapturePlayer(Player prisoner, Player carrier, CapturePlayersCarryMode mode, String jailTeam) {
        restoreCapturePlayerState(prisoner);
        prisoner.leaveVehicle();
        prisoner.setInvulnerable(true);
        prisoner.setGameMode(GameMode.ADVENTURE);
        prisoner.setHealth(prisoner.getMaxHealth());
        prisoner.setFoodLevel(20);
        prisoner.setSaturation(10.0F);
        prisoner.setVelocity(new Vector(0, 0, 0));
        capturePlayerCarrierByPrisoner.put(prisoner.getUniqueId(), carrier.getUniqueId());
        capturePlayerStacks.computeIfAbsent(carrier.getUniqueId(), ignored -> new ArrayList<>())
                .add(new CapturePlayersCarryEntry(prisoner.getUniqueId(), mode, jailTeam));
        rebuildCapturePlayersStack(carrier.getUniqueId());
    }

    private void rebuildCapturePlayersStack(UUID carrierId) {
        Player carrier = Bukkit.getPlayer(carrierId);
        if (carrier == null) {
            return;
        }
        List<CapturePlayersCarryEntry> stack = capturePlayerStacks.get(carrierId);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.forEach(entry -> capturePlayerCarrierByPrisoner.remove(entry.prisonerId()));
        Set<UUID> mounted = new HashSet<>();
        try {
            carrier.eject();
            Entity mount = carrier;
            for (CapturePlayersCarryEntry entry : stack) {
                Player prisoner = Bukkit.getPlayer(entry.prisonerId());
                if (prisoner == null) continue;
                prisoner.leaveVehicle();
                if (!mount.addPassenger(prisoner)) {
                    restoreCapturePlayerState(prisoner);
                    plugin.trace(eventManager.session(), "capture-mount-rejected", entry.prisonerId());
                    continue;
                }
                mounted.add(entry.prisonerId());
                mount = prisoner;
            }
        } finally {
            for (CapturePlayersCarryEntry entry : stack) {
                if (mounted.contains(entry.prisonerId())) capturePlayerCarrierByPrisoner.put(entry.prisonerId(), carrierId);
                else {
                    Player prisoner = Bukkit.getPlayer(entry.prisonerId());
                    if (prisoner != null) restoreCapturePlayerState(prisoner);
                }
            }
            stack.removeIf(entry -> !mounted.contains(entry.prisonerId()));
            if (stack.isEmpty()) capturePlayerStacks.remove(carrierId);
        }
    }

    private void releaseCapturePlayersStack(EventSession session, EventMap map, Player carrier, boolean respawnCapturedPlayers) {
        List<CapturePlayersCarryEntry> stack = capturePlayerStacks.remove(carrier.getUniqueId());
        if (stack == null || stack.isEmpty()) return;
        stack.forEach(entry -> capturePlayerCarrierByPrisoner.remove(entry.prisonerId()));
        carrier.eject();
        for (CapturePlayersCarryEntry entry : stack) {
            Player prisoner = Bukkit.getPlayer(entry.prisonerId());
            if (prisoner == null) {
                continue;
            }
            prisoner.leaveVehicle();
            if (entry.mode() == CapturePlayersCarryMode.RESCUE && entry.jailTeam() != null) {
                capturePlayerJails.put(prisoner.getUniqueId(), entry.jailTeam());
                sendCapturePlayerToJail(map, prisoner, entry.jailTeam());
                prisoner.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Your rescuer was killed. You were sent back to jail.");
                continue;
            }
            if (respawnCapturedPlayers) {
                respawnCapturePlayer(session, map, prisoner, carrier.getName() + " was killed, so you escaped.");
            }
        }
    }

    private void dismountCapturePlayersStack(UUID carrierId) {
        List<CapturePlayersCarryEntry> stack = capturePlayerStacks.remove(carrierId);
        if (stack == null) {
            return;
        }
        stack.forEach(entry -> capturePlayerCarrierByPrisoner.remove(entry.prisonerId()));
        for (CapturePlayersCarryEntry entry : stack) {
            Player prisoner = Bukkit.getPlayer(entry.prisonerId());
            if (prisoner != null) {
                prisoner.leaveVehicle();
                restoreCapturePlayerState(prisoner);
            }
        }
    }

    private void restoreCapturePlayerState(Player player) {
        player.setInvulnerable(false);
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private boolean isCapturePlayersRescueCarrier(UUID playerId) {
        List<CapturePlayersCarryEntry> stack = capturePlayerStacks.get(playerId);
        return stack != null && !stack.isEmpty() && stack.getFirst().mode() == CapturePlayersCarryMode.RESCUE;
    }

    private UUID jailedTeammateInTeamJail(EventSession session, String playerTeam, String jailTeam) {
        for (UUID uuid : session.participants()) {
            if (!playerTeam.equals(session.teams().getOrDefault(uuid, ""))) {
                continue;
            }
            if (jailTeam.equals(capturePlayerJails.get(uuid))) {
                return uuid;
            }
        }
        return null;
    }

    private void checkCapturePlayersRoundWin(EventSession session, EventMap map) {
        List<String> teams = capturePlayersConfiguredTeams(session, map);
        if (teams.size() < 2) {
            return;
        }
        List<String> survivingTeams = new ArrayList<>();
        for (String team : teams) {
            boolean allJailed = session.participants().stream()
                    .filter(uuid -> team.equals(session.teams().getOrDefault(uuid, "")))
                    .allMatch(capturePlayerJails::containsKey);
            if (!allJailed) {
                survivingTeams.add(team);
            }
        }
        if (survivingTeams.size() != 1) {
            return;
        }
        String winnerTeam = survivingTeams.getFirst();
        int roundsToWin = plugin.getConfig().getInt("capture-players.rounds-to-win", 3);
        int wins = capturePlayerRoundWins.merge(winnerTeam, 1, Integer::sum);
        updateCapturePlayersRoundScoreboard(session);
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                + displayTeam(winnerTeam) + " won round " + (capturePlayerRoundWins.values().stream().mapToInt(Integer::intValue).sum()) + ".");
        if (wins >= roundsToWin) {
            List<UUID> winners = session.teams().entrySet().stream()
                    .filter(entry -> winnerTeam.equals(entry.getValue()) && session.participants().contains(entry.getKey()))
                    .map(Map.Entry::getKey)
                    .toList();
            eventManager.endActiveEvent(winners);
            return;
        }
        resetCapturePlayersRound(session, map);
    }

    private void resetCapturePlayersRound(EventSession session, EventMap map) {
        clearCapturePlayersProgress(null);
        detachAllCapturePlayers();
        capturePlayerJails.clear();
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            respawnCapturePlayer(session, map, player, "Next round.");
        }
        updateCapturePlayersRoundScoreboard(session);
    }

    private void updateCapturePlayersRoundScoreboard(EventSession session) {
        List<String> teams = session.teams().values().stream().distinct().sorted().toList();
        int round = capturePlayerRoundWins.values().stream().mapToInt(Integer::intValue).sum() + 1;
        eventManager.setRuntimeScoreboardValue("capture-players-round", String.valueOf(round));
        for (String team : teams) {
            eventManager.setRuntimeScoreboardValue("capture-players-round-win-" + team.toLowerCase(Locale.ROOT),
                    String.valueOf(capturePlayerRoundWins.getOrDefault(team, 0)));
            long jailed = session.participants().stream()
                    .filter(uuid -> team.equals(session.teams().getOrDefault(uuid, "")))
                    .filter(capturePlayerJails::containsKey)
                    .count();
            eventManager.setRuntimeScoreboardValue("capture-players-jailed-" + team.toLowerCase(Locale.ROOT), String.valueOf(jailed));
        }
    }

    private List<String> capturePlayersConfiguredTeams(EventSession session, EventMap map) {
        List<String> configured = map.spawns().keySet().stream()
                .filter(key -> key.startsWith("team-") && key.endsWith("-spawn"))
                .map(key -> key.substring("team-".length(), key.length() - "-spawn".length()))
                .distinct()
                .sorted()
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return session.teams().values().stream().distinct().sorted().toList();
    }

    private void applyCapturePlayersLoadout(Player player, String team) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHelmet(leatherArmor(Material.LEATHER_HELMET, teamLeatherColor(team == null || team.isBlank() ? "1" : team)));
        player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        player.getInventory().addItem(namedItem(Material.NETHERITE_SWORD, "Capture Sword"));
        player.getInventory().addItem(namedItem(Material.IRON_AXE, "Capture Axe"));
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
        player.getInventory().setItemInOffHand(namedItem(Material.SHIELD, "Capture Shield"));
    }

    private String capturePlayersProgressBar(int progress, int total) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < total; i++) {
            builder.append(i < progress ? ChatColor.GREEN : ChatColor.DARK_GRAY).append('|');
        }
        return builder.toString();
    }

    private String progressKey(UUID playerId, String action) {
        return playerId + ":" + action;
    }

    private void clearCapturePlayersProgress(UUID playerId) {
        if (playerId == null) {
            capturePlayerProgress.clear();
            return;
        }
        String prefix = playerId + ":";
        capturePlayerProgress.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private Location safeLocation(Player player, EventMap map) {
        Location safe = raceSafeLocation.get(player.getUniqueId());
        if (safe != null) {
            return safe;
        }
        if (!map.spawns().isEmpty()) {
            safe = map.spawns().values().iterator().next().clone();
        } else {
            safe = player.getLocation().clone();
        }
        raceSafeLocation.put(player.getUniqueId(), safe);
        return safe;
    }

    private boolean touchesForbiddenElytraSurface(EventMap map, Player player, Location location) {
        if (location == null || location.getWorld() == null || isNearRaceSpawn(map, location)
                || isNearRaceSafeLocation(player, location)) {
            return false;
        }
        double halfWidth = Math.max(0.3D, player.getWidth() / 2.0D);
        int minX = (int) Math.floor(location.getX() - halfWidth);
        int maxX = (int) Math.floor(location.getX() + halfWidth);
        int minY = (int) Math.floor(location.getY() - 0.1D);
        int maxY = (int) Math.floor(location.getY() + player.getHeight());
        int minZ = (int) Math.floor(location.getZ() - halfWidth);
        int maxZ = (int) Math.floor(location.getZ() + halfWidth);
        boolean touchedSolid = false;
        boolean touchedCheckpoint = false;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = location.getWorld().getBlockAt(x, y, z);
                    if (!block.getType().isSolid()) {
                        continue;
                    }
                    touchedSolid = true;
                    if (isElytraCheckpointBlock(block.getLocation())) {
                        touchedCheckpoint = true;
                    }
                }
            }
        }
        return touchedSolid && !touchedCheckpoint;
    }

    private boolean isElytraCheckpointBlock(Location location) {
        return location.getWorld() != null && elytraCheckpointSurfaceBlocks.contains(locationKey(location));
    }

    private boolean isNearRaceSafeLocation(Player player, Location location) {
        Location safe = raceSafeLocation.get(player.getUniqueId());
        return safe != null && sameWorld(safe, location)
                && Math.abs(safe.getY() - location.getY()) <= 2.0D
                && horizontalDistanceSquared(safe, location) <= 2.25D;
    }

    private boolean isNearRaceSpawn(EventMap map, Location location) {
        return map.spawns().values().stream()
                .filter(spawn -> sameWorld(spawn, location))
                .anyMatch(spawn -> Math.abs(spawn.getY() - location.getY()) <= 3.0D
                        && horizontalDistanceSquared(spawn, location) <= 4.0D);
    }

    private double horizontalDistanceSquared(Location left, Location right) {
        double x = left.getX() - right.getX();
        double z = left.getZ() - right.getZ();
        return (x * x) + (z * z);
    }

    private void finishRace(Player player) {
        if (finishOrder.contains(player.getUniqueId())) {
            return;
        }
        EventSession session = eventManager.session();
        EventMap map = session == null ? null : session.selectedMap();
        if (session != null && session.definition().type() == EventType.ELYTRA_RACE
                && map != null && !completedAllElytraCheckpoints(player, map)) {
            long now = System.currentTimeMillis();
            if (now >= elytraFinishWarningCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
                elytraFinishWarningCooldowns.put(player.getUniqueId(), now + 2_000L);
                player.sendActionBar(ChatColor.RED + "Complete every checkpoint before finishing.");
            }
            return;
        }
        finishOrder.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "You finished.");
        eventManager.finishParticipant(player);
    }

    private void handleRedLightGreenLightMove(Player player, EventMap map, Location to) {
        if (to == null) {
            return;
        }
        CuboidRegion finishLine = map.areas().get("finish-line");
        if (greenLight && finishLine != null && finishLine.contains(to)) {
            finishRace(player);
        }
    }

    private void tickHotPotato(EventSession session) {
        List<UUID> participants = eventManager.activeParticipants();
        if (participants.size() <= 1) {
            return;
        }
        if (hotPotatoHolder == null || !participants.contains(hotPotatoHolder)) {
            hotPotatoHolder = participants.get(ThreadLocalRandom.current().nextInt(participants.size()));
            hotPotatoSeconds = 15;
            Player holder = Bukkit.getPlayer(hotPotatoHolder);
            if (holder != null) {
                holder.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You have the hot potato. Hit another player to pass it.");
            }
            return;
        }
        hotPotatoSeconds--;
        Player holder = Bukkit.getPlayer(hotPotatoHolder);
        if (holder != null && (hotPotatoSeconds == 10 || hotPotatoSeconds == 5 || hotPotatoSeconds <= 3)) {
            holder.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Hot potato explodes in " + hotPotatoSeconds + "s.");
        }
        if (hotPotatoSeconds <= 0 && holder != null) {
            UUID eliminated = hotPotatoHolder;
            hotPotatoHolder = null;
            eventManager.eliminateParticipant(holder, "held the hot potato too long");
            if (session.participants().contains(eliminated)) {
                session.participants().remove(eliminated);
            }
        }
    }

    private void tickRlglFast() {
        EventSession session = eventManager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE
                || session.definition().type() != EventType.RED_LIGHT_GREEN_LIGHT) {
            return;
        }
        redLightSeconds--;
        if (redLightSeconds <= 0) {
            if (greenLight) {
                // Green → Yellow transition (10 ticks / 0.5s)
                greenLight = false;
                yellowLight = true;
                redLightSeconds = plugin.getConfig().getInt("red-light-green-light.yellow-ticks", 20);
                redLightLastSafeLocation.clear();
                EventMap map = session.selectedMap();
                if (map != null) {
                    fillArea(map.areas().get("light-display"), Material.YELLOW_CONCRETE);
                }
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Yellow light. Get ready to stop.");
                playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
                for (UUID uuid : session.participants()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        redLightLastSafeLocation.put(uuid, player.getLocation().clone());
                    }
                }
                return;
            } else if (yellowLight) {
                // Yellow → Red transition
                yellowLight = false;
                redLightSeconds = 4 * 20; // 4 seconds
                redLightLastSafeLocation.clear();
                EventMap map = session.selectedMap();
                if (map != null) {
                    fillArea(map.areas().get("light-display"), Material.RED_CONCRETE);
                }
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Red light. Stop moving.");
                playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.5F);
                for (UUID uuid : session.participants()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        redLightLastSafeLocation.put(uuid, player.getLocation().clone());
                    }
                }
                return;
            } else {
                // Red → Green transition, biased toward 3-4 seconds
                yellowLight = false;
                greenLight = true;
                int r = ThreadLocalRandom.current().nextInt(100);
                if (r < 15) redLightSeconds = 1 * 20 + ThreadLocalRandom.current().nextInt(0, 10);
                else if (r < 35) redLightSeconds = 2 * 20 + ThreadLocalRandom.current().nextInt(0, 10);
                else if (r < 75) redLightSeconds = 3 * 20 + ThreadLocalRandom.current().nextInt(0, 10);
                else redLightSeconds = 4 * 20 + ThreadLocalRandom.current().nextInt(0, 10);
                redLightLastSafeLocation.clear();
                EventMap map = session.selectedMap();
                if (map != null) {
                    fillArea(map.areas().get("light-display"), Material.LIME_CONCRETE);
                }
                eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Green light.");
                for (UUID uuid : session.participants()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        redLightLastSafeLocation.put(uuid, player.getLocation().clone());
                    }
                }
                return;
            }
        }
    }

    private void checkRlglMovement(EventSession session) {
        // Only eliminate during RED light
        if (greenLight || yellowLight) {
            return;
        }
        for (UUID uuid : List.copyOf(session.participants())) {
            Player player = Bukkit.getPlayer(uuid);
            Location previous = redLightLastSafeLocation.get(uuid);
            if (player == null || previous == null) {
                continue;
            }
            Location now = player.getLocation();
            if (previous.getWorld() != null && now.getWorld() != null && previous.getWorld().getUID().equals(now.getWorld().getUID())) {
                double dx = Math.abs(previous.getX() - now.getX());
                double dy = Math.abs(previous.getY() - now.getY());
                double dz = Math.abs(previous.getZ() - now.getZ());
                // Ignore yaw/pitch rotation; only check physical position
                // Increased tolerance to prevent head-turn foot jitter from eliminating
                if (dx > 0.35D || dy > 0.35D || dz > 0.35D) {
                    eventManager.eliminateParticipant(player, "moved on red light");
                }
            }
        }
    }

    private void ensureBlockPartyTask() {
        if (blockPartyTask != null && !blockPartyTask.isCancelled()) {
            return;
        }
        blockPartyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EventSession current = eventManager.session();
            if (current == null || current.phase() != EventPhase.ACTIVE || current.definition().type() != EventType.BLOCK_PARTY) {
                if (blockPartyTask != null) {
                    blockPartyTask.cancel();
                    blockPartyTask = null;
                }
                return;
            }
            tickBlockParty(current);
        }, 0L, 5L);
    }

    private void tickBlockParty(EventSession session) {
        if (blockPartyClearDelay > 0) {
            blockPartyClearDelay--;
            if (blockPartyClearDelay == 0) {
                blockPartyTarget = null;
            }
            return;
        }

        if (blockPartyTarget == null) {
            startBlockPartyRound(session);
            return;
        }

        blockPartyTicksRemaining--;
        blockPartySeconds = Math.max(0, (int) Math.ceil(blockPartyTicksRemaining / 4.0D));
        updateBlockPartyTimer(session);
        playBlockPartyCountdownSound(session);
        if (blockPartyTicksRemaining <= 0) {
            EventMap map = session.selectedMap();
            if (map != null) {
                clearWrongBlockPartyColors(map.areas().get("color-floor"), blockPartyTarget);
            }
            playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 0.7F);
            blockPartyClearDelay = 8;
            blockPartyRound++;
            eventManager.setRuntimeScoreboardValue("block-party-round", String.valueOf(blockPartyRound));
        }
    }

    private void startBlockPartyRound(EventSession session) {
        blockPartyTarget = BLOCK_PARTY_COLORS.get(ThreadLocalRandom.current().nextInt(BLOCK_PARTY_COLORS.size()));
        eventManager.setRuntimeScoreboardValue("block-party-round", String.valueOf(blockPartyRound + 1));
        blockPartyRoundTicks = blockPartyRoundTicks();
        blockPartyTicksRemaining = blockPartyRoundTicks;
        blockPartySeconds = Math.max(1, (int) Math.ceil(blockPartyTicksRemaining / 4.0D));
        blockPartySoundStage = 0;
        EventMap map = session.selectedMap();
        if (map != null) {
            fillBlockPartyPattern(map.areas().get("color-floor"));
        }
        fillBlockPartyHotbars(session);
        updateBlockPartyTimer(session);
        playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0F, 1.0F);
        messageParticipants(session, ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "Stand on "
                + ChatColor.WHITE + readableMaterial(blockPartyTarget) + ChatColor.YELLOW + ".");
    }

    private int blockPartyRoundTicks() {
        if (blockPartyRound < 10) {
            return Math.max(12, (9 - (blockPartyRound / 2)) * 4);
        }
        double seconds = Math.max(1.0D, 3.0D - ((blockPartyRound - 9) * 0.10D));
        return Math.max(4, (int) Math.round(seconds * 4.0D));
    }

    private void playBlockPartyCountdownSound(EventSession session) {
        if (blockPartyRoundTicks <= 0 || blockPartySoundStage >= 3) {
            return;
        }
        int elapsed = blockPartyRoundTicks - blockPartyTicksRemaining;
        int first;
        int second;
        int third;
        if (blockPartyRoundTicks > 12) {
            first = Math.max(1, blockPartyRoundTicks - 12);
            second = Math.max(first + 1, blockPartyRoundTicks - 8);
            third = Math.max(second + 1, blockPartyRoundTicks - 4);
        } else {
            first = Math.max(1, (int) Math.round(blockPartyRoundTicks * 0.25D));
            second = Math.max(first + 1, (int) Math.round(blockPartyRoundTicks * 0.50D));
            third = Math.max(second + 1, (int) Math.round(blockPartyRoundTicks * 0.75D));
            third = Math.min(third, blockPartyRoundTicks - 1);
        }
        if ((blockPartySoundStage == 0 && elapsed >= first)
                || (blockPartySoundStage == 1 && elapsed >= second)
                || (blockPartySoundStage == 2 && elapsed >= third)) {
            playForParticipants(session, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
            blockPartySoundStage++;
        }
    }

    private void tickBedWarsGenerators(EventSession session) {
        bedWarsGeneratorTicks++;
        deliverBedWarsPendingResources(session);
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        map.generators().forEach((type, locations) -> {
            String lower = type.toLowerCase(Locale.ROOT);
            Material material;
            int interval;
            if (lower.equals("solo") || lower.equals("team") || lower.startsWith("solo-") || lower.startsWith("team-")) {
                String team = lower.contains("-") ? lower.substring(lower.indexOf('-') + 1) : "";
                int forge = bedWarsForgeLevels.getOrDefault(team, 0);
                // Iron: every 1 second
                if (bedWarsGeneratorTicks % 1 == 0) {
                    for (Location location : locations) {
                        if (location != null && location.getWorld() != null) {
                            spawnGeneratorDrop(location, new ItemStack(Material.IRON_INGOT));
                        }
                    }
                }
                // Gold: every 7 seconds, faster with forge
                int goldInterval = switch (forge) {
                    case 0 -> 7;
                    case 1 -> 5;
                    case 2 -> 4;
                    case 3, 4 -> 3;
                    default -> 7;
                };
                if (bedWarsGeneratorTicks % goldInterval == 0) {
                    for (Location location : locations) {
                        if (location != null && location.getWorld() != null) {
                            spawnGeneratorDrop(location, new ItemStack(Material.GOLD_INGOT));
                        }
                    }
                }
                // Emerald: only if forge >=3
                if (forge >= 3) {
                    int emeraldInterval = forge == 3 ? 30 : 20; // 30s or 20s
                    if (bedWarsGeneratorTicks % emeraldInterval == 0) {
                        for (Location location : locations) {
                            if (location != null && location.getWorld() != null) {
                                spawnGeneratorDrop(location, new ItemStack(Material.EMERALD));
                            }
                        }
                    }
                }
            } else if (lower.equals("diamond") || lower.startsWith("diamond-")) {
                material = Material.DIAMOND;
                int diamondTier = lower.contains("-") ? Integer.parseInt(lower.substring(lower.indexOf('-') + 1)) : 1;
                interval = switch (diamondTier) {
                    case 2 -> plugin.getConfig().getInt("bedwars.generator-intervals.diamond-tier2", 22);
                    case 3 -> plugin.getConfig().getInt("bedwars.generator-intervals.diamond-tier3", 15);
                    default -> plugin.getConfig().getInt("bedwars.generator-intervals.diamond", 30);
                };
                if (bedWarsGeneratorTicks % interval == 0) {
                    for (Location location : locations) {
                        if (location != null && location.getWorld() != null) {
                            spawnGeneratorDrop(location, new ItemStack(material));
                        }
                    }
                }
            } else if (lower.equals("emerald") || lower.startsWith("emerald-")) {
                material = Material.EMERALD;
                int emeraldTier = lower.contains("-") ? Integer.parseInt(lower.substring(lower.indexOf('-') + 1)) : 1;
                interval = switch (emeraldTier) {
                    case 2 -> plugin.getConfig().getInt("bedwars.generator-intervals.emerald-tier2", 50);
                    case 3 -> plugin.getConfig().getInt("bedwars.generator-intervals.emerald-tier3", 35);
                    default -> plugin.getConfig().getInt("bedwars.generator-intervals.emerald", 65);
                };
                if (bedWarsGeneratorTicks % interval == 0) {
                    for (Location location : locations) {
                        if (location != null && location.getWorld() != null) {
                            spawnGeneratorDrop(location, new ItemStack(material));
                        }
                    }
                }
            } else if (lower.equals("gold")) {
                material = Material.GOLD_INGOT;
                if (bedWarsGeneratorTicks % 7 == 0) {
                    for (Location location : locations) {
                        if (location != null && location.getWorld() != null) {
                            spawnGeneratorDrop(location, new ItemStack(material));
                        }
                    }
                }
            } else {
                material = Material.IRON_INGOT;
                if (bedWarsGeneratorTicks % 1 == 0) {
                    for (Location location : locations) {
                        if (location != null && location.getWorld() != null) {
                            spawnGeneratorDrop(location, new ItemStack(material));
                        }
                    }
                }
            }
        });
    }

    private void spawnGeneratorDrop(Location location, ItemStack stack) {
        Location center = centerOfBlock(location);
        Item item = location.getWorld().dropItem(center, stack);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(20);
    }

    private Location centerOfBlock(Location location) {
        return location.clone().add(0.5D, 0.6D, 0.5D);
    }

    private void tickBedWarsUpgrades(EventSession session) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            applyBedWarsArmor(player);
            for (ItemStack item : player.getInventory().getContents()) {
                applyBedWarsItemUpgrades(player, item);
            }
            String team = session.teams().get(uuid);
            int haste = bedWarsHasteLevels.getOrDefault(team, 0);
            if (haste > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, haste - 1, true, false, true));
            }
            EventMap map = session.selectedMap();
            Location healPoolLoc = map == null || team == null ? null : map.points().get("team-heal-pool-" + team);
            if (healPoolLoc != null && bedWarsHealPoolTeams.contains(team)
                    && sameWorld(player.getLocation(), healPoolLoc) && player.getLocation().distanceSquared(healPoolLoc) <= 25.0D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false, true));
            } else {
                Location spawn = map == null || team == null ? null : map.spawns().get("team-" + team + "-spawn");
                if (spawn != null && bedWarsHealPoolTeams.contains(team)
                        && sameWorld(player.getLocation(), spawn) && player.getLocation().distanceSquared(spawn) <= 144.0D) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false, true));
                }
            }
        }
        triggerBedWarsTraps(session);
    }

    private void applyBedWarsArmor(Player player) {
        int tier = bedWarsArmorTiers.getOrDefault(player.getUniqueId(), 0);
        if (tier <= 0) {
            return;
        }
        Material boots = tier == 1 ? Material.CHAINMAIL_BOOTS : tier == 2 ? Material.IRON_BOOTS : Material.DIAMOND_BOOTS;
        Material leggings = tier == 1 ? Material.CHAINMAIL_LEGGINGS : tier == 2 ? Material.IRON_LEGGINGS : Material.DIAMOND_LEGGINGS;
        if (player.getInventory().getBoots() == null || armorTier(player.getInventory().getBoots().getType()) < tier) {
            player.getInventory().setBoots(new ItemStack(boots));
        }
        if (player.getInventory().getLeggings() == null || armorTier(player.getInventory().getLeggings().getType()) < tier) {
            player.getInventory().setLeggings(new ItemStack(leggings));
        }
        applyBedWarsItemUpgrades(player, player.getInventory().getBoots());
        applyBedWarsItemUpgrades(player, player.getInventory().getLeggings());
    }

    private int armorTier(Material material) {
        String name = material.name();
        if (name.startsWith("DIAMOND_")) {
            return 3;
        }
        if (name.startsWith("IRON_")) {
            return 2;
        }
        if (name.startsWith("CHAINMAIL_")) {
            return 1;
        }
        return 0;
    }

    private void applyBedWarsItemUpgrades(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        String team = eventManager.teamFor(player.getUniqueId());
        if (bedWarsSharpnessTeams.contains(team)
                && (item.getType().name().endsWith("_SWORD") || item.getType().name().endsWith("_AXE"))) {
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
        }
        int protection = bedWarsProtectionLevels.getOrDefault(team, 0);
        String materialName = item.getType().name();
        if (protection > 0 && (materialName.endsWith("_CHESTPLATE")
                || materialName.endsWith("_LEGGINGS") || materialName.endsWith("_BOOTS"))) {
            item.addUnsafeEnchantment(Enchantment.PROTECTION, protection);
        }
        int featherFalling = bedWarsFeatherFallingLevels.getOrDefault(team, 0);
        if (featherFalling > 0 && materialName.endsWith("_BOOTS")) {
            item.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, featherFalling);
        }
    }

    private void triggerBedWarsTraps(EventSession session) {
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : List.copyOf(bedWarsTraps.entrySet())) {
            String team = entry.getKey();
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Location teamSpawn = map.spawns().get("team-" + team + "-spawn");
            if (teamSpawn == null) {
                continue;
            }
            Player intruder = session.participants().stream()
                    .filter(uuid -> !team.equals(session.teams().get(uuid)))
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                    .filter(player -> sameWorld(player.getLocation(), teamSpawn)
                            && player.getLocation().distanceSquared(teamSpawn) <= 100.0D)
                    .findFirst()
                    .orElse(null);
            if (intruder == null) {
                bedWarsTriggeredTraps.remove(team);
                continue;
            }
            if (!bedWarsTriggeredTraps.add(team)) {
                continue;
            }
            String trap = entry.getValue().removeFirst();
            applyBedWarsTrap(session, team, intruder, trap);
            messageTeam(session, team, ChatColor.GOLD + "[Events] " + ChatColor.RED + "Your trap was triggered by " + intruder.getName() + ".");
            intruder.playSound(intruder.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.8F, 1.2F);
        }
    }

    private void applyBedWarsTrap(EventSession session, String team, Player intruder, String trap) {
        switch (trap) {
            case "TRAP_MINER" -> intruder.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 0));
            case "TRAP_COUNTER" -> session.teams().entrySet().stream()
                    .filter(entry -> team.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .forEach(player -> {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 300, 1));
                    });
            case "TRAP_REVEAL" -> intruder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
            default -> {
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0));
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 0));
            }
        }
    }

    private void tickBedWarsShopLookAt(EventSession session) {
        if (bedWarsShopEntities.isEmpty()) return;
        List<Player> alive = session.participants().stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .toList();
        if (alive.isEmpty()) return;
        for (UUID entityUuid : bedWarsShopEntities) {
            Entity entity = Bukkit.getEntity(entityUuid);
            if (!(entity instanceof org.bukkit.entity.Mob mob) || !entity.isValid()) continue;
            Player nearest = alive.stream()
                    .filter(p -> p.getWorld().equals(entity.getWorld()))
                    .min(java.util.Comparator.comparingDouble(p -> p.getLocation().distanceSquared(entity.getLocation())))
                    .orElse(null);
            if (nearest == null) continue;
            Location look = nearest.getLocation().clone();
            look.setY(look.getY() + 1.6); // look at player head level
            org.bukkit.util.Vector dir = look.toVector().subtract(entity.getLocation().toVector());
            Location face = entity.getLocation().clone();
            face.setDirection(dir);
            mob.setRotation(face.getYaw(), face.getPitch());
        }
    }

    private boolean canBreakSpleefBlock(EventMap map, Block block) {
        CuboidRegion breakableArea = map.areas().get("breakable-area");
        if (breakableArea != null && !breakableArea.contains(block.getLocation())) {
            return false;
        }
        if (map.eventType() == EventType.SPLEGG) {
            return true;
        }
        Location materialSource = map.points().get("breakable-block");
        return materialSource == null
                || materialSource.getWorld() == null
                || materialSource.getBlock().getType() == block.getType();
    }

    private void ensureSession(EventSession session) {
        if (trackedSession != session) {
            resetForNewSession();
            trackedSession = session;
        }
    }

    private boolean isRaceType(EventType type) {
        return type == EventType.PARKOUR
                || type == EventType.BOAT_RACE
                || type == EventType.HORSE_RACE
                || type == EventType.ELYTRA_RACE;
    }

    private boolean isHungerLockedEvent(EventType type) {
        return type == EventType.RUNNER
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.PARKOUR
                || type == EventType.ELYTRA_RACE
                || type == EventType.BLOCK_PARTY
                || type == EventType.HORSE_RACE
                || type == EventType.BOAT_RACE
                || type == EventType.RED_LIGHT_GREEN_LIGHT
                || type == EventType.QUAKE
                || type == EventType.BEDWARS
                || type == EventType.ONE_IN_THE_CHAMBER
                || type == EventType.SUMO_1V1
                || type == EventType.SUMO_2V2
                || type == EventType.SUMO_FFA;
    }

    private boolean usesFallElimination(EventType type) {
        return type == EventType.RUNNER
                || type == EventType.SPLEEF
                || type == EventType.SPLEGG
                || type == EventType.SUMO_1V1
                || type == EventType.SUMO_2V2
                || type == EventType.SUMO_FFA
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.BLOCK_PARTY
                || type == EventType.QUAKE
                || type == EventType.SKYWARS
                || type == EventType.BEDWARS
                || type == EventType.FIGHT_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.FIGHT_FFA
                || type == EventType.HOT_POTATO;
    }

    private Player damagingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean sameTeam(EventSession session, Player first, Player second) {
        String firstTeam = session.teams().get(first.getUniqueId());
        String secondTeam = session.teams().get(second.getUniqueId());
        return firstTeam != null && firstTeam.equals(secondTeam);
    }

    private void fireQuake(Player shooter, EventSession session) {
        if (!quakeCooldownReady(shooter, quakeShotCooldowns)) {
            return;
        }
        Location eye = shooter.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult blockHit = shooter.getWorld().rayTraceBlocks(
                eye, direction, 80.0D, FluidCollisionMode.NEVER, true
        );
        double maxDistance = blockHit == null || blockHit.getHitPosition() == null
                ? 80.0D
                : Math.max(0.0D, blockHit.getHitPosition().distance(eye.toVector()) - 0.05D);
        for (double distance = 0.0D; distance <= maxDistance; distance += 1.2D) {
            Location point = eye.clone().add(direction.clone().multiply(distance));
            shooter.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9F, 1.7F);
        RayTraceResult result = shooter.getWorld().rayTraceEntities(
                eye,
                direction,
                maxDistance,
                0.45D,
                entity -> entity instanceof Player target
                        && !target.getUniqueId().equals(shooter.getUniqueId())
                        && session.participants().contains(target.getUniqueId())
                        && !quakeRespawns.containsKey(target.getUniqueId())
        );
        if (result != null && result.getHitEntity() instanceof Player target) {
            addQuakeScore(shooter, 1);
            addQuakeScore(target, -1);
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + shooter.getName()
                    + " hit " + target.getName() + ".");
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0F, 0.7F);
            rememberEventInventoryLayout(target);
            scheduleQuakeRespawn(session, target);
        }
    }

    private void launchQuake(Player player) {
        if (!quakeCooldownReady(player, quakeLaunchCooldowns)) {
            return;
        }
        Vector direction = player.getLocation().getDirection().normalize();
        Vector horizontal = direction.clone().setY(0.0D);
        if (horizontal.lengthSquared() > 0.0D) {
            horizontal.normalize().multiply(1.55D);
        }
        double upward = Math.max(0.65D, 0.65D + Math.max(0.0D, direction.getY()) * 1.25D);
        Vector velocity = horizontal.setY(upward);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9F, 1.25F);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0.0D, 0.2D, 0.0D), 16, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    private void addQuakeScore(Player player, int delta) {
        int score = quakeScores.merge(player.getUniqueId(), delta, Integer::sum);
        eventManager.setRuntimeScoreboardValue("quake-score-" + player.getUniqueId(), String.valueOf(score));
    }

    private void addOneInTheChamberScore(Player player, int delta) {
        int score = oneInTheChamberScores.merge(player.getUniqueId(), delta, Integer::sum);
        eventManager.setRuntimeScoreboardValue("oitc-score-" + player.getUniqueId(), String.valueOf(score));
    }

    private void handleOneInTheChamberKill(EventSession session, Player killer, Player target) {
        if (killer.getUniqueId().equals(target.getUniqueId()) || oneInTheChamberRespawns.containsKey(target.getUniqueId())) {
            return;
        }
        rememberEventInventoryLayout(target);
        addOneInTheChamberScore(killer, 1);
        refillOneInTheChamberArrow(killer);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9F, 1.5F);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.9F, 0.7F);
        eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + killer.getName()
                + " eliminated " + target.getName() + ".");
        scheduleOneInTheChamberRespawn(session, target);
    }

    private void scheduleOneInTheChamberRespawn(EventSession session, Player player) {
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.SPECTATOR);
        final int[] seconds = {3};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EventSession current = eventManager.session();
            if (current == null || current != session || current.phase() != EventPhase.ACTIVE
                    || current.definition().type() != EventType.ONE_IN_THE_CHAMBER
                    || !current.participants().contains(player.getUniqueId()) || !player.isOnline()) {
                BukkitTask currentTask = oneInTheChamberRespawns.remove(player.getUniqueId());
                if (currentTask != null) {
                    currentTask.cancel();
                }
                return;
            }
            if (seconds[0] > 0) {
                showRespawnCountdown(player, seconds[0], 3, ChatColor.YELLOW);
                seconds[0]--;
                return;
            }
            BukkitTask currentTask = oneInTheChamberRespawns.remove(player.getUniqueId());
            if (currentTask != null) {
                currentTask.cancel();
            }
            Location spawn = randomSpawn(current.selectedMap());
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(10.0F);
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            applyOneInTheChamberLoadout(player);
            if (spawn != null) {
                TeleportService.teleport(plugin, player, spawn, "one in the chamber respawn");
            }
            clearRespawnCountdown(player);
        }, 0L, 20L);
        oneInTheChamberRespawns.put(player.getUniqueId(), task);
    }

    private void refillOneInTheChamberArrow(Player player) {
        if (!player.getInventory().contains(Material.ARROW)) {
            placeEventItem(player, new ItemStack(Material.ARROW, 1));
        }
    }

    private void applyOneInTheChamberLoadout(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        placeEventItem(player, namedItem(Material.BOW, "One Shot Bow"));
        placeEventItem(player, namedItem(Material.IRON_AXE, "One Shot Axe"));
        placeEventItem(player, new ItemStack(Material.ARROW, 1));
        player.updateInventory();
    }

    private void scheduleQuakeRespawn(EventSession session, Player player) {
        if (quakeRespawns.containsKey(player.getUniqueId())) {
            return;
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + "You will respawn in 3 seconds.");
        final int[] seconds = {3};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EventSession current = eventManager.session();
            if (current == null || current != session || current.phase() != EventPhase.ACTIVE
                    || current.definition().type() != EventType.QUAKE
                    || !current.participants().contains(player.getUniqueId()) || !player.isOnline()) {
                BukkitTask currentTask = quakeRespawns.remove(player.getUniqueId());
                if (currentTask != null) {
                    currentTask.cancel();
                }
                return;
            }
            if (seconds[0] > 0) {
                showRespawnCountdown(player, seconds[0], 3, ChatColor.YELLOW);
                seconds[0]--;
                return;
            }
            BukkitTask currentTask = quakeRespawns.remove(player.getUniqueId());
            if (currentTask != null) {
                currentTask.cancel();
            }
            EventMap map = current.selectedMap();
            Location spawn = randomSpawn(map);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(10.0F);
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector(0, 0, 0));
            if (!player.getInventory().contains(Material.GOLDEN_HOE)) {
                placeEventItem(player, namedItem(Material.GOLDEN_HOE, "Quake Railgun"));
            }
            if (spawn != null) {
                TeleportService.teleport(plugin, player, spawn, "quake respawn");
            }
            clearRespawnCountdown(player);
        }, 0L, 20L);
        quakeRespawns.put(player.getUniqueId(), task);
    }

    private void showRespawnCountdown(Player player, int seconds, int totalSeconds, ChatColor color) {
        player.setLevel(seconds);
        player.setExp(Math.max(0.0F, Math.min(1.0F, seconds / (float) totalSeconds)));
        sendActionBar(player, color + "Respawning in " + seconds + "...");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 1.0F);
    }

    private void clearRespawnCountdown(Player player) {
        player.setLevel(0);
        player.setExp(0.0F);
        sendActionBar(player, ChatColor.GREEN + "Respawned");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 1.5F);
    }

    private Location randomSpawn(EventMap map) {
        if (map == null || map.spawns().isEmpty()) {
            return null;
        }
        List<Location> spawns = List.copyOf(map.spawns().values());
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())).clone();
    }

    private boolean quakeCooldownReady(Player player, Map<UUID, Long> cooldowns) {
        long now = System.currentTimeMillis();
        long previous = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 3000L) {
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    private void launchBedWarsFireball(Player player, ItemStack fireCharge, EquipmentSlot hand) {
        if (fireCharge.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            fireCharge.setAmount(fireCharge.getAmount() - 1);
        }
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location spawn = player.getEyeLocation().add(direction.clone().multiply(1.2D));
        Fireball fireball = player.getWorld().spawn(spawn, Fireball.class);
        fireball.setShooter(player);
        fireball.setDirection(direction);
        fireball.setVelocity(direction.multiply(1.6D));
        fireball.setYield(1.5F); // Lower explosion damage for knockback-focused fireballs
        fireball.setIsIncendiary(false);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.9F);

        // Schedule explosion sound on impact
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!fireball.isDead()) {
                fireball.getWorld().playSound(fireball.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F);
            }
        }, 40L);
    }

    private void spawnDreamDefender(Player player, ItemStack item, EquipmentSlot hand) {
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2)).add(0, 1, 0);
        IronGolem golem = player.getWorld().spawn(spawnLoc, IronGolem.class);
        golem.setPlayerCreated(false);
        golem.setPersistent(false);
        bedWarsGolems.add(golem.getUniqueId());
        // Track owner via metadata
        EventSession session = eventManager.session();
        String team = session == null ? null : session.teams().get(player.getUniqueId());
        // Use scoreboard tag for team
        if (team != null) {
            golem.addScoreboardTag("bw_owner_" + team);
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Dream Defender spawned.");
        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.7F, 1.0F);
    }

    private void trackThrownProjectile(Player player, String type) {
        // Find the most recently launched projectile matching the type
        for (Entity entity : player.getWorld().getEntitiesByClass(Projectile.class)) {
            if (!(entity instanceof Projectile projectile)) continue;
            if (projectile.getShooter() instanceof Player shooter && shooter.getUniqueId().equals(player.getUniqueId())
                    && !bedWarsProjectiles.containsKey(entity.getUniqueId())) {
                if (("BED_BUG".equals(type) && entity instanceof Snowball)
                        || ("BRIDGE_EGG".equals(type))) {
                    bedWarsProjectiles.put(entity.getUniqueId(), player.getUniqueId());
                    bedWarsProjectileType.put(entity.getUniqueId(), type);
                    if ("BRIDGE_EGG".equals(type)) {
                        startBridgeEggTrail(entity);
                    }
                    return;
                }
            }
        }
    }

    private void startBridgeEggTrail(Entity egg) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!egg.isValid() || egg.isDead()) {
                BukkitTask t = bridgeEggTasks.remove(egg.getUniqueId());
                if (t != null) t.cancel();
                return;
            }
            Location loc = egg.getLocation().clone().subtract(0, 1, 0);
            Material wool = teamWool(eventManager.teamFor(bedWarsProjectiles.get(egg.getUniqueId())));
            Block block = loc.getBlock();
            if ((block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA)
                    && !EventSessionHardeningListener.isBedWarsSpawnProtected(plugin, eventManager.activeMap(), block.getLocation())) {
                block.setType(wool, false);
                bedWarsPlacedBlocks.add(locationKey(block.getLocation()));
            }
        }, 0L, 2L); // Place every 2 ticks
        bridgeEggTasks.put(egg.getUniqueId(), task);
    }

    private void spawnBedBugSilverfish(Player owner, Location location) {
        Silverfish silverfish = location.getWorld().spawn(location, Silverfish.class);
        silverfish.setPersistent(false);
        // Tag with owner's team
        EventSession session = eventManager.session();
        String team = session == null || owner == null ? null : session.teams().get(owner.getUniqueId());
        if (team != null) {
            silverfish.addScoreboardTag("bw_owner_" + team);
        }
        bedWarsSilverfish.add(silverfish.getUniqueId());
        // Auto-despawn after 20 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (silverfish.isValid() && !silverfish.isDead()) {
                silverfish.remove();
            }
            bedWarsSilverfish.remove(silverfish.getUniqueId());
        }, 20L * 20);
        if (owner != null) {
            owner.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Bed Bug deployed.");
        }
    }

    private void placeBridgeEggBlocks(EventSession session, Player owner, Location location) {
        if (owner == null) return;
        Material wool = teamWool(session == null ? "" : session.teams().getOrDefault(owner.getUniqueId(), ""));
        Location loc = location.clone();
        // Place 5 blocks behind the impact in the direction the egg was traveling
        for (int i = 0; i < 5; i++) {
            Block block = loc.getBlock();
            if ((block.getType().isAir() || block.getType() == Material.WATER || block.getType() == Material.LAVA)
                    && !EventSessionHardeningListener.isBedWarsSpawnProtected(plugin, eventManager.activeMap(), block.getLocation())) {
                block.setType(wool, false);
                bedWarsPlacedBlocks.add(locationKey(block.getLocation()));
            }
            loc.add(0, -1, 0); // Move down one block
            if (loc.getBlockY() < loc.getWorld().getMinHeight()) break;
        }
        owner.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Bridge Egg deployed.");
    }

    private boolean inArea(EventMap map, String key, Location location) {
        CuboidRegion area = map.areas().get(key);
        return area != null && area.contains(location);
    }

    private CuboidRegion teamArea(EventMap map, String prefix, String team) {
        CuboidRegion area = map.areas().get(prefix + "-" + team);
        if (area != null) {
            return area;
        }
        return map.areas().get(prefix + "-zone");
    }

    private Location center(CuboidRegion area) {
        if (area == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                (area.minX() + area.maxX()) / 2.0D + 0.5D,
                area.minY() + 1.0D,
                (area.minZ() + area.maxZ()) / 2.0D + 0.5D
        );
    }

    private boolean isBedBlock(Material material) {
        return material.name().endsWith("_BED");
    }

    private void spawnConfiguredBedWarsBeds(EventMap map) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, String> entry : map.pointBlockData().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            Location footLocation = map.points().get(key);
            if (!key.startsWith("bed-") || footLocation == null || footLocation.getWorld() == null) {
                continue;
            }
            try {
                BlockData parsed = Bukkit.createBlockData(entry.getValue());
                if (!(parsed instanceof Bed savedBed)) {
                    plugin.getLogger().warning("Skipping invalid BedWars bed data for " + map.id() + " / " + key + ".");
                    continue;
                }
                Bed footData = (Bed) savedBed.clone();
                footData.setPart(Bed.Part.FOOT);
                Bed headData = (Bed) savedBed.clone();
                headData.setPart(Bed.Part.HEAD);
                Block foot = footLocation.getBlock();
                Block head = foot.getRelative(savedBed.getFacing());
                foot.setBlockData(footData, false);
                head.setBlockData(headData, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Could not restore BedWars bed " + key + " on map " + map.id()
                        + ": " + ex.getMessage());
            }
        }
    }

    private void handleBedWarsBedBreak(EventSession session, EventMap map, BlockBreakEvent event) {
        String bedTeam = nearestBedTeam(map, event.getBlock().getLocation());
        if (bedTeam == null) {
            event.setCancelled(true);
            return;
        }
        bedWarsPlacedBlocks.remove(locationKey(event.getBlock().getLocation()));
        String breakerTeam = session.teams().get(event.getPlayer().getUniqueId());
        if (bedTeam.equals(breakerTeam)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You cannot break your own bed.");
            return;
        }
        event.setDropItems(false);
        eventManager.recordArenaResetBlock(event.getBlock());
        if (event.getBlock().getBlockData() instanceof Bed bed) {
            Block otherHalf = event.getBlock().getRelative(
                    bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
            if (isBedBlock(otherHalf.getType())) {
                eventManager.recordArenaResetBlock(otherHalf);
            }
        }
        if (!brokenBedTeams.contains(bedTeam)) {
            brokenBedTeams.add(bedTeam);
            eventManager.setRuntimeScoreboardValue("bedwars-bed-" + bedTeam.toLowerCase(Locale.ROOT), "false");
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Team " + bedTeam + "'s bed was destroyed.");
        }
    }

    private void destroyBedWarsBedsOnTimer(EventSession session) {
        if (bedWarsBedsDestroyedByTimer || eventManager.activeSecondsRemaining() > 300L) {
            return;
        }
        bedWarsBedsDestroyedByTimer = true;
        final boolean[] removedLiveBed = {false};
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        map.points().forEach((key, location) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("bed-") || location == null || location.getWorld() == null) {
                return;
            }
            String team = lower.substring("bed-".length());
            breakBedBlocksNear(location);
            removedLiveBed[0] |= !brokenBedTeams.contains(team);
            if (!brokenBedTeams.contains(team)) {
                brokenBedTeams.add(team);
            }
            eventManager.setRuntimeScoreboardValue("bedwars-bed-" + team, "false");
        });
        if (removedLiveBed[0]) {
            eventManager.messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.RED
                    + "Deathmatch has started. All remaining beds were removed; every death is now final.");
            playForParticipants(session, Sound.ENTITY_WITHER_SPAWN, 0.8F, 1.2F);
        }
    }

    private void breakBedBlocksNear(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) {
            return;
        }
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int y = centerY - 2; y <= centerY + 2; y++) {
                for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isBedBlock(block.getType())) {
                        continue;
                    }
                    bedWarsTimedBedStates.putIfAbsent(locationKey(block.getLocation()), block.getState());
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void restoreTimedBedWarsBeds() {
        List<BlockState> states = List.copyOf(bedWarsTimedBedStates.values());
        bedWarsTimedBedStates.clear();
        states.forEach(state -> state.update(true, false));
    }

    private void respawnBedWarsPlayer(EventSession session, EventMap map, Player player) {
        Location spawn = bedWarsRespawn(session, map, player);
        if (spawn == null) {
            return;
        }
        player.setFallDistance(0.0F);
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setVelocity(new Vector(0, 0, 0));
        clearBedWarsInventory(player);

        // Team-colored leather armor
        String team = session.teams().get(player.getUniqueId());
        Color teamColor = teamLeatherColor(team);
        ItemStack helmet = leatherArmor(Material.LEATHER_HELMET, teamColor);
        helmet.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(leatherArmor(Material.LEATHER_CHESTPLATE, teamColor));
        player.getInventory().setLeggings(leatherArmor(Material.LEATHER_LEGGINGS, teamColor));
        player.getInventory().setBoots(leatherArmor(Material.LEATHER_BOOTS, teamColor));

        // Wooden sword
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        applyBedWarsItemUpgrades(player, sword);
        player.getInventory().addItem(sword);

        // Apply purchased permanent armor
        applyBedWarsArmor(player);

        // Give permanent shears if owned
        if (Boolean.TRUE.equals(bedWarsHasShears.get(player.getUniqueId()))) {
            ItemStack shears = new ItemStack(Material.SHEARS);
            applyBedWarsItemUpgrades(player, shears);
            player.getInventory().addItem(shears);
        }

        // Give pickaxe based on current tier
        int pickaxeTier = bedWarsPickaxeTiers.getOrDefault(player.getUniqueId(), 0);
        if (pickaxeTier >= 1) {
            String id = switch (pickaxeTier) {
                case 1 -> "WOODEN_PICKAXE";
                case 2 -> "IRON_PICKAXE";
                case 3 -> "GOLD_PICKAXE";
                case 4 -> "DIAMOND_PICKAXE";
                default -> null;
            };
            if (id != null) {
                ItemStack pickaxe = createBedWarsReward(player, Material.WOODEN_PICKAXE, 1, id);
                player.getInventory().addItem(pickaxe);
            }
        }

        // Give axe based on current tier
        int axeTier = bedWarsAxeTiers.getOrDefault(player.getUniqueId(), 0);
        if (axeTier >= 1) {
            String id = switch (axeTier) {
                case 1 -> "WOODEN_AXE";
                case 2 -> "STONE_AXE";
                case 3 -> "IRON_AXE";
                case 4 -> "DIAMOND_AXE";
                default -> null;
            };
            if (id != null) {
                ItemStack axe = createBedWarsReward(player, Material.WOODEN_AXE, 1, id);
                player.getInventory().addItem(axe);
            }
        }

        player.updateInventory();
        TeleportService.teleport(plugin, player, spawn, "bedwars bed respawn");
    }

    private Color teamLeatherColor(String team) {
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

    private ItemStack leatherArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void clearBedWarsInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void ensureBedWarsShops(EventSession session) {
        if (!bedWarsShopEntities.isEmpty()) {
            return;
        }
        EventMap map = session.selectedMap();
        if (map == null) {
            return;
        }
        map.points().forEach((key, location) -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if ((!lower.startsWith("item-shop-") && !lower.startsWith("upgrade-shop-"))
                    || location == null || location.getWorld() == null) {
                return;
            }
            Villager villager = location.getWorld().spawn(location, Villager.class, spawned -> {
                spawned.setAI(false);
                spawned.setInvulnerable(true);
                spawned.setCollidable(false);
                spawned.setPersistent(false);
                spawned.setSilent(true);
                spawned.setCustomNameVisible(true);
                spawned.setRotation(location.getYaw(), location.getPitch());
                spawned.setCustomName(lower.startsWith("item-shop-")
                        ? ChatColor.GREEN + "Item Shop"
                        : ChatColor.AQUA + "Team Upgrades");
                spawned.addScoreboardTag("enthusia_bedwars_shop");
                spawned.addScoreboardTag(lower.startsWith("item-shop-")
                        ? BEDWARS_ITEM_SHOP_TAG
                        : BEDWARS_UPGRADE_SHOP_TAG);
            });
            bedWarsShopEntities.add(villager.getUniqueId());
        });
    }

    private void removeBedWarsShops() {
        for (UUID uuid : bedWarsShopEntities) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        bedWarsShopEntities.clear();
    }

    private void clearBedWarsEntities() {
        for (UUID uuid : bedWarsGolems) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        bedWarsGolems.clear();
        for (UUID uuid : bedWarsSilverfish) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        bedWarsSilverfish.clear();
    }

    private void rewardKnockbackKill(Player killer) {
        EventSession session = eventManager.session();
        if (killer == null || session == null || session.definition().type() != EventType.KNOCKBACK_FFA
                || !session.participants().contains(killer.getUniqueId())) {
            return;
        }
        rememberEventInventoryLayout(killer);
        placeEventItem(killer, namedItem(Material.ENDER_PEARL, "Recovery Pearl"));
        killer.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Kill reward: +1 ender pearl.");
    }

    private void transferBedWarsResources(Player victim, Player killer) {
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        List<String> transferred = new ArrayList<>();
        for (Material material : BEDWARS_LOOT_MATERIALS) {
            int amount = countMaterial(victim, material);
            if (amount <= 0) {
                continue;
            }
            removeAllMaterial(victim, material);
            int undelivered = giveBedWarsResource(killer, material, amount);
            if (undelivered > 0) {
                bedWarsPendingResources.computeIfAbsent(killer.getUniqueId(), ignored -> new HashMap<>())
                        .merge(material, undelivered, Integer::sum);
            }
            transferred.add(amount + " " + readableMaterial(material));
        }
        if (transferred.isEmpty()) {
            return;
        }
        String summary = String.join(", ", transferred);
        killer.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Looted " + summary + " from " + victim.getName() + ".");
        victim.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + killer.getName() + " looted " + summary + " from you.");
    }

    private void deliverBedWarsPendingResources(EventSession session) {
        for (UUID uuid : List.copyOf(bedWarsPendingResources.keySet())) {
            if (!session.participants().contains(uuid)) {
                bedWarsPendingResources.remove(uuid);
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            Map<Material, Integer> pending = bedWarsPendingResources.get(uuid);
            if (pending == null) {
                continue;
            }
            for (Material material : List.copyOf(pending.keySet())) {
                int remaining = giveBedWarsResource(player, material, pending.getOrDefault(material, 0));
                if (remaining <= 0) {
                    pending.remove(material);
                } else {
                    pending.put(material, remaining);
                }
            }
            if (pending.isEmpty()) {
                bedWarsPendingResources.remove(uuid);
            }
        }
    }

    private int giveBedWarsResource(Player player, Material material, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(material.getMaxStackSize(), remaining);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, stackSize));
            int undelivered = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            remaining -= stackSize - undelivered;
            if (undelivered > 0) {
                break;
            }
        }
        return remaining;
    }

    private int countMaterial(Player player, Material material) {
        if (player == null) return 0;
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removeAllMaterial(Player player, Material material) {
        if (player == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() == material) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean isProtectedBedWarsExplosionBlock(Block block) {
        if (block == null) {
            return true;
        }
        if (isBedBlock(block.getType())) {
            return true;
        }
        // Protect glass (blast-proof), obsidian, and stained glass
        Material type = block.getType();
        if (type == Material.OBSIDIAN || type == Material.GLASS || type.name().endsWith("STAINED_GLASS")) {
            return true;
        }
        return !bedWarsPlacedBlocks.contains(locationKey(block.getLocation()));
    }

    private Location bedWarsRespawn(EventSession session, EventMap map, Player player) {
        if (map == null) {
            return null;
        }
        String team = session.teams().get(player.getUniqueId());
        Location spawn = team == null ? null : map.spawns().get("team-" + team + "-spawn");
        if (spawn == null && !map.spawns().isEmpty()) {
            spawn = map.spawns().values().iterator().next();
        }
        return spawn;
    }

    private String nearestBedTeam(EventMap map, Location location) {
        String nearest = null;
        double distance = Double.MAX_VALUE;
        for (Map.Entry<String, Location> entry : map.points().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("bed-") || entry.getValue() == null || entry.getValue().getWorld() == null
                    || location == null || location.getWorld() == null
                    || !entry.getValue().getWorld().getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            double current = entry.getValue().distanceSquared(location);
            if (current <= 4.0D && current < distance) {
                distance = current;
                nearest = key.substring("bed-".length());
            }
        }
        return nearest;
    }

    private String reachedFlagTeam(EventMap map, Location location) {
        for (Map.Entry<String, Location> entry : map.points().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("flag-")) {
                continue;
            }
            Location point = entry.getValue();
            if (point != null && location != null && point.getWorld() != null && location.getWorld() != null
                    && point.getWorld().getUID().equals(location.getWorld().getUID())
                    && point.distanceSquared(location) <= 4.0D) {
                return key.substring("flag-".length());
            }
        }
        return null;
    }

    private String nearestTeamByFlag(EventMap map, Location location) {
        String nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Location> entry : map.points().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith("flag-")) {
                continue;
            }
            Location point = entry.getValue();
            if (point == null || location == null || point.getWorld() == null || location.getWorld() == null
                    || !point.getWorld().getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            double distance = point.distanceSquared(location);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = key.substring("flag-".length());
            }
        }
        return nearest;
    }

    private void fillArea(CuboidRegion area, Material material) {
        if (area == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized event area color update (" + blocks + " blocks). Resize the special area.");
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private void fillBlockPartyPattern(CuboidRegion area) {
        if (area == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized block party pattern update (" + blocks + " blocks). Resize the special area.");
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean placedTarget = false;
        double targetChance = blockPartyTargetChance();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material material;
                    if (random.nextDouble() < targetChance) {
                        material = blockPartyTarget;
                    } else {
                        material = randomNonTargetBlockPartyColor(random);
                    }
                    if (material == blockPartyTarget) {
                        placedTarget = true;
                    }
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
        if (!placedTarget) {
            world.getBlockAt((minX + maxX) / 2, minY, (minZ + maxZ) / 2).setType(blockPartyTarget, false);
        }
    }

    private double blockPartyTargetChance() {
        double baseChance = 1.0D / BLOCK_PARTY_COLORS.size();
        if (blockPartyRound < 10) {
            return baseChance;
        }
        double reduction = Math.max(0.18D, 1.0D - ((blockPartyRound - 9) * 0.07D));
        return baseChance * reduction;
    }

    private Material randomNonTargetBlockPartyColor(ThreadLocalRandom random) {
        Material material;
        do {
            material = BLOCK_PARTY_COLORS.get(random.nextInt(BLOCK_PARTY_COLORS.size()));
        } while (material == blockPartyTarget);
        return material;
    }

    private void clearWrongBlockPartyColors(CuboidRegion area, Material target) {
        if (area == null || target == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized block party clear update (" + blocks + " blocks). Resize the special area.");
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (BLOCK_PARTY_COLORS.contains(block.getType()) && block.getType() != target) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void fillBlockPartyHotbars(EventSession session) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            for (int slot = 0; slot < 9; slot++) {
                player.getInventory().setItem(slot, new ItemStack(blockPartyTarget));
            }
            player.updateInventory();
        }
    }

    private void updateBlockPartyTimer(EventSession session) {
        float progress = blockPartyRoundTicks <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, blockPartyTicksRemaining / (float) blockPartyRoundTicks));
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.setLevel(Math.max(0, blockPartySeconds));
            player.setExp(progress);
        }
    }

    private void messageParticipants(EventSession session, String message) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void playForParticipants(EventSession session, Sound sound, float volume, float pitch) {
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private List<Map.Entry<String, Location>> orderedCheckpoints(EventMap map) {
        return map.checkpoints().entrySet().stream()
                .sorted((left, right) -> checkpointOrder(left.getKey()) - checkpointOrder(right.getKey()))
                .toList();
    }

    private Map.Entry<String, CuboidRegion> reachedRaceArea(EventMap map, Location location, EventType type) {
        if (map == null || location == null) {
            return null;
        }
        return map.areas().entrySet().stream()
                .filter(entry -> isRaceAreaKey(entry.getKey()))
                .filter(entry -> entry.getValue() != null && containsRaceArea(entry.getValue(), location, type))
                .sorted((left, right) -> checkpointOrder(left.getKey()) - checkpointOrder(right.getKey()))
                .findFirst()
                .orElse(null);
    }

    private Map.Entry<String, List<Location>> reachedCheckpointBlocks(EventMap map, Location location, EventType type) {
        if (map == null || location == null || type != EventType.ELYTRA_RACE) {
            return null;
        }
        return map.checkpointBlocks().entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(block -> touchesCheckpointBlock(block, location)))
                .sorted((left, right) -> checkpointOrder(left.getKey()) - checkpointOrder(right.getKey()))
                .findFirst()
                .orElse(null);
    }

    private boolean touchesCheckpointBlock(Location block, Location location) {
        if (!sameWorld(block, location)) {
            return false;
        }
        double margin = 0.35D;
        return location.getX() >= block.getBlockX() - margin
                && location.getX() <= block.getBlockX() + 1.0D + margin
                && location.getY() >= block.getBlockY() - margin
                && location.getY() <= block.getBlockY() + 1.8D
                && location.getZ() >= block.getBlockZ() - margin
                && location.getZ() <= block.getBlockZ() + 1.0D + margin;
    }

    private boolean containsRaceArea(CuboidRegion area, Location location, EventType type) {
        if (area.contains(location)) {
            return true;
        }
        if (type != EventType.HORSE_RACE) {
            return false;
        }
        if (location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(area.worldName())) {
            return false;
        }
        double margin = 0.75D;
        return location.getX() >= area.minX() - margin && location.getX() <= area.maxX() + margin
                && location.getZ() >= area.minZ() - margin && location.getZ() <= area.maxZ() + margin
                && location.getY() >= area.minY() - 2.0D && location.getY() <= area.maxY() + 6.0D;
    }

    private boolean isRaceAreaKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("checkpoint")
                || lower.startsWith("ring")
                || lower.startsWith("cp")
                || lower.startsWith("finish");
    }

    private Location checkpointRespawn(EventMap map, String key, Location fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        Location exact = map.points().get("checkpoint-spawn-" + normalized);
        if (exact != null) {
            return exact;
        }
        String number = checkpointNumber(normalized);
        if (!number.isBlank()) {
            Location numbered = map.points().get("checkpoint-spawn-" + number);
            if (numbered != null) {
                return numbered;
            }
            Location cpNumbered = map.points().get("checkpoint-spawn-cp" + number);
            if (cpNumbered != null) {
                return cpNumbered;
            }
            Location ringNumbered = map.points().get("checkpoint-spawn-ring" + number);
            if (ringNumbered != null) {
                return ringNumbered;
            }
        }
        return fallback;
    }

    private int checkpointOrder(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.equals("finish") || lower.startsWith("finish-")) {
            return Integer.MAX_VALUE;
        }
        String digits = lower.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return 10_000;
        }
        return Integer.parseInt(digits);
    }

    private Map.Entry<String, Location> nearestCheckpoint(EventMap map, Location location) {
        return orderedCheckpoints(map).stream()
                .filter(entry -> sameWorld(entry.getValue(), location) && entry.getValue().distanceSquared(location) <= 4.0D)
                .findFirst()
                .orElse(null);
    }

    private String checkpointMessage(String key) {
        String digits = checkpointNumber(key);
        if (digits.isBlank()) {
            return "Checkpoint reached.";
        }
        return "Reached Checkpoint #" + digits + ".";
    }

    private String checkpointNumber(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("\\D+", "");
    }

    private boolean isFinishCheckpoint(String key) {
        return key.toLowerCase(Locale.ROOT).startsWith("finish");
    }

    private boolean completedAllElytraCheckpoints(Player player, EventMap map) {
        int required = java.util.stream.Stream.of(
                        map.checkpointBlocks().keySet(),
                        map.areas().keySet(),
                        map.checkpoints().keySet()
                )
                .flatMap(java.util.Collection::stream)
                .filter(this::isRaceAreaKey)
                .filter(key -> !isFinishCheckpoint(key))
                .mapToInt(this::checkpointOrder)
                .filter(order -> order < Integer.MAX_VALUE)
                .max()
                .orElse(0);
        return raceCheckpointOrder.getOrDefault(player.getUniqueId(), 0) >= required;
    }

    private void clearDroppedEventItems(EventMap map) {
        if (map == null || map.region() == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(map.worldName());
        if (world == null) {
            return;
        }
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (map.region().contains(item.getLocation())) {
                item.remove();
            }
        }
    }

    private String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private Inventory createBedWarsItemShop(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, BEDWARS_ITEM_SHOP_TITLE);
        String page = bedWarsShopPage.getOrDefault(player.getUniqueId(), "QUICK_BUY");

        inventory.setItem(0, pageButton(Material.NETHER_STAR, ChatColor.GREEN + "Quick Buy", "QUICK_BUY", page));
        inventory.setItem(1, pageButton(Material.WHITE_WOOL, ChatColor.YELLOW + "Blocks", "BLOCKS", page));
        inventory.setItem(2, pageButton(Material.IRON_SWORD, ChatColor.YELLOW + "Melee", "MELEE", page));
        inventory.setItem(3, pageButton(Material.CHAINMAIL_BOOTS, ChatColor.YELLOW + "Armor", "ARMOR", page));
        inventory.setItem(4, pageButton(Material.IRON_PICKAXE, ChatColor.YELLOW + "Tools", "TOOLS", page));
        inventory.setItem(5, pageButton(Material.BOW, ChatColor.YELLOW + "Ranged", "RANGED", page));
        inventory.setItem(6, pageButton(Material.BREWING_STAND, ChatColor.YELLOW + "Potions", "POTIONS", page));
        inventory.setItem(7, pageButton(Material.TNT, ChatColor.YELLOW + "Utility", "UTILITY", page));
        for (int slot = 9; slot <= 17; slot++) {
            inventory.setItem(slot, infoItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        List<ItemStack> items;
        switch (page) {
            case "QUICK_BUY" -> items = buildQuickBuyPage(player);
            case "BLOCKS" -> items = buildBlocksPage(player);
            case "MELEE" -> items = buildMeleePage(player);
            case "ARMOR" -> items = buildArmorPage(player);
            case "TOOLS" -> items = buildToolsPage(player);
            case "RANGED" -> items = buildRangedPage(player);
            case "POTIONS" -> items = buildPotionsPage(player);
            case "UTILITY" -> items = buildUtilityPage(player);
            default -> items = buildQuickBuyPage(player);
        }

        int[] displaySlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < items.size() && i < displaySlots.length; i++) {
            inventory.setItem(displaySlots[i], items.get(i));
        }
        return inventory;
    }

    private ItemStack pageButton(Material material, String name, String targetPage, String currentPage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            boolean active = targetPage.equalsIgnoreCase(currentPage);
            meta.setLore(List.of(active ? ChatColor.GREEN + "(Selected)" : ChatColor.GRAY + "Click to view"));
            if (active) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, "PAGE:" + targetPage);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<ItemStack> buildQuickBuyPage(Player player) {
        List<String> slots = quickBuySlots.computeIfAbsent(player.getUniqueId(), uuid -> defaultQuickBuyLayout());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            String itemId = i < slots.size() ? slots.get(i) : "EMPTY";
            if ("EMPTY".equals(itemId)) {
                ItemStack empty = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = empty.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GRAY + "Empty Slot!");
                    meta.setLore(List.of(
                            ChatColor.GRAY + "This is a Quick Buy Slot!",
                            ChatColor.YELLOW + "Sneak Click any item in the shop",
                            ChatColor.YELLOW + "to add it here."
                    ));
                    meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, "EMPTY");
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "qb-slot"), PersistentDataType.INTEGER, i);
                    empty.setItemMeta(meta);
                }
                items.add(empty);
            } else {
                ItemStack item;
                if (isToolItem(itemId)) {
                    // Quick Buy tool slot: show next tier dynamically like Tools tab
                    boolean isPickaxe = isPickaxeId(itemId);
                    item = buildToolUpgradeSlot(player, isPickaxe, true);
                } else {
                    item = createShopItemById(player, itemId, true);
                }
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "qb-slot"), PersistentDataType.INTEGER, i);
                    item.setItemMeta(meta);
                }
                items.add(item);
            }
        }
        return items;
    }

    private List<String> defaultQuickBuyLayout() {
        return new ArrayList<>(List.of(
                "WOOL", "STONE_SWORD", "CHAINMAIL_ARMOR", "WOODEN_PICKAXE", "BOW",
                "SPEED_POTION", "TNT", "OAK_PLANKS", "IRON_SWORD", "IRON_ARMOR",
                "SHEARS", "ARROWS", "INVISIBILITY_POTION", "WATER_BUCKET",
                "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY"
        ));
    }

    private List<ItemStack> buildBlocksPage(Player player) {
        return List.of(
                createShopItemById(player, "WOOL", false),
                createShopItemById(player, "HARDENED_CLAY", false),
                createShopItemById(player, "BLAST_PROOF_GLASS", false),
                createShopItemById(player, "END_STONE", false),
                createShopItemById(player, "LADDER", false),
                createShopItemById(player, "OAK_PLANKS", false),
                createShopItemById(player, "OBSIDIAN", false)
        );
    }

    private List<ItemStack> buildMeleePage(Player player) {
        return List.of(
                createShopItemById(player, "STONE_SWORD", false),
                createShopItemById(player, "IRON_SWORD", false),
                createShopItemById(player, "DIAMOND_SWORD", false),
                createShopItemById(player, "KNOCKBACK_STICK", false)
        );
    }

    private List<ItemStack> buildArmorPage(Player player) {
        return List.of(
                createShopItemById(player, "CHAINMAIL_ARMOR", false),
                createShopItemById(player, "IRON_ARMOR", false),
                createShopItemById(player, "DIAMOND_ARMOR", false)
        );
    }

    private List<ItemStack> buildToolsPage(Player player) {
        return List.of(
                createShopItemById(player, "SHEARS", false),
                buildToolUpgradeSlot(player, true, false),
                buildToolUpgradeSlot(player, false, false)
        );
    }

    private ItemStack buildToolUpgradeSlot(Player player, boolean isPickaxe, boolean isQuickBuy) {
        int tier = isPickaxe ? bedWarsPickaxeTiers.getOrDefault(player.getUniqueId(), 0)
                : bedWarsAxeTiers.getOrDefault(player.getUniqueId(), 0);
        int nextTier = tier + 1;
        if (nextTier > 4) {
            String type = isPickaxe ? "Pickaxe" : "Axe";
            return infoItem(Material.GRAY_STAINED_GLASS_PANE,
                    ChatColor.GREEN + type + " (Maxed)",
                    "You have the maximum tier.");
        }
        String id = isPickaxe
                ? switch (nextTier) { case 1 -> "WOODEN_PICKAXE"; case 2 -> "IRON_PICKAXE"; case 3 -> "GOLD_PICKAXE"; case 4 -> "DIAMOND_PICKAXE"; default -> "WOODEN_PICKAXE"; }
                : switch (nextTier) { case 1 -> "WOODEN_AXE"; case 2 -> "STONE_AXE"; case 3 -> "IRON_AXE"; case 4 -> "DIAMOND_AXE"; default -> "WOODEN_AXE"; };
        int cost = isPickaxe
                ? switch (nextTier) { case 1, 2 -> 10; case 3 -> 3; case 4 -> 6; default -> 10; }
                : switch (nextTier) { case 1, 2 -> 10; case 3 -> 3; case 4 -> 6; default -> 10; };
        Material currency = nextTier <= 2 ? Material.IRON_INGOT : Material.GOLD_INGOT;
        Material material = switch (nextTier) {
            case 1 -> isPickaxe ? Material.WOODEN_PICKAXE : Material.WOODEN_AXE;
            case 2 -> isPickaxe ? Material.IRON_PICKAXE : Material.STONE_AXE;
            case 3 -> isPickaxe ? Material.GOLDEN_PICKAXE : Material.IRON_AXE;
            case 4 -> isPickaxe ? Material.DIAMOND_PICKAXE : Material.DIAMOND_AXE;
            default -> isPickaxe ? Material.WOODEN_PICKAXE : Material.WOODEN_AXE;
        };
        String name = "Tier " + nextTier + " " + (isPickaxe ? "Pickaxe" : "Axe");
        String nextName = tier == 0 ? "Wooden" : isPickaxe
                ? switch (nextTier) { case 2 -> "Iron"; case 3 -> "Golden"; case 4 -> "Diamond"; default -> "Wooden"; }
                : switch (nextTier) { case 2 -> "Stone"; case 3 -> "Iron"; case 4 -> "Diamond"; default -> "Wooden"; };
        List<String> lore = new ArrayList<>();
        ChatColor costColor = switch (currency) {
            case IRON_INGOT -> ChatColor.WHITE;
            case GOLD_INGOT -> ChatColor.GOLD;
            case EMERALD -> ChatColor.DARK_GREEN;
            case DIAMOND -> ChatColor.AQUA;
            default -> ChatColor.GRAY;
        };
        lore.add(ChatColor.GRAY + "Cost: " + costColor + cost + " " + readableMaterial(currency));
        lore.add("");
        lore.add(ChatColor.GRAY + "Upgrade to " + nextName + " " + (isPickaxe ? "Pickaxe" : "Axe"));
        lore.add("");
        lore.add(ChatColor.GRAY + "This is an upgradable item.");
        lore.add(ChatColor.GRAY + "It will lose 1 tier upon death!");
        lore.add("");
        lore.add(ChatColor.GRAY + "You will permanently respawn");
        lore.add(ChatColor.GRAY + "with at least the lowest tier.");
        lore.add("");
        boolean canAfford = countMaterial(player, currency) >= cost;
        if (canAfford) {
            lore.add(ChatColor.YELLOW + "Click to purchase!");
        } else {
            lore.add(ChatColor.RED + "You don't have enough " + readableMaterial(currency) + "s!");
        }
        if (isQuickBuy) {
            lore.add(ChatColor.AQUA + "Sneak Click to remove from Quick Buy!");
        } else {
            lore.add(ChatColor.AQUA + "Sneak Click to add to Quick Buy!");
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(canAfford ? (ChatColor.GREEN + name) : (ChatColor.RED + name));
            meta.setLore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(shopRewardKey, PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(shopCostKey, PersistentDataType.INTEGER, cost);
            meta.getPersistentDataContainer().set(shopCurrencyKey, PersistentDataType.STRING, currency.name());
            meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<ItemStack> buildRangedPage(Player player) {
        return List.of(
                createShopItemById(player, "BOW", false),
                createShopItemById(player, "POWER_BOW", false),
                createShopItemById(player, "PUNCH_BOW", false),
                createShopItemById(player, "ARROWS", false)
        );
    }

    private List<ItemStack> buildPotionsPage(Player player) {
        return List.of(
                createShopItemById(player, "SPEED_POTION", false),
                createShopItemById(player, "JUMP_POTION", false),
                createShopItemById(player, "INVISIBILITY_POTION", false)
        );
    }

    private List<ItemStack> buildUtilityPage(Player player) {
        return List.of(
                createShopItemById(player, "GOLDEN_APPLE", false),
                createShopItemById(player, "BED_BUG", false),
                createShopItemById(player, "DREAM_DEFENDER", false),
                createShopItemById(player, "FIREBALL", false),
                createShopItemById(player, "TNT", false),
                createShopItemById(player, "ENDER_PEARL", false),
                createShopItemById(player, "WATER_BUCKET", false),
                createShopItemById(player, "BRIDGE_EGG", false),
                createShopItemById(player, "MAGIC_MILK", false),
                createShopItemById(player, "SPONGE", false),
                createShopItemById(player, "POPUP_TOWER", false)
        );
    }

    private ItemStack createShopItemById(Player player, String itemId, boolean isQuickBuy) {
        return switch (itemId) {
            case "WOOL" -> shopItem(player, teamWool(player == null ? "" : eventManager.teamFor(player.getUniqueId())),
                    16, ChatColor.WHITE + "Wool", 4, Material.IRON_INGOT,
                    List.of("Great for bridging across islands.", "Turns into your team's color."), itemId, isQuickBuy);
            case "HARDENED_CLAY" -> shopItem(player, teamConcrete(player == null ? "" : eventManager.teamFor(player.getUniqueId())),
                    16, ChatColor.GOLD + "Concrete", 12, Material.IRON_INGOT,
                    List.of("Team-colored block to defend your bed."), itemId, isQuickBuy);
            case "BLAST_PROOF_GLASS" -> shopItem(player, Material.GLASS,
                    4, ChatColor.AQUA + "Blast-Proof Glass", 12, Material.IRON_INGOT,
                    List.of("Immune to explosions."), itemId, isQuickBuy);
            case "END_STONE" -> shopItem(player, Material.END_STONE,
                    12, ChatColor.YELLOW + "End Stone", 24, Material.IRON_INGOT,
                    List.of("Solid block to defend your bed."), itemId, isQuickBuy);
            case "LADDER" -> shopItem(player, Material.LADDER,
                    8, ChatColor.WHITE + "Ladder", 4, Material.IRON_INGOT,
                    List.of("Useful to save cats stuck in trees."), itemId, isQuickBuy);
            case "OAK_PLANKS" -> shopItem(player, Material.OAK_PLANKS,
                    16, ChatColor.GOLD + "Wood", 4, Material.GOLD_INGOT,
                    List.of("Good block to defend your bed."), itemId, isQuickBuy);
            case "OBSIDIAN" -> shopItem(player, Material.OBSIDIAN,
                    4, ChatColor.DARK_PURPLE + "Obsidian", 4, Material.EMERALD,
                    List.of("Extreme protection for your bed."), itemId, isQuickBuy);
            case "STONE_SWORD" -> shopItem(player, Material.STONE_SWORD,
                    1, ChatColor.GRAY + "Stone Sword", 10, Material.IRON_INGOT,
                    List.of(), itemId, isQuickBuy);
            case "IRON_SWORD" -> shopItem(player, Material.IRON_SWORD,
                    1, ChatColor.WHITE + "Iron Sword", 7, Material.GOLD_INGOT,
                    List.of(), itemId, isQuickBuy);
            case "DIAMOND_SWORD" -> shopItem(player, Material.DIAMOND_SWORD,
                    1, ChatColor.AQUA + "Diamond Sword", 4, Material.EMERALD,
                    List.of(), itemId, isQuickBuy);
            case "KNOCKBACK_STICK" -> shopActionItem(player, Material.STICK,
                    1, ChatColor.GOLD + "Knockback Stick", 5, Material.GOLD_INGOT,
                    List.of("Stick enchanted with Knockback I.", "Good for knocking players into the void."),
                    itemId, isQuickBuy, true);
            case "CHAINMAIL_ARMOR" -> shopItem(player, Material.CHAINMAIL_BOOTS,
                    1, ChatColor.GRAY + "Permanent Chainmail Armor", 24, Material.IRON_INGOT,
                    List.of("Chainmail leggings and boots", "which you will always spawn with."), itemId, isQuickBuy);
            case "IRON_ARMOR" -> shopItem(player, Material.IRON_BOOTS,
                    1, ChatColor.WHITE + "Permanent Iron Armor", 12, Material.GOLD_INGOT,
                    List.of("Iron leggings and boots which", "you will always spawn with."), itemId, isQuickBuy);
            case "DIAMOND_ARMOR" -> shopItem(player, Material.DIAMOND_BOOTS,
                    1, ChatColor.AQUA + "Permanent Diamond Armor", 6, Material.EMERALD,
                    List.of("Diamond leggings and boots which", "you will always spawn with."), itemId, isQuickBuy);
            case "SHEARS" -> shopItem(player, Material.SHEARS,
                    1, ChatColor.AQUA + "Permanent Shears", 20, Material.IRON_INGOT,
                    List.of("Great to get rid of wool.", "You will always spawn with these shears."), itemId, isQuickBuy);
            case "WOODEN_PICKAXE" -> toolShopItem(player, Material.WOODEN_PICKAXE,
                    ChatColor.GOLD + "Wooden Pickaxe", 10, Material.IRON_INGOT,
                    List.of("Efficiency I"), "WOODEN_PICKAXE", 1, isQuickBuy);
            case "IRON_PICKAXE" -> toolShopItem(player, Material.IRON_PICKAXE,
                    ChatColor.WHITE + "Iron Pickaxe", 10, Material.IRON_INGOT,
                    List.of("Efficiency II"), "IRON_PICKAXE", 2, isQuickBuy);
            case "GOLD_PICKAXE" -> toolShopItem(player, Material.GOLDEN_PICKAXE,
                    ChatColor.GOLD + "Golden Pickaxe", 3, Material.GOLD_INGOT,
                    List.of("Efficiency III, Sharpness II"), "GOLD_PICKAXE", 3, isQuickBuy);
            case "DIAMOND_PICKAXE" -> toolShopItem(player, Material.DIAMOND_PICKAXE,
                    ChatColor.AQUA + "Diamond Pickaxe", 6, Material.GOLD_INGOT,
                    List.of("Efficiency III"), "DIAMOND_PICKAXE", 4, isQuickBuy);
            case "WOODEN_AXE" -> toolShopItem(player, Material.WOODEN_AXE,
                    ChatColor.GOLD + "Wooden Axe", 10, Material.IRON_INGOT,
                    List.of("Efficiency I"), "WOODEN_AXE", 1, isQuickBuy);
            case "STONE_AXE" -> toolShopItem(player, Material.STONE_AXE,
                    ChatColor.GRAY + "Stone Axe", 10, Material.IRON_INGOT,
                    List.of("Efficiency I"), "STONE_AXE", 2, isQuickBuy);
            case "IRON_AXE" -> toolShopItem(player, Material.IRON_AXE,
                    ChatColor.WHITE + "Iron Axe", 3, Material.GOLD_INGOT,
                    List.of("Efficiency II"), "IRON_AXE", 3, isQuickBuy);
            case "DIAMOND_AXE" -> toolShopItem(player, Material.DIAMOND_AXE,
                    ChatColor.AQUA + "Diamond Axe", 6, Material.GOLD_INGOT,
                    List.of("Efficiency III"), "DIAMOND_AXE", 4, isQuickBuy);
            case "BOW" -> shopItem(player, Material.BOW,
                    1, ChatColor.YELLOW + "Bow", 12, Material.GOLD_INGOT,
                    List.of(), itemId, isQuickBuy);
            case "POWER_BOW" -> shopActionItem(player, Material.BOW,
                    1, ChatColor.YELLOW + "Bow (Power I)", 20, Material.GOLD_INGOT,
                    List.of("Bow enchanted with Power I.", "Requires arrows."), itemId, isQuickBuy, true);
            case "PUNCH_BOW" -> shopActionItem(player, Material.BOW,
                    1, ChatColor.LIGHT_PURPLE + "Bow (Power I, Punch I)", 6, Material.EMERALD,
                    List.of("Bow enchanted with Power I", "and Punch I. Requires arrows."), itemId, isQuickBuy, true);
            case "ARROWS" -> shopItem(player, Material.ARROW,
                    6, ChatColor.WHITE + "Arrows", 2, Material.GOLD_INGOT,
                    List.of(), itemId, isQuickBuy);
            case "SPEED_POTION" -> {
                ItemStack item = shopActionItem(player, Material.POTION,
                        1, ChatColor.AQUA + "Speed II Potion (45 sec)", 1, Material.EMERALD,
                        List.of("Grants Speed II for 45 seconds."), itemId, isQuickBuy, false);
                setPotionShopIcon(item, PotionType.SWIFTNESS);
                yield item;
            }
            case "JUMP_POTION" -> {
                ItemStack item = shopActionItem(player, Material.POTION,
                        1, ChatColor.GREEN + "Jump V Potion (45 sec)", 1, Material.EMERALD,
                        List.of("Grants Jump Boost V for 45 seconds."), itemId, isQuickBuy, false);
                setPotionShopIcon(item, PotionType.LEAPING);
                yield item;
            }
            case "INVISIBILITY_POTION" -> {
                ItemStack item = shopActionItem(player, Material.POTION,
                        1, ChatColor.GRAY + "Invisibility Potion (30 sec)", 2, Material.EMERALD,
                        List.of("Grants Invisibility for 30 seconds."), itemId, isQuickBuy, false);
                setPotionShopIcon(item, PotionType.INVISIBILITY);
                yield item;
            }
            case "GOLDEN_APPLE" -> shopItem(player, Material.GOLDEN_APPLE,
                    1, ChatColor.GOLD + "Golden Apple", 3, Material.GOLD_INGOT,
                    List.of(), itemId, isQuickBuy);
            case "BED_BUG" -> shopItem(player, Material.SNOWBALL,
                    1, ChatColor.GRAY + "Bed Bug", 24, Material.IRON_INGOT,
                    List.of("Spawns a Silverfish where the", "snowball lands to distract",
                            "and attack enemies."), itemId, isQuickBuy);
            case "DREAM_DEFENDER" -> shopItem(player, Material.IRON_GOLEM_SPAWN_EGG,
                    1, ChatColor.WHITE + "Dream Defender", 120, Material.IRON_INGOT,
                    List.of("Spawns an Iron Golem to help", "defend your base."), itemId, isQuickBuy);
            case "FIREBALL" -> shopActionItem(player, Material.FIRE_CHARGE,
                    1, ChatColor.RED + "Fireball", 40, Material.IRON_INGOT,
                    List.of("Right-click to launch!", "Explodes on impact and knocks", "players back."),
                    itemId, isQuickBuy, true);
            case "TNT" -> shopItem(player, Material.TNT,
                    1, ChatColor.RED + "TNT", 4, Material.GOLD_INGOT,
                    List.of("Instantly ignites when placed.", "Useful for breaking bed defenses."), itemId, isQuickBuy);
            case "ENDER_PEARL" -> shopItem(player, Material.ENDER_PEARL,
                    1, ChatColor.DARK_PURPLE + "Ender Pearl", 4, Material.EMERALD,
                    List.of(), itemId, isQuickBuy);
            case "WATER_BUCKET" -> shopItem(player, Material.WATER_BUCKET,
                    1, ChatColor.BLUE + "Water Bucket", 2, Material.GOLD_INGOT,
                    List.of(), itemId, isQuickBuy);
            case "BRIDGE_EGG" -> shopItem(player, Material.EGG,
                    1, ChatColor.WHITE + "Bridge Egg", 1, Material.EMERALD,
                    List.of("Creates a bridge in its trail."), itemId, isQuickBuy);
            case "MAGIC_MILK" -> shopItem(player, Material.MILK_BUCKET,
                    1, ChatColor.WHITE + "Magic Milk", 4, Material.GOLD_INGOT,
                    List.of("Avoid triggering traps for", "30 seconds after drinking."), itemId, isQuickBuy);
            case "SPONGE" -> shopItem(player, Material.SPONGE,
                    4, ChatColor.YELLOW + "Sponge", 2, Material.GOLD_INGOT,
                    List.of("Removes nearby water when placed."), itemId, isQuickBuy);
            case "POPUP_TOWER" -> shopItem(player, Material.CHEST,
                    1, ChatColor.GREEN + "Compact Pop-up Tower", 24, Material.IRON_INGOT,
                    List.of("Place a compact pop-up tower."), itemId, isQuickBuy);
            default -> new ItemStack(Material.BARRIER);
        };
    }

    private ItemStack shopItem(Player player, Material material, int amount, String name, int cost, Material currency,
                               List<String> description, String itemId, boolean isQuickBuy) {
        return shopActionItem(player, material, amount, name, cost, currency, description, itemId, isQuickBuy, false);
    }

    private ItemStack shopActionItem(Player player, Material material, int amount, String name, int cost, Material currency,
                                     List<String> description, String itemId, boolean isQuickBuy, boolean enchanted) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean canAfford = countMaterial(player, currency) >= cost;
            boolean alreadyInQB = !isQuickBuy && itemId != null && !"EMPTY".equals(itemId)
                    && quickBuySlots.computeIfAbsent(player.getUniqueId(), uuid -> defaultQuickBuyLayout()).contains(itemId);
            // Name color: GREEN if affordable, RED if not
            String stripped = ChatColor.stripColor(name);
            meta.setDisplayName(canAfford ? (ChatColor.GREEN + stripped) : (ChatColor.RED + stripped));
            List<String> lore = new ArrayList<>();
            // Colorized cost line
            ChatColor costColor = switch (currency) {
                case IRON_INGOT -> ChatColor.WHITE;
                case GOLD_INGOT -> ChatColor.GOLD;
                case EMERALD -> ChatColor.DARK_GREEN;
                case DIAMOND -> ChatColor.AQUA;
                default -> ChatColor.GRAY;
            };
            lore.add(ChatColor.GRAY + "Cost: " + costColor + cost + " " + readableMaterial(currency));
            lore.add("");
            // Description lines in LIGHT_GRAY
            for (String desc : description) {
                lore.add(ChatColor.GRAY + desc);
            }
            lore.add("");
            // Action text
            if (canAfford) {
                lore.add(ChatColor.YELLOW + "Click to purchase!");
            } else {
                lore.add(ChatColor.RED + "You don't have enough " + readableMaterial(currency) + "s!");
            }
            if (isQuickBuy) {
                lore.add(ChatColor.AQUA + "Sneak Click to remove from Quick Buy!");
            } else if (!alreadyInQB) {
                lore.add(ChatColor.AQUA + "Sneak Click to add to Quick Buy!");
            }
            meta.setLore(lore);
            if (enchanted) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            meta.getPersistentDataContainer().set(shopRewardKey, PersistentDataType.STRING, itemId);
            meta.getPersistentDataContainer().set(shopCostKey, PersistentDataType.INTEGER, cost);
            meta.getPersistentDataContainer().set(shopCurrencyKey, PersistentDataType.STRING, currency.name());
            meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, itemId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack toolShopItem(Player player, Material material, String name, int cost, Material currency,
                                   List<String> enchants, String itemId, int tier, boolean isQuickBuy) {
        int currentTier = isPickaxeId(itemId) ? bedWarsPickaxeTiers.getOrDefault(player.getUniqueId(), 0)
                : bedWarsAxeTiers.getOrDefault(player.getUniqueId(), 0);
        if (currentTier >= tier) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + ChatColor.stripColor(name) + " (Purchased)");
                meta.setLore(List.of(ChatColor.GREEN + "You already own this tier or higher."));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            return item;
        }
        if (currentTier + 1 < tier) {
            return infoItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "Locked", "Buy previous tier first.");
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Cost: " + cost + " " + readableMaterial(currency));
        lore.add("");
        lore.addAll(enchants);
        lore.add("");
        lore.add(ChatColor.GRAY + "This is an upgradable item.");
        lore.add(ChatColor.GRAY + "It will lose 1 tier upon death!");
        lore.add("");
        lore.add(ChatColor.GRAY + "You will permanently respawn");
        lore.add(ChatColor.GRAY + "with at least the lowest tier.");
        lore.add("");
        if (isQuickBuy) {
            lore.add(ChatColor.YELLOW + "Click to purchase!");
            lore.add(ChatColor.YELLOW + "Sneak Click to remove from Quick Buy!");
        } else {
            lore.add(ChatColor.YELLOW + "Click to purchase!");
            lore.add(ChatColor.YELLOW + "Sneak Click to add to Quick Buy!");
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(shopRewardKey, PersistentDataType.STRING, itemId);
            meta.getPersistentDataContainer().set(shopCostKey, PersistentDataType.INTEGER, cost);
            meta.getPersistentDataContainer().set(shopCurrencyKey, PersistentDataType.STRING, currency.name());
            meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, itemId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isPickaxeId(String id) {
        return id != null && id.contains("PICKAXE");
    }

    private Material teamWool(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> Material.RED_WOOL;
            case "2", "blue" -> Material.BLUE_WOOL;
            case "3", "green" -> Material.GREEN_WOOL;
            case "4", "yellow" -> Material.YELLOW_WOOL;
            case "5", "orange" -> Material.ORANGE_WOOL;
            case "6", "purple" -> Material.PURPLE_WOOL;
            case "7", "cyan" -> Material.CYAN_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    private Material teamConcrete(String team) {
        return switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> Material.RED_CONCRETE;
            case "2", "blue" -> Material.BLUE_CONCRETE;
            case "3", "green" -> Material.GREEN_CONCRETE;
            case "4", "yellow" -> Material.YELLOW_CONCRETE;
            case "5", "orange" -> Material.ORANGE_CONCRETE;
            case "6", "purple" -> Material.PURPLE_CONCRETE;
            case "7", "cyan" -> Material.CYAN_CONCRETE;
            default -> Material.WHITE_CONCRETE;
        };
    }

    private Inventory createBedWarsUpgradeShop(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, BEDWARS_UPGRADE_SHOP_TITLE);
        // Upgrades on the left
        inventory.setItem(10, upgradeItem(player, Material.IRON_SWORD, ChatColor.AQUA + "Sharpened Swords", "SHARPNESS"));
        inventory.setItem(11, upgradeItem(player, Material.IRON_CHESTPLATE, ChatColor.GREEN + "Reinforced Armor", "PROTECTION"));
        inventory.setItem(12, upgradeItem(player, Material.GOLDEN_PICKAXE, ChatColor.YELLOW + "Maniac Miner", "HASTE"));
        inventory.setItem(19, upgradeItem(player, Material.FURNACE, ChatColor.GOLD + "Forge", "FORGE"));
        inventory.setItem(20, upgradeItem(player, Material.BEACON, ChatColor.LIGHT_PURPLE + "Heal Pool", "HEAL_POOL"));
        inventory.setItem(21, upgradeItem(player, Material.DIAMOND_BOOTS, ChatColor.WHITE + "Cushioned Boots", "CUSHIONED_BOOTS"));
        // Traps on the right
        inventory.setItem(14, upgradeItem(player, Material.TRIPWIRE_HOOK, ChatColor.RED + "Blindness Trap", "TRAP_BLIND"));
        inventory.setItem(15, upgradeItem(player, Material.IRON_PICKAXE, ChatColor.GRAY + "Miner Fatigue Trap", "TRAP_MINER"));
        inventory.setItem(16, upgradeItem(player, Material.FEATHER, ChatColor.GREEN + "Counter-Offensive Trap", "TRAP_COUNTER"));
        inventory.setItem(23, upgradeItem(player, Material.REDSTONE_TORCH, ChatColor.AQUA + "Reveal Trap", "TRAP_REVEAL"));
        // Filler/divider row
        for (int slot = 27; slot <= 35; slot++)
            inventory.setItem(slot, infoItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        // Trap queue display
        String team = eventManager.teamFor(player.getUniqueId());
        List<String> traps = bedWarsTraps.getOrDefault(team, List.of());
        for (int i = 0; i < 3; i++) {
            int slot = 39 + i;
            if (i < traps.size()) {
                String trap = traps.get(i);
                inventory.setItem(slot, trapQueueItem(trap, i));
            } else {
                inventory.setItem(slot, infoItemLines(Material.LIGHT_GRAY_STAINED_GLASS,
                        ChatColor.GRAY + "Trap #" + (i + 1) + ": No Trap!",
                        "The first enemy to walk into your base triggers this trap.",
                        "Purchasing traps queues them here.",
                        "Cost depends on number of queued traps.",
                        ChatColor.AQUA + "Next trap: " + bedWarsUpgradeCost(team, "TRAP_BLIND") + " Diamond"));
            }
        }
        return inventory;
    }

    private ItemStack trapQueueItem(String trap, int index) {
        Material icon = switch (trap) {
            case "TRAP_BLIND" -> Material.TRIPWIRE_HOOK;
            case "TRAP_MINER" -> Material.IRON_PICKAXE;
            case "TRAP_COUNTER" -> Material.FEATHER;
            case "TRAP_REVEAL" -> Material.REDSTONE_TORCH;
            default -> Material.BARRIER;
        };
        String name = switch (trap) {
            case "TRAP_BLIND" -> ChatColor.RED + "Blindness Trap";
            case "TRAP_MINER" -> ChatColor.GRAY + "Miner Fatigue Trap";
            case "TRAP_COUNTER" -> ChatColor.GREEN + "Counter-Offensive Trap";
            case "TRAP_REVEAL" -> ChatColor.AQUA + "Reveal Trap";
            default -> ChatColor.RED + "Unknown Trap";
        };
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Queued trap #" + (index + 1),
                    ChatColor.YELLOW + "Shift-Click to remove"
            ));
            meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, "TRAP_REMOVE:" + trap + ":" + index);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack upgradeItem(Player player, Material material, String name, String action) {
        String team = eventManager.teamFor(player.getUniqueId());
        int cost = bedWarsUpgradeCost(team, action);
        int level = getUpgradeLevel(team, action);
        int maxLevel = switch (action) {
            case "SHARPNESS" -> 1;
            case "PROTECTION" -> 4;
            case "HASTE" -> 2;
            case "FORGE" -> 4;
            case "TRAP_BLIND", "TRAP_MINER", "TRAP_COUNTER", "TRAP_REVEAL" -> 3;
            case "HEAL_POOL" -> 1;
            case "CUSHIONED_BOOTS" -> 2;
            default -> 1;
        };
        boolean canAfford = countMaterial(player, Material.DIAMOND) >= cost;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + buildUpgradeDescription(action));

        // Tier list
        lore.add("");
        for (int i = 1; i <= maxLevel; i++) {
            String tierLine;
            if (i <= level) {
                tierLine = ChatColor.GREEN + "Tier " + toRoman(i) + ": " + tierDescription(action, i) + " " + ChatColor.GREEN + "\u2714";
            } else {
                tierLine = ChatColor.GRAY + "Tier " + toRoman(i) + ": " + tierDescription(action, i);
            }
            lore.add(tierLine);
        }

        lore.add("");
        if (level >= maxLevel) {
            lore.add(ChatColor.GREEN + "Maximum level reached.");
        } else {
            lore.add(ChatColor.AQUA + "Cost: " + cost + " Diamond" + (cost != 1 ? "s" : ""));
            lore.add("");
            if (canAfford) {
                lore.add(ChatColor.YELLOW + "Click to purchase!");
            } else {
                lore.add(ChatColor.RED + "You don't have enough Diamonds!");
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((level >= maxLevel ? ChatColor.GREEN : canAfford ? ChatColor.GREEN : ChatColor.RED) + ChatColor.stripColor(name));
            meta.setLore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(shopActionKey, PersistentDataType.STRING, action);
            meta.getPersistentDataContainer().set(shopCostKey, PersistentDataType.INTEGER, cost);
            meta.getPersistentDataContainer().set(shopCurrencyKey, PersistentDataType.STRING, "DIAMOND");
            item.setItemMeta(meta);
        }
        return item;
    }

    private String buildUpgradeDescription(String action) {
        return switch (action) {
            case "SHARPNESS" -> "Grants Sharpness I to your team's swords and axes.";
            case "PROTECTION" -> "Grants Protection to your team's armor.";
            case "HASTE" -> "Grants Haste (faster mining) to your team.";
            case "FORGE" -> "Upgrades your team's resource generator.";
            case "TRAP_BLIND" -> "Blinds and slows the first enemy to enter your base.";
            case "TRAP_MINER" -> "Gives Mining Fatigue to the first enemy to enter your base.";
            case "TRAP_COUNTER" -> "Grants your team Speed II and Jump Boost on trap activation.";
            case "TRAP_REVEAL" -> "Reveals invisible enemies who enter your base.";
            case "HEAL_POOL" -> "Grants Regeneration I to allies near your base.";
            case "CUSHIONED_BOOTS" -> "Grants Feather Falling to your team's boots.";
            default -> "Team upgrade.";
        };
    }

    private String tierDescription(String action, int tier) {
        return switch (action) {
            case "SHARPNESS" -> "Sharpness I, " + (tier == 1 ? "4" : "-") + " Diamonds";
            case "PROTECTION" -> "Protection " + toRoman(tier) + ", " + switch (tier) { case 1 -> "2"; case 2 -> "4"; case 3 -> "8"; case 4 -> "16"; default -> "?"; } + " Diamonds";
            case "HASTE" -> "Haste " + toRoman(tier) + ", " + switch (tier) { case 1 -> "2"; case 2 -> "4"; default -> "?"; } + " Diamonds";
            case "FORGE" -> switch (tier) {
                case 1 -> "Iron Forge, 2 Diamonds";
                case 2 -> "Gold Forge, 4 Diamonds";
                case 3 -> "Emerald Forge, 6 Diamonds";
                case 4 -> "Molten Forge, 8 Diamonds";
                default -> "Forge " + tier;
            };
            case "TRAP_BLIND", "TRAP_MINER", "TRAP_COUNTER", "TRAP_REVEAL" -> "Trap " + tier + ", " + switch (tier) { case 1 -> "1"; case 2 -> "2"; case 3 -> "4"; default -> "?"; } + " Diamonds";
            case "HEAL_POOL" -> "Heal Pool, 1 Diamond";
            case "CUSHIONED_BOOTS" -> "Feather Falling " + toRoman(tier) + ", " + switch (tier) { case 1 -> "1"; case 2 -> "2"; default -> "?"; } + " Diamonds";
            default -> "Tier " + tier;
        };
    }

    private int getUpgradeLevel(String team, String action) {
        return switch (action) {
            case "SHARPNESS" -> bedWarsSharpnessTeams.contains(team) ? 1 : 0;
            case "PROTECTION" -> bedWarsProtectionLevels.getOrDefault(team, 0);
            case "HASTE" -> bedWarsHasteLevels.getOrDefault(team, 0);
            case "FORGE" -> bedWarsForgeLevels.getOrDefault(team, 0);
            case "TRAP_BLIND", "TRAP_MINER", "TRAP_COUNTER", "TRAP_REVEAL" -> bedWarsTraps.getOrDefault(team, List.of()).size();
            case "HEAL_POOL" -> bedWarsHealPoolTeams.contains(team) ? 1 : 0;
            case "CUSHIONED_BOOTS" -> bedWarsFeatherFallingLevels.getOrDefault(team, 0);
            default -> 0;
        };
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private ItemStack infoItem(Material material, String name, String description) {
        return infoItemLines(material, name, List.of(description));
    }

    private ItemStack infoItemLines(Material material, String name, String... descriptions) {
        return infoItemLines(material, name, List.of(descriptions));
    }

    private ItemStack infoItemLines(Material material, String name, List<String> descriptions) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(descriptions.stream().map(d -> ChatColor.GRAY + d).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void buyBedWarsShopItem(Player player, ItemStack shopEntry) {
        ItemMeta meta = shopEntry.getItemMeta();
        if (meta == null) {
            return;
        }
        String itemId = meta.getPersistentDataContainer().get(shopActionKey, PersistentDataType.STRING);
        Integer cost = meta.getPersistentDataContainer().get(shopCostKey, PersistentDataType.INTEGER);
        String currencyName = meta.getPersistentDataContainer().get(shopCurrencyKey, PersistentDataType.STRING);
        if (itemId == null || cost == null || currencyName == null || "EMPTY".equals(itemId) || "PAGE:".equals(itemId)) {
            return;
        }
        Material currency = Material.matchMaterial(currencyName);
        if (currency == null) {
            return;
        }
        if (countMaterial(player, currency) < cost) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You need " + cost + " " + readableMaterial(currency) + ".");
            return;
        }

        // Handle special item types
        if (isArmorItem(itemId)) {
            int tier = armorTierFromId(itemId);
            int current = bedWarsArmorTiers.getOrDefault(player.getUniqueId(), 0);
            if (tier <= current) {
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You already own this armor or better.");
                return;
            }
            removeCurrency(player, currency, cost);
            bedWarsArmorTiers.put(player.getUniqueId(), tier);
            applyBedWarsArmor(player);
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Purchased " + readableArmor(itemId) + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
            player.openInventory(createBedWarsItemShop(player));
            return;
        }

        if ("SHEARS".equals(itemId)) {
            if (Boolean.TRUE.equals(bedWarsHasShears.get(player.getUniqueId()))) {
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You already own permanent shears.");
                return;
            }
            removeCurrency(player, currency, cost);
            bedWarsHasShears.put(player.getUniqueId(), true);
            ItemStack shears = new ItemStack(Material.SHEARS);
            applyBedWarsItemUpgrades(player, shears);
            player.getInventory().addItem(shears);
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Purchased Permanent Shears.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
            player.openInventory(createBedWarsItemShop(player));
            return;
        }

        if (isToolItem(itemId)) {
            int tier = toolTierFromId(itemId);
            boolean isPickaxe = isPickaxeId(itemId);
            int currentTier = isPickaxe ? bedWarsPickaxeTiers.getOrDefault(player.getUniqueId(), 0)
                    : bedWarsAxeTiers.getOrDefault(player.getUniqueId(), 0);
            if (tier <= currentTier) {
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You already own this tool tier or higher.");
                return;
            }
            if (tier > 1 && currentTier + 1 < tier) {
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You must buy the previous tier first.");
                return;
            }
            removeCurrency(player, currency, cost);
            if (isPickaxe) {
                removeInventoryPickaxe(player);
                bedWarsPickaxeTiers.put(player.getUniqueId(), tier);
            } else {
                removeInventoryAxe(player);
                bedWarsAxeTiers.put(player.getUniqueId(), tier);
            }
            ItemStack tool = createBedWarsReward(player, shopEntry.getType(), 1, itemId);
            player.getInventory().addItem(tool);
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Purchased " + readableItem(itemId) + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
            player.openInventory(createBedWarsItemShop(player));
            return;
        }

        // Generic shop item
        ItemStack rewardItem = createBedWarsReward(player, shopEntry.getType(), shopEntry.getAmount(), itemId);
        if (rewardItem == null || rewardItem.getType().isAir()) {
            // Items like potions create their own reward
            removeCurrency(player, currency, cost);
            createAndDeliverReward(player, itemId, shopEntry.getAmount());
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Purchased " + readableItem(itemId) + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "Your inventory is full.");
            return;
        }
        // Replace wooden sword if buying a better sword
        if (itemId != null && (itemId.equals("STONE_SWORD") || itemId.equals("IRON_SWORD") || itemId.equals("DIAMOND_SWORD"))) {
            removeInventoryWoodenSword(player);
        }
        removeCurrency(player, currency, cost);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewardItem);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Purchased " + readableItem(itemId) + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
    }

    private boolean isArmorItem(String itemId) {
        return "CHAINMAIL_ARMOR".equals(itemId) || "IRON_ARMOR".equals(itemId) || "DIAMOND_ARMOR".equals(itemId);
    }

    private int armorTierFromId(String itemId) {
        return switch (itemId) {
            case "CHAINMAIL_ARMOR" -> 1;
            case "IRON_ARMOR" -> 2;
            case "DIAMOND_ARMOR" -> 3;
            default -> 0;
        };
    }

    private boolean isToolItem(String itemId) {
        return itemId != null && (itemId.endsWith("_PICKAXE") || itemId.endsWith("_AXE"));
    }

    private int toolTierFromId(String itemId) {
        return switch (itemId) {
            case "WOODEN_PICKAXE", "WOODEN_AXE" -> 1;
            case "IRON_PICKAXE", "STONE_AXE" -> 2;
            case "GOLD_PICKAXE", "IRON_AXE" -> 3;
            case "DIAMOND_PICKAXE", "DIAMOND_AXE" -> 4;
            default -> 0;
        };
    }

    private void removeInventoryPickaxe(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().endsWith("_PICKAXE")) {
                item.setAmount(0);
            }
        }
    }

    private void removeInventoryAxe(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().endsWith("_AXE")) {
                item.setAmount(0);
            }
        }
    }

    private void removeInventoryWoodenSword(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WOODEN_SWORD) {
                item.setAmount(0);
                return;
            }
        }
    }

    private void createAndDeliverReward(Player player, String itemId, int amount) {
        ItemStack reward = createBedWarsReward(player, Material.AIR, amount, itemId);
        if (reward != null && !reward.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private String readableItem(String itemId) {
        return switch (itemId) {
            case "WOOL" -> "Wool";
            case "HARDENED_CLAY" -> "Hardened Clay";
            case "BLAST_PROOF_GLASS" -> "Blast-Proof Glass";
            case "END_STONE" -> "End Stone";
            case "LADDER" -> "Ladder";
            case "OAK_PLANKS" -> "Wood Planks";
            case "OBSIDIAN" -> "Obsidian";
            case "STONE_SWORD" -> "Stone Sword";
            case "IRON_SWORD" -> "Iron Sword";
            case "DIAMOND_SWORD" -> "Diamond Sword";
            case "KNOCKBACK_STICK" -> "Knockback Stick";
            case "CHAINMAIL_ARMOR" -> "Chainmail Armor";
            case "IRON_ARMOR" -> "Iron Armor";
            case "DIAMOND_ARMOR" -> "Diamond Armor";
            case "SHEARS" -> "Shears";
            case "WOODEN_PICKAXE" -> "Wooden Pickaxe";
            case "IRON_PICKAXE" -> "Iron Pickaxe";
            case "GOLD_PICKAXE" -> "Golden Pickaxe";
            case "DIAMOND_PICKAXE" -> "Diamond Pickaxe";
            case "WOODEN_AXE" -> "Wooden Axe";
            case "STONE_AXE" -> "Stone Axe";
            case "IRON_AXE" -> "Iron Axe";
            case "DIAMOND_AXE" -> "Diamond Axe";
            case "BOW" -> "Bow";
            case "POWER_BOW" -> "Bow (Power I)";
            case "PUNCH_BOW" -> "Bow (Power I, Punch I)";
            case "ARROWS" -> "Arrows";
            case "SPEED_POTION" -> "Speed II Potion";
            case "JUMP_POTION" -> "Jump V Potion";
            case "INVISIBILITY_POTION" -> "Invisibility Potion";
            case "GOLDEN_APPLE" -> "Golden Apple";
            case "BED_BUG" -> "Bed Bug";
            case "DREAM_DEFENDER" -> "Dream Defender";
            case "FIREBALL" -> "Fireball";
            case "TNT" -> "TNT";
            case "ENDER_PEARL" -> "Ender Pearl";
            case "WATER_BUCKET" -> "Water Bucket";
            case "BRIDGE_EGG" -> "Bridge Egg";
            case "MAGIC_MILK" -> "Magic Milk";
            case "SPONGE" -> "Sponge";
            case "POPUP_TOWER" -> "Pop-up Tower";
            default -> itemId;
        };
    }

    private String readableArmor(String itemId) {
        return switch (itemId) {
            case "CHAINMAIL_ARMOR" -> "Chainmail Armor";
            case "IRON_ARMOR" -> "Iron Armor";
            case "DIAMOND_ARMOR" -> "Diamond Armor";
            default -> itemId;
        };
    }

    private ItemStack createBedWarsReward(Player player, Material material, int amount, String action) {
        ItemStack item = new ItemStack(material, amount);
        if (action == null) {
            return item;
        }
        switch (action) {
            case "KNOCKBACK_STICK" -> {
                item.setType(Material.STICK);
                item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
            }
            case "POWER_BOW" -> {
                item.setType(Material.BOW);
                item.addUnsafeEnchantment(Enchantment.POWER, 1);
            }
            case "PUNCH_BOW" -> {
                item.setType(Material.BOW);
                item.addUnsafeEnchantment(Enchantment.POWER, 1);
                item.addUnsafeEnchantment(Enchantment.PUNCH, 1);
            }
            case "SPEED_POTION" -> {
                ItemStack potion = new ItemStack(Material.POTION);
                if (potion.getItemMeta() instanceof PotionMeta pm) {
                    pm.setBasePotionType(PotionType.SWIFTNESS);
                    pm.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 45 * 20, 1), true);
                    pm.setDisplayName(ChatColor.AQUA + "Speed II Potion (45 sec)");
                    pm.setLore(List.of());
                    potion.setItemMeta(pm);
                }
                return potion;
            }
            case "JUMP_POTION" -> {
                ItemStack potion = new ItemStack(Material.POTION);
                if (potion.getItemMeta() instanceof PotionMeta pm) {
                    pm.setBasePotionType(PotionType.LEAPING);
                    pm.addCustomEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 45 * 20, 4), true);
                    pm.setDisplayName(ChatColor.GREEN + "Jump V Potion (45 sec)");
                    pm.setLore(List.of());
                    potion.setItemMeta(pm);
                }
                return potion;
            }
            case "INVISIBILITY_POTION" -> {
                ItemStack potion = new ItemStack(Material.POTION);
                if (potion.getItemMeta() instanceof PotionMeta pm) {
                    pm.setBasePotionType(PotionType.INVISIBILITY);
                    pm.addCustomEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30 * 20, 0), true);
                    pm.setDisplayName(ChatColor.GRAY + "Invisibility Potion (30 sec)");
                    pm.setLore(List.of());
                    potion.setItemMeta(pm);
                }
                return potion;
            }
            case "WOODEN_PICKAXE" -> {
                item.setType(Material.WOODEN_PICKAXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
            }
            case "IRON_PICKAXE" -> {
                item.setType(Material.IRON_PICKAXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 2);
            }
            case "GOLD_PICKAXE" -> {
                item.setType(Material.GOLDEN_PICKAXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
            }
            case "DIAMOND_PICKAXE" -> {
                item.setType(Material.DIAMOND_PICKAXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
            }
            case "WOODEN_AXE" -> {
                item.setType(Material.WOODEN_AXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
            }
            case "STONE_AXE" -> {
                item.setType(Material.STONE_AXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
            }
            case "IRON_AXE" -> {
                item.setType(Material.IRON_AXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 2);
            }
            case "DIAMOND_AXE" -> {
                item.setType(Material.DIAMOND_AXE);
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
            }
            case "FIREBALL" -> {
                item.setType(Material.FIRE_CHARGE);
                item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
            }
            default -> {}
        }
        applyBedWarsItemUpgrades(player, item);
        return item;
    }

    private void addPotionEffect(ItemStack item, PotionEffectType type, int seconds, int amplifier) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return;
        }
        meta.addCustomEffect(new PotionEffect(type, seconds * 20, amplifier), true);
        item.setItemMeta(meta);
    }

    private void setPotionShopIcon(ItemStack item, PotionType potionType) {
        if (item.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(potionType);
            item.setItemMeta(meta);
        }
    }

    private void buyBedWarsUpgrade(Player player, ItemStack shopEntry) {
        ItemMeta meta = shopEntry.getItemMeta();
        if (meta == null) {
            return;
        }
        String action = meta.getPersistentDataContainer().get(shopActionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        EventSession session = eventManager.session();
        String team = session == null ? null : session.teams().get(player.getUniqueId());
        if (team == null) {
            return;
        }
        int cost = bedWarsUpgradeCost(team, action);
        if (cost < 0) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "That upgrade is already at its maximum level.");
            return;
        }
        if (countMaterial(player, Material.DIAMOND) < cost) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You need " + cost + " diamonds.");
            return;
        }
        removeCurrency(player, Material.DIAMOND, cost);
        applyBedWarsUpgrade(team, action);
        player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Purchased " + readableUpgrade(action) + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.5F);
        player.openInventory(createBedWarsUpgradeShop(player));
    }

    private int bedWarsUpgradeCost(String team, String action) {
        return switch (action) {
            case "SHARPNESS" -> bedWarsSharpnessTeams.contains(team) ? -1 : 4;
            case "PROTECTION" -> nextTierCost(bedWarsProtectionLevels.getOrDefault(team, 0), 2, 4, 8, 16);
            case "HASTE" -> nextTierCost(bedWarsHasteLevels.getOrDefault(team, 0), 2, 4);
            case "FORGE" -> nextTierCost(bedWarsForgeLevels.getOrDefault(team, 0), 2, 4, 6, 8);
            case "TRAP_BLIND", "TRAP_MINER", "TRAP_COUNTER", "TRAP_REVEAL" ->
                    nextTierCost(bedWarsTraps.getOrDefault(team, List.of()).size(), 1, 2, 4);
            case "HEAL_POOL" -> bedWarsHealPoolTeams.contains(team) ? -1 : 1;
            case "CUSHIONED_BOOTS" -> nextTierCost(bedWarsFeatherFallingLevels.getOrDefault(team, 0), 1, 2);
            default -> -1;
        };
    }

    private int nextTierCost(int currentLevel, int... costs) {
        return currentLevel >= costs.length ? -1 : costs[currentLevel];
    }

    private void applyBedWarsUpgrade(String team, String action) {
        switch (action) {
            case "SHARPNESS" -> bedWarsSharpnessTeams.add(team);
            case "PROTECTION" -> bedWarsProtectionLevels.merge(team, 1, Integer::sum);
            case "HASTE" -> bedWarsHasteLevels.merge(team, 1, Integer::sum);
            case "FORGE" -> bedWarsForgeLevels.merge(team, 1, Integer::sum);
            case "TRAP_BLIND", "TRAP_MINER", "TRAP_COUNTER", "TRAP_REVEAL" ->
                    bedWarsTraps.computeIfAbsent(team, ignored -> new ArrayList<>()).add(action);
            case "HEAL_POOL" -> bedWarsHealPoolTeams.add(team);
            case "CUSHIONED_BOOTS" -> bedWarsFeatherFallingLevels.merge(team, 1, Integer::sum);
            default -> {
            }
        }
    }

    private String readableUpgrade(String action) {
        return switch (action) {
            case "SHARPNESS" -> "Sharpened Swords";
            case "PROTECTION" -> "Reinforced Armor";
            case "HASTE" -> "Maniac Miner";
            case "FORGE" -> "Forge Upgrade";
            case "TRAP_BLIND" -> "Blindness Trap";
            case "TRAP_MINER" -> "Miner Fatigue Trap";
            case "TRAP_COUNTER" -> "Counter-Offensive Trap";
            case "TRAP_REVEAL" -> "Reveal Trap";
            case "HEAL_POOL" -> "Heal Pool";
            case "CUSHIONED_BOOTS" -> "Cushioned Boots";
            default -> action;
        };
    }

    private void removeCurrency(Player player, Material currency, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != currency) {
                continue;
            }
            int remove = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - remove);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            remaining -= remove;
        }
        player.updateInventory();
    }

    private String readableMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private record CtfInventoryLayout(Map<Material, Integer> preferredSlots) {
    }
}
