package org.enthusia.events.infrastructure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
class SessionTraceWriterTest {
    @TempDir Path directory;
    @Test void flushesSanitizedRecordsAndBoundsRecentHistory() throws Exception {
        UUID id = UUID.randomUUID(); var warnings = new CopyOnWriteArrayList<String>();
        try (var trace = new SessionTraceWriter(directory, warnings::add)) {
            for (int i=0;i<300;i++) trace.record(id, "RUNNER", "map\nwith\ttabs", "ACTIVE", "action-" + i, null);
            assertEquals(256, trace.recent(id).size()); assertTrue(trace.recent(UUID.randomUUID()).isEmpty());
        }
        var lines = Files.readAllLines(directory.resolve("sessions.log"));
        assertEquals(300, lines.size()); assertTrue(lines.getFirst().contains("map with tabs")); assertTrue(warnings.isEmpty());
    }
    @Test void unwritableDestinationStillKeepsRecentDiagnostics() throws Exception {
        Path file = directory.resolve("not-a-directory"); Files.writeString(file, "existing");
        var warnings = new CopyOnWriteArrayList<String>();
        try (var trace = new SessionTraceWriter(file, warnings::add)) {
            trace.record(UUID.randomUUID(), "SKYWARS", "map", "END", "restore-failed", null);
            assertEquals(1, trace.recent(null).size());
        }
        assertFalse(warnings.isEmpty()); assertEquals("existing", Files.readString(file));
    }
}
