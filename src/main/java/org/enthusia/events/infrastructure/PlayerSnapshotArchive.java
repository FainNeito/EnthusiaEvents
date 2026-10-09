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
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Path root;
    public PlayerSnapshotArchive(Path root) { this.root = root; }
    public void capture(UUID player, UUID id, long capturedAt, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Snapshot exceeds archive limit");
        Path file = directory(player).resolve(id + ".snapshot");
        if (Files.exists(file)) throw new IOException("Capture ID already exists");
        String envelope = "EE-SNAPSHOT-1\n" + player + "\n" + id + "\n" + capturedAt + "\n" + digest(bytes) + "\n" + payload;
        AtomicFiles.replace(file, envelope.getBytes(StandardCharsets.UTF_8));
    }
    public void resolve(UUID player, UUID id) throws IOException {
        if (!Files.isRegularFile(directory(player).resolve(id + ".snapshot"))) throw new IOException("Missing archive capture");
        AtomicFiles.replace(directory(player).resolve(id + ".resolved"), Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
    }
    public List<String> history(UUID player) throws IOException {
        Path directory = directory(player);
        if (!Files.exists(directory)) return List.of("No archived versions.");
        List<String> results = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".snapshot")).sorted().limit(21).toList()) {
                if (results.size() == 20) { results.add("More versions retained on disk; nothing was pruned."); break; }
                if (Files.size(file) > MAX_BYTES + 1024) { results.add(file.getFileName() + " CORRUPT/oversized; retained"); continue; }
                String[] parts = Files.readString(file).split("\n", 6);
                boolean valid = parts.length == 6 && parts[0].equals("EE-SNAPSHOT-1") && parts[1].equals(player.toString())
                        && file.getFileName().toString().equals(parts[2] + ".snapshot") && parts[4].equals(digest(parts[5].getBytes(StandardCharsets.UTF_8)));
                results.add(file.getFileName() + (valid ? " captured=" + parts[3] + " "
                        + (Files.exists(directory.resolve(parts[2] + ".resolved")) ? "resolved" : "pending/unlinked") : " CORRUPT; retained"));
            }
        }
        results.add("Inspection only. Archived items may have been spent/transferred; no historical apply command exists.");
        return List.copyOf(results);
    }
    private Path directory(UUID player) { return root.resolve(player.toString()); }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
