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
    private static final String NO_COSMETIC = "none";
    private static final String WIN_CREDIT = "win";
    private static final String PARTICIPATION_CREDIT = "participation";
    private static final String CREDIT_SUFFIX = ".credit";
    private static final String MINIMUM_ACTIVE_CONFIG = "seasons.minimum-active-seconds";
    private final EnthusiaEventsPlugin plugin;
    private final Path root;
    private final List<SeasonDefinition> seasons = new ArrayList<>();
    private final Map<UUID, Pin> sessions = new HashMap<>();
    private final Map<String, Map<UUID, int[]>> progress = new HashMap<>();
    private final Map<Path, String> recorded = new HashMap<>();
    private String namespace;
    private final java.time.Clock clock;
    private final Map<String, Map<UUID, Map<String, Long>>> bests = new HashMap<>();
    private final Map<String, Cosmetic> cosmetics = new TreeMap<>();
    private String unavailable;

    public SeasonProgress(EnthusiaEventsPlugin plugin) { this(plugin, java.time.Clock.systemUTC()); }
    public SeasonProgress(EnthusiaEventsPlugin plugin, java.time.Clock clock) {
        this.plugin = plugin;
        this.clock = clock;
        root = plugin.getDataFolder().toPath().resolve("seasons");
        if (!plugin.getConfig().getBoolean("seasons.enabled", false)) { unavailable = "Season progress is disabled."; return; }
        if (!"production".equals(plugin.getConfig().getString("seasons.environment", "test"))) { unavailable = "This environment is excluded from season credit."; return; }
        try {
            namespace = plugin.getConfig().getString("seasons.namespace", "");
            if (!namespace.matches("[a-z0-9][a-z0-9-]{0,47}")) throw new IllegalArgumentException("Set an explicit seasons.namespace");
            ZoneId.of(plugin.getConfig().getString("seasons.display-timezone", "UTC"));
            loadDefinitions();
            loadCosmetics();
            for (SeasonDefinition season : seasons) if (Files.exists(root.resolve(namespace).resolve(season.id()).resolve("policy.seal"))) sealPolicy(season);
            load();
        } catch (IOException | RuntimeException error) { unavailable = "Season configuration/ledger unavailable; retained original data. " + error.getClass().getSimpleName(); plugin.getLogger().warning(unavailable); }
    }
    private void loadDefinitions() {
            var definitions = plugin.getConfig().getConfigurationSection("seasons.definitions");
            if (definitions != null) for (String id : definitions.getKeys(false)) seasons.add(new SeasonDefinition(id,
                    Instant.parse(definitions.getString(id + ".start", "")), Instant.parse(definitions.getString(id + ".end", ""))));
            seasons.sort(Comparator.comparing(SeasonDefinition::start));
            for (int i = 1; i < seasons.size(); i++) if (seasons.get(i).start().isBefore(seasons.get(i - 1).end())) throw new IllegalArgumentException("Overlapping seasons");
    }
    private void loadCosmetics() {
            var catalogue = plugin.getConfig().getConfigurationSection("seasons.cosmetics");
            if (catalogue != null) for (String id : catalogue.getKeys(false)) {
                String title = catalogue.getString(id + ".title", "");
                int completed = catalogue.getInt(id + ".completed", 0), wins = catalogue.getInt(id + ".wins", 0);
                validateCosmetic(id, title, completed, wins);
                cosmetics.put(id, new Cosmetic(id, title, completed, wins));
            }
    }
    private static void validateCosmetic(String id, String title, int completed, int wins) {
        if (!id.matches("[a-z0-9][a-z0-9-]{0,47}") || !validTitle(title)) throw new IllegalArgumentException("Invalid cosmetic milestone/title");
        if (completed < 0 || wins < 0 || (long) completed + wins < 1) throw new IllegalArgumentException("Invalid cosmetic milestone/title");
    }
    private static boolean validTitle(String title) {
        return !title.isBlank() && title.length() <= 48 && title.chars().noneMatch(Character::isISOControl);
    }
    public void pin(EventSession session, Collection<UUID> roster) {
        if (unavailable != null || session.privateSession() || roster.size() > 512) return;
        Instant now = clock.instant();
        seasons.stream().filter(s -> s.contains(now)).findFirst().ifPresent(season -> {
            try {
                sealPolicy(season);
                Map<UUID, Activity> activity = new HashMap<>();
                for (UUID player : roster) activity.put(player, initialActivity(player));
                sessions.putIfAbsent(session.id(), new Pin(season.id(), now, Set.copyOf(roster), activity, new HashMap<>(), courseKey(session)));
            } catch (IOException failure) { unavailable = "Season policy changed/storage unavailable; progress frozen."; }
        });
    }
    public void complete(EventSession session, Set<UUID> winners) {
        Pin pin = sessions.remove(session.id());
        if (unavailable != null || pin == null || session.privateSession() || session.finalRankings().isEmpty()) return;
        SeasonDefinition definition = seasons.stream().filter(s -> s.id().equals(pin.season())).findFirst().orElseThrow();
        long grace = Math.clamp(plugin.getConfig().getLong("seasons.closing-grace-seconds", 0), 0, 604800);
        if (clock.instant().isAfter(definition.end().plusSeconds(grace))) return;
        long elapsed = java.time.Duration.between(pin.started(), clock.instant()).getSeconds();
        if (elapsed < Math.max(1, plugin.getConfig().getLong(MINIMUM_ACTIVE_CONFIG, 60))) return;
        // Late completions retain the ACTIVE-time season ID. Only players still in the normal outcome roster qualify.
        Set<UUID> eligible = new TreeSet<>(pin.roster());
        eligible.removeIf(id -> (!session.participants().contains(id) && !session.spectators().contains(id) && !winners.contains(id))
                || pin.activity().get(id).activeMillis < Math.max(1, plugin.getConfig().getLong(MINIMUM_ACTIVE_CONFIG, 60)) * 1000L);
        if (eligible.size() > 512) { unavailable = "Season roster exceeds bounded credit limit; reconcile manually."; return; }
        publishCompletion(session, pin, completionPayload(session, pin, eligible, winners));
    }
    private String completionPayload(EventSession session, Pin pin, Set<UUID> eligible, Set<UUID> winners) {
        StringBuilder body = new StringBuilder("EE-SEASON-3\n").append(namespace).append('\n').append(pin.season()).append('\n').append(session.id()).append('\n').append(session.definition().type().name()).append('\n').append(java.util.Base64.getEncoder().encodeToString(pin.course().getBytes(StandardCharsets.UTF_8))).append('\n');
        for (UUID id : eligible) body.append(id).append(' ').append(winners.contains(id) ? WIN_CREDIT : PARTICIPATION_CREDIT).append(' ').append(pin.metrics().getOrDefault(id, 0L)).append('\n');
        return body.toString();
    }
    private void publishCompletion(EventSession session, Pin pin, String text) {
        Path file = root.resolve(namespace).resolve(pin.season()).resolve(session.id() + CREDIT_SUFFIX);
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
        Instant now = clock.instant();
        Optional<SeasonDefinition> current = seasons.stream().filter(s -> s.contains(now)).findFirst();
        if (current.isEmpty()) return List.of("No season is active. Previous season credit is retained; lifetime stats are unchanged.");
        SeasonDefinition season = current.get();
        int[] value = progress.getOrDefault(season.id(), Map.of()).getOrDefault(player, new int[2]);
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("seasons.display-timezone", "UTC"));
        List<String> lines = new ArrayList<>(List.of("Season " + season.id() + " ends " + season.end().atZone(zone), "Completed eligible events: " + value[0] + "; wins: " + value[1],
                "Private/test/cancelled and inactive players are excluded; no currency or external grants."));
        bests.getOrDefault(season.id(), Map.of()).getOrDefault(player, Map.of()).forEach((type, milliseconds) -> lines.add("Personal best " + type + ": " + milliseconds + "ms"));
        for (Cosmetic cosmetic : cosmetics.values()) lines.add(cosmetic.id() + " " + (owned(season.id(), player, cosmetic) ? "UNLOCKED" : "LOCKED")
                + " requires completed=" + cosmetic.completed() + ", wins=" + cosmetic.wins() + "; victory title=" + cosmetic.title());
        lines.add("Selected cosmetic: " + selected(player, season.id()) + "; /event season equip <id|none>");
        return List.copyOf(lines);
    }
    private void load() throws IOException {
        Path directory = root.resolve(namespace);
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory, 2)) {
            List<Path> credits = files.filter(p -> p.getFileName().toString().endsWith(CREDIT_SUFFIX)).limit(10_001).toList();
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
        Credit credit = parseCredit(file, payload);
        for (var entry : credit.values().entrySet()) {
            int[] totals = progress.computeIfAbsent(credit.season(), ignored -> new HashMap<>()).computeIfAbsent(entry.getKey(), ignored -> new int[2]);
            totals[0]++; if (entry.getValue()) totals[1]++;
            long metric = credit.metrics().get(entry.getKey());
            if (metric > 0) bests.computeIfAbsent(credit.season(), ignored -> new HashMap<>()).computeIfAbsent(entry.getKey(), ignored -> new HashMap<>()).merge(credit.course(), metric, Math::min);
        }
        recorded.put(file, payload);
    }
    private Credit parseCredit(Path file, String payload) throws IOException {
        String[] lines = payload.split("\n");
        validateCreditIdentity(file, lines);
        Map<UUID, Boolean> values = new LinkedHashMap<>();
        boolean currentSchema = !lines[0].equals("EE-SEASON-1");
        boolean courseSchema = lines[0].equals("EE-SEASON-3");
        if (currentSchema && lines.length < 5) throw new IOException("Missing metric event type");
        String type = currentSchema ? org.enthusia.events.event.EventType.valueOf(lines[4]).name() : "legacy";
        if (courseSchema && lines.length < 6) throw new IOException("Missing course identity");
        String course = courseSchema ? new String(java.util.Base64.getDecoder().decode(lines[5]), StandardCharsets.UTF_8) : type + "/legacy-unqualified";
        validateCourse(course, type);
        Map<UUID, Long> metrics = new HashMap<>();
        int firstPlayer = firstPlayerLine(courseSchema, currentSchema);
        for (int i = firstPlayer; i < lines.length; i++) parsePlayerCredit(lines[i], currentSchema, type, values, metrics);
        if (values.size() > 512) throw new IOException("Oversized credit roster");
        return new Credit(lines[2], course, values, metrics);
    }
    private static void validateCourse(String course, String type) throws IOException {
        if (course.length() > 512 || course.chars().anyMatch(Character::isISOControl) || !course.startsWith(type + "/")) throw new IOException("Invalid course identity");
    }
    private static int firstPlayerLine(boolean courseSchema, boolean currentSchema) { return courseSchema ? 6 : currentSchema ? 5 : 4; }
    private static void parsePlayerCredit(String line, boolean currentSchema, String type, Map<UUID, Boolean> values, Map<UUID, Long> metrics) throws IOException {
            String[] value = line.split(" ");
            if (value.length != (currentSchema ? 3 : 2) || !Set.of(WIN_CREDIT, PARTICIPATION_CREDIT).contains(value[1]) || values.put(UUID.fromString(value[0]), value[1].equals(WIN_CREDIT)) != null) throw new IOException("Invalid duplicate player credit");
            long metric = currentSchema ? Long.parseLong(value[2]) : 0;
            if (metric < 0 || metric > 86_400_000 || metric > 0 && !timedType(type)) throw new IOException("Unsupported personal best metric");
            metrics.put(UUID.fromString(value[0]), metric);
    }
    private void validateCreditIdentity(Path file, String[] lines) throws IOException {
        if (lines.length < 4 || !Set.of("EE-SEASON-1", "EE-SEASON-2", "EE-SEASON-3").contains(lines[0]) || !lines[1].equals(namespace)
                || !file.getParent().getFileName().toString().equals(lines[2]) || !file.getFileName().toString().equals(UUID.fromString(lines[3]) + CREDIT_SUFFIX)) throw new IOException("Credit identity mismatch");
        if (seasons.stream().noneMatch(s -> s.id().equals(lines[2]))) throw new IOException("Keep closed season definitions for retained history");
    }
    private record Credit(String season, String course, Map<UUID, Boolean> values, Map<UUID, Long> metrics) { }
    public void sample(EventSession session) {
        Pin pin = sessions.get(session.id());
        if (pin == null) return;
        long now = clock.millis();
        for (UUID id : pin.roster()) samplePlayer(session, pin, id, now);
    }
    private void samplePlayer(EventSession session, Pin pin, UUID id, long now) {
            Activity activity = pin.activity().get(id);
            if (now - activity.sampled < 1000) return;
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(id);
            boolean engaged = false;
            if (player != null && player.isOnline() && session.participants().contains(id)) {
                org.bukkit.Location position = player.getLocation();
                if (observedInput(position, activity.position)) { activity.lastInput = now; activity.seenInput = true; }
                activity.position = position.clone();
                engaged = activity.seenInput && now - activity.lastInput <= Math.clamp(plugin.getConfig().getLong("seasons.idle-seconds", 60), 1, 3600) * 1000L;
            }
            if (engaged) activity.activeMillis += Math.min(2000, Math.max(0, now - activity.sampled));
            activity.sampled = now;
    }
    private Activity initialActivity(UUID id) {
        Activity activity = new Activity(clock.millis());
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(id);
        if (player != null && player.isOnline()) activity.position = player.getLocation().clone();
        return activity;
    }
    private static boolean observedInput(org.bukkit.Location current, org.bukkit.Location previous) {
        if (previous == null) return false;
        return !Objects.equals(current.getWorld(), previous.getWorld()) || current.distanceSquared(previous) > 0.01
                || Math.abs(current.getYaw() - previous.getYaw()) >= 3 || Math.abs(current.getPitch() - previous.getPitch()) >= 3;
    }
    public void finished(EventSession session, UUID player) {
        Pin pin = sessions.get(session.id());
        if (pin == null || !pin.roster().contains(player) || !timedType(session.definition().type().name())) return;
        long milliseconds = Math.max(1, java.time.Duration.between(pin.started(), clock.instant()).toMillis());
        if (milliseconds <= 86_400_000) pin.metrics().putIfAbsent(player, milliseconds);
    }
    private static String courseKey(EventSession session) {
        var map = session.selectedMap();
        String type = session.definition().type().name();
        if (map == null) return type + "/unconfigured";
        StringBuilder setup = new StringBuilder(String.valueOf(map.region()));
        new TreeMap<>(map.spawns()).forEach((key, point) -> setup.append(key).append(org.enthusia.events.util.LocationCodec.encode(point)));
        new TreeMap<>(map.points()).forEach((key, point) -> setup.append(key).append(org.enthusia.events.util.LocationCodec.encode(point)));
        new TreeMap<>(map.checkpoints()).forEach((key, point) -> setup.append(key).append(org.enthusia.events.util.LocationCodec.encode(point)));
        new TreeMap<>(map.checkpointBlocks()).forEach((key, points) -> { setup.append(key); points.forEach(point -> setup.append(org.enthusia.events.util.LocationCodec.encode(point))); });
        new TreeMap<>(map.areas()).forEach((key, area) -> setup.append(key).append(area));
        String world = map.spawns().values().stream().filter(point -> point != null && point.getWorld() != null).map(point -> point.getWorld().getUID().toString()).findFirst().orElse(map.worldName());
        setup.append(world);
        return type + "/" + map.id() + "#" + PlayerSnapshotArchive.digest(setup.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }
    private static boolean timedType(String type) { return Set.of("PARKOUR", "HORSE_RACE", "BOAT_RACE", "ELYTRA_RACE").contains(type); }
    private void sealPolicy(SeasonDefinition season) throws IOException {
        String policy = season + "\n" + plugin.getConfig().getLong(MINIMUM_ACTIVE_CONFIG, 60) + "\n"
                + plugin.getConfig().getLong("seasons.closing-grace-seconds", 0) + "\n" + plugin.getConfig().getLong("seasons.idle-seconds", 60) + "\n" + cosmetics;
        String fingerprint = PlayerSnapshotArchive.digest(policy.getBytes(StandardCharsets.UTF_8));
        Path file = root.resolve(namespace).resolve(season.id()).resolve("policy.seal");
        if (Files.exists(file)) { if (!Files.readString(file).equals(fingerprint)) throw new IOException("Season policy immutable once pinned"); }
        else AtomicFiles.replace(file, fingerprint.getBytes(StandardCharsets.UTF_8));
    }
    private boolean owned(String season, UUID player, Cosmetic cosmetic) {
        int[] counts = progress.getOrDefault(season, Map.of()).getOrDefault(player, new int[2]);
        return counts[0] >= cosmetic.completed() && counts[1] >= cosmetic.wins();
    }
    public String equip(UUID player, String id) throws IOException {
        if (unavailable != null) throw new IOException(unavailable);
        SeasonDefinition season = seasons.stream().filter(s -> s.contains(clock.instant())).findFirst().orElseThrow(() -> new IllegalArgumentException("No active season"));
        if (!id.equals(NO_COSMETIC) && (!cosmetics.containsKey(id) || !owned(season.id(), player, cosmetics.get(id)))) throw new IllegalArgumentException("Cosmetic is locked or unknown");
        String payload = season.id() + "\n" + id;
        AtomicFiles.replace(root.resolve(namespace).resolve("selected").resolve(player + ".selection"), (PlayerSnapshotArchive.digest(payload.getBytes(StandardCharsets.UTF_8)) + "\n" + payload).getBytes(StandardCharsets.UTF_8));
        return "Selected victory title: " + id + ". No items, permissions or currency granted.";
    }
    private String selected(UUID player, String season) {
        Path file = root.resolve(namespace).resolve("selected").resolve(player + ".selection");
        try {
            if (!Files.exists(file) || Files.size(file) > 256) return NO_COSMETIC;
            String[] value = Files.readString(file).split("\n", 3);
            if (value.length != 3 || !value[1].equals(season) || !value[0].equals(PlayerSnapshotArchive.digest((value[1] + "\n" + value[2]).getBytes(StandardCharsets.UTF_8)))) return NO_COSMETIC;
            Cosmetic cosmetic = cosmetics.get(value[2]);
            return cosmetic != null && owned(season, player, cosmetic) ? cosmetic.id() : NO_COSMETIC;
        } catch (IOException | RuntimeException failure) { return NO_COSMETIC; }
    }
    public void showVictoryTitle(EventSession session, UUID player) {
        if (unavailable != null || session.privateSession()) return;
        seasons.stream().filter(s -> s.contains(clock.instant())).findFirst().ifPresent(season -> {
            Cosmetic cosmetic = cosmetics.get(selected(player, season.id()));
            org.bukkit.entity.Player online = plugin.getServer().getPlayer(player);
            if (cosmetic != null && online != null) online.showTitle(net.kyori.adventure.title.Title.title(net.kyori.adventure.text.Component.text(cosmetic.title()), net.kyori.adventure.text.Component.text("Season " + season.id())));
        });
    }
    private static final class Activity {
        private long sampled, lastInput, activeMillis;
        private boolean seenInput;
        private org.bukkit.Location position;
        private Activity(long now) { sampled = now; lastInput = now; }
    }
    private record Cosmetic(String id, String title, int completed, int wins) { }
    private record Pin(String season, Instant started, Set<UUID> roster, Map<UUID, Activity> activity, Map<UUID, Long> metrics, String course) { }
}
