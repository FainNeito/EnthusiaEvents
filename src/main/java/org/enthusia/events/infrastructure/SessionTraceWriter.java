package org.enthusia.events.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Bounded background file writer; Bukkit objects never leave the server thread. */
public final class SessionTraceWriter implements AutoCloseable {
    private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(2048);
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private final AtomicLong dropped = new AtomicLong();
    private final Thread worker;
    private final Path path;
    private final Consumer<String> warning;
    private volatile boolean running = true;
    public SessionTraceWriter(Path directory, Consumer<String> warning) {
        this.path = directory.resolve("sessions.log");
        this.warning = warning;
        worker = new Thread(this::writeLoop, "EnthusiaEvents-trace");
        worker.setDaemon(true); worker.start();
    }
    public void record(UUID sessionId, String event, String map, String phase, String action, UUID player) {
        String line = Instant.now() + "\t" + sessionId + "\t" + clean(event) + "\t" + clean(map)
                + "\t" + clean(phase) + "\t" + clean(action) + "\t" + (player == null ? "-" : player);
        synchronized (recent) {
            if (recent.size() == 256) recent.removeFirst();
            recent.addLast(line);
        }
        if (!queue.offer(line)) dropped.incrementAndGet();
    }
    public List<String> recent(UUID sessionId) {
        synchronized (recent) {
            return recent.stream().filter(line -> sessionId == null || line.contains("\t" + sessionId + "\t")).toList();
        }
    }
    public long dropped() { return dropped.get(); }
    private static String clean(String value) {
        if (value == null) return "-";
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').substring(0, Math.min(160, value.length()));
    }
    private void writeLoop() {
        try {
            Files.createDirectories(path.getParent());
            while (running || !queue.isEmpty()) {
                String line = queue.poll(100, TimeUnit.MILLISECONDS);
                if (line == null) continue;
                // A bounded current log plus one rotated file. Existing files are never exposed by commands.
                if (Files.exists(path) && Files.size(path) > 2_000_000)
                    Files.move(path, path.resolveSibling("sessions.previous.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(path, line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException exception) {
            warning.accept("Session trace file failed: " + exception.getClass().getSimpleName() + "; recent in-memory trace remains available");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
    @Override public void close() {
        running = false;
        try { worker.join(2_000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        if (worker.isAlive()) warning.accept("Session trace flush timed out");
        if (dropped.get() > 0) warning.accept("Session trace queue dropped " + dropped.get() + " records");
    }
}
