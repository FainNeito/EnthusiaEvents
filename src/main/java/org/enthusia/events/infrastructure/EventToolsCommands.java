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
    private static final int TARGET_ARGUMENT_COUNT = 3;
    private static final int FINDING_LIMIT = 24;
    private static final int EVENT_ARGUMENT_COUNT = 2;
    private static final int LOOT_ARGUMENT_COUNT = 3;
    private static final int EDIT_ARGUMENT_COUNT = 8;
    private static final int LIMIT_ARGUMENT_COUNT = 7;
    private final EnthusiaEventsPlugin plugin;
    private final EventManager manager;
    private final EventToolsGui gui;
    private final LootTableService lootTables;
    private final ArenaResetService reset;
    public EventToolsCommands(EnthusiaEventsPlugin plugin, EventManager manager, EventToolsGui gui,
                              LootTableService loot, ArenaResetService reset) {
        this.plugin = plugin; this.manager = manager; this.gui = gui; this.lootTables = loot; this.reset = reset;
    }
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 || !List.of("readiness", "loot", "trace", "resetarena", "preflight", "recovery").contains(args[0].toLowerCase(Locale.ROOT))) return false;
        if (!sender.hasPermission("enthusia.events.admin")) { sender.sendMessage("No permission."); return true; }
        try {
            dispatch(sender, args);
        } catch (IllegalArgumentException failure) { sender.sendMessage(failure.getMessage()); }
        catch (IOException failure) { sender.sendMessage("Tool storage operation failed; retained files require inspection. See console."); plugin.getLogger().warning("Event tool storage failed: " + failure); }
        return true;
    }
    private void dispatch(CommandSender sender, String[] args) throws IOException {
        switch (args[0].toLowerCase(Locale.ROOT)) {
                case "preflight" -> preflight(sender, args);
                case "recovery" -> recovery(sender, args);
                case "trace" -> trace(sender, args);
                case "resetarena" -> retryReset(sender);
                case "readiness" -> readiness(sender, args);
                case "loot" -> loot(sender, args);
                default -> { }
        }
    }
    private void retryReset(CommandSender sender) {
        if (manager.hasSession()) throw new IllegalArgumentException("Stop the event before retrying arena reset.");
        reset.reset();
        String failure = reset.failure();
        sender.sendMessage("Arena reset pending blocks: " + reset.pendingBlocks() + "; failure: " + (failure == null ? "none" : failure));
    }
    private void readiness(CommandSender sender, String[] args) {
        if (args.length < EVENT_ARGUMENT_COUNT) throw new IllegalArgumentException("/ee readiness <EVENT>");
        gui.readiness(player(sender), EventType.parse(args[1]), 0);
    }
    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw new IllegalArgumentException("Open this view in-game.");
    }
    private void preflight(CommandSender sender, String[] args) {
        if (args.length != TARGET_ARGUMENT_COUNT) throw new IllegalArgumentException("/ee preflight <EVENT> <map>");
        EventType type = EventType.parse(args[1]);
        var map = plugin.maps().find(type, args[2]).orElseThrow(() -> new IllegalArgumentException("Unknown map"));
        var findings = plugin.arenaPreflight().inspect(map);
        sender.sendMessage("Preflight " + type + "/" + map.id() + " world=" + map.worldName() + ": "
                + (ArenaPreflight.ready(findings) && !manager.isArenaResetting() ? "READY" : "BLOCKED/UNKNOWN"));
        if (manager.isArenaResetting()) sender.sendMessage("Arena recovery/reset is outstanding; activation remains blocked.");
        findings.stream().limit(FINDING_LIMIT).map(org.enthusia.events.domain.PreflightFinding::text).forEach(sender::sendMessage);
        if (findings.size() > FINDING_LIMIT) sender.sendMessage("Additional findings omitted; repair listed blockers and inspect again.");
    }
    private void recovery(CommandSender sender, String[] args) throws IOException {
        if (!sender.hasPermission("enthusia.events.recovery.inspect")) throw new IllegalArgumentException("No recovery inspection permission.");
        if (args.length < EVENT_ARGUMENT_COUNT) throw recoveryUsage();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "preview", "reconcile" -> recoveryPreview(sender, args);
            case "confirm" -> recoveryConfirm(sender, args);
            case "prune" -> recoveryPrune(sender, args);
            case "player" -> recoveryPlayer(sender, args);
            default -> recoveryArena(sender, args);
        }
    }
    private void recoveryPreview(CommandSender sender, String[] args) throws IOException {
        boolean reconcile = args[1].equalsIgnoreCase("reconcile");
        int reasonIndex = reconcile ? TARGET_ARGUMENT_COUNT : TARGET_ARGUMENT_COUNT + 1;
        if (args.length <= reasonIndex) throw new IllegalArgumentException("Supply target UUID, capture UUID (preview only), and a reconciliation reason");
        UUID target = UUID.fromString(args[2]);
        UUID capture = reconcile ? target : UUID.fromString(args[3]);
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonIndex, args.length));
        plugin.snapshots().previewHistorical(sender, plugin.getServer().getPlayer(target), capture, reason, reconcile).forEach(sender::sendMessage);
    }
    private void recoveryConfirm(CommandSender sender, String[] args) throws IOException {
        requireTargetArgs(args);
        sender.sendMessage(plugin.snapshots().confirmHistorical(sender, UUID.fromString(args[2])));
    }
    private void recoveryPrune(CommandSender sender, String[] args) throws IOException {
        requireTargetArgs(args);
        if (!sender.hasPermission("enthusia.events.recovery.apply")) throw new IllegalArgumentException("No recovery apply permission");
        plugin.snapshots().pruneHistory(UUID.fromString(args[2])).forEach(sender::sendMessage);
    }
    private void recoveryPlayer(CommandSender sender, String[] args) throws IOException {
        requireTargetArgs(args);
        UUID target = UUID.fromString(args[2]);
        sender.sendMessage("Recovery state: " + plugin.snapshots().recoveryState(target));
        plugin.snapshots().history(target).forEach(sender::sendMessage);
    }
    private static void requireTargetArgs(String[] args) {
        if (args.length != TARGET_ARGUMENT_COUNT) throw recoveryUsage();
    }
    private void recoveryArena(CommandSender sender, String[] args) {
        if (args.length != EVENT_ARGUMENT_COUNT) throw recoveryUsage();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list", "inspect" -> sender.sendMessage(plugin.arenaRecovery().status());
            case "retry" -> recoveryRetry(sender);
            default -> throw recoveryUsage();
        }
    }
    private void recoveryRetry(CommandSender sender) {
        if (!sender.hasPermission("enthusia.events.recovery.retry")) throw new IllegalArgumentException("No recovery retry permission.");
        if (manager.hasSession()) throw new IllegalArgumentException("Stop the event before recovery retry.");
        if (reset.pendingBlocks() > 0) throw new IllegalArgumentException("Retry retained legacy blocks with /ee resetarena before durable recovery.");
        plugin.arenaRecovery().retry(); sender.sendMessage(plugin.arenaRecovery().status());
    }
    private static IllegalArgumentException recoveryUsage() {
        return new IllegalArgumentException("/ee recovery <list|inspect|retry|player|prune <uuid>|preview <uuid> <capture> <reason>|reconcile <uuid> <reason>|confirm <token>>");
    }
    private void loot(CommandSender sender, String[] args) throws IOException {
        if (args.length < LOOT_ARGUMENT_COUNT) throw new IllegalArgumentException("/ee loot <SKYWARS|BEDWARS> <1-3> [sample|edit <id> <min> <max> <chance 0-1>|limits <armor> <weapons> <total>]");
        EventType type = EventType.parse(args[1]); int tier = Integer.parseInt(args[2]);
        lootTables.inspect(type, tier); // validate event/tier for both reads and writes
        if (args.length == LOOT_ARGUMENT_COUNT || args[3].equalsIgnoreCase("sample")) {
            gui.loot(player(sender), type, tier, 0, args.length > LOOT_ARGUMENT_COUNT); return;
        }
        if (manager.hasSession() || manager.isArenaResetting()) throw new IllegalArgumentException("Loot edits are blocked while an event or arena reset is in progress.");
        saveLoot(type, tier, args);
        sender.sendMessage("Loot saved and reloaded. Use /ee loot " + type + " " + tier + " sample to inspect rolls.");
    }
    private void saveLoot(EventType type, int tier, String[] args) throws IOException {
        if (args[3].equalsIgnoreCase("edit") && args.length == EDIT_ARGUMENT_COUNT) {
            lootTables.edit(type, tier, args[4], Integer.parseInt(args[5]), Integer.parseInt(args[6]), Double.parseDouble(args[7]));
        } else if (args[3].equalsIgnoreCase("limits") && args.length == LIMIT_ARGUMENT_COUNT) {
            lootTables.setLimits(type, tier, new LootLimits(Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6])));
        } else throw new IllegalArgumentException("Use edit <id> <min> <max> <chance>, or limits <armor> <weapons> <total>.");
    }
    private void trace(CommandSender sender, String[] args) {
        UUID id = args.length > 1 ? UUID.fromString(args[1]) : manager.session() == null ? null : manager.session().id();
        var lines = plugin.traceWriter().recent(id);
        sender.sendMessage("Session trace " + (id == null ? "recent" : id) + "; file logs/sessions.log; dropped: " + plugin.traceWriter().dropped());
        lines.stream().skip(Math.max(0, lines.size() - 12)).forEach(sender::sendMessage);
    }
}
