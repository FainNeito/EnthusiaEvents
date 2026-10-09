package org.enthusia.events.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventDefinition;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;
import org.enthusia.events.gui.EventVoteGui;
import org.enthusia.events.stats.EventStatsGuiService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
public final class EventCommand implements CommandExecutor, TabCompleter {

    private final EnthusiaEventsPlugin plugin;
    private final EventManager eventManager;
    private final EventStatsGuiService statsGuiService;
    private final EventVoteGui voteGui;

    public EventCommand(EnthusiaEventsPlugin plugin, EventManager eventManager,
                        EventStatsGuiService statsGuiService, EventVoteGui voteGui) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.statsGuiService = statsGuiService;
        this.voteGui = voteGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            sendActiveEvent(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(player);
            case "spectate" -> spectate(player);
            case "vote" -> openVote(player);
            case "leave" -> leave(player);
            case "start" -> start(player, args);
            case "stats" -> openStats(player);
            case "browse" -> { plugin.eventTools().browse(player); yield true; }
            case "schedule" -> { plugin.eventTools().schedule(player); yield true; }
            case "next", "time", "timer" -> sendNextEvent(player);
            default -> false;
        };
    }

    private void sendActiveEvent(Player player) {
        EventSession session = eventManager.session();
        if (session == null) {
            plugin.messages().send(player, "event-not-running");
            return;
        }
        plugin.messages().send(player, "active-event", Map.of(
                "phase", session.phase().name(),
                "event", session.definition().displayName()
        ));
    }

    private boolean join(Player player) {
        if (!eventManager.join(player)) {
            EventSession session = eventManager.session();
            if (session == null) {
                plugin.messages().send(player, "event-not-running");
            } else {
                plugin.messages().send(player, "event-join-closed", Map.of("phase", session.phase().name()));
            }
        }
        return true;
    }

    private boolean spectate(Player player) {
        if (!eventManager.spectate(player)) {
            plugin.messages().send(player, "event-spectate-failed");
        }
        return true;
    }

    private boolean openVote(Player player) {
        voteGui.open(player);
        return true;
    }

    private boolean leave(Player player) {
        if (!eventManager.leave(player)) {
            plugin.messages().send(player, "event-not-running");
        }
        return true;
    }

    private boolean start(Player player, String[] args) {
        if (eventManager.hasSession()) {
            sendStartFailure(player, null);
            return true;
        }
        if (args.length < 2) {
            voteGui.openStart(player);
            return true;
        }
        EventDefinition selected = findDefinition(args[1]);
        if (selected == null) {
            plugin.messages().send(player, "invalid-event", Map.of("event", args[1]));
            return true;
        }
        if (!eventManager.startManualVote(player, selected, false)) {
            sendStartFailure(player, selected);
        }
        return true;
    }

    private EventDefinition findDefinition(String rawType) {
        try {
            return plugin.eventRegistry().definition(EventType.parse(rawType));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void sendStartFailure(Player player, EventDefinition selected) {
        plugin.messages().send(player, "event-start-failed", Map.of(
                "reason", eventManager.manualStartFailureReason(player, selected, false)
        ));
    }

    private boolean openStats(Player player) {
        statsGuiService.openMain(player);
        return true;
    }

    private boolean sendNextEvent(Player player) {
        plugin.messages().send(player, "next-event", Map.of("time", eventManager.nextHourlyVoteLabel()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return AdminCommandSupport.filter(
                    args[0],
                    List.of("join", "leave", "spectate", "vote", "start", "stats", "next", "time", "timer")
            );
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return AdminCommandSupport.filter(
                    args[1],
                    List.of(EventType.values()).stream().map(Enum::name).toList()
            );
        }
        return List.of();
    }
}
