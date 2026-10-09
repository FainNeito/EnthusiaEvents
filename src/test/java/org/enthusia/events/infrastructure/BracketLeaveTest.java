package org.enthusia.events.infrastructure;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.config.Messages;
import org.enthusia.events.event.*;
import org.enthusia.events.kit.EventKitService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
// Test-only inspection of legacy private state; do not widen the production API for fixtures.
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class BracketLeaveTest {
    @Test void runnerSpectateSchedulesLastSurvivorCompletionAndRecordsDeparture() throws Exception {
        assertSpectateWithdrawal(EventType.RUNNER, true, 60L);
    }
    @Test void skywarsFinalParticipantSpectateSchedulesImmediateCompletion() throws Exception {
        assertSpectateWithdrawal(EventType.SKYWARS, false, 0L);
    }
    private void assertSpectateWithdrawal(EventType type, boolean opponentRemains, long delay) throws Exception {
        var plugin = mock(EnthusiaEventsPlugin.class);
        when(plugin.messages()).thenReturn(mock(Messages.class));
        var manager = new EventManager(plugin, null, mock(PlayerSnapshotService.class), null, null, null, null, mock(EventKitService.class), null);
        var player = mock(Player.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var session = new EventSession(new EventDefinition(type, "Test", Material.STICK, "", false, false, false), EventPhase.ACTIVE);
        session.participants().add(id);
        if (opponentRemains) session.participants().add(UUID.randomUUID());
        var map = mock(EventMap.class);
        when(map.spectatorSpawn()).thenReturn(new org.bukkit.Location(null, 0, 70, 0));
        session.selectedMap(map); set(manager, "session", session);
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class); var teleports = mockStatic(org.enthusia.events.util.TeleportService.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertTrue(manager.spectate(player));
        }
        assertNotNull(get(manager, "phaseTask"), "withdrawal must schedule completion without waiting for another elimination");
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(delay));
        verify(plugin).recordSessionEvidence(session, "departure", id);
        assertTrue(session.spectators().contains(id)); assertFalse(session.participants().contains(id));
    }
    @SuppressWarnings("unchecked")
    @Test void voluntaryExitForfeitsCurrentSoloMatchAndQueuesOpponent() throws Exception {
        var plugin = mock(EnthusiaEventsPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration()); when(plugin.messages()).thenReturn(mock(Messages.class));
        var snapshots = mock(PlayerSnapshotService.class);
        var manager = spy(new EventManager(plugin, null, snapshots, null, null, null, null, mock(EventKitService.class), null));
        var leaver = mock(Player.class); UUID id = UUID.randomUUID(), opponent = UUID.randomUUID();
        when(leaver.getUniqueId()).thenReturn(id); when(leaver.getName()).thenReturn("leaver");
        when(leaver.getInventory()).thenReturn(mock(PlayerInventory.class));
        doNothing().when(manager).emergencyCleanupPlayer(leaver, false);
        var session = new EventSession(new EventDefinition(EventType.SUMO_1V1, "Sumo", Material.STICK, "", false, false, false), EventPhase.ACTIVE);
        session.participants().addAll(List.of(id, opponent));
        set(manager,"session",session);
        Set<UUID> contestants = (Set<UUID>) get(manager,"bracketContestants"); contestants.addAll(List.of(id,opponent));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            assertTrue(manager.leave(leaver));
        }
        assertFalse(session.participants().contains(id)); assertTrue(contestants.isEmpty());
        var queue = (Queue<UUID>) get(manager,"bracketQueue");
        assertEquals(List.of(opponent), List.copyOf(queue));
    }
    private static Object get(Object target,String name) throws Exception {
        var field=EventManager.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        var field=EventManager.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
