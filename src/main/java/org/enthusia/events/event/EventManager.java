package org.enthusia.events.event;

import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.config.EventConfigService;
import org.enthusia.events.kit.EventKitService;
import org.enthusia.events.loot.LootTableService;
import org.enthusia.events.reset.ArenaResetService;
import org.enthusia.events.scoreboard.EventScoreboardService;
import org.enthusia.events.stats.EventStatsService;
import org.enthusia.events.trophy.PodiumService;
import org.enthusia.events.util.LocationCodec;
import org.enthusia.events.util.TeleportService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings({
        "PMD.AvoidDuplicateLiterals",
        "PMD.AvoidFieldNameMatchingMethodName",
        "PMD.AvoidLiteralsInIfCondition",
        "PMD.NPathComplexity",
        "PMD.NullAssignment"
})
public final class EventManager {

    public static final String EVENT_SCOREBOARD_TAG = "enthusia_event";
    public static final String EVENT_METADATA_KEY = "enthusia_event";

    private final EnthusiaEventsPlugin plugin;
    private final EventRegistry registry;
    private final PlayerSnapshotService snapshotService;
    private final EventStatsService statsService;
    private final Economy economy;
    private final MapSetupService mapSetupService;
    private final EventConfigService eventConfigService;
    private final EventKitService kitService;
    private final LootTableService lootTableService;
    private final Queue<EventDefinition> queue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, EventType> playerVotes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> allowedTeleports = new ConcurrentHashMap<>();
    private final Map<String, String> runtimeScoreboardValues = new ConcurrentHashMap<>();
    private final Set<UUID> spawnLocked = ConcurrentHashMap.newKeySet();
    private final Queue<UUID> bracketQueue = new ArrayDeque<>();
    private final Queue<List<UUID>> bracketTeamQueue = new ArrayDeque<>();
    private final Set<UUID> bracketContestants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<UUID>> bracketTeamByPlayer = new HashMap<>();
    private EventSession session;
    private BukkitTask phaseTask;
    private BukkitTask activeTask;
    private BukkitTask preStartTask;
    private BukkitTask bracketTask;
    private BukkitTask raceFinishTask;
    private int countdownRemaining;
    private int preStartRemaining;
    private int voteRemaining;
    private long activeEndsAtMillis;
    private boolean readyCountdownApplied;
    private Runnable voteCloseHandler = () -> {
    };
    private Consumer<Player> kitVotingItemsHandler = player -> {
    };
    private Runnable gameplayRuntimeReset = () -> {
    };
    private Consumer<EventSession> gameplayPreStart = ignored -> {
    };
    private Consumer<Player> gameplayPlayerLeave = ignored -> {};
    public void gameplayPlayerLeave(Consumer<Player> callback) { gameplayPlayerLeave = callback; }
    public boolean isArenaResetting() { return arenaResetService != null && arenaResetService.isResetting(); }
    private EventScoreboardService scoreboardService;
    private ArenaResetService arenaResetService;
    private PodiumService podiumService;
    private BoatRaceService boatRaceService;

    public EventManager(EnthusiaEventsPlugin plugin, EventRegistry registry, PlayerSnapshotService snapshotService,
                        EventStatsService statsService, Economy economy, MapSetupService mapSetupService,
                        EventConfigService eventConfigService, EventKitService kitService, LootTableService lootTableService) {
        this.plugin = plugin;
        this.registry = registry;
        this.snapshotService = snapshotService;
        this.statsService = statsService;
        this.economy = economy;
        this.mapSetupService = mapSetupService;
        this.eventConfigService = eventConfigService;
        this.kitService = kitService;
        this.lootTableService = lootTableService;
    }

    public List<EventDefinition> availableStartChoices() {
        if (isArenaResetting()) return List.of();
        return registry.all().stream()
                .filter(definition -> !isEventDisabled(definition.type()))
                .filter(definition -> !mapSetupService.usableMapsFor(definition.type()).isEmpty())
                .toList();
    }

    public boolean hasSession() {
        return session != null;
    }

    public boolean startScheduledVote() {
        if (isArenaResetting()) return false;
        if (session != null) {
            if (session.privateSession()) {
                return false;
            }
            randomAvailableChoices().stream().findFirst().ifPresent(definition -> {
                queue.add(definition);
                Bukkit.broadcastMessage(plugin.messages().format("event-queued", Map.of("event", definition.displayName())));
            });
            return false;
        }
        List<EventDefinition> choices = randomAvailableChoices();
        if (choices.isEmpty()) {
            Bukkit.broadcastMessage(plugin.messages().get("vote-empty"));
            return false;
        }
        createVoteSession(choices, null, false);
        broadcastVotePrompt();
        startVoteCountdown();
        return true;
    }

    public boolean startManualVote(CommandSender initiator, EventDefinition forced, boolean discountedRandom) {
        if (forced != null && forced.type() == EventType.SPEEDBUILDERS) return false;
        if (isArenaResetting()) return false;
        if (session != null) {
            return false;
        }
        List<EventDefinition> choices = forced == null ? randomAvailableChoices() : validChoice(forced);
        if (choices.isEmpty()) {
            return false;
        }
        if (initiator instanceof Player player && economy != null && !player.hasPermission("enthusia.events.start.free")) {
            double cost = discountedRandom
                    ? plugin.getConfig().getDouble("economy.random-start-cost", 750.0D)
                    : plugin.getConfig().getDouble("economy.manual-start-cost", 150.0D);
            if (!economy.has(player, cost)) {
                return false;
            }
            economy.withdrawPlayer(player, cost);
        }

        EventDefinition definition = choices.getFirst();
        if (initiator instanceof Player player) {
            resetRuntimeServices();
            runtimeScoreboardValues.clear();
            session = newSession(definition, EventPhase.JOIN);
            session.startedBy(player.getName());
            session.waitingHub(configuredLocation("locations.waiting-hub"));
            session.trophyRoom(configuredLocation("locations.trophy-room"));
            selectMapForSession();
            Bukkit.broadcastMessage(plugin.messages().format("manual-event-started-by", Map.of(
                    "event", definition.displayName(),
                    "player", player.getName()
            )));
            broadcastJoinPrompt(definition.displayName(), "player");
            startJoinCountdown();
        } else {
            createVoteSession(choices, null, false);
            broadcastVotePrompt();
            startVoteCountdown();
        }
        return true;
    }

    public boolean startPrivateEvent(CommandSender initiator, EventType type, List<Player> invitedPlayers) {
        if (type == EventType.SPEEDBUILDERS && (!plugin.getConfig().getBoolean("speedbuilders.enabled", false)
                || plugin.arenaRecovery() == null || !plugin.arenaRecovery().enabled(type))) return false;
        if (isArenaResetting()) return false;
        if (session != null) {
            return false;
        }
        EventDefinition definition = registry.definition(type);
        if (definition == null || mapSetupService.usableMapsFor(type).isEmpty()) {
            return false;
        }
        resetRuntimeServices();
        runtimeScoreboardValues.clear();
        session = newSession(definition, EventPhase.JOIN);
        session.adminStarted(true);
        session.privateSession(true);
        session.startedBy(initiator.getName());
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        for (Player player : invitedPlayers) {
            session.invitedPlayers().add(player.getUniqueId());
        }
        if (initiator instanceof Player player) {
            session.invitedPlayers().add(player.getUniqueId());
        }
        selectMapForSession();
        for (UUID uuid : session.invitedPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, "private-event-invite", Map.of("event", definition.displayName()));
            }
        }
        return true;
    }

    public boolean invitePrivatePlayer(Player player) {
        if (!isPrivateSessionWaiting() || player == null) {
            return false;
        }
        session.invitedPlayers().add(player.getUniqueId());
        plugin.messages().send(player, "private-event-invite", Map.of("event", session.definition().displayName()));
        return true;
    }

    public String manualStartFailureReason(Player player, EventDefinition forced, boolean discountedRandom) {
        if (isArenaResetting()) return "the previous arena reset is pending; staff can retry it with /ee resetarena";
        if (session != null) {
            return "a " + session.definition().displayName() + " event is already running";
        }
        if (forced != null && mapSetupService.usableMapsFor(forced.type()).isEmpty()) {
            return forced.displayName() + " has no completed map setup";
        }
        if (forced != null && isEventDisabled(forced.type())) {
            return forced.displayName() + " is disabled by staff";
        }
        if (forced == null && availableStartChoices().isEmpty()) {
            return "no events with completed map setup are available";
        }
        if (player != null && economy != null && !player.hasPermission("enthusia.events.start.free")) {
            double cost = discountedRandom
                    ? plugin.getConfig().getDouble("economy.random-start-cost", 750.0D)
                    : plugin.getConfig().getDouble("economy.manual-start-cost", 150.0D);
            if (!economy.has(player, cost)) {
                return "you need " + cost + " to start an event";
            }
        }
        return "start conditions were not met";
    }

    public void voteCloseHandler(Runnable voteCloseHandler) {
        this.voteCloseHandler = voteCloseHandler == null ? () -> {
        } : voteCloseHandler;
    }

    public void kitVotingItemsHandler(Consumer<Player> kitVotingItemsHandler) {
        this.kitVotingItemsHandler = kitVotingItemsHandler == null ? player -> {
        } : kitVotingItemsHandler;
    }

    public void gameplayRuntimeReset(Runnable gameplayRuntimeReset) {
        this.gameplayRuntimeReset = gameplayRuntimeReset == null ? () -> {
        } : gameplayRuntimeReset;
    }

    public void gameplayPreStart(Consumer<EventSession> gameplayPreStart) {
        this.gameplayPreStart = gameplayPreStart == null ? ignored -> {
        } : gameplayPreStart;
    }

    public void recordArenaResetBlock(Block block) {
        if (arenaResetService != null) {
            arenaResetService.recordBlock(block);
        }
    }

    public void services(EventScoreboardService scoreboardService, ArenaResetService arenaResetService, PodiumService podiumService,
                         BoatRaceService boatRaceService) {
        this.scoreboardService = scoreboardService;
        this.arenaResetService = arenaResetService;
        this.podiumService = podiumService;
        this.boatRaceService = boatRaceService;
    }

    public boolean startForcedEvent(EventType type) {
        if (type == EventType.SPEEDBUILDERS) return false;
        if (isArenaResetting()) return false;
        if (session != null) {
            return false;
        }
        EventDefinition definition = registry.definition(type);
        if (definition == null || isEventDisabled(type) || mapSetupService.usableMapsFor(type).isEmpty()) {
            return false;
        }
        resetRuntimeServices();
        session = newSession(definition, EventPhase.JOIN);
        runtimeScoreboardValues.clear();
        session.adminStarted(true);
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        selectMapForSession();
        Bukkit.broadcastMessage(plugin.messages().format("force-started", Map.of("event", definition.displayName())));
        broadcastJoinPrompt(definition.displayName(), "staff");
        startJoinCountdown();
        return true;
    }

    public String forcedStartFailureReason(EventType type) {
        if (isArenaResetting()) return "the previous arena reset is pending; staff can retry it with /ee resetarena";
        if (session != null) {
            return "a " + session.definition().displayName() + " event is already running";
        }
        if (registry.definition(type) == null) {
            return type.name() + " is not enabled in config.yml";
        }
        if (isEventDisabled(type)) {
            return type.name() + " is disabled by staff";
        }
        if (mapSetupService.usableMapsFor(type).isEmpty()) {
            return type.name() + " has no completed map setup";
        }
        return "start conditions were not met";
    }

    public String privateStartFailureReason(EventType type) {
        if (isArenaResetting()) return "the previous arena reset is pending; staff can retry it with /ee resetarena";
        if (session != null) {
            return "a " + session.definition().displayName() + " event is already running";
        }
        if (registry.definition(type) == null) {
            return type.name() + " is not enabled in config.yml";
        }
        if (mapSetupService.usableMapsFor(type).isEmpty()) {
            return type.name() + " has no completed map setup";
        }
        return "start conditions were not met";
    }

    public boolean isEventDisabled(EventType type) {
        return plugin.getConfig().getStringList("events.disabled").stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.equals(type.name()));
    }

    public void setEventDisabled(EventType type, boolean disabled) {
        List<String> disabledEvents = new ArrayList<>(plugin.getConfig().getStringList("events.disabled"));
        disabledEvents.removeIf(value -> value.equalsIgnoreCase(type.name()));
        if (disabled) {
            disabledEvents.add(type.name());
        }
        plugin.getConfig().set("events.disabled", disabledEvents);
        plugin.saveConfig();
    }

    public List<EventType> disabledEvents() {
        return plugin.getConfig().getStringList("events.disabled").stream()
                .map(value -> {
                    try {
                        return EventType.parse(value);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<EventType> enabledEvents() {
        return registry.all().stream()
                .map(EventDefinition::type)
                .filter(type -> !isEventDisabled(type))
                .toList();
    }

    public boolean isPrivateSessionWaiting() {
        return session != null && session.privateSession()
                && (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN);
    }

    public String adminStatusLine() {
        String active = session == null
                ? "none"
                : session.definition().displayName() + " / " + session.phase().name()
                + (session.privateSession() ? " / private / invited " + session.invitedPlayers().size() : "");
        String auto = plugin.getConfig().getBoolean("schedule.enabled", false) ? "enabled" : "disabled";
        String disabled = disabledEvents().isEmpty()
                ? "none"
                : disabledEvents().stream().map(Enum::name).collect(Collectors.joining(", "));
        return "Active: " + active + " | Autostart: " + auto + " | Disabled: " + disabled;
    }

    public boolean join(Player player) {
        if (session == null || (session.phase() != EventPhase.VOTE
                && session.phase() != EventPhase.JOIN
                && session.phase() != EventPhase.COUNTDOWN)) {
            return false;
        }
        if (session.privateSession() && !session.invitedPlayers().contains(player.getUniqueId())) {
            plugin.messages().send(player, "private-event-not-invited");
            return true;
        }
        if (isEventPlayer(player.getUniqueId())) {
            plugin.messages().send(player, "event-already-joined");
            return true;
        }
        if (snapshotService.hasUnrestoredSnapshot(player.getUniqueId())) {
            plugin.messages().send(player, "event-join-blocked-unrestored");
            return true;
        }
        if (!snapshotService.capture(player)) {
            plugin.messages().send(player, "event-snapshot-capture-failed");
            return true;
        }
        healForEvent(player);
        session.participants().add(player.getUniqueId());
        markEventPlayer(player);
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.updateInventory();
        kitVotingItemsHandler.accept(player);
        Location waitingHub = session.waitingHub();
        if (waitingHub != null) {
            allowTeleport(player.getUniqueId());
            TeleportService.teleport(plugin, player, waitingHub, "event waiting hub");
        }
        plugin.messages().send(player, "event-joined");
        notifySessionPlayers("event-player-joined", player.getName(), player.getUniqueId());
        playConfiguredSound(player, "sounds.event-join");
        maybeApplyReadyCountdown();
        return true;
    }

    public boolean spectate(Player player) {
        if (session == null || (session.phase() != EventPhase.ACTIVE && session.phase() != EventPhase.TROPHY)) {
            return false;
        }
        if (session.privateSession() && !session.invitedPlayers().contains(player.getUniqueId())
                && !session.participants().contains(player.getUniqueId())
                && !session.spectators().contains(player.getUniqueId())) {
            plugin.messages().send(player, "private-event-not-invited");
            return true;
        }
        EventMap map = session.selectedMap();
        if (map == null || map.spectatorSpawn() == null) {
            return false;
        }
        if (!isEventPlayer(player.getUniqueId())) {
            if (snapshotService.hasUnrestoredSnapshot(player.getUniqueId())) {
                plugin.messages().send(player, "event-join-blocked-unrestored");
                return true;
            }
            if (!snapshotService.capture(player)) {
                plugin.messages().send(player, "event-snapshot-capture-failed");
                return true;
            }
        }
        boolean wasParticipant = session.participants().remove(player.getUniqueId());
        gameplayPlayerLeave.accept(player);
        if (wasParticipant) {
            plugin.recordSessionEvidence(session, "departure", player.getUniqueId());
            cleanupBoatRacePlayer(player.getUniqueId());
            kitService.clearSelection(player.getUniqueId());
            spawnLocked.remove(player.getUniqueId());
        }
        if (wasParticipant) finishActiveWithdrawal(player.getUniqueId());
        session.spectators().add(player.getUniqueId());
        markEventPlayer(player);
        player.setGameMode(GameMode.SPECTATOR);
        allowTeleport(player.getUniqueId());
        TeleportService.teleport(plugin, player, map.spectatorSpawn(), "event spectator spawn");
        plugin.messages().send(player, "event-spectating");
        notifySessionPlayers("event-player-spectating", player.getName(), player.getUniqueId());
        return true;
    }

    public boolean leave(Player player) {
        if (session == null) {
            return false;
        }
        boolean wasParticipant = session.participants().remove(player.getUniqueId());
        plugin.recordSessionEvidence(session, "departure", player.getUniqueId());
        gameplayPlayerLeave.accept(player);
        session.spectators().remove(player.getUniqueId());
        playerVotes.remove(player.getUniqueId());
        kitService.clearSelection(player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        allowTeleport(player.getUniqueId());
        cleanupEventInventory(player);
        restorePlayerState(player, false).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (restored) {
                plugin.messages().send(player, "event-left");
            }
        }));
        notifySessionPlayers("event-player-left", player.getName(), player.getUniqueId());
        if (wasParticipant) finishActiveWithdrawal(player.getUniqueId());
        return true;
    }

    private void finishActiveWithdrawal(UUID playerId) {
        if (session == null || session.phase() != EventPhase.ACTIVE || handleBracketQuit(playerId)) return;
        if (session.participants().isEmpty()) scheduleEndActiveEvent(List.copyOf(session.finalRankings()), 0L);
        else if (isLastPlayerStandingEvent(session.definition().type()) && session.participants().size() <= 1)
            scheduleEndActiveEvent(List.copyOf(session.participants()), 60L);
    }

    public void finishParticipant(Player player) {
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().remove(player.getUniqueId())) {
            return;
        }
        if (plugin.seasonProgress() != null) plugin.seasonProgress().finished(session, player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        if (!session.finalRankings().contains(player.getUniqueId())) {
            session.finalRankings().add(player.getUniqueId());
        }
        messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN + player.getName() + " finished.");
        session.spectators().add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        EventMap map = session.selectedMap();
        if (map != null && map.spectatorSpawn() != null) {
            allowTeleport(player.getUniqueId());
            TeleportService.teleport(plugin, player, map.spectatorSpawn(), "finished spectator spawn");
        }
        if (session.participants().isEmpty()) {
            scheduleEndActiveEvent(List.copyOf(session.finalRankings()), 60L);
        } else if (session.definition().type() == EventType.ELYTRA_RACE && session.finalRankings().size() == 1) {
            scheduleRaceFinishGrace();
        }
    }

    public void eliminateParticipant(Player player, String reason) {
        if (handleBracketElimination(player, reason)) {
            return;
        }
        if (session == null || session.phase() != EventPhase.ACTIVE || !session.participants().remove(player.getUniqueId())) {
            return;
        }
        cleanupBoatRacePlayer(player.getUniqueId());
        restoreTemporaryEventAttributes(player);
        session.spectators().add(player.getUniqueId());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.SPECTATOR);
        EventMap map = session.selectedMap();
        if (map != null && map.spectatorSpawn() != null) {
            allowTeleport(player.getUniqueId());
            TeleportService.teleport(plugin, player, map.spectatorSpawn(), "eliminated spectator spawn");
        }
        if (session.definition().type() == EventType.BLOCK_PARTY) {
            player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.RED + "You were eliminated " + reason + ".");
            messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW + player.getName() + " was eliminated.");
        } else if (!(session.definition().type() == EventType.BLOCK_PARTY && "stood on the wrong color".equalsIgnoreCase(reason))) {
            plugin.messages().send(player, "event-eliminated", Map.of("reason", reason));
        }
        player.setGlowing(false);
        if (session.participants().isEmpty()) {
            scheduleEndActiveEvent(List.copyOf(session.finalRankings()), 60L);
        } else if (isLastPlayerStandingEvent(session.definition().type()) && session.participants().size() <= 1) {
            scheduleEndActiveEvent(List.copyOf(session.participants()), 60L);
        }
    }

    public void messageEventPlayers(String message) {
        if (session == null) {
            return;
        }
        for (UUID uuid : eventPlayers()) {
            Player eventPlayer = Bukkit.getPlayer(uuid);
            if (eventPlayer != null) {
                eventPlayer.sendMessage(message);
            }
        }
    }

    public List<UUID> activeParticipants() {
        if (session == null) {
            return List.of();
        }
        return List.copyOf(session.participants());
    }

    public boolean isBracketContestant(UUID uuid) {
        return bracketContestants.contains(uuid);
    }

    public boolean isBracketEvent() {
        return session != null && isBracketEvent(session.definition().type());
    }

    public boolean isTeamBracketEvent() {
        return session != null && isTeamBracketEvent(session.definition().type());
    }

    public CompletableFuture<Boolean> restoreSnapshot(Player player) {
        if (session != null) {
            session.participants().remove(player.getUniqueId());
            session.spectators().remove(player.getUniqueId());
        }
        playerVotes.remove(player.getUniqueId());
        kitService.clearSelection(player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        allowTeleport(player.getUniqueId());
        return restorePlayerState(player, false);
    }

    public int retryPendingOnlineRestores() {
        int attempted = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (snapshotService.hasUnrestoredSnapshot(player.getUniqueId())) {
                restoreSnapshot(player);
                attempted++;
            }
        }
        return attempted;
    }

    public String stuckCheck(Player player) {
        UUID uuid = player.getUniqueId();
        boolean participant = session != null && session.participants().contains(uuid);
        boolean spectator = session != null && session.spectators().contains(uuid);
        boolean eventMetadata = player.hasMetadata(EVENT_METADATA_KEY) || player.hasMetadata(EVENT_METADATA_KEY + "_type");
        boolean eventTag = player.getScoreboardTags().stream().anyMatch(tag -> tag.equals(EVENT_SCOREBOARD_TAG)
                || tag.startsWith(EVENT_SCOREBOARD_TAG + "_"));
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        String vehicle = player.isInsideVehicle() ? player.getVehicle().getType().name() : "none";
        String passengers = player.getPassengers().isEmpty()
                ? "none"
                : player.getPassengers().stream().map(entity -> entity.getType().name()).collect(Collectors.joining(","));
        String effects = player.getActivePotionEffects().stream()
                .map(effect -> effect.getType().getKey().getKey() + ":" + effect.getAmplifier())
                .collect(Collectors.joining(", "));
        if (effects.isBlank()) {
            effects = "none";
        }
        return ChatColor.GOLD + "Event stuck check for " + ChatColor.WHITE + player.getName() + "\n"
                + ChatColor.YELLOW + "Markers: " + ChatColor.GRAY + "tag=" + eventTag
                + ", metadata=" + eventMetadata + ", participant=" + participant + ", spectator=" + spectator + "\n"
                + ChatColor.YELLOW + "Snapshot: " + ChatColor.GRAY + "exists=" + snapshotService.hasSnapshot(uuid)
                + ", unrestored=" + snapshotService.hasUnrestoredSnapshot(uuid) + "\n"
                + ChatColor.YELLOW + "State: " + ChatColor.GRAY + "maxHealth="
                + (maxHealth == null ? "unknown" : String.format(Locale.ROOT, "%.1f", maxHealth.getBaseValue()))
                + ", gamemode=" + player.getGameMode()
                + ", glowing=" + player.isGlowing() + "\n"
                + ChatColor.YELLOW + "Movement: " + ChatColor.GRAY + "vehicle=" + vehicle
                + ", passengers=" + passengers + "\n"
                + ChatColor.YELLOW + "Effects: " + ChatColor.GRAY + effects;
    }

    public CompletableFuture<Boolean> emergencyRestore(Player player) {
        emergencyCleanupPlayer(player, true);
        allowTeleport(player.getUniqueId());
        if (!snapshotService.hasSnapshot(player.getUniqueId())) {
            resetStuckMaxHealth(player);
            return CompletableFuture.completedFuture(false);
        }
        return snapshotService.restore(player, false).thenApply(restored -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (restored) {
                    restoreScoreboard(player);
                    unmarkEventPlayer(player);
                    allowedTeleports.remove(player.getUniqueId());
                } else {
                    teleportToSafeFallback(player);
                    plugin.getLogger().warning("Emergency restore for " + player.getName()
                            + " could not fully apply. Snapshot remains pending.");
                }
            });
            return restored;
        });
    }

    public void handleQuit(Player player) {
        if (session == null) {
            return;
        }
        boolean wasParticipant = session.participants().remove(player.getUniqueId());
        plugin.recordSessionEvidence(session, "departure", player.getUniqueId());
        gameplayPlayerLeave.accept(player);
        session.spectators().remove(player.getUniqueId());
        playerVotes.remove(player.getUniqueId());
        cleanupBoatRacePlayer(player.getUniqueId());
        kitService.clearSelection(player.getUniqueId());
        allowedTeleports.remove(player.getUniqueId());
        spawnLocked.remove(player.getUniqueId());
        restoreScoreboard(player);
        unmarkEventPlayer(player);
        if (wasParticipant && session.phase() == EventPhase.ACTIVE) {
            if (handleBracketQuit(player.getUniqueId())) {
                return;
            }
            if (session.participants().isEmpty()) {
                cancelTask();
                scheduleEndActiveEvent(List.of(), 0L);
            } else if (isLastPlayerStandingEvent(session.definition().type()) && session.participants().size() <= 1) {
                scheduleEndActiveEvent(List.copyOf(session.participants()), 60L);
            }
        }
    }

    private boolean handleBracketQuit(UUID quitter) {
        if (session == null || !isBracketEvent(session.definition().type())) {
            return false;
        }
        bracketQueue.remove(quitter);
        bracketTeamQueue.forEach(team -> team.remove(quitter));
        bracketTeamQueue.removeIf(List::isEmpty);
        if (!bracketContestants.remove(quitter)) {
            bracketTeamByPlayer.remove(quitter);
            return false;
        }
        if (isTeamBracketEvent(session.definition().type())) {
            resolveTeamBracketQuit(quitter);
        } else {
            resolveSoloBracketQuit();
        }
        bracketTeamByPlayer.remove(quitter);
        return true;
    }

    private void resolveSoloBracketQuit() {
        UUID winnerId = bracketContestants.stream()
                .filter(session.participants()::contains)
                .findFirst()
                .orElse(null);
        bracketContestants.clear();
        if (winnerId != null) {
            bracketQueue.add(winnerId);
            prepareBracketWinnerForNextMatch(winnerId);
        }
        scheduleNextBracketMatch();
    }

    private void resolveTeamBracketQuit(UUID quitter) {
        List<UUID> losingTeam = bracketTeamByPlayer.getOrDefault(quitter, List.of(quitter));
        boolean teammateAlive = losingTeam.stream()
                .filter(uuid -> !uuid.equals(quitter))
                .anyMatch(uuid -> bracketContestants.contains(uuid) && session.participants().contains(uuid));
        if (teammateAlive) {
            return;
        }
        List<UUID> winningTeam = bracketContestants.stream()
                .filter(uuid -> !losingTeam.contains(uuid) && session.participants().contains(uuid))
                .map(uuid -> bracketTeamByPlayer.getOrDefault(uuid, List.of(uuid)))
                .findFirst()
                .orElse(List.of());
        bracketContestants.clear();
        List<UUID> onlineWinners = winningTeam.stream()
                .filter(session.participants()::contains)
                .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                .toList();
        if (!onlineWinners.isEmpty()) {
            registerBracketTeam(onlineWinners);
            bracketTeamQueue.add(new ArrayList<>(onlineWinners));
            onlineWinners.forEach(this::prepareBracketWinnerForNextMatch);
        }
        scheduleNextBracketMatch();
    }

    private void prepareBracketWinnerForNextMatch(UUID winnerId) {
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner == null) {
            return;
        }
        winner.setHealth(winner.getMaxHealth());
        winner.setFoodLevel(20);
        winner.setFireTicks(0);
        winner.setGameMode(GameMode.SPECTATOR);
        spawnLocked.remove(winnerId);
    }

    public void handleJoin(Player player) {
        if (snapshotService.hasUnrestoredSnapshot(player.getUniqueId()) && !isEventPlayer(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !snapshotService.hasUnrestoredSnapshot(player.getUniqueId()) || isEventPlayer(player.getUniqueId())) {
                    return;
                }
                allowTeleport(player.getUniqueId());
                restorePlayerState(player, false).thenAccept(restored -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (restored) {
                        plugin.messages().send(player, "event-restored-on-join");
                    }
                }));
            }, 20L);
        }
    }

    public void castVote(Player player, EventType type) {
        if (session == null || session.phase() != EventPhase.VOTE || !session.votes().containsKey(type)) {
            return;
        }
        EventType previous = playerVotes.put(player.getUniqueId(), type);
        if (previous != null) {
            session.votes().computeIfPresent(previous, (key, value) -> Math.max(0, value - 1));
        }
        session.votes().computeIfPresent(type, (key, value) -> value + 1);
        playConfiguredSound(player, "sounds.vote-cast");
    }

    private EventSession newSession(EventDefinition definition, EventPhase phase) {
        EventSession created = new EventSession(definition, phase);
        created.phaseObserver(next -> plugin.recordSessionEvidence(created, "phase", null));
        plugin.recordSessionEvidence(created, "created", null);
        return created;
    }

    public EventSession session() {
        return session;
    }

    public Map<EventType, Integer> liveVotes() {
        return session == null ? Map.of() : new HashMap<>(session.votes());
    }

    public void endActiveEvent(List<UUID> rankedPlayers) {
        if (session == null) {
            return;
        }
        session.finalRankings().clear();
        session.finalRankings().addAll(rankedPlayers);
        session.phase(EventPhase.TROPHY);
        if (boatRaceService != null) {
            boatRaceService.cleanupAll();
        }
        broadcastEventResult(session);
        Location trophyRoom = session.trophyRoom();
        if (trophyRoom != null) {
            for (UUID uuid : allEventPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    restoreTemporaryEventAttributes(player);
                    prepareTrophyPlayer(player);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    allowTeleport(uuid);
                    TeleportService.teleport(plugin, player, trophyRoom, "event trophy room");
                }
            }
        }
        if (arenaResetService != null) {
            arenaResetService.reset();
            plugin.recordSessionEvidence(session, "arena-reset pending=" + arenaResetService.pendingBlocks(), null);
        }
        if (podiumService != null) {
            podiumService.show(rankedPlayers);
        }
        if (!rankedPlayers.isEmpty()) {
            Set<UUID> winners = session.definition().type() == EventType.CAPTURE_PLAYERS || session.definition().type() == EventType.SPEEDBUILDERS
                    ? Set.copyOf(rankedPlayers)
                    : Set.of(rankedPlayers.getFirst());
            for (UUID winner : winners) {
                statsService.recordWin(winner, session.definition().type());
            }
            for (UUID uuid : session.participants()) {
                if (!winners.contains(uuid)) {
                    statsService.recordLoss(uuid, session.definition().type());
                }
            }
        }
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
        cancelRaceFinishTask();
        spawnLocked.clear();
        resetBracketState();
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin, this::finishSession,
                plugin.getConfig().getLong("schedule.trophy-room-seconds", 10L) * 20L);
    }

    private void scheduleEndActiveEvent(List<UUID> rankedPlayers, long delayTicks) {
        if (phaseTask != null) {
            return;
        }
        cancelActiveTask();
        phaseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                phaseTask = null;
                if (session != null && session.phase() == EventPhase.ACTIVE) {
                    endActiveEvent(rankedPlayers);
                }
            }
        }, delayTicks);
    }

    public void endActiveEventDelayed(List<UUID> rankedPlayers, long delayTicks) {
        scheduleEndActiveEvent(rankedPlayers, delayTicks);
    }

    public void stop(String reason) {
        if (plugin.speedbuilders() != null) plugin.speedbuilders().stop();
        if (session != null && plugin.seasonProgress() != null) plugin.seasonProgress().cancel(session.id());
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
        cancelRaceFinishTask();
        spawnLocked.clear();
        if (session != null) {
            boolean privateSession = session.privateSession();
            restoreAll(session.participants());
            restoreAll(session.spectators());
            resetRuntimeServices();
            if (!privateSession) {
                announce(plugin.messages().format("event-ended-no-winner", Map.of("event", session.definition().displayName())));
            }
            session = null;
        }
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        kitService.clearSelections();
    }

    public boolean advancePhase() {
        if (session == null) {
            return false;
        }
        if (session.phase() == EventPhase.VOTE) {
            cancelTask();
            advanceToJoin();
            return true;
        }
        if (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN) {
            cancelTask();
            cancelPreStartTask();
            session.adminStarted(true);
            return beginActivePhase();
        }
        if (session.phase() == EventPhase.TROPHY) {
            cancelTask();
            cancelActiveTask();
            cancelPreStartTask();
            finishSession();
            return true;
        }
        return false;
    }

    public void queue(EventDefinition definition) {
        queue.add(definition);
    }

    public boolean isEventPlayer(UUID uuid) {
        return session != null && (session.participants().contains(uuid) || session.spectators().contains(uuid));
    }

    public boolean isSpawnLocked(UUID uuid) {
        return spawnLocked.contains(uuid);
    }

    public boolean isWaitingLocked() {
        return session != null && (session.phase() == EventPhase.JOIN || session.phase() == EventPhase.COUNTDOWN)
                && plugin.getConfig().getBoolean("restrictions.lock-waiting-hub", true);
    }

    public boolean isTrophyLocked() {
        return session != null && session.phase() == EventPhase.TROPHY
                && plugin.getConfig().getBoolean("restrictions.lock-trophy-room", true);
    }

    public EventMap activeMap() {
        return session == null ? null : session.selectedMap();
    }

    public Set<UUID> eventPlayers() {
        return allEventPlayers();
    }

    public String teamFor(UUID uuid) {
        if (session == null) {
            return "";
        }
        return session.teams().getOrDefault(uuid, "");
    }

    public String runtimeScoreboardValue(String key, String fallback) {
        return runtimeScoreboardValues.getOrDefault(key, fallback);
    }

    public void setRuntimeScoreboardValue(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        runtimeScoreboardValues.put(key, value == null ? "" : value);
    }

    public long activeSecondsRemaining() {
        if (session == null || session.phase() != EventPhase.ACTIVE || activeEndsAtMillis <= 0L) {
            return 0L;
        }
        long remaining = activeEndsAtMillis - System.currentTimeMillis();
        return remaining <= 0L ? 0L : (long) Math.ceil(remaining / 1000.0D);
    }

    public Location waitingHubLocation() {
        return configuredLocation("locations.waiting-hub");
    }

    public Location trophyRoomLocation() {
        return configuredLocation("locations.trophy-room");
    }

    public CuboidRegion waitingHubRegion() {
        return configuredRegion("locations.waiting-hub-region");
    }

    public CuboidRegion trophyRoomRegion() {
        return configuredRegion("locations.trophy-room-region");
    }

    public boolean canExitByTeleport(UUID uuid) {
        Long expiresAt = allowedTeleports.get(uuid);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            allowedTeleports.remove(uuid);
            return false;
        }
        return true;
    }

    public String nextHourlyVoteLabel() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        return next.toString().replace('T', ' ');
    }

    public long minutesUntilNextHour() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.plusHours(1).truncatedTo(ChronoUnit.HOURS)).toMinutes();
    }

    public void shutdown() {
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
        cancelRaceFinishTask();
        if (session != null) {
            restoreAllSynchronously(session.participants());
            restoreAllSynchronously(session.spectators());
            resetRuntimeServices();
            session = null;
        }
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        spawnLocked.clear();
        resetBracketState();
        kitService.clearSelections();
    }

    private void createVoteSession(List<EventDefinition> choices, String startedBy, boolean adminStarted) {
        resetRuntimeServices();
        runtimeScoreboardValues.clear();
        session = newSession(choices.getFirst(), EventPhase.VOTE);
        session.startedBy(startedBy);
        session.adminStarted(adminStarted);
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        choices.forEach(choice -> session.votes().put(choice.type(), 0));
    }

    private List<EventDefinition> randomAvailableChoices() {
        List<EventDefinition> pool = new ArrayList<>(availableStartChoices());
        List<EventDefinition> out = new ArrayList<>();
        int amount = plugin.getConfig().getInt("events.vote-options", 5);
        while (!pool.isEmpty() && out.size() < amount) {
            out.add(pool.remove(ThreadLocalRandom.current().nextInt(pool.size())));
        }
        return out;
    }

    private List<EventDefinition> validChoice(EventDefinition definition) {
        return isEventDisabled(definition.type()) || mapSetupService.usableMapsFor(definition.type()).isEmpty()
                ? List.of()
                : List.of(definition);
    }

    private void startVoteCountdown() {
        cancelTask();
        voteRemaining = plugin.getConfig().getInt("schedule.vote-phase-seconds", 60);
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (this) {
                if (session == null || session.phase() != EventPhase.VOTE) {
                    cancelTask();
                    return;
                }
                if (voteRemaining <= 0) {
                    cancelTask();
                    advanceToJoin();
                    return;
                }
                if (voteRemaining == 30) {
                    announce(plugin.messages().format("vote-reminder", Map.of("time", formatDuration(voteRemaining))));
                }
                voteRemaining--;
            }
        }, 20L, 20L);
    }

    private void advanceToJoin() {
        if (session == null) {
            return;
        }
        EventType winner = session.votes().entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(session.definition().type());
        EventDefinition definition = registry.definition(winner);
        session.definition(definition == null ? session.definition() : definition);
        announce(plugin.messages().format("vote-winner", Map.of("event", session.definition().displayName())));
        voteCloseHandler.run();
        session.phase(EventPhase.JOIN);
        selectMapForSession();
        broadcastJoinPrompt(session.definition().displayName(), "vote");
        startJoinCountdown();
    }

    private void startJoinCountdown() {
        cancelTask();
        if (session.selectedMap() == null) {
            cancelForNotEnoughPlayers();
            return;
        }
        readyCountdownApplied = false;
        countdownRemaining = plugin.getConfig().getInt("schedule.join-phase-seconds", 300);
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickJoinCountdown, 20L, 20L);
    }

    private void tickJoinCountdown() {
        if (session == null || (session.phase() != EventPhase.JOIN && session.phase() != EventPhase.COUNTDOWN)) {
            cancelTask();
            return;
        }
        refreshKitVotingItems();
        maybeApplyReadyCountdown();
        if (countdownRemaining <= 0) {
            if (!hasEnoughPlayersForStart()) {
                cancelForNotEnoughPlayers();
                return;
            }
            beginActivePhase();
            return;
        }
        if (shouldAnnounceCountdown(countdownRemaining)) {
            announce(plugin.messages().format("countdown-tick", Map.of(
                    "event", session.definition().displayName(),
                    "seconds", String.valueOf(countdownRemaining),
                    "time", formatDuration(countdownRemaining),
                    "players", String.valueOf(session.participants().size()),
                "min", String.valueOf(minPlayers(session.definition().type()))
            )));
        }
        updateCountdownExperience(countdownRemaining, plugin.getConfig().getInt("schedule.join-phase-seconds", 300));
        countdownRemaining--;
    }

    private void maybeApplyReadyCountdown() {
        if (session == null || readyCountdownApplied) {
            return;
        }
        if (session.privateSession()) {
            return;
        }
        int requiredReadyPlayers = session.adminStarted() ? 1 : readyPlayers(session.definition().type());
        if (session.participants().size() < requiredReadyPlayers) {
            return;
        }
        int readySeconds = plugin.getConfig().getInt("schedule.ready-countdown-seconds", 60);
        if (countdownRemaining > readySeconds) {
            countdownRemaining = readySeconds;
        }
        readyCountdownApplied = true;
        session.phase(EventPhase.COUNTDOWN);
        announce(plugin.messages().format("join-countdown-ready", Map.of(
                "event", session.definition().displayName(),
                "seconds", String.valueOf(countdownRemaining),
                "time", formatDuration(countdownRemaining)
        )));
    }

    private boolean beginActivePhase() {
        if (session == null) {
            return false;
        }
        if (!hasEnoughPlayersForStart()) {
            cancelForNotEnoughPlayers();
            return false;
        }
        if (plugin.arenaPreflight() != null && (plugin.getConfig().getBoolean("preflight.enforce-start", false)
                || session.definition().type() == EventType.SPEEDBUILDERS
                || plugin.arenaRecovery().enabled(session.definition().type()))) {
            var findings = session.selectedMap() == null ? List.<org.enthusia.events.domain.PreflightFinding>of()
                    : plugin.arenaPreflight().inspect(session.selectedMap());
            if (session.selectedMap() == null || !org.enthusia.events.infrastructure.ArenaPreflight.ready(findings)) {
                messageEventPlayers("Arena preflight blocked activation. Staff: /ee preflight " + session.definition().type() + " <map>");
                stop("Arena preflight failed"); return false;
            }
        }
        if (session.definition().type() == EventType.SPEEDBUILDERS && plugin.speedbuilders() != null) {
            var errors = plugin.speedbuilders().validate(session);
            if (!errors.isEmpty()) { messageEventPlayers(String.join("; ", errors)); stop("Speedbuilders setup incomplete"); return false; }
        }
        if (plugin.arenaRecovery() != null && plugin.arenaRecovery().enabled(session.definition().type()) && !plugin.arenaRecovery().prepared(session)) {
            if (plugin.arenaRecovery().blocked()) return false;
            cancelTask(); cancelPreStartTask();
            session.phase(EventPhase.PRESTART);
            EventSession preparing = session;
            plugin.arenaRecovery().prepare(preparing, () -> {
                if (session == preparing && preparing.phase() == EventPhase.PRESTART) beginActivePhase();
                else plugin.arenaRecovery().reset();
            }, failure -> {
                if (session == preparing) { messageEventPlayers(failure); stop("Arena baseline not committed"); }
            });
            return true;
        }
        cancelTask();
        cancelPreStartTask();
        resetBracketState();
        session.phase(EventPhase.PRESTART);
        EventSession startingSession = session;
        spawnLocked.clear();
        session.teams().clear();
        EventMap map = session.selectedMap();
        gameplayPreStart.accept(session);
        if ((session.definition().type() == EventType.BOAT_RACE || session.definition().type() == EventType.PARKOUR
                || session.definition().type() == EventType.HORSE_RACE)
                && boatRaceService != null) {
            boatRaceService.preparePreStart(map);
        }
        if (session.definition().type() == EventType.BLOCK_PARTY) {
            prepareBlockPartyFloor(map);
        }
        if (map != null && !map.spawns().isEmpty()) {
            List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
            List<Map.Entry<String, Location>> spawns = new ArrayList<>(map.spawns().entrySet());
            if (session.definition().type() == EventType.FIGHT_FFA
                    || session.definition().type() == EventType.SUMO_FFA) {
                java.util.Collections.shuffle(spawns);
            }
            int index = 0;
            for (UUID uuid : session.participants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    Map.Entry<String, Location> spawn = spawns.get(index % spawns.size());
                    session.teams().put(uuid, teamFromSpawnKey(spawn.getKey(), index));
                    prepareActivePlayer(player, session.definition().type());
                    allowTeleport(uuid);
                    teleports.add(TeleportService.teleport(plugin, player, spawn.getValue(), "event spawn").thenApply(success -> {
                        if (success && session != null && session.definition().type() == EventType.BOAT_RACE && boatRaceService != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> boatRaceService.mountPlayer(player, spawn.getValue()));
                        }
                        if (success && session != null && session.definition().type() == EventType.HORSE_RACE && boatRaceService != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> boatRaceService.mountHorse(player, spawn.getValue()));
                        }
                        return success;
                    }));
                    if (!canMoveDuringPreStart(session.definition().type())) {
                        spawnLocked.add(uuid);
                        player.setAllowFlight(true);
                        player.setFlying(false);
                    }
                }
                index++;
            }
            if (!teleports.isEmpty()) {
                CompletableFuture.allOf(teleports.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            synchronized (this) {
                                if (session == startingSession && session != null && session.phase() == EventPhase.PRESTART) {
                                    prepareBracketPreStart();
                                    startPreStartCountdown();
                                }
                            }
                        }));
                return true;
            }
        }
        prepareBracketPreStart();
        startPreStartCountdown();
        return true;
    }

    private void startPreStartCountdown() {
        preStartRemaining = 5;
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            synchronized (this) {
                if (session == null || session.phase() != EventPhase.PRESTART) {
                    cancelPreStartTask();
                    return;
                }
                if (preStartRemaining <= 0) {
                    releaseActivePlayers();
                    return;
                }
                refreshKitVotingItems();
                messageEventPlayers(plugin.messages().format("prestart-countdown", Map.of(
                        "event", session.definition().displayName(),
                        "seconds", String.valueOf(preStartRemaining)
                )));
                updateCountdownExperience(preStartRemaining, 5);
                playPreStartCountdownSound(preStartRemaining);
                preStartRemaining--;
            }
        }, 0L, 20L);
        preStartTask = phaseTask;
    }

    private void playPreStartCountdownSound(int seconds) {
        if (session == null) {
            return;
        }
        if (seconds > 3) {
            return;
        }
        Sound sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        float pitch = 1.0F;
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, 0.9F, pitch);
            }
        }
    }

    private void releaseActivePlayers() {
        if (session == null) {
            return;
        }
        boolean enforce = plugin.getConfig().getBoolean("preflight.enforce-start", false) || session.definition().type() == EventType.SPEEDBUILDERS
                || (plugin.arenaRecovery() != null && plugin.arenaRecovery().enabled(session.definition().type()));
        if (enforce && (session.selectedMap() == null || !org.enthusia.events.infrastructure.ArenaPreflight.ready(plugin.arenaPreflight().inspect(session.selectedMap())))) {
            messageEventPlayers("Arena changed or checks became unavailable during preparation; activation cancelled.");
            stop("Final arena preflight failed"); return;
        }
        if (plugin.arenaRecovery() != null && plugin.arenaRecovery().enabled(session.definition().type()) && !plugin.arenaRecovery().prepared(session)) {
            stop("Durable arena lease unavailable"); return;
        }
        cancelPreStartTask();
        Set<UUID> lockedPlayers = Set.copyOf(spawnLocked);
        spawnLocked.clear();
        for (UUID uuid : lockedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        session.phase(EventPhase.ACTIVE);
        if (plugin.seasonProgress() != null) plugin.seasonProgress().pin(session, session.participants());
        clearCountdownExperience();
        playGoSound();
        playGlobalEventStartSound();
        if ((session.definition().type() == EventType.BOAT_RACE || session.definition().type() == EventType.PARKOUR
                || session.definition().type() == EventType.HORSE_RACE)
                && boatRaceService != null) {
            boatRaceService.releaseWall(session.selectedMap());
        }
        populateMapContainers(session.selectedMap(), session.definition().type());
        for (UUID uuid : session.participants()) {
            statsService.recordParticipation(uuid, session.definition().type());
        }
        messageEventPlayers(plugin.messages().format("event-active-started", Map.of("event", session.definition().displayName())));
        if (session.definition().type() == EventType.SPEEDBUILDERS && plugin.speedbuilders() != null) {
            plugin.speedbuilders().start(session); return;
        }
        scheduleActiveTimer(session.definition().type());
    }

    private void updateCountdownExperience(int seconds, int totalSeconds) {
        if (session == null) {
            return;
        }
        float progress = totalSeconds <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, seconds / (float) totalSeconds));
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setLevel(Math.max(0, seconds));
                player.setExp(progress);
            }
        }
    }

    private void refreshKitVotingItems() {
        if (session == null) {
            return;
        }
        if (session.definition().type() != EventType.FIGHT_1V1
                && session.definition().type() != EventType.FIGHT_2V2
                && session.definition().type() != EventType.FIGHT_FFA) {
            return;
        }
        if (session.phase() != EventPhase.JOIN && session.phase() != EventPhase.COUNTDOWN) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                kitVotingItemsHandler.accept(player);
            }
        }
    }

    private void clearCountdownExperience() {
        if (session == null || session.definition().type() == EventType.BLOCK_PARTY) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setLevel(0);
                player.setExp(0.0F);
            }
        }
    }

    private void playGoSound() {
        if (session == null) {
            return;
        }
        for (UUID uuid : session.participants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.6F);
            }
        }
    }

    private void playGlobalEventStartSound() {
        if (session == null) {
            return;
        }
        if (session.privateSession()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (session.participants().contains(player.getUniqueId()) || session.spectators().contains(player.getUniqueId())) {
                continue;
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75F, 1.1F);
        }
    }

    private void cancelForNotEnoughPlayers() {
        if (session == null) {
            return;
        }
        announce(plugin.messages().format("event-cancelled-not-enough", Map.of(
                "event", session.definition().displayName(),
                "players", String.valueOf(session.participants().size()),
                "min", String.valueOf(minPlayers(session.definition().type()))
        )));
        restoreAll(session.participants());
        restoreAll(session.spectators());
        session = null;
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        spawnLocked.clear();
        resetBracketState();
        kitService.clearSelections();
        resetRuntimeServices();
        cancelTask();
        cancelActiveTask();
        cancelPreStartTask();
    }

    private boolean hasEnoughPlayersForStart() {
        if (session == null) {
            return false;
        }
        int required = session.adminStarted() ? 1 : minPlayers(session.definition().type());
        return session.participants().size() >= required;
    }

    private int minPlayers(EventType type) {
        return eventConfigService.minPlayers(type, plugin.getConfig().getInt("events.min-players", 2));
    }

    private int readyPlayers(EventType type) {
        return eventConfigService.readyPlayers(type, plugin.getConfig().getInt("events.ready-players", minPlayers(type)));
    }

    private boolean shouldAnnounceCountdown(int seconds) {
        if (seconds > 60) {
            return seconds % 60 == 0;
        }
        return seconds == 60 || seconds == 30 || seconds == 15 || seconds == 5;
    }

    private String formatDuration(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        if (seconds > 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + " second" + (seconds == 1 ? "" : "s");
    }

    private void selectMapForSession() {
        if (session == null || session.selectedMap() != null) {
            return;
        }
        List<EventMap> maps = mapSetupService.usableMapsFor(session.definition().type());
        if (!maps.isEmpty()) {
            session.selectedMap(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
        }
    }

    private List<UUID> rankedByRuntimeScore(String prefix) {
        if (session == null) {
            return List.of();
        }
        return session.participants().stream()
                .sorted((left, right) -> Integer.compare(runtimeScore(prefix, right), runtimeScore(prefix, left)))
                .toList();
    }

    private List<UUID> ctfTimerWinners(EventSession session) {
        if (session == null) return List.of();
        Map<String, Integer> scores = new HashMap<>();
        for (String team : session.teams().values().stream().distinct().toList()) {
            String key = "ctf-score-" + team.toLowerCase(Locale.ROOT);
            int score = Integer.parseInt(runtimeScoreboardValues.getOrDefault(key, "0"));
            scores.put(team, score);
        }
        int maxScore = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxScore <= 0) return List.of();
        List<String> winningTeams = scores.entrySet().stream()
                .filter(e -> e.getValue() == maxScore)
                .map(Map.Entry::getKey)
                .toList();
        if (winningTeams.size() != 1) return List.of(); // tie = no winner
        String winner = winningTeams.getFirst();
        return session.teams().entrySet().stream()
                .filter(e -> winner.equals(e.getValue()) && session.participants().contains(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private int runtimeScore(String prefix, UUID uuid) {
        String value = runtimeScoreboardValues.get(prefix + uuid);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void prepareBlockPartyFloor(EventMap map) {
        CuboidRegion area = map == null ? null : map.areas().get("color-floor");
        if (area == null) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(area.worldName());
        if (world == null) {
            return;
        }
        Material[] colors = {
                Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE,
                Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
                Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE
        };
        int minX = (int) Math.floor(area.minX());
        int minY = (int) Math.floor(area.minY());
        int minZ = (int) Math.floor(area.minZ());
        int maxX = (int) Math.floor(area.maxX());
        int maxY = (int) Math.floor(area.maxY());
        int maxZ = (int) Math.floor(area.maxZ());
        long blocks = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (blocks > 20_000L) {
            plugin.getLogger().warning("Skipping oversized Block Party prestart floor update (" + blocks + " blocks). Resize the color floor.");
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(colors[random.nextInt(colors.length)], false);
                }
            }
        }
    }

    private void finishSession() {
        if (session == null) {
            return;
        }
        EventSession finished = session;
        plugin.recordSessionEvidence(finished, "finished participants=" + finished.participants().size(), null);
        if (!finished.finalRankings().isEmpty()) {
            payWinner(finished);
        }
        restoreAll(finished.participants());
        restoreAll(finished.spectators());
        resetRuntimeServices();
        session = null;
        playerVotes.clear();
        runtimeScoreboardValues.clear();
        allowedTeleports.clear();
        spawnLocked.clear();
        kitService.clearSelections();
        cancelActiveTask();
        cancelPreStartTask();
        EventDefinition queued = queue.poll();
        if (queued != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> startQueuedEvent(queued),
                    plugin.getConfig().getLong("schedule.queue-delay-seconds", 30L) * 20L);
        }
    }

    private void startQueuedEvent(EventDefinition definition) {
        if (isArenaResetting()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> startQueuedEvent(definition), 100L);
            return;
        }
        if (session != null) {
            queue.add(definition);
            return;
        }
        if (definition == null || isEventDisabled(definition.type()) || mapSetupService.usableMapsFor(definition.type()).isEmpty()) {
            return;
        }
        resetRuntimeServices();
        runtimeScoreboardValues.clear();
        session = newSession(definition, EventPhase.JOIN);
        session.startedBy("Queue");
        session.waitingHub(configuredLocation("locations.waiting-hub"));
        session.trophyRoom(configuredLocation("locations.trophy-room"));
        selectMapForSession();
        Bukkit.broadcastMessage(plugin.messages().format("queued-event-started", Map.of("event", definition.displayName())));
        broadcastJoinPrompt(definition.displayName(), "queue");
        startJoinCountdown();
    }

    private void restoreAll(Set<UUID> uuids) {
        for (UUID uuid : List.copyOf(uuids)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                allowTeleport(uuid);
                cleanupEventInventory(player);
                restorePlayerState(player, false);
            }
        }
    }

    private void restoreAllSynchronously(Set<UUID> uuids) {
        for (UUID uuid : List.copyOf(uuids)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            allowTeleport(uuid);
            cleanupEventInventory(player);
            emergencyCleanupPlayer(player, false);
            boolean restored = snapshotService.restoreSynchronously(player, false);
            if (restored) {
                restoreScoreboard(player);
                unmarkEventPlayer(player);
                allowedTeleports.remove(uuid);
            }
        }
    }

    private CompletableFuture<Boolean> restorePlayerState(Player player, boolean consumeSnapshot) {
        UUID uuid = player.getUniqueId();
        emergencyCleanupPlayer(player, false);
        if (!snapshotService.hasSnapshot(uuid)) {
            allowedTeleports.remove(uuid);
            return CompletableFuture.completedFuture(false);
        }
        EventSession restoringSession = session;
        return snapshotService.restore(player, consumeSnapshot).thenApply(restored -> {
            plugin.recordSessionEvidence(restoringSession, restored ? "restore-success" : "restore-failed", uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (restored) {
                    restoreScoreboard(player);
                    unmarkEventPlayer(player);
                    allowedTeleports.remove(uuid);
                } else {
                    plugin.getLogger().warning("Event snapshot restore failed for " + player.getName()
                            + ". Event markers were kept and the snapshot remains pending.");
                    teleportToSafeFallback(player);
                }
            });
            return restored;
        });
    }

    public void emergencyCleanupPlayer(Player player, boolean clearMarkers) {
        UUID uuid = player.getUniqueId();
        cleanupBoatRacePlayer(uuid);
        player.setGlowing(false);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        for (Entity passenger : List.copyOf(player.getPassengers())) {
            player.removePassenger(passenger);
        }
        removeEventPotionEffects(player);
        if (clearMarkers || player.getScoreboardTags().contains(EVENT_SCOREBOARD_TAG) || player.hasMetadata(EVENT_METADATA_KEY)) {
            player.setLevel(0);
            player.setExp(0.0F);
        }
        if (!snapshotService.hasSnapshot(uuid)) {
            resetStuckMaxHealth(player);
        }
        if (clearMarkers) {
            playerVotes.remove(uuid);
            kitService.clearSelection(uuid);
            allowedTeleports.remove(uuid);
            spawnLocked.remove(uuid);
            restoreScoreboard(player);
            unmarkEventPlayer(player);
        }
    }

    private void removeEventPotionEffects(Player player) {
        for (PotionEffectType type : List.of(
                PotionEffectType.SLOWNESS,
                PotionEffectType.GLOWING,
                PotionEffectType.RESISTANCE,
                PotionEffectType.HASTE,
                PotionEffectType.REGENERATION,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.SPEED,
                PotionEffectType.JUMP_BOOST,
                PotionEffectType.BLINDNESS,
                PotionEffectType.INVISIBILITY
        )) {
            player.removePotionEffect(type);
        }
    }

    private void resetStuckMaxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null && attribute.getBaseValue() <= 2.0D) {
            attribute.setBaseValue(20.0D);
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private void teleportToSafeFallback(Player player) {
        Location fallback = waitingHubLocation();
        if (fallback == null) {
            fallback = player.getWorld().getSpawnLocation();
        }
        allowTeleport(player.getUniqueId());
        TeleportService.teleport(plugin, player, fallback, "emergency restore fallback");
    }

    private void payWinner(EventSession finished) {
        if (finished.definition().type() == EventType.SPEEDBUILDERS) return;
        if (finished.finalRankings().isEmpty() || economy == null) {
            return;
        }
        double reward = eventConfigService.winnerReward(finished.definition().type(),
                plugin.getConfig().getDouble("economy.winner-reward", 100.0D));
        if (reward <= 0.0D) {
            return;
        }
        List<UUID> winners = (finished.definition().type() == EventType.CAPTURE_THE_FLAG
                || finished.definition().type() == EventType.CAPTURE_PLAYERS)
                ? finished.finalRankings()
                : List.of(finished.finalRankings().getFirst());
        for (UUID winner : winners) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(winner);
            economy.depositPlayer(offlinePlayer, reward);
            Player online = offlinePlayer.getPlayer();
            if (online != null) {
                plugin.messages().send(online, "event-winner-paid", Map.of("amount", String.valueOf(reward)));
            }
        }
    }

    private void broadcastEventResult(EventSession finished) {
        if (finished.privateSession()) {
            return;
        }
        if (finished.finalRankings().isEmpty()) {
            announce(plugin.messages().format("event-ended-no-winner", Map.of("event", finished.definition().displayName())));
            return;
        }
        announce(plugin.messages().format("event-ended", Map.of(
                "event", finished.definition().displayName(),
                "winners", winnerNames(finished.finalRankings())
        )));
    }

    private String winnerNames(List<UUID> rankings) {
        int limit = session != null
                && (session.definition().type() == EventType.CAPTURE_THE_FLAG
                || session.definition().type() == EventType.CAPTURE_PLAYERS)
                ? Integer.MAX_VALUE : 1;
        return rankings.stream()
                .limit(limit)
                .map(uuid -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    return player.getName() == null ? uuid.toString() : player.getName();
                })
                .collect(Collectors.joining(", "));
    }

    private Set<UUID> allEventPlayers() {
        if (session == null) {
            return Set.of();
        }
        Set<UUID> players = new java.util.LinkedHashSet<>(session.participants());
        players.addAll(session.spectators());
        return players;
    }

    private void notifySessionPlayers(String messageKey, String playerName, UUID exclude) {
        if (session == null) {
            return;
        }
        for (UUID uuid : allEventPlayers()) {
            if (uuid.equals(exclude)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, messageKey, Map.of("player", playerName));
            }
        }
    }

    private void announce(String message) {
        if (session != null && session.privateSession()) {
            for (UUID uuid : privateAudience()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(message);
                }
            }
            return;
        }
        Bukkit.broadcastMessage(message);
    }

    private Set<UUID> privateAudience() {
        Set<UUID> audience = new java.util.LinkedHashSet<>();
        if (session == null) {
            return audience;
        }
        audience.addAll(session.invitedPlayers());
        audience.addAll(session.participants());
        audience.addAll(session.spectators());
        return audience;
    }

    private void cancelTask() {
        if (phaseTask != null) {
            phaseTask.cancel();
            phaseTask = null;
        }
    }

    private void cancelPreStartTask() {
        if (preStartTask != null) {
            preStartTask.cancel();
            if (phaseTask == preStartTask) {
                phaseTask = null;
            }
            preStartTask = null;
        }
    }

    private void scheduleActiveTimer(EventType type) {
        cancelActiveTask();
        long seconds = eventConfigService.activePhaseSeconds(type,
                plugin.getConfig().getLong("schedule.active-phase-seconds", 600L));
        if (seconds <= 0L) {
            activeEndsAtMillis = 0L;
            return;
        }
        activeEndsAtMillis = System.currentTimeMillis() + (seconds * 1000L);
        activeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                if (session != null && session.phase() == EventPhase.ACTIVE) {
                    if (session.definition().type() == EventType.QUAKE) {
                        endActiveEvent(rankedByRuntimeScore("quake-score-"));
                    } else if (session.definition().type() == EventType.ONE_IN_THE_CHAMBER) {
                        endActiveEvent(rankedByRuntimeScore("oitc-score-"));
                    } else if (session.definition().type() == EventType.CAPTURE_THE_FLAG) {
                        endActiveEvent(ctfTimerWinners(session));
                    } else if (session.definition().type() == EventType.CAPTURE_PLAYERS) {
                        endActiveEvent(capturePlayersTimerWinners(session));
                    } else {
                        endActiveEvent(List.copyOf(session.finalRankings()));
                    }
                }
            }
        }, seconds * 20L);
    }

    private List<UUID> capturePlayersTimerWinners(EventSession session) {
        if (session == null) {
            return List.of();
        }
        Map<String, Integer> scores = new HashMap<>();
        for (String team : session.teams().values().stream().distinct().toList()) {
            int score = Integer.parseInt(runtimeScoreboardValues.getOrDefault(
                    "capture-players-round-win-" + team.toLowerCase(Locale.ROOT), "0"));
            scores.put(team, score);
        }
        int maxScore = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxScore <= 0) {
            return List.of();
        }
        List<String> winners = scores.entrySet().stream()
                .filter(entry -> entry.getValue() == maxScore)
                .map(Map.Entry::getKey)
                .toList();
        if (winners.size() != 1) {
            return List.of();
        }
        String winner = winners.getFirst();
        return session.teams().entrySet().stream()
                .filter(entry -> winner.equals(entry.getValue()) && session.participants().contains(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void cancelActiveTask() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        activeEndsAtMillis = 0L;
    }

    private void broadcastVotePrompt() {
        Bukkit.broadcast(Component.text("[Events] ", NamedTextColor.GOLD)
                .append(Component.text("An event is starting soon. ", NamedTextColor.YELLOW))
                .append(Component.text("Click Here", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/event vote")))
                .append(Component.text(" or type /event vote to vote on what event to play next.", NamedTextColor.GRAY)));
    }

    private void broadcastJoinPrompt(String eventName, String source) {
        String sourceText = source.equals("vote") ? "The vote chose " + eventName + ". " : eventName + " is open. ";
        Component message = Component.text("[Events] ", NamedTextColor.GOLD)
                .append(Component.text(sourceText, NamedTextColor.YELLOW))
                .append(Component.text("Click to join", NamedTextColor.GREEN).decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/event join")))
                .append(Component.text(" or type /event join.", NamedTextColor.GRAY));
        if (session != null && session.privateSession()) {
            for (UUID uuid : privateAudience()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(message);
                }
            }
            return;
        }
        Bukkit.broadcast(message);
    }

    public boolean teleportGameParticipant(Player player, Location destination) {
        if (player == null || destination == null || session == null || session.phase() != EventPhase.ACTIVE
                || !session.participants().contains(player.getUniqueId()) || session.selectedMap() == null
                || session.selectedMap().region() == null || !session.selectedMap().region().contains(destination)) return false;
        allowTeleport(player.getUniqueId());
        return player.teleport(destination);
    }

    private void allowTeleport(UUID uuid) {
        allowedTeleports.put(uuid, System.currentTimeMillis() + 10_000L);
    }

    private void markEventPlayer(Player player) {
        player.addScoreboardTag(EVENT_SCOREBOARD_TAG);
        player.addScoreboardTag(EVENT_SCOREBOARD_TAG + "_" + session.definition().type().name().toLowerCase(Locale.ROOT));
        player.setMetadata(EVENT_METADATA_KEY, new FixedMetadataValue(plugin, true));
        player.setMetadata(EVENT_METADATA_KEY + "_type", new FixedMetadataValue(plugin, session.definition().type().name()));
    }

    private void unmarkEventPlayer(Player player) {
        player.removeScoreboardTag(EVENT_SCOREBOARD_TAG);
        for (EventType type : EventType.values()) {
            player.removeScoreboardTag(EVENT_SCOREBOARD_TAG + "_" + type.name().toLowerCase(Locale.ROOT));
        }
        player.removeMetadata(EVENT_METADATA_KEY, plugin);
        player.removeMetadata(EVENT_METADATA_KEY + "_type", plugin);
    }

    private void prepareBracketPreStart() {
        if (session == null || !isBracketEvent(session.definition().type()) || session.participants().size() < 2) {
            return;
        }
        if (isTeamBracketEvent(session.definition().type())) {
            prepareTeamBracketPreStart();
            return;
        }
        bracketQueue.clear();
        bracketContestants.clear();
        bracketQueue.addAll(session.participants());
        UUID first = bracketQueue.poll();
        UUID second = bracketQueue.poll();
        if (first != null) {
            bracketContestants.add(first);
        }
        if (second != null) {
            bracketContestants.add(second);
        }
        for (UUID uuid : session.participants()) {
            if (bracketContestants.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                spawnLocked.remove(uuid);
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY
                        + "You are waiting for your bracket match.");
            }
        }
    }

    private boolean handleBracketElimination(Player loser, String reason) {
        if (session == null || session.phase() != EventPhase.ACTIVE
                || !isBracketEvent(session.definition().type())
                || !bracketContestants.contains(loser.getUniqueId())) {
            return false;
        }
        if (isTeamBracketEvent(session.definition().type())) {
            return handleTeamBracketElimination(loser, reason);
        }
        UUID winnerId = bracketContestants.stream()
                .filter(uuid -> !uuid.equals(loser.getUniqueId()) && session.participants().contains(uuid))
                .findFirst()
                .orElse(null);
        bracketContestants.clear();
        session.participants().remove(loser.getUniqueId());
        session.spectators().add(loser.getUniqueId());
        loser.getInventory().clear();
        loser.getInventory().setArmorContents(null);
        loser.getInventory().setItemInOffHand(null);
        loser.setGameMode(GameMode.SPECTATOR);
        plugin.messages().send(loser, "event-eliminated", Map.of("reason", reason));

        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        if (winnerId != null && session.participants().contains(winnerId)) {
            bracketQueue.add(winnerId);
            if (winner != null) {
                winner.setHealth(winner.getMaxHealth());
                winner.setFoodLevel(20);
                winner.setFireTicks(0);
                winner.setGameMode(GameMode.SPECTATOR);
                spawnLocked.remove(winnerId);
                messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                        + winner.getName() + " won the bracket match.");
            }
        }
        scheduleNextBracketMatch();
        return true;
    }

    private void scheduleNextBracketMatch() {
        cancelBracketTask();
        long delay = Math.max(1L, plugin.getConfig().getLong("brackets.match-delay-seconds", 3L)) * 20L;
        bracketTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bracketTask = null;
            startNextBracketMatch();
        }, delay);
    }

    private void startNextBracketMatch() {
        if (session == null || session.phase() != EventPhase.ACTIVE || !isBracketEvent(session.definition().type())) {
            return;
        }
        if (isTeamBracketEvent(session.definition().type())) {
            startNextTeamBracketMatch();
            return;
        }
        bracketQueue.removeIf(uuid -> !session.participants().contains(uuid) || Bukkit.getPlayer(uuid) == null);
        if (bracketQueue.size() <= 1) {
            UUID winner = bracketQueue.poll();
            if (winner == null && session.participants().size() == 1) {
                winner = session.participants().iterator().next();
            }
            endActiveEvent(winner == null ? List.of() : List.of(winner));
            return;
        }
        UUID first = bracketQueue.poll();
        UUID second = bracketQueue.poll();
        bracketContestants.add(first);
        bracketContestants.add(second);
        List<Map.Entry<String, Location>> spawns = session.selectedMap() == null
                ? List.of()
                : List.copyOf(session.selectedMap().spawns().entrySet());
        prepareBracketPlayer(first, spawns.isEmpty() ? null : spawns.get(0).getValue(), 0);
        prepareBracketPlayer(second, spawns.size() < 2 ? (spawns.isEmpty() ? null : spawns.get(0).getValue()) : spawns.get(1).getValue(), 1);
        Player firstPlayer = Bukkit.getPlayer(first);
        Player secondPlayer = Bukkit.getPlayer(second);
        if (firstPlayer != null && secondPlayer != null) {
            messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW
                    + firstPlayer.getName() + " vs " + secondPlayer.getName() + ".");
        }
    }

    private void prepareBracketPlayer(UUID uuid, Location spawn, int teamIndex) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        prepareActivePlayer(player, session.definition().type());
        session.teams().put(uuid, String.valueOf(teamIndex + 1));
        if (spawn != null) {
            allowTeleport(uuid);
            TeleportService.teleport(plugin, player, spawn, "bracket match spawn");
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 1.4F);
    }

    private void prepareTeamBracketPreStart() {
        bracketTeamQueue.clear();
        bracketContestants.clear();
        bracketTeamByPlayer.clear();
        List<UUID> players = session.participants().stream()
                .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                .toList();
        for (int index = 0; index < players.size(); index += 2) {
            List<UUID> team = new ArrayList<>();
            team.add(players.get(index));
            if (index + 1 < players.size()) {
                team.add(players.get(index + 1));
            }
            registerBracketTeam(team);
            bracketTeamQueue.add(team);
        }
        startNextTeamBracketMatch();
    }

    private void startNextTeamBracketMatch() {
        bracketTeamQueue.removeIf(team -> team.stream().noneMatch(uuid -> session.participants().contains(uuid) && Bukkit.getPlayer(uuid) != null));
        if (bracketTeamQueue.size() <= 1) {
            List<UUID> winners = bracketTeamQueue.poll();
            endActiveEvent(winners == null ? List.of() : List.copyOf(winners));
            return;
        }
        List<UUID> firstTeam = bracketTeamQueue.poll();
        List<UUID> secondTeam = bracketTeamQueue.poll();
        if (firstTeam == null || secondTeam == null) {
            endActiveEvent(firstTeam == null ? List.of() : List.copyOf(firstTeam));
            return;
        }
        bracketContestants.clear();
        bracketContestants.addAll(firstTeam);
        bracketContestants.addAll(secondTeam);
        registerBracketTeam(firstTeam);
        registerBracketTeam(secondTeam);
        List<Map.Entry<String, Location>> spawns = session.selectedMap() == null
                ? List.of()
                : List.copyOf(session.selectedMap().spawns().entrySet());
        prepareBracketTeam(firstTeam, spawns, 0);
        prepareBracketTeam(secondTeam, spawns, 1);
        prepareWaitingBracketPlayers();
        messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.YELLOW
                + teamNames(firstTeam) + " vs " + teamNames(secondTeam) + ".");
    }

    private boolean handleTeamBracketElimination(Player loser, String reason) {
        List<UUID> losingTeam = bracketTeamByPlayer.getOrDefault(loser.getUniqueId(), List.of(loser.getUniqueId()));
        session.participants().remove(loser.getUniqueId());
        session.spectators().add(loser.getUniqueId());
        loser.getInventory().clear();
        loser.getInventory().setArmorContents(null);
        loser.getInventory().setItemInOffHand(null);
        loser.setGameMode(GameMode.SPECTATOR);
        plugin.messages().send(loser, "event-eliminated", Map.of("reason", reason));

        boolean teammateAlive = losingTeam.stream()
                .filter(uuid -> !uuid.equals(loser.getUniqueId()))
                .anyMatch(uuid -> bracketContestants.contains(uuid) && session.participants().contains(uuid));
        if (teammateAlive) {
            return true;
        }

        List<UUID> winningTeam = bracketContestants.stream()
                .filter(uuid -> !losingTeam.contains(uuid))
                .map(uuid -> bracketTeamByPlayer.getOrDefault(uuid, List.of(uuid)))
                .findFirst()
                .orElse(List.of());
        bracketContestants.clear();
        List<UUID> onlineWinners = winningTeam.stream()
                .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                .toList();
        if (!onlineWinners.isEmpty()) {
            registerBracketTeam(onlineWinners);
            bracketTeamQueue.add(new ArrayList<>(onlineWinners));
            for (UUID uuid : onlineWinners) {
                Player winner = Bukkit.getPlayer(uuid);
                if (winner == null) {
                    continue;
                }
                session.participants().add(uuid);
                session.spectators().remove(uuid);
                winner.setHealth(winner.getMaxHealth());
                winner.setFoodLevel(20);
                winner.setFireTicks(0);
                winner.setGameMode(GameMode.SPECTATOR);
                spawnLocked.remove(uuid);
            }
            messageEventPlayers(ChatColor.GOLD + "[Events] " + ChatColor.GREEN
                    + teamNames(onlineWinners) + " won the bracket match.");
        }
        scheduleNextBracketMatch();
        return true;
    }

    private void prepareBracketTeam(List<UUID> team, List<Map.Entry<String, Location>> spawns, int teamIndex) {
        for (int offset = 0; offset < team.size(); offset++) {
            UUID uuid = team.get(offset);
            Location spawn = bracketTeamSpawn(spawns, teamIndex, offset);
            prepareBracketPlayer(uuid, spawn, teamIndex);
            session.participants().add(uuid);
            session.spectators().remove(uuid);
        }
    }

    private Location bracketTeamSpawn(List<Map.Entry<String, Location>> spawns, int teamIndex, int offset) {
        if (spawns.isEmpty()) {
            return null;
        }
        int index = Math.min(spawns.size() - 1, (teamIndex * 2) + offset);
        return spawns.get(index).getValue();
    }

    private void prepareWaitingBracketPlayers() {
        for (UUID uuid : session.participants()) {
            if (bracketContestants.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                spawnLocked.remove(uuid);
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.GOLD + "[Events] " + ChatColor.GRAY
                        + "You are waiting for your bracket match.");
            }
        }
    }

    private void registerBracketTeam(List<UUID> team) {
        List<UUID> copy = List.copyOf(team);
        for (UUID uuid : copy) {
            bracketTeamByPlayer.put(uuid, copy);
        }
    }

    private String teamNames(List<UUID> team) {
        return team.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(player -> player.getName() == null ? "?" : player.getName())
                .collect(Collectors.joining(" + "));
    }

    private boolean isBracketEvent(EventType type) {
        return type == EventType.FIGHT_1V1
                || type == EventType.SUMO_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.SUMO_2V2;
    }

    private boolean isTeamBracketEvent(EventType type) {
        return type == EventType.FIGHT_2V2 || type == EventType.SUMO_2V2;
    }

    private void resetBracketState() {
        cancelBracketTask();
        cancelRaceFinishTask();
        bracketQueue.clear();
        bracketTeamQueue.clear();
        bracketContestants.clear();
        bracketTeamByPlayer.clear();
    }

    private void cancelBracketTask() {
        if (bracketTask != null) {
            bracketTask.cancel();
            bracketTask = null;
        }
    }

    private void cancelRaceFinishTask() {
        if (raceFinishTask != null) {
            raceFinishTask.cancel();
            raceFinishTask = null;
        }
    }

    private void prepareActivePlayer(Player player, EventType type) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setAllowFlight(false);
        player.setFlying(false);
        healForEvent(player);
        switch (type) {
            case PARKOUR, BLOCK_PARTY, HOT_POTATO, RED_LIGHT_GREEN_LIGHT -> player.setGameMode(GameMode.ADVENTURE);
            case CAPTURE_PLAYERS -> {
                player.setGameMode(GameMode.SURVIVAL);
                String captureTeam = session != null ? session.teams().get(player.getUniqueId()) : null;
                player.getInventory().setHelmet(captureTeam == null ? new ItemStack(Material.LEATHER_HELMET) : coloredLeatherHelmet(captureTeam));
                player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                player.getInventory().addItem(namedItem(Material.NETHERITE_SWORD, "Capture Sword"));
                player.getInventory().addItem(namedItem(Material.IRON_AXE, "Capture Axe"));
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
                player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
                player.getInventory().setItemInOffHand(namedItem(Material.SHIELD, "Capture Shield"));
            }
            case CAPTURE_THE_FLAG -> {
                player.setGameMode(GameMode.SURVIVAL);
                // Iron leggings, diamond chestplate, diamond boots, team-colored leather helmet
                String ctfTeam = session != null ? session.teams().get(player.getUniqueId()) : null;
                player.getInventory().setHelmet(ctfTeam == null ? new ItemStack(Material.LEATHER_HELMET) : coloredLeatherHelmet(ctfTeam));
                player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                player.getInventory().addItem(namedItem(Material.IRON_SWORD, "CTF Sword"));
                player.getInventory().addItem(namedItem(Material.IRON_AXE, "CTF Axe"));
                player.getInventory().addItem(namedItem(Material.BOW, "CTF Bow"));
                player.getInventory().setItemInOffHand(namedItem(Material.SHIELD, "CTF Shield"));
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 24));
                player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
            }
            case BOAT_RACE -> {
                player.setGameMode(GameMode.SURVIVAL);
            }
            case HORSE_RACE -> {
                player.setGameMode(GameMode.SURVIVAL);
            }
            case ELYTRA_RACE -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().setChestplate(namedItem(Material.ELYTRA, "Race Elytra"));
                player.getInventory().setItem(8, namedItem(Material.RECOVERY_COMPASS, "Checkpoint Return"));
                // One heart only
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(2.0D);
                player.setHealth(2.0D);
            }
            case RUNNER -> player.setGameMode(GameMode.ADVENTURE);
            case SPLEEF -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.DIAMOND_SHOVEL, "Spleef Shovel"));
            }
            case SPLEGG -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.DIAMOND_SHOVEL, "Splegg Shovel"));
            }
            case SUMO_1V1, SUMO_2V2, SUMO_FFA -> {
                player.setGameMode(GameMode.ADVENTURE);
            }
            case KNOCKBACK_FFA -> {
                player.setGameMode(GameMode.ADVENTURE);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 60 * 60, 1, true, false, true));
                ItemStack stick = namedItem(Material.STICK, "Knockback Stick");
                stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
                player.getInventory().addItem(stick);
                player.getInventory().addItem(namedItem(Material.ENDER_PEARL, "Recovery Pearl"));
            }
            case QUAKE -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.GOLDEN_HOE, "Quake Railgun"));
            }
            case FIGHT_1V1, FIGHT_2V2, FIGHT_FFA -> {
                player.setGameMode(GameMode.SURVIVAL);
                if (kitService.winningKit().isPresent()) {
                    kitService.applyWinningKit(player);
                } else {
                    equipArmor(player, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
                    player.getInventory().addItem(namedItem(Material.IRON_SWORD, "Event Sword"));
                    player.getInventory().addItem(namedItem(Material.BOW, "Event Bow"));
                    player.getInventory().addItem(new ItemStack(Material.ARROW, 16));
                    player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
                }
            }
            case ONE_IN_THE_CHAMBER -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().addItem(namedItem(Material.BOW, "One Shot Bow"));
                player.getInventory().addItem(namedItem(Material.IRON_AXE, "One Shot Axe"));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            }
            case SKYWARS -> {
                player.setGameMode(GameMode.SURVIVAL);
            }
            case BEDWARS -> {
                player.setGameMode(GameMode.SURVIVAL);
                // Starter kit: leather armor + wooden sword
                // May need to retry if team not yet assigned
                equipBedWarsStarterKit(player);
            }
            default -> player.setGameMode(GameMode.SURVIVAL);
        }
        player.updateInventory();
    }

    private void scheduleRaceFinishGrace() {
        if (raceFinishTask != null) {
            return;
        }
        long seconds = plugin.getConfig().getLong("elytra-race.finish-grace-seconds", 120L);
        if (seconds <= 0L) {
            return;
        }
        raceFinishTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                raceFinishTask = null;
                if (session != null && session.phase() == EventPhase.ACTIVE
                        && session.definition().type() == EventType.ELYTRA_RACE
                        && !session.finalRankings().isEmpty()) {
                    endActiveEvent(List.copyOf(session.finalRankings()));
                }
            }
        }, seconds * 20L);
    }

    private void restoreTemporaryEventAttributes(Player player) {
        if (session == null || session.definition().type() != EventType.ELYTRA_RACE) {
            return;
        }
        if (!snapshotService.hasSnapshot(player.getUniqueId())) {
            resetStuckMaxHealth(player);
        }
    }

    private ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, 1);
    }

    private ItemStack namedItem(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material);
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void equipArmor(Player player, Material helmet, Material chestplate, Material leggings, Material boots) {
        player.getInventory().setHelmet(new ItemStack(helmet));
        player.getInventory().setChestplate(new ItemStack(chestplate));
        player.getInventory().setLeggings(new ItemStack(leggings));
        player.getInventory().setBoots(new ItemStack(boots));
    }

    private ItemStack leatherArmorItem(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void equipBedWarsStarterKit(Player player) {
        String bwTeam = session != null ? session.teams().get(player.getUniqueId()) : null;
        if (bwTeam == null) {
            // Retry in 1 tick — team assignment may be pending
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (session != null && session.phase() == EventPhase.ACTIVE
                        && session.definition().type() == EventType.BEDWARS
                        && session.participants().contains(player.getUniqueId())) {
                    equipBedWarsStarterKit(player);
                }
            });
            return;
        }
        Color teamColor = switch (bwTeam.toLowerCase(Locale.ROOT)) {
            case "1", "red" -> Color.fromRGB(255, 0, 0);
            case "2", "blue" -> Color.fromRGB(0, 80, 255);
            case "3", "green" -> Color.fromRGB(0, 255, 0);
            case "4", "yellow" -> Color.fromRGB(255, 255, 0);
            case "5", "orange" -> Color.fromRGB(255, 165, 0);
            case "6", "purple" -> Color.fromRGB(128, 0, 128);
            case "7", "cyan" -> Color.fromRGB(0, 255, 255);
            default -> Color.fromRGB(255, 255, 255);
        };
        ItemStack helmet = leatherArmorItem(Material.LEATHER_HELMET, teamColor);
        helmet.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(leatherArmorItem(Material.LEATHER_CHESTPLATE, teamColor));
        player.getInventory().setLeggings(leatherArmorItem(Material.LEATHER_LEGGINGS, teamColor));
        player.getInventory().setBoots(leatherArmorItem(Material.LEATHER_BOOTS, teamColor));
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
        player.getInventory().setItemInOffHand(null);
    }

    private ItemStack coloredLeatherHelmet(String team) {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
            Color color = switch (team == null ? "" : team.toLowerCase(Locale.ROOT)) {
                case "1", "red" -> Color.fromRGB(255, 0, 0);
                case "2", "blue" -> Color.fromRGB(0, 80, 255);
                case "3", "green" -> Color.fromRGB(0, 255, 0);
                case "4", "yellow" -> Color.fromRGB(255, 255, 0);
                default -> Color.fromRGB(255, 255, 255);
            };
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void populateMapContainers(EventMap map, EventType type) {
        if (map == null || (type != EventType.SKYWARS && type != EventType.BEDWARS)) {
            return;
        }
        for (Map.Entry<Integer, List<Location>> entry : map.chests().entrySet()) {
            for (Location location : entry.getValue()) {
                if (location == null || location.getWorld() == null) {
                    continue;
                }
                BlockState state = location.getBlock().getState();
                if (!(state instanceof Container container)) {
                    continue;
                }
                Inventory inventory = container.getInventory();
                inventory.clear();
                placeLootRandomly(inventory, lootTableService.roll(type, entry.getKey()));
            }
        }
    }

    private void placeLootRandomly(Inventory inventory, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            stacks.addAll(splitLootStack(item, random));
        }
        Collections.shuffle(stacks);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        Collections.shuffle(slots);
        int slotIndex = 0;
        for (ItemStack stack : stacks) {
            while (slotIndex < slots.size() && inventory.getItem(slots.get(slotIndex)) != null) {
                slotIndex++;
            }
            if (slotIndex >= slots.size()) {
                inventory.addItem(stack);
                continue;
            }
            inventory.setItem(slots.get(slotIndex++), stack);
        }
    }

    private List<ItemStack> splitLootStack(ItemStack item, ThreadLocalRandom random) {
        int amount = item.getAmount();
        int maxStack = item.getMaxStackSize();
        if (amount <= 1 || maxStack <= 1) {
            return List.of(item);
        }
        int pieces = Math.min(amount, random.nextInt(2, Math.min(4, amount) + 1));
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (int i = pieces; i > 0; i--) {
            int next = i == 1 ? remaining : random.nextInt(1, Math.max(2, remaining - i + 2));
            ItemStack split = item.clone();
            split.setAmount(next);
            result.add(split);
            remaining -= next;
        }
        return result;
    }

    private void cleanupEventInventory(Player player) {
        player.getInventory().remove(Material.ENDER_PEARL);
        player.updateInventory();
    }

    private void healForEvent(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
    }

    private void prepareTrophyPlayer(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(20.0D);
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        healForEvent(player);
        player.updateInventory();
    }

    private void restoreScoreboard(Player player) {
        if (scoreboardService != null) {
            scoreboardService.restore(player);
        }
    }

    private void resetRuntimeServices() {
        if (plugin.speedbuilders() != null) plugin.speedbuilders().stop();
        gameplayRuntimeReset.run();
        if (arenaResetService != null) {
            arenaResetService.reset();
            plugin.recordSessionEvidence(session, "arena-reset pending=" + arenaResetService.pendingBlocks(), null);
        }
        if (session != null && session.definition().type() == EventType.BLOCK_PARTY) {
            prepareBlockPartyFloor(session.selectedMap());
        }
        if (podiumService != null) {
            podiumService.clear();
        }
        if (boatRaceService != null) {
            boatRaceService.cleanupAll();
        }
    }

    private boolean isLastPlayerStandingEvent(EventType type) {
        return type == EventType.SKYWARS
                || type == EventType.BEDWARS
                || type == EventType.FIGHT_1V1
                || type == EventType.FIGHT_2V2
                || type == EventType.FIGHT_FFA
                || type == EventType.SUMO_1V1
                || type == EventType.SUMO_2V2
                || type == EventType.SUMO_FFA
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.ONE_IN_THE_CHAMBER
                || type == EventType.BLOCK_PARTY
                || type == EventType.HOT_POTATO
                || type == EventType.SPLEEF
                || type == EventType.SPLEGG
                || type == EventType.RUNNER;
    }

    private boolean canMoveDuringPreStart(EventType type) {
        return type == EventType.FIGHT_FFA
                || type == EventType.KNOCKBACK_FFA
                || type == EventType.QUAKE
                || type == EventType.BOAT_RACE
                || type == EventType.HORSE_RACE
                || type == EventType.PARKOUR
                || type == EventType.ONE_IN_THE_CHAMBER
                || type == EventType.BLOCK_PARTY
                || type == EventType.SPLEEF
                || type == EventType.SPLEGG
                || type == EventType.RUNNER;
    }

    private void cleanupBoatRacePlayer(UUID uuid) {
        if (boatRaceService != null) {
            boatRaceService.cleanupPlayer(uuid);
        }
    }

    public void remountRaceVehicle(Player player, Location spawn) {
        if (session == null || boatRaceService == null || player == null || spawn == null || !session.participants().contains(player.getUniqueId())) {
            return;
        }
        EventType type = session.definition().type();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (type == EventType.BOAT_RACE) {
                boatRaceService.mountPlayer(player, spawn);
            } else if (type == EventType.HORSE_RACE) {
                boatRaceService.mountHorse(player, spawn);
            }
        });
    }

    private String teamFromSpawnKey(String key, int fallbackIndex) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher namedMatcher = java.util.regex.Pattern.compile("team-(red|blue|green|yellow|orange|purple|cyan|white)").matcher(lower);
        if (namedMatcher.find()) {
            return namedMatcher.group(1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("team-(\\d+)").matcher(lower);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = java.util.regex.Pattern.compile("spawn-?(\\d+)").matcher(lower);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return String.valueOf(fallbackIndex + 1);
    }

    private Location configuredLocation(String path) {
        return LocationCodec.decode(plugin.getConfig().getString(path, ""));
    }

    private void playConfiguredSound(Player player, String path) {
        String soundName = plugin.getConfig().getString(path + ".sound", "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            float volume = (float) plugin.getConfig().getDouble(path + ".volume", 0.7D);
            float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 1.0D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid configured sound at " + path + ": " + soundName);
        }
    }

    private CuboidRegion configuredRegion(String path) {
        Location pos1 = LocationCodec.decode(plugin.getConfig().getString(path + ".pos1", ""));
        Location pos2 = LocationCodec.decode(plugin.getConfig().getString(path + ".pos2", ""));
        if (pos1 == null || pos2 == null) {
            return null;
        }
        try {
            return CuboidRegion.fromCorners(pos1, pos2);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid configured region at " + path + ": " + ex.getMessage());
            return null;
        }
    }
}
