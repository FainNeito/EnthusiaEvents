package org.enthusia.events.domain;

import java.time.Instant;

public record SeasonDefinition(String id, Instant start, Instant end) {
    public SeasonDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{0,47}") || start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("Invalid season identity/dates");
    }
    public boolean contains(Instant instant) { return !instant.isBefore(start) && instant.isBefore(end); }
}
