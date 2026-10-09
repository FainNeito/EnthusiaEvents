package org.enthusia.events.infrastructure;

import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.domain.SeasonDefinition;
import org.enthusia.events.event.EventSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/** Season-qualified, immutable completion ledger. No economy or companion grant API. */
public final class SeasonProgress {
    private final EnthusiaEventsPlugin plugin;
    private final Path root;
    private final List<SeasonDefinition> seasons = new ArrayList<>();
    private final Map<UUID, Pin> sessions = new HashMap<>();
    private final Map<String, Map<UUID, int[]>> progress = new HashMap<>();
    private final Map<Path, String> recorded = new HashMap<>();
    private String namespace;
    private String unavailable;

    public SeasonProgress(EnthusiaEventsPlugin plugin) {
        this.plugin = plugin;
        root = plugin.getDataFolder().toPath().resolve("seasons");
        if (!plugin.getConfig().getBoolean("seasons.enabled", false)) { unavailable = "Season progress is disabled."; return; }
        if (!"production".equals(plugin.getConfig().getString("seasons.environment", "test"))) { unavailable = "This environment is excluded from season credit."; return; }
        try {
            namespace = plugin.getConfig().getString("seasons.namespace", "");
            if (!namespace.matches("[a-z0-9][a-z0-9-]{0,47}")) throw new IllegalArgumentException("Set an explicit seasons.namespace");
            ZoneId.of(plugin.getConfig().getString("seasons.display-timezone", "UTC"));
            var definitions = plugin.getConfig().getConfigurationSection("seasons.definitions");
            if (definitions != null) for (String id : definitions.getKeys(false)) seasons.add(new SeasonDefinition(id,
                    Instant.parse(definitions.getString(id + ".start", "")), Instant.parse(definitions.getString(id + ".end", ""))));
            seasons.sort(Comparator.comparing(SeasonDefinition::start));
            for (int i = 1; i < seasons.size(); i++) if (seasons.get(i).start().isBefore(seasons.get(i - 1).end())) throw new IllegalArgumentException("Overlapping seasons");
            load();
        } catch (IOException | RuntimeException error) { unavailable = "Season configuration/ledger unavailable; retained original data. " + error.getClass().getSimpleName(); plugin.getLogger().warning(unavailable); }
    }
    public void pin(EventSession session, Collection<UUID> roster) {
        if (unavailable != null || session.privateSession()) return;
        Instant now = Instant.now();
        seasons.stream().filter(s -> s.contains(now)).findFirst().ifPresent(season -> sessions.putIfAbsent(session.id(), new Pin(season.id(), now, Set.copyOf(roster))));
    }
    public void complete(EventSession session, Set<UUID> winners) {
        Pin pin = sessions.remove(session.id());
        if (unavailable != null || pin == null || session.privateSession() || session.finalRankings().isEmpty()) return;
        long elapsed = java.time.Duration.between(pin.started(), Instant.now()).getSeconds();
        if (elapsed < Math.max(1, plugin.getConfig().getLong("seasons.minimum-active-seconds", 60))) return;
        // Late completions retain the ACTIVE-time season ID. Only players still in the normal outcome roster qualify.
        Set<UUID> eligible = new TreeSet<>(pin.roster());
        eligible.removeIf(id -> !session.participants().contains(id) && !session.spectators().contains(id) && !winners.contains(id));
        if (eligible.size() > 512) { unavailable = "Season roster exceeds bounded credit limit; reconcile manually."; return; }
        StringBuilder body = new StringBuilder("EE-SEASON-1\n").append(namespace).append('\n').append(pin.season()).append('\n').append(session.id()).append('\n');
        for (UUID id : eligible) body.append(id).append(' ').append(winners.contains(id) ? "win" : "participation").append('\n');
        Path file = root.resolve(namespace).resolve(pin.season()).resolve(session.id() + ".credit");
        String text = body.toString();
        try {
            if (Files.exists(file)) {
                if (Files.size(file) > 64 * 1024) throw new IOException("Oversized credit");
                String envelope = Files.readString(file);
                int separator = envelope.indexOf('\n');
                if (separator < 0 || !envelope.substring(separator + 1).equals(text)
                        || !envelope.substring(0, separator).equals(PlayerSnapshotArchive.digest(text.getBytes(StandardCharsets.UTF_8)))) throw new IOException("Completion identity conflict");
                apply(file, text);
                return;
            }
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            AtomicFiles.replace(file, (PlayerSnapshotArchive.digest(bytes) + "\n" + text).getBytes(StandardCharsets.UTF_8));
            apply(file, text);
        } catch (IOException | RuntimeException error) {
            unavailable = "Season completion failed; progress frozen for reconciliation.";
            plugin.getLogger().log(java.util.logging.Level.SEVERE, unavailable, error);
        }
    }
    public void cancel(UUID session) { sessions.remove(session); }
    public List<String> view(UUID player) {
        if (unavailable != null) return List.of(unavailable);
        Instant now = Instant.now();
        Optional<SeasonDefinition> current = seasons.stream().filter(s -> s.contains(now)).findFirst();
        if (current.isEmpty()) return List.of("No season is active. Previous season credit is retained; lifetime stats are unchanged.");
        SeasonDefinition season = current.get();
        int[] value = progress.getOrDefault(season.id(), Map.of()).getOrDefault(player, new int[2]);
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("seasons.display-timezone", "UTC"));
        return List.of("Season " + season.id() + " ends " + season.end().atZone(zone), "Completed eligible events: " + value[0] + "; wins: " + value[1],
                "Private/test/cancelled events are excluded. Cosmetic grants are not configured.");
    }
    private void load() throws IOException {
        Path directory = root.resolve(namespace);
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory, 2)) {
            List<Path> credits = files.filter(p -> p.getFileName().toString().endsWith(".credit")).limit(10_001).toList();
            if (credits.size() > 10_000) throw new IOException("Ledger exceeds startup budget; requires reviewed compaction");
            for (Path file : credits) {
                if (Files.size(file) > 64 * 1024) throw new IOException("Oversized credit");
                String envelope = Files.readString(file);
                int separator = envelope.indexOf('\n');
                if (separator < 0) throw new IOException("Missing credit checksum");
                String payload = envelope.substring(separator + 1);
                if (!envelope.substring(0, separator).equals(PlayerSnapshotArchive.digest(payload.getBytes(StandardCharsets.UTF_8)))) throw new IOException("Credit checksum mismatch");
                apply(file, payload);
            }
        }
    }
    private void apply(Path file, String payload) throws IOException {
        if (recorded.containsKey(file)) { if (!recorded.get(file).equals(payload)) throw new IOException("Conflicting credit"); return; }
        String[] lines = payload.split("\n");
        if (lines.length < 4 || !lines[0].equals("EE-SEASON-1") || !lines[1].equals(namespace)
                || !file.getParent().getFileName().toString().equals(lines[2]) || !file.getFileName().toString().equals(UUID.fromString(lines[3]) + ".credit")) throw new IOException("Credit identity mismatch");
        if (seasons.stream().noneMatch(s -> s.id().equals(lines[2]))) throw new IOException("Keep closed season definitions for retained history");
        Map<UUID, Boolean> values = new LinkedHashMap<>();
        for (int i = 4; i < lines.length; i++) {
            String[] value = lines[i].split(" ");
            if (value.length != 2 || !Set.of("win", "participation").contains(value[1]) || values.put(UUID.fromString(value[0]), value[1].equals("win")) != null) throw new IOException("Invalid duplicate player credit");
        }
        for (var entry : values.entrySet()) {
            int[] totals = progress.computeIfAbsent(lines[2], ignored -> new HashMap<>()).computeIfAbsent(entry.getKey(), ignored -> new int[2]);
            totals[0]++; if (entry.getValue()) totals[1]++;
        }
        recorded.put(file, payload);
    }
    private record Pin(String season, Instant started, Set<UUID> roster) { }
}
