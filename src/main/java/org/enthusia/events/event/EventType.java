package org.enthusia.events.event;

public enum EventType {
    SKYWARS,
    BEDWARS,
    FIGHT_1V1,
    FIGHT_2V2,
    FIGHT_FFA,
    SUMO_1V1,
    SUMO_2V2,
    SUMO_FFA,
    KNOCKBACK_FFA,
    QUAKE,
    ONE_IN_THE_CHAMBER,
    CAPTURE_THE_FLAG,
    CAPTURE_PLAYERS,
    BLOCK_PARTY,
    HOT_POTATO,
    SPLEEF,
    SPLEGG,
    RED_LIGHT_GREEN_LIGHT,
    BOAT_RACE,
    HORSE_RACE,
    ELYTRA_RACE,
    PARKOUR,
    RUNNER;

    public static EventType parse(String raw) {
        String normalized = raw == null ? "" : raw.toUpperCase(java.util.Locale.ROOT);
        if ("SPLEEG".equals(normalized)) {
            normalized = "SPLEGG";
        }
        return EventType.valueOf(normalized);
    }
}
