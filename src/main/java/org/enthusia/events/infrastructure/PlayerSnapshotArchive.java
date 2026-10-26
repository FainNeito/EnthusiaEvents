package org.enthusia.events.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Immutable capture payloads; resolution is separate and cannot overwrite inventory history. */
public final class PlayerSnapshotArchive {
    private static final String SNAPSHOT_SUFFIX = ".snapshot";
    private static final int HISTORY_LIMIT = 20;
    private static final int VERSION_LIMIT = 10_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Path root;
    public PlayerSnapshotArchive(Path root) { this.root = root; }
    public void capture(UUID player, UUID id, long capturedAt, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Snapshot exceeds archive limit");
        Path file = directory(player).resolve(id + SNAPSHOT_SUFFIX);
        if (Files.exists(file)) throw new IOException("Capture ID already exists");
        String envelope = "EE-SNAPSHOT-1\n" + player + "\n" + id + "\n" + capturedAt + "\n" + digest(bytes) + "\n" + payload;
        AtomicFiles.replace(file, envelope.getBytes(StandardCharsets.UTF_8));
    }
    public void resolve(UUID player, UUID id) throws IOException {
        if (!Files.isRegularFile(directory(player).resolve(id + SNAPSHOT_SUFFIX))) throw new IOException("Missing archive capture");
        AtomicFiles.replace(directory(player).resolve(id + ".resolved"), Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
    }
    public record Version(UUID id, long capturedAt, String hash, String payload, boolean resolved) { }
    public Version read(UUID player, UUID id) throws IOException {
        Path file = directory(player).resolve(id + SNAPSHOT_SUFFIX);
        if (Files.size(file) > MAX_BYTES + 1024) throw new IOException("Oversized snapshot; retained");
        String[] parts = Files.readString(file).split("\n", 6);
        if (parts.length != 6 || !parts[0].equals("EE-SNAPSHOT-1") || !parts[1].equals(player.toString())
                || !parts[2].equals(id.toString()) || !parts[4].equals(digest(parts[5].getBytes(StandardCharsets.UTF_8))))
            throw new IOException("Corrupt archive identity/checksum; retained");
        long captured = Long.parseLong(parts[3]);
        if (captured <= 0) throw new IOException("Invalid archive time");
        return new Version(id, captured, parts[4], parts[5], resolved(player, id));
    }
    private boolean resolved(UUID player, UUID id) throws IOException {
        Path marker = directory(player).resolve(id + ".resolved");
        return Files.exists(marker) && Files.size(marker) < 32 && Long.parseLong(Files.readString(marker)) > 0;
    }
    public List<String> history(UUID player) throws IOException {
        List<String> results = new ArrayList<>();
        for (Path file : files(player)) {
            if (results.size() == HISTORY_LIMIT) { results.add("More versions retained on disk."); break; }
            try {
                Version version = read(player, UUID.fromString(file.getFileName().toString().replace(SNAPSHOT_SUFFIX, "")));
                results.add(version.id() + " captured=" + version.capturedAt() + " " + (version.resolved() ? "resolved" : "pending/unlinked"));
            } catch (IOException | RuntimeException invalid) { results.add(file.getFileName() + " CORRUPT; retained"); }
        }
        results.add("Historical items may have been spent/transferred. Use preview then explicit confirmation with a reconciliation reason.");
        return List.copyOf(results);
    }
    public List<String> prune(UUID player, int keep, long oldestAllowed, java.util.function.Predicate<UUID> protectedVersion) throws IOException {
        List<Version> resolved = new ArrayList<>();
        int retained = 0;
        for (Path file : files(player)) {
            try {
                Version version = read(player, UUID.fromString(file.getFileName().toString().replace(SNAPSHOT_SUFFIX, "")));
                if (version.resolved() && !protectedVersion.test(version.id())) resolved.add(version); else retained++;
            } catch (IOException | RuntimeException invalid) { retained++; }
        }
        resolved.sort(java.util.Comparator.comparingLong(Version::capturedAt).reversed());
        int removed = 0;
        for (int i = 0; i < resolved.size(); i++) {
            Version version = resolved.get(i);
            if (i < Math.max(1, keep) && version.capturedAt() >= oldestAllowed) continue;
            Files.delete(directory(player).resolve(version.id() + SNAPSHOT_SUFFIX));
            Files.deleteIfExists(directory(player).resolve(version.id() + ".resolved"));
            removed++;
        }
        return List.of("Removed " + removed + " verified resolved versions; retained " + retained + " pending/protected/corrupt versions.");
    }
    private List<Path> files(UUID player) throws IOException {
        Path directory = directory(player);
        if (!Files.exists(directory)) return List.of();
        try (var files = Files.list(directory)) {
            List<Path> versions = files.filter(p -> p.getFileName().toString().endsWith(SNAPSHOT_SUFFIX)).limit(VERSION_LIMIT + 1L).toList();
            if (versions.size() > VERSION_LIMIT) throw new IOException("History exceeds inspection budget; operator storage review required");
            return versions;
        }
    }
    private Path directory(UUID player) { return root.resolve(player.toString()); }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
