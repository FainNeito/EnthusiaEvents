package org.enthusia.events.event;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.config.EventConfigService;
import org.enthusia.events.stats.EventStatsService;
import org.enthusia.events.stats.PlayerEventStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Normalizes end-of-event outcome bookkeeping without changing the event manager's
 * intentionally sticky winner list. A player who has already won remains a winner even
 * if they leave before the trophy phase finishes.
 */
public final class EventOutcomeService {

    private static final Set<EventType> TEAM_RESULT_TYPES = Set.of(
            EventType.CAPTURE_THE_FLAG,
            EventType.CAPTURE_PLAYERS,
            EventType.BEDWARS,
            EventType.FIGHT_2V2,
            EventType.SUMO_2V2
    );
    private static final Set<EventType> TEAM_RESULT_ANNOUNCEMENT_FIX_TYPES = Set.of(
            EventType.BEDWARS,
            EventType.FIGHT_2V2,
            EventType.SUMO_2V2
    );
    private static final Set<EventType> GENERIC_LAST_STANDING_TYPES = Set.of(
            EventType.SKYWARS,
            EventType.FIGHT_FFA,
            EventType.SUMO_FFA,
            EventType.KNOCKBACK_FFA,
            EventType.ONE_IN_THE_CHAMBER,
            EventType.BLOCK_PARTY,
            EventType.HOT_POTATO,
            EventType.SPLEEF,
            EventType.SPLEGG,
            EventType.RUNNER
    );

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final EventStatsService statsService;
    private final Economy economy;
    private final EventConfigService eventConfigService;
    private final Set<UUID> prestartRoster = new LinkedHashSet<>();
    private final Set<UUID> activeRoster = new LinkedHashSet<>();
    private final Map<UUID, OutcomeBaseline> baselines = new HashMap<>();

    private EventSession trackedSession;
    private EventPhase lastPhase;
    private BukkitTask task;
    private boolean activeRosterFrozen;
    private boolean outcomeReconciled;
    private EventType completedType;
    private List<UUID> completedRankings = List.of();
    private double pendingReward;
    private double firstWinnerBalanceBeforeReward;
    private boolean firstWinnerBalanceCaptured;

    public EventOutcomeService(EnthusiaEventsPlugin plugin, EventManager eventManager,
                               EventStatsService statsService, Economy economy,
                               EventConfigService eventConfigService) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.statsService = statsService;
        this.economy = economy;
        this.eventConfigService = eventConfigService;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        trackedSession = null;
        lastPhase = null;
        clearSessionTracking();
    }

    private void tick() {
        EventSession session = eventManager.session();
        if (session != trackedSession) {
            if (trackedSession != null) {
                payMissingTeamRewardsAfterNormalFinish();
            }
            trackedSession = session;
            lastPhase = null;
            clearSessionTracking();
        }
        if (session == null) {
            return;
        }

        if (session.phase() == EventPhase.PRESTART) {
            for (UUID uuid : session.participants()) {
                prestartRoster.add(uuid);
                rememberBaseline(uuid, session.definition().type());
            }
        }

        if (session.phase() == EventPhase.ACTIVE) {
            if (!activeRosterFrozen) {
                freezeActiveRoster(session);
            }
            enforceGenericLastStanding(session);
        }

        if (session.phase() == EventPhase.TROPHY && !outcomeReconciled) {
            reconcileOutcome(session);
        }

        lastPhase = session.phase();
    }

    private void freezeActiveRoster(EventSession session) {
        activeRosterFrozen = true;
        for (UUID uuid : prestartRoster) {
            if (session.participants().contains(uuid) || session.spectators().contains(uuid)) {
                activeRoster.add(uuid);
            }
        }
        activeRoster.addAll(session.participants());
        for (UUID uuid : activeRoster) {
            rememberBaseline(uuid, session.definition().type());
        }
    }

    private void rememberBaseline(UUID uuid, EventType type) {
        baselines.computeIfAbsent(uuid, ignored -> OutcomeBaseline.capture(statsService.stats(uuid), type));
    }

    private void enforceGenericLastStanding(EventSession session) {
        if (!GENERIC_LAST_STANDING_TYPES.contains(session.definition().type())
                || session.participants().size() != 1) {
            return;
        }
        eventManager.endActiveEventDelayed(List.copyOf(session.participants()), 60L);
    }

    private void reconcileOutcome(EventSession session) {
        outcomeReconciled = true;
        completedType = session.definition().type();
        completedRankings = List.copyOf(session.finalRankings());
        if (completedRankings.isEmpty()) {
            return;
        }

        Set<UUID> winners = intendedWinners(completedType, completedRankings);
        for (UUID uuid : activeRoster) {
            OutcomeBaseline baseline = baselines.get(uuid);
            if (baseline == null) {
                continue;
            }
            statsService.stats(uuid).reconcileOutcome(
                    completedType,
                    baseline.totalWins(),
                    baseline.totalLosses(),
                    baseline.winStreak(),
                    baseline.bestStreak(),
                    baseline.eventWins(),
                    baseline.eventLosses(),
                    winners.contains(uuid)
            );
        }
        statsService.save();

        if (TEAM_RESULT_ANNOUNCEMENT_FIX_TYPES.contains(completedType) && completedRankings.size() > 1) {
            String message = ChatColor.GOLD + "[Events] " + ChatColor.GREEN + "Winning team: "
                    + ChatColor.WHITE + playerNames(completedRankings) + ".";
            if (session.privateSession()) {
                eventManager.messageEventPlayers(message);
            } else {
                Bukkit.broadcastMessage(message);
            }
        }

        prepareMissingTeamRewardCheck(completedType, completedRankings);
    }

    private Set<UUID> intendedWinners(EventType type, List<UUID> rankings) {
        if (rankings.isEmpty()) {
            return Set.of();
        }
        if (TEAM_RESULT_TYPES.contains(type)) {
            return new LinkedHashSet<>(rankings);
        }
        return Set.of(rankings.getFirst());
    }

    private void prepareMissingTeamRewardCheck(EventType type, List<UUID> rankings) {
        if (economy == null || !TEAM_RESULT_ANNOUNCEMENT_FIX_TYPES.contains(type) || rankings.size() <= 1) {
            return;
        }
        pendingReward = eventConfigService.winnerReward(type,
                plugin.getConfig().getDouble("economy.winner-reward", 100.0D));
        if (pendingReward <= 0.0D) {
            return;
        }
        OfflinePlayer firstWinner = Bukkit.getOfflinePlayer(rankings.getFirst());
        firstWinnerBalanceBeforeReward = economy.getBalance(firstWinner);
        firstWinnerBalanceCaptured = true;
    }

    private void payMissingTeamRewardsAfterNormalFinish() {
        if (!outcomeReconciled || economy == null || !firstWinnerBalanceCaptured
                || pendingReward <= 0.0D || completedRankings.size() <= 1
                || !TEAM_RESULT_ANNOUNCEMENT_FIX_TYPES.contains(completedType)) {
            return;
        }

        OfflinePlayer firstWinner = Bukkit.getOfflinePlayer(completedRankings.getFirst());
        double firstWinnerBalanceAfter = economy.getBalance(firstWinner);
        if (firstWinnerBalanceAfter + 0.000_001D < firstWinnerBalanceBeforeReward + pendingReward) {
            plugin.getLogger().warning("Skipped supplemental team winner payouts for " + completedType
                    + " because the normal first-winner payout was not observed.");
            return;
        }

        for (int index = 1; index < completedRankings.size(); index++) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(completedRankings.get(index));
            economy.depositPlayer(winner, pendingReward);
            Player online = winner.getPlayer();
            if (online != null) {
                plugin.messages().send(online, "event-winner-paid",
                        Map.of("amount", String.valueOf(pendingReward)));
            }
        }
    }

    private String playerNames(List<UUID> players) {
        return players.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(player -> player.getName() == null ? player.getUniqueId().toString() : player.getName())
                .collect(Collectors.joining(", "));
    }

    private void clearSessionTracking() {
        prestartRoster.clear();
        activeRoster.clear();
        baselines.clear();
        activeRosterFrozen = false;
        outcomeReconciled = false;
        completedType = null;
        completedRankings = List.of();
        pendingReward = 0.0D;
        firstWinnerBalanceBeforeReward = 0.0D;
        firstWinnerBalanceCaptured = false;
    }

    private record OutcomeBaseline(int totalWins, int totalLosses, int winStreak, int bestStreak,
                                   int eventWins, int eventLosses) {
        private static OutcomeBaseline capture(PlayerEventStats stats, EventType type) {
            return new OutcomeBaseline(
                    stats.wins(),
                    stats.losses(),
                    stats.winStreak(),
                    stats.bestStreak(),
                    stats.winsByEvent().getOrDefault(type, 0),
                    stats.lossesByEvent().getOrDefault(type, 0)
            );
        }
    }
}
