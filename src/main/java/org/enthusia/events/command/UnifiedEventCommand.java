package org.enthusia.events.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
public final class UnifiedEventCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS = List.of(
            "join", "leave", "spectate", "vote", "start", "stats", "next", "time", "timer", "browse", "schedule", "season"
    );
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
            "autostart", "disable", "enable", "enabled", "disabled", "status", "private", "invite",
            "forcestart", "simulatevote", "advance", "forcestop", "eventtp", "stop", "restore", "stuckcheck",
            "emergencyrestore", "remove", "reload", "retryrestores", "kit", "resetconfigs", "resetloot", "map", "setup", "readiness", "loot", "trace", "resetarena", "preflight", "recovery"
    );

    private final EventCommand eventCommand;
    private final AdminCommand adminCommand;

    public UnifiedEventCommand(EventCommand eventCommand, AdminCommand adminCommand) {
        this.eventCommand = eventCommand;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || isPlayerSubcommand(args[0])) {
            return eventCommand.onCommand(sender, command, label, args);
        }
        return adminCommand.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Set<String> commands = new LinkedHashSet<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission("enthusia.events.admin")) {
                commands.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(args[0], new ArrayList<>(commands));
        }
        if (isPlayerSubcommand(args[0])) {
            return eventCommand.onTabComplete(sender, command, alias, args);
        }
        if (sender.hasPermission("enthusia.events.admin") && isAdminSubcommand(args[0])) {
            return adminCommand.onTabComplete(sender, command, alias, args);
        }
        return List.of();
    }

    private boolean isPlayerSubcommand(String value) {
        return PLAYER_SUBCOMMANDS.contains(value.toLowerCase(Locale.ROOT));
    }

    private boolean isAdminSubcommand(String value) {
        return ADMIN_SUBCOMMANDS.contains(value.toLowerCase(Locale.ROOT));
    }

    private List<String> filter(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
