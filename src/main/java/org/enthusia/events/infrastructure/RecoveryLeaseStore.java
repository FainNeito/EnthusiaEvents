package org.enthusia.events.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Durable application fence. Unknown/corrupt states fail closed; no automatic inventory replay. */
public final class RecoveryLeaseStore {
    private final Path root;
    public RecoveryLeaseStore(Path root) { this.root = root; }
    public boolean blocked(UUID player) {
        try { String state = state(player); return !state.equals("CLEAR") && !state.equals("APPLIED") && !state.equals("RECONCILED"); }
        catch (IOException | RuntimeException failure) { return true; }
    }
    public String state(UUID player) throws IOException {
        Path file = file(player);
        if (Files.notExists(file)) return "CLEAR";
        if (!Files.exists(file)) throw new IOException("Lease presence cannot be determined");
        String[] value = read(file);
        if (!value[1].equals(player.toString())) throw new IOException("Lease identity mismatch");
        return value[3];
    }
    public UUID begin(UUID player, UUID capture, UUID undo, String actor, String reason, String fingerprint) throws IOException {
        if (blocked(player)) throw new IOException("Recovery lease requires reconciliation");
        UUID operation = UUID.randomUUID();
        String payload = "EE-PLAYER-LEASE-1\n" + player + "\n" + operation + "\nAPPLYING\n" + capture + "\n" + undo
                + "\n" + clean(actor) + "\n" + clean(reason) + "\n" + fingerprint + "\n";
        publish(file(player), payload);
        publish(root.resolve("audit").resolve(operation + ".begin"), payload);
        return operation;
    }
    public void finish(UUID player, UUID operation, String state, String actor, String reason) throws IOException {
        if (!state.equals("APPLIED") && !state.equals("RECONCILED")) throw new IOException("Invalid recovery result");
        String[] value = read(file(player));
        if (!value[1].equals(player.toString()) || !value[2].equals(operation.toString()) || !value[3].equals("APPLYING"))
            throw new IOException("Recovery generation changed");
        value[3] = state;
        String payload = String.join("\n", value) + "\n";
        // Keep the unresolved lease if audit acknowledgement cannot be written.
        publish(root.resolve("audit").resolve(operation + "." + state.toLowerCase(java.util.Locale.ROOT)),
                payload + clean(actor) + "\n" + clean(reason) + "\n");
        publish(file(player), payload);
    }
    public UUID operation(UUID player) throws IOException { return UUID.fromString(read(file(player))[2]); }
    private Path file(UUID player) { return root.resolve(player + ".lease"); }
    private static String clean(String text) {
        if (text == null || text.isBlank() || text.length() > 240 || text.contains("\n") || text.contains("\r"))
            throw new IllegalArgumentException("Reason/actor must be 1-240 characters on one line");
        return text;
    }
    private static void publish(Path file, String payload) throws IOException {
        AtomicFiles.replace(file, (PlayerSnapshotArchive.digest(payload.getBytes(StandardCharsets.UTF_8)) + "\n" + payload).getBytes(StandardCharsets.UTF_8));
    }
    private static String[] read(Path file) throws IOException {
        if (Files.size(file) > 4096) throw new IOException("Oversized recovery lease");
        String envelope = Files.readString(file);
        int separator = envelope.indexOf('\n');
        if (separator < 0) throw new IOException("Missing recovery checksum");
        String payload = envelope.substring(separator + 1);
        String[] value = payload.split("\n");
        if (!envelope.substring(0, separator).equals(PlayerSnapshotArchive.digest(payload.getBytes(StandardCharsets.UTF_8)))
                || value.length != 9 || !value[0].equals("EE-PLAYER-LEASE-1")) throw new IOException("Corrupt recovery lease");
        UUID.fromString(value[1]); UUID.fromString(value[2]); UUID.fromString(value[4]); UUID.fromString(value[5]);
        return value;
    }
}
