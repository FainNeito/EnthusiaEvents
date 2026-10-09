package org.enthusia.events.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.enthusia.events.event.EventMap;
import org.enthusia.events.event.EventType;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.kit.EventKitService;

import java.util.List;
import java.util.Locale;

final class AdminCommandTabCompleter {

    private static final String LOOT_COMMAND = "loot";
    private static final List<String> ROOT_COMMANDS = List.of(
            "autostart", "disable", "enable", "enabled", "disabled", "status", "private", "invite",
            "forcestart", "simulatevote", "advance", "forcestop", "eventtp", "stop", "restore",
            "stuckcheck", "emergencyrestore", "remove", "reload", "retryrestores", "kit",
            "resetconfigs", "resetloot", "map", "setup", "readiness", LOOT_COMMAND, "trace", "resetarena"
    );

    private final MapSetupService mapSetupService;
    private final EventKitService kitService;
    private final AdminMapTabCompleter mapCompleter;
    private final AdminSetupTabCompleter setupCompleter;

    AdminCommandTabCompleter(MapSetupService mapSetupService, MapCopyService mapCopyService,
                             EventKitService kitService) {
        this.mapSetupService = mapSetupService;
        this.kitService = kitService;
        this.mapCompleter = new AdminMapTabCompleter(mapSetupService, mapCopyService);
        this.setupCompleter = new AdminSetupTabCompleter(mapSetupService);
    }

    List<String> complete(String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("private")) {
            return onlinePlayers(args[args.length - 1]);
        }
        if (args[0].equalsIgnoreCase("map")) {
            return mapCompleter.complete(args);
        }
        if (args[0].equalsIgnoreCase("setup")) {
            return setupCompleter.complete(args);
        }
        return switch (args.length) {
            case 1 -> AdminCommandSupport.filter(args[0], ROOT_COMMANDS);
            case 2 -> completeSecondArgument(args);
            case 3 -> completeThirdArgument(args);
            case 4 -> completeFourthArgument(args);
            default -> List.of();
        };
    }

    private List<String> completeSecondArgument(String[] args) {
        String command = args[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "disable", "enable", "private", "forcestart", "eventtp", "eventteleport", "readiness", "loot" ->
                    AdminCommandSupport.filter(args[1], eventTypes());
            case "invite", "restore", "stuckcheck", "emergencyrestore", "remove" -> onlinePlayers(args[1]);
            case "autostart" -> AdminCommandSupport.filter(args[1], List.of("on", "off", "status"));
            case "kit" -> AdminCommandSupport.filter(args[1], List.of("save", "list", "select", "override"));
            default -> List.of();
        };
    }

    private List<String> completeThirdArgument(String[] args) {
        String command = args[0].toLowerCase(Locale.ROOT);
        if (command.equals("loot")) return AdminCommandSupport.filter(args[2], List.of("1", "2", "3"));
        if (command.equals("kit") && (args[1].equalsIgnoreCase("select") || args[1].equalsIgnoreCase("override"))) {
            return AdminCommandSupport.filter(args[2], kitNames());
        }
        if (command.equals("eventtp") || command.equals("eventteleport")) {
            EventType type = AdminCommandSupport.parseEventSilently(args[1]);
            return type == null ? List.of() : AdminCommandSupport.filter(args[2], mapIds(type));
        }
        return List.of();
    }

    private List<String> completeFourthArgument(String[] args) {
        if (args[0].equalsIgnoreCase(LOOT_COMMAND)) return AdminCommandSupport.filter(args[3], List.of("sample", "edit", "limits"));
        if (args[0].equalsIgnoreCase("kit") && args[1].equalsIgnoreCase("override")) {
            return AdminCommandSupport.filter(args[3], kitNames());
        }
        return List.of();
    }

    private List<String> onlinePlayers(String input) {
        return AdminCommandSupport.filter(input, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    private List<String> eventTypes() {
        return List.of(EventType.values()).stream().map(Enum::name).toList();
    }

    private List<String> kitNames() {
        return kitService.kits().stream().map(EventKitService.EventKit::name).toList();
    }

    private List<String> mapIds(EventType type) {
        return mapSetupService.mapsFor(type).stream().map(EventMap::id).toList();
    }
}
