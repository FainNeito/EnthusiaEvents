package org.enthusia.events.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.util.LocationCodec;
import org.enthusia.events.util.TeleportService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.enthusia.events.infrastructure.AtomicFiles;
import org.enthusia.events.infrastructure.PlayerSnapshotArchive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerSnapshotService {

    private static final long SNAPSHOT_TTL_MILLIS = Duration.ofDays(7).toMillis();
    private static final int[] RESTORE_RETRY_DELAYS_TICKS = {20, 40, 80, 120};

    private final EnthusiaEventsPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> capturedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> restored = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnedOldUnrestored = new ConcurrentHashMap<>();
    private final PlayerSnapshotArchive archive;
    private final Map<UUID, UUID> archiveIds = new ConcurrentHashMap<>();
    private boolean unreadableStorage;
    private final org.enthusia.events.infrastructure.RecoveryLeaseStore leases;
    private final Map<UUID, HistoricalConfirmation> confirmations = new java.util.HashMap<>();

    public PlayerSnapshotService(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "snapshots.yml");
        this.archive = new PlayerSnapshotArchive(plugin.getDataFolder().toPath().resolve("recovery/players"));
        this.leases = new org.enthusia.events.infrastructure.RecoveryLeaseStore(plugin.getDataFolder().toPath().resolve("recovery/player-leases"));
        load();
    }

    public boolean capture(Player player) {
        purgeExpiredRestored();
        if (unreadableStorage || recoveryBlocked(player.getUniqueId())) return false;
        if (hasUnrestoredSnapshot(player.getUniqueId())) {
            plugin.getLogger().warning("Refusing to overwrite unrestored event snapshot for " + player.getName()
                    + ". Restore or discard the existing snapshot first.");
            return false;
        }
        UUID uuid = player.getUniqueId();
        PlayerSnapshot previous = snapshots.get(uuid);
        Long previousTime = capturedAt.get(uuid);
        Boolean previousRestored = restored.get(uuid);
        UUID previousId = archiveIds.get(uuid);
        PlayerSnapshot captured;
        try { captured = PlayerSnapshot.capture(player); }
        catch (RuntimeException failure) { plugin.getLogger().log(Level.SEVERE, "Snapshot capture rejected before mutation", failure); return false; }
        snapshots.put(uuid, captured);
        capturedAt.put(uuid, System.currentTimeMillis());
        restored.put(uuid, false);
        UUID captureId = UUID.randomUUID();
        try {
            if (plugin.getConfig().getBoolean("recovery.player-history.enabled", false)) {
                archiveIds.put(uuid, captureId);
                YamlConfiguration record = new YamlConfiguration();
                record.set("archive-schema", 1);
                record.set("session-id", plugin.currentSessionId() == null ? "manual" : plugin.currentSessionId().toString());
                writeSnapshot(record, uuid, snapshots.get(uuid));
                archive.capture(uuid, captureId, capturedAt.get(uuid), record.saveToString());
            } else archiveIds.remove(uuid);
            if (persist()) return true;
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not archive player snapshot; capture rejected for " + uuid, failure);
        }
        restoreEntry(snapshots, uuid, previous);
        restoreEntry(capturedAt, uuid, previousTime);
        restoreEntry(restored, uuid, previousRestored);
        restoreEntry(archiveIds, uuid, previousId);
        return false;
    }

    private static <T> void restoreEntry(Map<UUID, T> map, UUID uuid, T value) {
        if (value == null) map.remove(uuid); else map.put(uuid, value);
    }
    public List<String> history(UUID player) throws IOException { return archive.history(player); }

    public boolean recoveryBlocked(UUID player) { return leases.blocked(player); }
    public String recoveryState(UUID player) throws IOException { return leases.state(player); }

    public List<String> pruneHistory(UUID player) throws IOException {
        if (!plugin.getConfig().getBoolean("recovery.player-history.retention.enabled", false)) return List.of("Retention is disabled.");
        if (recoveryBlocked(player)) throw new IOException("Reconcile recovery before pruning history");
        int keep = Math.clamp(plugin.getConfig().getInt("recovery.player-history.retention.keep-resolved", 5), 1, 100);
        long days = Math.clamp(plugin.getConfig().getInt("recovery.player-history.retention.days", 30), 1, 3650);
        return archive.prune(player, keep, System.currentTimeMillis() - Duration.ofDays(days).toMillis(),
                id -> id.equals(archiveIds.get(player)) || confirmations.values().stream().anyMatch(c -> c.player().equals(player) && c.capture().equals(id)));
    }
    public List<String> previewHistorical(org.bukkit.command.CommandSender actor, Player player, UUID capture, String reason, boolean reconcile) throws IOException {
        requireHistoryActor(actor);
        checkHistoricalPlayer(player, reconcile);
        if (reason.isBlank() || reason.length() > 240 || reason.contains("\n") || reason.contains("\r")) throw new IllegalArgumentException("Supply a reconciliation reason (1-240 characters).");
        String fingerprint = currentFingerprint(player);
        UUID token = UUID.randomUUID();
        String hash = "reconcile";
        List<String> lines = new ArrayList<>();
        if (reconcile) {
            capture = leases.operation(player.getUniqueId());
            lines.add("Reconciliation acknowledges CURRENT player state; no inventory will be applied.");
        } else {
            if (!plugin.getConfig().getBoolean("recovery.player-history.enabled", false)) throw new IllegalArgumentException("Player history must be enabled.");
            var version = archive.read(player.getUniqueId(), capture);
            if (!version.resolved()) throw new IllegalArgumentException("Only verified resolved history may be applied.");
            PlayerSnapshot candidate = decodeHistorical(player.getUniqueId(), version);
            hash = version.hash();
            PlayerSnapshot current = PlayerSnapshot.capture(player);
            lines.add("Capture=" + capture + " timestamp=" + version.capturedAt() + " checksum=" + hash);
            lines.add("Inventory differs=" + !java.util.Arrays.equals(current.inventory(), candidate.inventory())
                    + "; armor differs=" + !java.util.Arrays.equals(current.armor(), candidate.armor())
                    + "; XP=" + current.totalExperience() + " -> " + candidate.totalExperience()
                    + "; mode=" + current.gameMode() + " -> " + candidate.gameMode());
            lines.add("Historical items may have been transferred/spent. Reconcile those items before confirming; current state will be archived as undo.");
        }
        confirmations.put(token, new HistoricalConfirmation(player.getUniqueId(), capture, actor.getName(), reason, fingerprint, hash,
                System.currentTimeMillis() + 90_000L, reconcile));
        confirmations.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
        lines.add("Reason=" + reason + "; confirm within 90s: /ee recovery confirm " + token);
        return List.copyOf(lines);
    }
    public String confirmHistorical(org.bukkit.command.CommandSender actor, UUID token) throws IOException {
        requireHistoryActor(actor);
        HistoricalConfirmation confirmation = confirmations.get(token);
        if (confirmation == null || confirmation.expires() < System.currentTimeMillis() || !confirmation.actor().equals(actor.getName()))
            throw new IllegalArgumentException("Confirmation expired or belongs to a different operator.");
        Player player = plugin.getServer().getPlayer(confirmation.player());
        checkHistoricalPlayer(player, confirmation.reconcile());
        if (!currentFingerprint(player).equals(confirmation.fingerprint())) throw new IllegalArgumentException("Player state changed; inspect again.");
        confirmations.remove(token);
        if (confirmation.reconcile()) {
            // Keep the durable lease blocked until both active-pointer and audit acknowledgement succeed.
            if (snapshots.containsKey(confirmation.player()) && !markRestored(confirmation.player(), false))
                throw new IOException("Snapshot resolution failed; pending state retained");
            leases.finish(confirmation.player(), confirmation.capture(), "RECONCILED", actor.getName(), confirmation.reason());
            return "Current state accepted and audited; no historical inventory replayed.";
        }
        var version = archive.read(confirmation.player(), confirmation.capture());
        if (!version.resolved() || !version.hash().equals(confirmation.hash())) throw new IOException("Historical version changed");
        PlayerSnapshot candidate = decodeHistorical(confirmation.player(), version);
        if (!capture(player)) throw new IOException("Undo capture failed; no historical application attempted");
        UUID undo = archiveIds.get(confirmation.player());
        if (undo == null) throw new IOException("Undo archive missing; no application attempted");
        UUID operation = leases.begin(confirmation.player(), confirmation.capture(), undo, actor.getName(), confirmation.reason(), confirmation.fingerprint());
        try {
            if (candidate.location() != null && !player.teleport(candidate.location())) throw new IOException("Historical teleport rejected");
            if (!applySnapshot(player, candidate) || !markRestored(confirmation.player(), false)) throw new IOException("Historical state/acknowledgement failed");
            leases.finish(confirmation.player(), operation, "APPLIED", actor.getName(), confirmation.reason());
            return "Historical recovery applied and audited; undo capture=" + undo;
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Historical recovery requires reconciliation for " + confirmation.player(), failure);
            throw new IOException("Uncertain historical outcome retained; inspect current player state, then use reconcile", failure);
        }
    }
    private void checkHistoricalPlayer(Player player, boolean reconcile) {
        if (player == null || !player.isOnline() || plugin.eventManager().isEventPlayer(player.getUniqueId()))
            throw new IllegalArgumentException("Target must be online and outside an event.");
        if (!reconcile && (unreadableStorage || hasUnrestoredSnapshot(player.getUniqueId())))
            throw new IllegalArgumentException("Pending/uncertain restoration blocks historical recovery.");
        if (reconcile && !recoveryBlocked(player.getUniqueId())) throw new IllegalArgumentException("No uncertain recovery to reconcile.");
    }
    private void requireHistoryActor(org.bukkit.command.CommandSender actor) {
        if (!actor.hasPermission("enthusia.events.admin") || !actor.hasPermission("enthusia.events.recovery.apply"))
            throw new IllegalArgumentException("No historical recovery permission.");
    }
    private String currentFingerprint(Player player) {
        YamlConfiguration state = new YamlConfiguration();
        writeSnapshot(state, player.getUniqueId(), PlayerSnapshot.capture(player));
        String path = "players." + player.getUniqueId();
        state.set(path + ".captured-at", 0L); state.set(path + ".capture-id", null); state.set(path + ".restored", null);
        return PlayerSnapshotArchive.digest(state.saveToString().getBytes(StandardCharsets.UTF_8));
    }
    private PlayerSnapshot decodeHistorical(UUID uuid, PlayerSnapshotArchive.Version version) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try { config.loadFromString(version.payload()); }
        catch (org.bukkit.configuration.InvalidConfigurationException invalid) { throw new IOException("Invalid archived snapshot", invalid); }
        String path = "players." + uuid;
        if (config.getInt("archive-schema") != 1 || !version.id().toString().equals(config.getString(path + ".capture-id"))
                || config.getLong(path + ".captured-at") != version.capturedAt()) throw new IOException("Historical snapshot identity/schema mismatch");
        String[] position = config.getString(path + ".location", "").split(",");
        if (position.length != 6) throw new IOException("Historical location missing");
        org.bukkit.World world = plugin.getServer().getWorld(position[0]);
        if (world == null || !world.getUID().toString().equals(config.getString(path + ".world-uuid")))
            throw new IOException("Original historical world UUID unavailable; legacy history remains inspection-only");
        Location location = new Location(world, Double.parseDouble(position[1]), Double.parseDouble(position[2]), Double.parseDouble(position[3]), Float.parseFloat(position[4]), Float.parseFloat(position[5]));
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
                || !Float.isFinite(location.getYaw()) || !Float.isFinite(location.getPitch()) || location.getY() < world.getMinHeight() || location.getY() >= world.getMaxHeight()
                || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) throw new IOException("Historical location invalid or unloaded");
        validateHistoricalFields(config, path);
        int totalExperience = config.getInt(path + ".total-experience");
        ExperienceDisplay experienceDisplay = loadExperienceDisplay(config, path, totalExperience);
        return new PlayerSnapshot(
                        location,
                        itemStackArray(config.getList(path + ".inventory"), 41),
                        itemStackArray(config.getList(path + ".armor"), 4),
                        config.getItemStack(path + ".offhand"),
                        config.getDouble(path + ".max-health", 20.0D),
                        config.getDouble(path + ".health", 20.0D),
                        config.getInt(path + ".food-level", 20),
                        (float) config.getDouble(path + ".saturation", 5.0D),
                        (float) config.getDouble(path + ".exhaustion", 0.0D),
                        totalExperience,
                        experienceDisplay.level(),
                        experienceDisplay.progress(),
                        parseGameMode(config.getString(path + ".game-mode", GameMode.SURVIVAL.name())),
                        potionEffects(config.getList(path + ".potion-effects")),
                        config.getBoolean(path + ".allow-flight", false),
                        config.getBoolean(path + ".flying", false),
                        (float) config.getDouble(path + ".walk-speed", 0.2D),
                        (float) config.getDouble(path + ".fly-speed", 0.1D)
                );
    }
    private void validateHistoricalFields(YamlConfiguration config, String path) throws IOException {
        for (String key : List.of("max-health", "health", "food-level", "saturation", "exhaustion", "total-experience", "experience-level", "experience-progress", "walk-speed", "fly-speed")) {
            double value = config.getDouble(path + "." + key);
            if (!config.contains(path + "." + key) || !Double.isFinite(value)) throw new IOException("Missing/nonfinite archived field " + key);
        }
        double maximum = config.getDouble(path + ".max-health");
        if (maximum <= 0 || maximum > 2048 || config.getDouble(path + ".health") < 0 || config.getDouble(path + ".health") > maximum
                || config.getInt(path + ".food-level") < 0 || config.getInt(path + ".food-level") > 20
                || config.getDouble(path + ".saturation") < 0 || config.getDouble(path + ".saturation") > 20
                || config.getDouble(path + ".exhaustion") < 0 || config.getDouble(path + ".exhaustion") > 40
                || config.getInt(path + ".total-experience") < 0 || config.getInt(path + ".experience-level") < 0
                || config.getDouble(path + ".experience-progress") < 0 || config.getDouble(path + ".experience-progress") >= 1
                || Math.abs(config.getDouble(path + ".walk-speed")) > 1 || Math.abs(config.getDouble(path + ".fly-speed")) > 1)
            throw new IOException("Archived numeric state out of range");
        try { GameMode.valueOf(config.getString(path + ".game-mode", "")); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid archived game mode", invalid); }
        for (String key : List.of("inventory", "armor")) {
            List<?> items = config.getList(path + "." + key);
            if (items == null || items.size() != (key.equals("inventory") ? 41 : 4)
                    || items.stream().anyMatch(item -> item != null && !(item instanceof ItemStack))) throw new IOException("Invalid archived " + key);
        }
        List<?> effects = config.getList(path + ".potion-effects");
        if (effects == null || effects.stream().anyMatch(effect -> !(effect instanceof PotionEffect))) throw new IOException("Invalid archived effects");
        if (config.getBoolean(path + ".flying") && !config.getBoolean(path + ".allow-flight")) throw new IOException("Invalid archived flight state");
    }
    private record HistoricalConfirmation(UUID player, UUID capture, String actor, String reason, String fingerprint, String hash, long expires, boolean reconcile) { }

    public boolean hasSnapshot(UUID uuid) {
        purgeExpiredRestored();
        return snapshots.containsKey(uuid);
    }

    public boolean hasUnrestoredSnapshot(UUID uuid) {
        purgeExpiredRestored();
        return recoveryBlocked(uuid) || snapshots.containsKey(uuid) && !restored.getOrDefault(uuid, false);
    }

    public CompletableFuture<Boolean> restore(Player player, boolean consume) {
        purgeExpiredRestored();
        PlayerSnapshot snapshot = snapshots.get(player.getUniqueId());
        if (snapshot == null || recoveryBlocked(player.getUniqueId())) {
            return CompletableFuture.completedFuture(false);
        }

        UUID operation;
        try { operation = beginOrdinaryRestore(player); }
        catch (IOException failure) { return CompletableFuture.completedFuture(false); }
        if (snapshot.location() != null) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            restoreWithRetry(player, snapshot, consume, 0, result, operation);
            return result;
        } else {
            return CompletableFuture.completedFuture(finishOrdinaryRestore(player, snapshot, consume, operation));
        }
    }

    public boolean restoreSynchronously(Player player, boolean consume) {
        purgeExpiredRestored();
        PlayerSnapshot snapshot = snapshots.get(player.getUniqueId());
        if (snapshot == null || !player.isOnline() || recoveryBlocked(player.getUniqueId())) {
            return false;
        }
        UUID operation;
        try { operation = beginOrdinaryRestore(player); }
        catch (IOException failure) { return false; }
        if (snapshot.location() != null && !player.teleport(snapshot.location())) {
            releaseUnapplied(player, operation);
            plugin.getLogger().warning("Could not synchronously teleport " + player.getName()
                    + " for event snapshot restore. Snapshot remains pending.");
            plugin.messages().send(player, "event-restore-failed");
            return false;
        }
        return finishOrdinaryRestore(player, snapshot, consume, operation);
    }

    private void restoreWithRetry(Player player, PlayerSnapshot snapshot, boolean consume, int attempt,
                                  CompletableFuture<Boolean> result, UUID operation) {
        if (!player.isOnline()) {
            releaseUnapplied(player, operation);
            result.complete(false);
            return;
        }
        TeleportService.teleport(plugin, player, snapshot.location(), "restore event snapshot")
                .thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.isDone()) {
                        return;
                    }
                    if (success) {
                        result.complete(finishOrdinaryRestore(player, snapshot, consume, operation));
                        return;
                    }
                    if (attempt < RESTORE_RETRY_DELAYS_TICKS.length) {
                        int delay = RESTORE_RETRY_DELAYS_TICKS[attempt];
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                                () -> restoreWithRetry(player, snapshot, consume, attempt + 1, result, operation), delay);
                        return;
                    }
                    plugin.getLogger().warning("Could not restore event snapshot for " + player.getName()
                            + ". Snapshot was kept for a later retry.");
                    plugin.messages().send(player, "event-restore-failed");
                    releaseUnapplied(player, operation);
                    result.complete(false);
                }));
    }

    private UUID beginOrdinaryRestore(Player player) throws IOException {
        UUID capture = archiveIds.getOrDefault(player.getUniqueId(), UUID.randomUUID());
        return leases.begin(player.getUniqueId(), capture, capture, "system", "ordinary snapshot restoration", currentFingerprint(player));
    }
    private void releaseUnapplied(Player player, UUID operation) {
        try { leases.finish(player.getUniqueId(), operation, "RECONCILED", "system", "teleport rejected before inventory application"); }
        catch (IOException failure) { plugin.getLogger().log(Level.SEVERE, "Restore lease retained for reconciliation", failure); }
    }
    private boolean finishOrdinaryRestore(Player player, PlayerSnapshot snapshot, boolean consume, UUID operation) {
        try {
            if (!applySnapshot(player, snapshot) || !markRestored(player.getUniqueId(), consume)) return false;
            leases.finish(player.getUniqueId(), operation, "APPLIED", "system", "snapshot application acknowledged");
            return true;
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Snapshot application uncertain; automatic retry blocked", failure);
            return false;
        }
    }
    public int retryPendingOnlineRestores() {
        int attempted = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hasUnrestoredSnapshot(player.getUniqueId())) {
                restore(player, false);
                attempted++;
            }
        }
        return attempted;
    }

    private boolean markRestored(UUID uuid, boolean consume) {
        PlayerSnapshot previous = snapshots.get(uuid);
        Long previousTime = capturedAt.get(uuid);
        Boolean previousRestored = restored.get(uuid);
        UUID captureId = archiveIds.get(uuid);
        if (consume) {
            snapshots.remove(uuid);
            capturedAt.remove(uuid);
            restored.remove(uuid);
        } else {
            restored.put(uuid, true);
        }
        if (!persist()) {
            restoreEntry(snapshots, uuid, previous); restoreEntry(capturedAt, uuid, previousTime); restoreEntry(restored, uuid, previousRestored);
            return false;
        }
        if (captureId != null) {
            try { archive.resolve(uuid, captureId); }
            catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Snapshot history resolution remains pending for " + uuid, failure); }
        }
        return true;
    }

    private boolean applySnapshot(Player player, PlayerSnapshot snapshot) {
        if (!player.isOnline()) {
            return false;
        }
        player.closeInventory();
        player.getInventory().setContents(snapshot.inventory());
        player.getInventory().setArmorContents(snapshot.armor());
        player.getInventory().setItemInOffHand(snapshot.offhand());
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        restoreExperience(player, snapshot);
        player.setGameMode(snapshot.gameMode());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying());
        player.setWalkSpeed(snapshot.walkSpeed());
        player.setFlySpeed(snapshot.flySpeed());
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        snapshot.potionEffects().forEach(player::addPotionEffect);
        restoreMaxHealth(player, snapshot.maxHealth());
        player.setHealth(Math.min(snapshot.health(), player.getMaxHealth()));
        player.updateInventory();
        return true;
    }

    private void restoreExperience(Player player, PlayerSnapshot snapshot) {
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0F);
        player.setTotalExperience(Math.max(0, snapshot.totalExperience()));
        player.setLevel(Math.max(0, snapshot.experienceLevel()));
        player.setExp(Math.max(0.0F, Math.min(0.999_999F, snapshot.experienceProgress())));
    }

    public void restoreMaxHealth(Player player, double maxHealth) {
        if (maxHealth <= 0.0D) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
        }
    }

    public void discard(UUID uuid) {
        archiveIds.remove(uuid);
        snapshots.remove(uuid);
        capturedAt.remove(uuid);
        restored.remove(uuid);
        save();
    }

    public void save() {
        persist();
    }

    private boolean persist() {
        if (unreadableStorage) return false;
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder for event snapshots.");
                return false;
            }
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerSnapshot> entry : snapshots.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerSnapshot snapshot = entry.getValue();
                writeSnapshot(config, uuid, snapshot);
            }
            AtomicFiles.replace(file.toPath(), config.saveToString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save event player snapshots; capture not acknowledged.", ex);
            return false;
        }
    }

    private void writeSnapshot(YamlConfiguration config, UUID uuid, PlayerSnapshot snapshot) {
        String path = "players." + uuid;
        config.set(path + ".capture-id", archiveIds.containsKey(uuid) ? archiveIds.get(uuid).toString() : null);
        config.set(path + ".captured-at", capturedAt.getOrDefault(uuid, System.currentTimeMillis()));
        config.set(path + ".restored", restored.getOrDefault(uuid, false));
        config.set(path + ".location", LocationCodec.encode(snapshot.location()));
        config.set(path + ".world-uuid", snapshot.location() == null || snapshot.location().getWorld() == null ? null : snapshot.location().getWorld().getUID().toString());
        config.set(path + ".inventory", nullableList(snapshot.inventory()));
        config.set(path + ".armor", nullableList(snapshot.armor()));
        config.set(path + ".offhand", snapshot.offhand());
        config.set(path + ".max-health", snapshot.maxHealth());
        config.set(path + ".health", snapshot.health());
        config.set(path + ".food-level", snapshot.foodLevel());
        config.set(path + ".saturation", snapshot.saturation());
        config.set(path + ".exhaustion", snapshot.exhaustion());
        config.set(path + ".total-experience", snapshot.totalExperience());
        config.set(path + ".experience-level", snapshot.experienceLevel());
        config.set(path + ".experience-progress", snapshot.experienceProgress());
        config.set(path + ".game-mode", snapshot.gameMode().name());
        config.set(path + ".potion-effects", new ArrayList<>(snapshot.potionEffects()));
        config.set(path + ".allow-flight", snapshot.allowFlight());
        config.set(path + ".flying", snapshot.flying());
        config.set(path + ".walk-speed", snapshot.walkSpeed());
        config.set(path + ".fly-speed", snapshot.flySpeed());
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try { config.load(file); }
        catch (IOException | org.bukkit.configuration.InvalidConfigurationException failure) {
            unreadableStorage = true;
            plugin.getLogger().log(Level.SEVERE, "Snapshot storage unreadable; preserved original file and blocked captures.", failure);
            return;
        }
        if (!config.isConfigurationSection("players")) {
            if (!config.getKeys(false).isEmpty()) unreadableStorage = true;
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String key : Objects.requireNonNull(config.getConfigurationSection("players")).getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                long timestamp = config.getLong(path + ".captured-at", 0L);
                boolean entryRestored = config.getBoolean(path + ".restored", false);
                if (timestamp <= 0L) { unreadableStorage = true; continue; }
                if (entryRestored && now - timestamp > SNAPSHOT_TTL_MILLIS) {
                    changed = true;
                    continue;
                }
                if (!entryRestored && now - timestamp > SNAPSHOT_TTL_MILLIS) {
                    logOldUnrestoredWarning(uuid, timestamp);
                }
                Location location = LocationCodec.decode(config.getString(path + ".location", ""));
                if (location == null) {
                    unreadableStorage = true;
                    changed = true;
                    continue;
                }
                int totalExperience = config.getInt(path + ".total-experience", 0);
                ExperienceDisplay experienceDisplay = loadExperienceDisplay(config, path, totalExperience);
                PlayerSnapshot snapshot = new PlayerSnapshot(
                        location,
                        itemStackArray(config.getList(path + ".inventory"), 41),
                        itemStackArray(config.getList(path + ".armor"), 4),
                        config.getItemStack(path + ".offhand"),
                        config.getDouble(path + ".max-health", 20.0D),
                        config.getDouble(path + ".health", 20.0D),
                        config.getInt(path + ".food-level", 20),
                        (float) config.getDouble(path + ".saturation", 5.0D),
                        (float) config.getDouble(path + ".exhaustion", 0.0D),
                        totalExperience,
                        experienceDisplay.level(),
                        experienceDisplay.progress(),
                        parseGameMode(config.getString(path + ".game-mode", GameMode.SURVIVAL.name())),
                        potionEffects(config.getList(path + ".potion-effects")),
                        config.getBoolean(path + ".allow-flight", false),
                        config.getBoolean(path + ".flying", false),
                        (float) config.getDouble(path + ".walk-speed", 0.2D),
                        (float) config.getDouble(path + ".fly-speed", 0.1D)
                );
                snapshots.put(uuid, snapshot);
                capturedAt.put(uuid, timestamp);
                restored.put(uuid, entryRestored);
                String id = config.getString(path + ".capture-id");
                if (id != null) archiveIds.put(uuid, UUID.fromString(id));
            } catch (RuntimeException ex) {
                unreadableStorage = true;
                changed = true;
                plugin.getLogger().log(Level.WARNING, "Skipped corrupt event snapshot entry: " + key, ex);
            }
        }
        if (changed) {
            save();
        }
    }

    private ExperienceDisplay loadExperienceDisplay(YamlConfiguration config, String path, int totalExperience) {
        if (config.contains(path + ".experience-level") && config.contains(path + ".experience-progress")) {
            int level = Math.max(0, config.getInt(path + ".experience-level", 0));
            float progress = (float) config.getDouble(path + ".experience-progress", 0.0D);
            return new ExperienceDisplay(level, Math.max(0.0F, Math.min(0.999_999F, progress)));
        }
        return deriveExperienceDisplay(totalExperience);
    }

    private ExperienceDisplay deriveExperienceDisplay(int totalExperience) {
        int remaining = Math.max(0, totalExperience);
        int level = 0;
        int required = experienceToNextLevel(level);
        while (remaining >= required && level < 100_000) {
            remaining -= required;
            level++;
            required = experienceToNextLevel(level);
        }
        float progress = required <= 0 ? 0.0F : remaining / (float) required;
        return new ExperienceDisplay(level, Math.max(0.0F, Math.min(0.999_999F, progress)));
    }

    private int experienceToNextLevel(int level) {
        if (level <= 15) {
            return (2 * level) + 7;
        }
        if (level <= 30) {
            return (5 * level) - 38;
        }
        return (9 * level) - 158;
    }

    private void purgeExpiredRestored() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Map.Entry<UUID, Long> entry : new LinkedHashMap<>(capturedAt).entrySet()) {
            UUID uuid = entry.getKey();
            if (now - entry.getValue() <= SNAPSHOT_TTL_MILLIS) {
                continue;
            }
            if (!restored.getOrDefault(uuid, false)) {
                logOldUnrestoredWarning(uuid, entry.getValue());
                continue;
            }
            if (now - entry.getValue() > SNAPSHOT_TTL_MILLIS) {
                snapshots.remove(entry.getKey());
                capturedAt.remove(entry.getKey());
                restored.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void logOldUnrestoredWarning(UUID uuid, long timestamp) {
        long lastWarned = warnedOldUnrestored.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        if (now - lastWarned < Duration.ofHours(6).toMillis()) {
            return;
        }
        warnedOldUnrestored.put(uuid, now);
        long ageDays = Math.max(1L, Duration.ofMillis(now - timestamp).toDays());
        plugin.getLogger().warning("Unrestored event snapshot for " + uuid + " is " + ageDays
                + " day(s) old and was not purged. Restore or review it manually.");
    }

    private List<ItemStack> nullableList(ItemStack[] items) {
        List<ItemStack> result = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            result.add(item);
        }
        return result;
    }

    private ItemStack[] itemStackArray(List<?> values, int fallbackSize) {
        if (values == null) {
            return new ItemStack[fallbackSize];
        }
        ItemStack[] items = new ItemStack[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof ItemStack item) {
                items[i] = item;
            }
        }
        return items;
    }

    private Collection<PotionEffect> potionEffects(List<?> values) {
        List<PotionEffect> effects = new ArrayList<>();
        if (values == null) {
            return effects;
        }
        for (Object value : values) {
            if (value instanceof PotionEffect effect) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private GameMode parseGameMode(String raw) {
        try {
            return GameMode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return GameMode.SURVIVAL;
        }
    }

    private record ExperienceDisplay(int level, float progress) {
    }
}
