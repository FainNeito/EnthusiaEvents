package org.enthusia.events.infrastructure;

import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;

/** One global recovery lease. First codec supports ordinary block data, rejects ALL tile state. */
public final class ArenaRecovery {
    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CELLS = 50_000;
    private final EnthusiaEventsPlugin plugin;
    private final Path pending;
    private Baseline baseline;
    private UUID preparedSession;
    private BukkitTask work;
    private boolean writing;
    private boolean restoreAfterWrite;
    private String failure;
    private volatile boolean closed;

    public ArenaRecovery(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        pending = plugin.getDataFolder().toPath().resolve("recovery/arena/pending.bin");
        if (Files.exists(pending)) {
            try { baseline = decode(Files.readAllBytes(checkedFile())); failure = "Unfinished baseline found on startup; inspect then retry."; }
            catch (IOException | RuntimeException ex) { failure = "Unreadable baseline retained: " + ex.getClass().getSimpleName(); }
        }
    }
    private Path checkedFile() throws IOException {
        if (Files.size(pending) > MAX_FILE_BYTES) throw new IOException("Oversized baseline");
        return pending;
    }
    public boolean enabled(EventType type) {
        return plugin.getConfig().getBoolean("recovery.arena.enabled", false)
                && Set.of(EventType.RUNNER, EventType.SPLEEF, EventType.SPLEGG, EventType.BLOCK_PARTY, EventType.SKYWARS, EventType.BEDWARS, EventType.SPEEDBUILDERS).contains(type);
    }
    public boolean blocked() { return work != null || writing || baseline != null || failure != null || Files.exists(pending); }
    public boolean prepared(EventSession session) { return baseline != null && session.id().equals(preparedSession) && work == null && !writing && failure == null; }
    public boolean quarantine() { return blocked() && preparedSession == null; }
    public String status() { return "Arena recovery: " + (blocked() ? "leased/quarantined" : "clear") + "; "
            + (baseline == null ? "no decoded baseline" : "session=" + baseline.session() + " world=" + baseline.world() + " blocks=" + baseline.cells().size())
            + "; failure=" + (failure == null ? "none" : failure); }

    public void prepare(EventSession session, Runnable ready, Consumer<String> failed) {
        if (closed) { failed.accept("Recovery service is shutting down"); return; }
        if (blocked()) { failed.accept(status()); return; }
        EventMap map = session.selectedMap();
        CuboidRegion region = map == null ? null : map.region();
        World world = region == null ? null : plugin.getServer().getWorld(region.worldName());
        if (world == null) { failed.accept("Original world unavailable"); return; }
        if (!integerBounds(region)) { failed.accept("Recovery requires finite integer region corners"); return; }
        long dx = (long) Math.floor(region.maxX()) - (long) Math.floor(region.minX()) + 1;
        long dy = (long) Math.floor(region.maxY()) - (long) Math.floor(region.minY()) + 1;
        long dz = (long) Math.floor(region.maxZ()) - (long) Math.floor(region.minZ()) + 1;
        int limit = Math.clamp(plugin.getConfig().getInt("recovery.arena.max-blocks", 20_000), 1, MAX_CELLS);
        if (dx <= 0 || dy <= 0 || dz <= 0 || dx > limit || dy > limit || dz > limit || dx * dy > limit || dx * dy * dz > limit
                || region.minY() < world.getMinHeight() || region.maxY() >= world.getMaxHeight()) {
            failed.accept("Recovery region invalid/too large (limit " + limit + ")"); return;
        }
        int count = (int) (dx * dy * dz);
        List<Cell> cells = new ArrayList<>(count);
        int[] cursor = {0};
        restoreAfterWrite = false;
        work = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                int budget = Math.clamp(plugin.getConfig().getInt("recovery.arena.blocks-per-tick", 128), 1, 512);
                for (int n = 0; n < budget && cursor[0] < count; n++, cursor[0]++) {
                    int index = cursor[0];
                    int x = (int) Math.floor(region.minX()) + index % (int) dx;
                    int y = (int) Math.floor(region.minY()) + index / (int) dx % (int) dy;
                    int z = (int) Math.floor(region.minZ()) + index / (int) (dx * dy);
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) throw new IOException("Unloaded recovery chunk " + (x >> 4) + "," + (z >> 4));
                    var block = world.getBlockAt(x, y, z);
                    if (block.getState() instanceof TileState) throw new IOException("Unsupported tile at " + x + "," + y + "," + z + "; no lossless codec yet");
                    cells.add(new Cell(x, y, z, block.getBlockData().getAsString()));
                }
                if (cursor[0] < count) return;
                cancelWork();
                Baseline captured = new Baseline(session.id(), world.getUID(), map.eventType(), map.id(), region, List.copyOf(cells));
                writing = true;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    String error = null;
                    try { AtomicFiles.replace(pending, encode(captured)); }
                    catch (IOException | RuntimeException ex) { error = "Baseline commit failed: " + ex.getClass().getSimpleName(); }
                    String result = error;
                    if (closed) return;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        writing = false;
                        if (result != null) { failure = result; failed.accept(result); return; }
                        baseline = captured;
                        preparedSession = session.id();
                        if (restoreAfterWrite) retry(); else ready.run();
                    });
                });
            } catch (IOException | RuntimeException ex) {
                cancelWork(); failure = "Preparation rejected: " + ex.getMessage(); failed.accept(failure);
                // No game mutation occurred and no baseline was published. Explicit retry may clear this preparation-only failure.
            }
        }, 1L, 1L);
    }

    public void reset() {
        if (closed) return;
        preparedSession = null;
        if (writing) { restoreAfterWrite = true; return; }
        if (work != null && baseline == null) { cancelWork(); return; }
        if (baseline != null && work == null) retry();
    }
    public void retry() {
        if (closed) return;
        if (writing || work != null) return;
        if (baseline == null && !Files.exists(pending)) { failure = null; return; }
        if (baseline == null) {
            try { baseline = decode(Files.readAllBytes(checkedFile())); }
            catch (IOException | RuntimeException ex) { failure = "Baseline still unreadable; retained original bytes"; return; }
        }
        Baseline restoring = baseline;
        World world = plugin.getServer().getWorld(restoring.world());
        if (world == null) { failure = "Original world UUID unavailable; load it before retry"; return; }
        EventMap configured = plugin.maps().find(restoring.type(), restoring.map()).orElse(null);
        if (configured == null || !Objects.equals(configured.region(), restoring.region())) {
            failure = "Arena identity/bounds changed; restore original setup and inspect before retry"; return;
        }
        preparedSession = null;
        failure = null;
        int[] cursor = {0};
        boolean[] verifying = {false};
        work = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                int budget = Math.clamp(plugin.getConfig().getInt("recovery.arena.blocks-per-tick", 128), 1, 512);
                for (int n = 0; n < budget && cursor[0] < restoring.cells().size(); n++, cursor[0]++) {
                    Cell cell = restoring.cells().get(cursor[0]);
                    if (cell.y() < world.getMinHeight() || cell.y() >= world.getMaxHeight()) throw new IOException("World height changed; manual reconciliation required");
                    if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) throw new IOException("Restore chunk unloaded; load it and retry");
                    var block = world.getBlockAt(cell.x(), cell.y(), cell.z());
                    if (block.getState() instanceof TileState) throw new IOException("Unexpected tile encountered; manual reconciliation required");
                    if (!verifying[0]) block.setBlockData(plugin.getServer().createBlockData(cell.data()), false);
                    if (!block.getBlockData().getAsString().equals(cell.data())) throw new IOException("Block verification failed");
                }
                if (cursor[0] < restoring.cells().size()) return;
                if (!verifying[0]) { verifying[0] = true; cursor[0] = 0; return; }
                cancelWork();
                writing = true;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    String error = null;
                    try {
                        Path complete = pending.resolveSibling(restoring.session() + ".complete.bin");
                        Files.move(pending, complete, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException | RuntimeException ex) { error = "Restored but completion publication failed; retry required"; }
                    String result = error;
                    if (closed) return;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        writing = false; failure = result;
                        if (result == null) baseline = null;
                    });
                });
            } catch (IOException | RuntimeException ex) { cancelWork(); failure = ex.getMessage(); }
        }, 1L, 1L);
    }
    public void shutdown() { closed = true; cancelWork(); preparedSession = null; }
    private void cancelWork() { if (work != null) { work.cancel(); work = null; } }

    private static byte[] encode(Baseline baseline) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(payload)) {
            out.writeUTF("EE-ARENA-2"); out.writeUTF(baseline.session().toString()); out.writeUTF(baseline.world().toString());
            out.writeUTF(baseline.type().name()); out.writeUTF(baseline.map());
            CuboidRegion r = baseline.region(); out.writeUTF(r.worldName());
            out.writeDouble(r.minX()); out.writeDouble(r.minY()); out.writeDouble(r.minZ());
            out.writeDouble(r.maxX()); out.writeDouble(r.maxY()); out.writeDouble(r.maxZ());
            out.writeInt(baseline.cells().size());
            for (Cell c : baseline.cells()) { out.writeInt(c.x()); out.writeInt(c.y()); out.writeInt(c.z()); out.writeUTF(c.data()); }
        }
        byte[] bytes = payload.toByteArray();
        if (bytes.length > MAX_FILE_BYTES - 128) throw new IOException("Baseline too large");
        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(envelope)) { out.writeUTF(PlayerSnapshotArchive.digest(bytes)); out.writeInt(bytes.length); out.write(bytes); }
        return envelope.toByteArray();
    }
    private static Baseline decode(byte[] envelope) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(envelope))) {
            String hash = in.readUTF(); int size = in.readInt();
            if (size < 1 || size > MAX_FILE_BYTES || size != in.available()) throw new IOException("Invalid baseline length");
            byte[] payload = in.readNBytes(size);
            if (!hash.equals(PlayerSnapshotArchive.digest(payload))) throw new IOException("Baseline integrity mismatch");
            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload))) {
                if (!data.readUTF().equals("EE-ARENA-2")) throw new IOException("Unsupported schema");
                UUID session = UUID.fromString(data.readUTF()), world = UUID.fromString(data.readUTF());
                EventType type = EventType.parse(data.readUTF()); String map = data.readUTF();
                CuboidRegion region = new CuboidRegion(data.readUTF(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble());
                int count = data.readInt();
                if (count < 1 || count > MAX_CELLS) throw new IOException("Invalid block count");
                if (!integerBounds(region) || (region.maxX() - region.minX() + 1) * (region.maxY() - region.minY() + 1) * (region.maxZ() - region.minZ() + 1) != count) throw new IOException("Baseline coverage/bounds mismatch");
                List<Cell> cells = new ArrayList<>(count);
                Set<String> coordinates = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    Cell cell = new Cell(data.readInt(), data.readInt(), data.readInt(), data.readUTF());
                    if (cell.x() < region.minX() || cell.x() > region.maxX() || cell.y() < region.minY() || cell.y() > region.maxY()
                            || cell.z() < region.minZ() || cell.z() > region.maxZ() || !coordinates.add(cell.x() + ":" + cell.y() + ":" + cell.z())) throw new IOException("Invalid/duplicate baseline cell");
                    cells.add(cell);
                }
                if (data.available() != 0) throw new IOException("Unexpected baseline data");
                return new Baseline(session, world, type, map, region, List.copyOf(cells));
            }
        }
    }
    private record Cell(int x, int y, int z, String data) { }
    private static boolean integerBounds(CuboidRegion r) {
        for (double value : new double[]{r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()}) {
            if (!Double.isFinite(value) || value != Math.floor(value) || Math.abs(value) > 30_000_000) return false;
        }
        return r.minX() <= r.maxX() && r.minY() <= r.maxY() && r.minZ() <= r.maxZ();
    }
    private record Baseline(UUID session, UUID world, EventType type, String map, CuboidRegion region, List<Cell> cells) { }
}
