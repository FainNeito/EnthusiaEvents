package org.enthusia.events.infrastructure;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.domain.LootLimits;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventType;
import org.enthusia.events.loot.LootTableService;
import org.enthusia.events.reset.ArenaResetService;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Validated tooling separated from the legacy command dispatcher. */
public final class EventToolsCommands {
    private final EnthusiaEventsPlugin plugin;
    private final EventManager manager;
    private final EventToolsGui gui;
    private final LootTableService loot;
    private final ArenaResetService reset;
    public EventToolsCommands(EnthusiaEventsPlugin plugin, EventManager manager, EventToolsGui gui,
                              LootTableService loot, ArenaResetService reset) {
        this.plugin = plugin; this.manager = manager; this.gui = gui; this.loot = loot; this.reset = reset;
    }
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 || !List.of("readiness", "loot", "trace", "resetarena").contains(args[0].toLowerCase(Locale.ROOT))) return false;
        if (!sender.hasPermission("enthusia.events.admin")) { sender.sendMessage("No permission."); return true; }
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "trace" -> trace(sender, args);
                case "resetarena" -> {
                    if (manager.hasSession()) throw new IllegalArgumentException("Stop the event before retrying arena reset.");
                    reset.reset();
                    sender.sendMessage("Arena reset pending blocks: " + reset.pendingBlocks() + "; failure: " + reset.failure());
                }
                case "readiness" -> {
                    if (args.length < 2) throw new IllegalArgumentException("/ee readiness <EVENT>");
                    gui.readiness(player(sender), EventType.parse(args[1]), 0);
                }
                case "loot" -> loot(sender, args);
                default -> { }
            }
        } catch (IllegalArgumentException failure) { sender.sendMessage(failure.getMessage()); }
        catch (IOException failure) { sender.sendMessage("Save failed; previous loot remains active. See console."); plugin.getLogger().warning("Loot save failed: " + failure); }
        return true;
    }
    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw new IllegalArgumentException("Open this view in-game.");
    }
    private void loot(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) throw new IllegalArgumentException("/ee loot <SKYWARS|BEDWARS> <1-3> [sample|edit <id> <min> <max> <chance 0-1>|limits <armor> <weapons> <total>]");
        EventType type = EventType.parse(args[1]); int tier = Integer.parseInt(args[2]);
        loot.inspect(type, tier); // validate event/tier for both reads and writes
        if (args.length == 3 || args[3].equalsIgnoreCase("sample")) {
            gui.loot(player(sender), type, tier, 0, args.length > 3); return;
        }
        if (manager.hasSession() || manager.isArenaResetting()) throw new IllegalArgumentException("Loot edits are blocked while an event or arena reset is in progress.");
        if (args[3].equalsIgnoreCase("edit") && args.length == 8) {
            loot.edit(type, tier, args[4], Integer.parseInt(args[5]), Integer.parseInt(args[6]), Double.parseDouble(args[7]));
        } else if (args[3].equalsIgnoreCase("limits") && args.length == 7) {
            loot.setLimits(type, tier, new LootLimits(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6])));
        } else throw new IllegalArgumentException("Use edit <id> <min> <max> <chance>, or limits <armor> <weapons> <total>.");
        sender.sendMessage("Loot saved and reloaded. Use /ee loot " + type + " " + tier + " sample to inspect rolls.");
    }
    private void trace(CommandSender sender, String[] args) {
        UUID id = args.length > 1 ? UUID.fromString(args[1]) : manager.session() == null ? null : manager.session().id();
        var lines = plugin.traceWriter().recent(id);
        sender.sendMessage("Session trace " + (id == null ? "recent" : id) + "; file logs/sessions.log; dropped: " + plugin.traceWriter().dropped());
        lines.stream().skip(Math.max(0, lines.size() - 12)).forEach(sender::sendMessage);
    }
}
