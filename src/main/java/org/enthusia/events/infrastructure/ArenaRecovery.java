package org.enthusia.events.infrastructure;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;

/** One global recovery lease. Supports ordinary cells and explicitly supported container state. */
// Nullable lease/task references are cleared only after cancellation or acknowledged recovery.
@SuppressWarnings("PMD.NullAssignment")
public final class ArenaRecovery {
    private static final String CURRENT_SCHEMA = "EE-ARENA-3";
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
        Capture capture;
        try { capture = prepareCapture(session); }
        catch (IOException failure) { failed.accept(failure.getMessage()); return; }
        restoreAfterWrite = false;
        work = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> captureTick(capture, ready, failed), 1L, 1L);
    }
    private Capture prepareCapture(EventSession session) throws IOException {
        EventMap map = session.selectedMap();
        CuboidRegion region = map == null ? null : map.region();
        World world = region == null ? null : plugin.getServer().getWorld(region.worldName());
        if (world == null) throw new IOException("Original world unavailable");
        if (!integerBounds(region)) throw new IOException("Recovery requires finite integer region corners");
        long dx = (long) Math.floor(region.maxX()) - (long) Math.floor(region.minX()) + 1;
        long dy = (long) Math.floor(region.maxY()) - (long) Math.floor(region.minY()) + 1;
        long dz = (long) Math.floor(region.maxZ()) - (long) Math.floor(region.minZ()) + 1;
        int limit = Math.clamp(plugin.getConfig().getInt("recovery.arena.max-blocks", 20_000), 1, MAX_CELLS);
        if (!validVolume(dx, dy, dz, limit) || region.minY() < world.getMinHeight() || region.maxY() >= world.getMaxHeight()) {
            throw new IOException("Recovery region invalid/too large (limit " + limit + ")");
        }
        int count = (int) (dx * dy * dz);
        return new Capture(session, map, region, world, dx, dy, count);
    }
    private static boolean validVolume(long dx, long dy, long dz, int limit) {
        for (long dimension : new long[]{dx, dy, dz}) if (dimension <= 0 || dimension > limit) return false;
        return dx * dy <= limit && dx * dy * dz <= limit;
    }
    private void captureTick(Capture capture, Runnable ready, Consumer<String> failed) {
        try {
            int budget = batchBudget();
            for (int n = 0; n < budget && capture.cursor < capture.count; n++, capture.cursor++) captureCell(capture);
            if (capture.cursor < capture.count) return;
            cancelWork();
            Baseline captured = new Baseline(capture.session.id(), capture.world.getUID(), capture.map.eventType(), capture.map.id(), capture.region, List.copyOf(capture.cells));
            publishCapture(captured, ready, failed);
        } catch (IOException | RuntimeException ex) {
            cancelWork(); failure = "Preparation rejected: " + ex.getMessage(); failed.accept(failure);
        }
    }
    private int batchBudget() { return Math.clamp(plugin.getConfig().getInt("recovery.arena.blocks-per-tick", 128), 1, 512); }
    private void captureCell(Capture capture) throws IOException {
        int index = capture.cursor;
        int x = (int) capture.region.minX() + index % (int) capture.dx;
        int y = (int) capture.region.minY() + index / (int) capture.dx % (int) capture.dy;
        int z = (int) capture.region.minZ() + index / (int) (capture.dx * capture.dy);
        if (!capture.world.isChunkLoaded(x >> 4, z >> 4)) throw new IOException("Unloaded recovery chunk " + (x >> 4) + "," + (z >> 4));
        var block = capture.world.getBlockAt(x, y, z);
        String tile = ContainerCodec.capture(block.getState());
        String blockData = block.getBlockData().getAsString();
        capture.bytes += 32L + blockData.length() * 3L + tile.length() * 3L;
        if (tile.length() > 2 * 1024 * 1024 || capture.bytes > MAX_FILE_BYTES - 128) throw new IOException("Baseline payload budget exceeded");
        capture.cells.add(new Cell(x, y, z, blockData, tile));
    }
    private void publishCapture(Baseline captured, Runnable ready, Consumer<String> failed) {
        writing = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = writeCapture(captured);
            if (closed) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (closed) return;
                writing = false;
                if (!result.isEmpty()) { failure = result; failed.accept(result); return; }
                baseline = captured; preparedSession = captured.session();
                if (restoreAfterWrite) retry(); else ready.run();
            });
        });
    }
    private String writeCapture(Baseline captured) {
        try { AtomicFiles.replace(pending, encode(captured)); return ""; }
        catch (IOException | RuntimeException ex) { return "Baseline commit failed: " + ex.getClass().getSimpleName(); }
    }
    private static final class Capture {
        private final EventSession session;
        private final EventMap map;
        private final CuboidRegion region;
        private final World world;
        private final long dx, dy;
        private final int count;
        private final List<Cell> cells;
        private int cursor;
        private long bytes = 1024;
        private Capture(EventSession session, EventMap map, CuboidRegion region, World world, long dx, long dy, int count) {
            this.session = session; this.map = map; this.region = region; this.world = world;
            this.dx = dx; this.dy = dy; this.count = count; cells = new ArrayList<>(count);
        }
    }

    public void reset() {
        if (closed) return;
        preparedSession = null;
        if (writing) { restoreAfterWrite = true; return; }
        if (work != null && baseline == null) { cancelWork(); return; }
        if (baseline != null && work == null) retry();
    }
    public void retry() {
        if (busy()) return;
        if (baseline == null && !Files.exists(pending)) { failure = null; return; }
        if (!loadPendingBaseline()) return;
        Baseline restoring = baseline;
        World world = plugin.getServer().getWorld(restoring.world());
        if (world == null) { failure = "Original world UUID unavailable; load it before retry"; return; }
        if (!restoringIdentityMatches(restoring)) return;
        preparedSession = null;
        failure = null;
        Replay replay = new Replay(restoring, world);
        work = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> replayTick(replay), 1L, 1L);
    }
    private boolean busy() { return closed || writing || work != null; }
    private boolean restoringIdentityMatches(Baseline restoring) {
        EventMap configured = plugin.maps().find(restoring.type(), restoring.map()).orElse(null);
        if (configured == null || !Objects.equals(configured.region(), restoring.region())) {
            failure = "Arena identity/bounds changed; restore original setup and inspect before retry"; return false;
        }
        return true;
    }
    private boolean loadPendingBaseline() {
        if (baseline != null) return true;
        try { baseline = decode(Files.readAllBytes(checkedFile())); return true; }
        catch (IOException | RuntimeException ex) { failure = "Baseline still unreadable; retained original bytes"; return false; }
    }
    private void replayTick(Replay replay) {
        try {
            int budget = batchBudget();
            for (int n = 0; n < budget && replay.cursor < replay.baseline.cells().size(); n++, replay.cursor++)
                replayCell(replay.world, replay.baseline.cells().get(replay.cursor), replay.verifying);
            if (replay.cursor < replay.baseline.cells().size()) return;
            if (!replay.verifying) { replay.verifying = true; replay.cursor = 0; return; }
            cancelWork();
            publishCompletion(replay.baseline);
        } catch (IOException | RuntimeException ex) { cancelWork(); failure = ex.getMessage(); }
    }
    private void replayCell(World world, Cell cell, boolean verifying) throws IOException {
        if (cell.y() < world.getMinHeight() || cell.y() >= world.getMaxHeight()) throw new IOException("World height changed; manual reconciliation required");
        if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) throw new IOException("Restore chunk unloaded; load it and retry");
        var block = world.getBlockAt(cell.x(), cell.y(), cell.z());
        if (!verifying) {
            ContainerCodec.capture(block.getState());
            block.setBlockData(plugin.getServer().createBlockData(cell.data()), false);
            if (!cell.tile().isEmpty()) ContainerCodec.apply(block, cell.tile());
        }
        verifyCell(block, cell);
    }
    private static void verifyCell(org.bukkit.block.Block block, Cell cell) throws IOException {
        if (!block.getBlockData().getAsString().equals(cell.data()) || !ContainerCodec.capture(block.getState()).equals(cell.tile()))
            throw new IOException("Block/tile verification failed");
    }
    private void publishCompletion(Baseline restoring) {
        writing = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String result = moveCompletion(restoring);
            if (closed) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> acknowledgeCompletion(result));
        });
    }
    private String moveCompletion(Baseline restoring) {
        try { Files.move(pending, pending.resolveSibling(restoring.session() + ".complete.bin"), StandardCopyOption.ATOMIC_MOVE); return ""; }
        catch (IOException | RuntimeException ex) { return "Restored but completion publication failed; retry required"; }
    }
    private void acknowledgeCompletion(String result) {
        if (closed) return;
        writing = false;
        failure = result.isEmpty() ? null : result;
        if (result.isEmpty()) baseline = null;
    }
    private static final class Replay {
        private final Baseline baseline;
        private final World world;
        private int cursor;
        private boolean verifying;
        private Replay(Baseline baseline, World world) { this.baseline = baseline; this.world = world; }
    }
    public void shutdown() { closed = true; cancelWork(); preparedSession = null; }
    private void cancelWork() { if (work != null) { work.cancel(); work = null; } }

    private static byte[] encode(Baseline baseline) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(payload)) {
            out.writeUTF(CURRENT_SCHEMA); out.writeUTF(baseline.session().toString()); out.writeUTF(baseline.world().toString());
            out.writeUTF(baseline.type().name()); out.writeUTF(baseline.map());
            CuboidRegion r = baseline.region(); out.writeUTF(r.worldName());
            out.writeDouble(r.minX()); out.writeDouble(r.minY()); out.writeDouble(r.minZ());
            out.writeDouble(r.maxX()); out.writeDouble(r.maxY()); out.writeDouble(r.maxZ());
            out.writeInt(baseline.cells().size());
            for (Cell c : baseline.cells()) { out.writeInt(c.x()); out.writeInt(c.y()); out.writeInt(c.z()); out.writeUTF(c.data()); byte[] tile = c.tile().getBytes(java.nio.charset.StandardCharsets.UTF_8); out.writeInt(tile.length); out.write(tile); }
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
            return decodePayload(payload);
        }
    }
    private static Baseline decodePayload(byte[] payload) throws IOException {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload))) {
                String schema = data.readUTF();
                if (!schema.equals(CURRENT_SCHEMA) && !schema.equals("EE-ARENA-2")) throw new IOException("Unsupported schema");
                UUID session = UUID.fromString(data.readUTF()), world = UUID.fromString(data.readUTF());
                EventType type = EventType.parse(data.readUTF()); String map = data.readUTF();
                CuboidRegion region = new CuboidRegion(data.readUTF(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble(), data.readDouble());
                int count = data.readInt();
                validateCoverage(region, count);
                List<Cell> cells = new ArrayList<>(count);
                Set<String> coordinates = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    Cell cell = readCell(data, schema, region, coordinates);
                    cells.add(cell);
                }
                if (data.available() != 0) throw new IOException("Unexpected baseline data");
                return new Baseline(session, world, type, map, region, List.copyOf(cells));
        }
    }
    private static void validateCoverage(CuboidRegion region, int count) throws IOException {
        if (count < 1 || count > MAX_CELLS) throw new IOException("Invalid block count");
        double volume = (region.maxX() - region.minX() + 1) * (region.maxY() - region.minY() + 1) * (region.maxZ() - region.minZ() + 1);
        if (!integerBounds(region) || volume != count) throw new IOException("Baseline coverage/bounds mismatch");
    }
    private static Cell readCell(DataInputStream data, String schema, CuboidRegion region, Set<String> coordinates) throws IOException {
                    int x = data.readInt(), y = data.readInt(), z = data.readInt(); String blockData = data.readUTF();
                    int tileLength = schema.equals(CURRENT_SCHEMA) ? data.readInt() : 0;
                    if (tileLength < 0 || tileLength > 2 * 1024 * 1024 || tileLength > data.available()) throw new IOException("Invalid tile payload size");
                    Cell cell = new Cell(x, y, z, blockData, new String(data.readNBytes(tileLength), java.nio.charset.StandardCharsets.UTF_8));
                    if (!insideCell(region, cell) || !coordinates.add(cell.x() + ":" + cell.y() + ":" + cell.z()))
                        throw new IOException("Invalid/duplicate baseline cell");
                    return cell;
    }
    private static boolean insideCell(CuboidRegion region, Cell cell) {
        return cell.x() >= region.minX() && cell.x() <= region.maxX() && cell.y() >= region.minY() && cell.y() <= region.maxY()
                && cell.z() >= region.minZ() && cell.z() <= region.maxZ();
    }
    private record Cell(int x, int y, int z, String data, String tile) { }
    private static boolean integerBounds(CuboidRegion r) {
        for (double value : new double[]{r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ()}) {
            if (!Double.isFinite(value) || value != Math.floor(value) || Math.abs(value) > 30_000_000) return false;
        }
        return r.minX() <= r.maxX() && r.minY() <= r.maxY() && r.minZ() <= r.maxZ();
    }
    private record Baseline(UUID session, UUID world, EventType type, String map, CuboidRegion region, List<Cell> cells) { }
}
