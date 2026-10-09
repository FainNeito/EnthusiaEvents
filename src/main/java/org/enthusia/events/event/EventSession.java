package org.enthusia.events.event;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("PMD.UseConcurrentHashMap")
public final class EventSession {

    private final UUID sessionId = UUID.randomUUID();
    private java.util.function.Consumer<EventPhase> phaseObserver = ignored -> {};
    public UUID id() { return sessionId; }
    public void phaseObserver(java.util.function.Consumer<EventPhase> observer) { phaseObserver = observer; }
    private EventDefinition currentDefinition;
    private final Set<UUID> participantIds = new LinkedHashSet<>();
    private final Set<UUID> spectatorIds = new LinkedHashSet<>();
    private final Map<EventType, Integer> voteCounts = new LinkedHashMap<>();
    private EventPhase currentPhase;
    private Location waitingHubLocation;
    private Location trophyRoomLocation;
    private EventMap activeMap;
    private String starterName;
    private boolean startedByAdmin;
    private boolean privateSession;
    private final Map<UUID, String> playerTeams = new LinkedHashMap<>();
    private final List<UUID> rankings = new ArrayList<>();
    private final Set<UUID> invitedPlayers = new LinkedHashSet<>();

    public EventSession(EventDefinition definition, EventPhase phase) {
        this.currentDefinition = definition;
        this.currentPhase = phase;
    }

    public EventDefinition definition() {
        return currentDefinition;
    }

    public void definition(EventDefinition definition) {
        this.currentDefinition = definition;
    }

    public Set<UUID> participants() {
        return participantIds;
    }

    public Set<UUID> spectators() {
        return spectatorIds;
    }

    public Map<EventType, Integer> votes() {
        return voteCounts;
    }

    public EventPhase phase() {
        return currentPhase;
    }

    public void phase(EventPhase phase) {
        if (currentPhase == phase) return;
        this.currentPhase = phase;
        phaseObserver.accept(phase);
    }

    public Location waitingHub() {
        return waitingHubLocation;
    }

    public void waitingHub(Location waitingHub) {
        this.waitingHubLocation = waitingHub;
    }

    public Location trophyRoom() {
        return trophyRoomLocation;
    }

    public void trophyRoom(Location trophyRoom) {
        this.trophyRoomLocation = trophyRoom;
    }

    public EventMap selectedMap() {
        return activeMap;
    }

    public void selectedMap(EventMap selectedMap) {
        this.activeMap = selectedMap;
    }

    public String startedBy() {
        return starterName;
    }

    public void startedBy(String startedBy) {
        this.starterName = startedBy;
    }

    public boolean adminStarted() {
        return startedByAdmin;
    }

    public void adminStarted(boolean adminStarted) {
        this.startedByAdmin = adminStarted;
    }

    public boolean privateSession() {
        return privateSession;
    }

    public void privateSession(boolean privateSession) {
        this.privateSession = privateSession;
    }

    public Set<UUID> invitedPlayers() {
        return invitedPlayers;
    }

    public Map<UUID, String> teams() {
        return playerTeams;
    }

    public List<UUID> finalRankings() {
        return rankings;
    }
}
