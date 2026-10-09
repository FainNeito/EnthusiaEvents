package org.enthusia.events.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.MapCopyService;
import org.enthusia.events.event.MapSetupService;
import org.enthusia.events.gui.RestoreConfirmGui;
import org.enthusia.events.kit.EventKitService;
import org.enthusia.events.setup.SetupWizard;

import java.util.List;
import java.util.Locale;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final EnthusiaEventsPlugin plugin;
    private final AdminCoreCommandHandler coreHandler;
    private final AdminMapCommandHandler mapHandler;
    private final AdminSetupCommandHandler setupHandler;
    private final AdminKitCommandHandler kitHandler;
    private final AdminCommandTabCompleter tabCompleter;

    public AdminCommand(EnthusiaEventsPlugin plugin, EventManager eventManager, MapSetupService mapSetupService,
                        SetupWizard setupWizard, MapCopyService mapCopyService, RestoreConfirmGui restoreConfirmGui,
                        EventKitService kitService) {
        this.plugin = plugin;
        this.coreHandler = new AdminCoreCommandHandler(plugin, eventManager, mapSetupService, restoreConfirmGui);
        this.mapHandler = new AdminMapCommandHandler(plugin, eventManager, mapSetupService, mapCopyService);
        this.setupHandler = new AdminSetupCommandHandler(plugin, mapSetupService, setupWizard);
        this.kitHandler = new AdminKitCommandHandler(plugin, kitService);
        this.tabCompleter = new AdminCommandTabCompleter(mapSetupService, mapCopyService, kitService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enthusia.events.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            return false;
        }
        if (plugin.toolsCommands().handle(sender, args)) return true;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "map" -> mapHandler.handle(sender, args);
            case "setup" -> setupHandler.handle(sender, args);
            case "kit" -> kitHandler.handle(sender, args);
            default -> coreHandler.handle(sender, args);
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabCompleter.complete(args);
    }
}
