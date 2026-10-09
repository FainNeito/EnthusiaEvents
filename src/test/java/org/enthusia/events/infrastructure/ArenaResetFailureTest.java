package org.enthusia.events.infrastructure;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.enthusia.events.reset.ArenaResetService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Test-only inspection of legacy private state; do not widen the production API for fixtures.
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class ArenaResetFailureTest {
    @SuppressWarnings("unchecked")
    private ArenaResetService tracked(EnthusiaEventsPlugin plugin, EventManager manager, BlockState... states) throws Exception {
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var service = new ArenaResetService(plugin, manager);
        var field = ArenaResetService.class.getDeclaredField("originalStates"); field.setAccessible(true);
        var originals = (Map<String, BlockState>) field.get(service);
        for (int i=0;i<states.length;i++) originals.put("block-" + i, states[i]);
        return service;
    }
    @Test void falseUpdateRetainsFailedAndUnattemptedStatesAndBlocksEveryStart() throws Exception {
        var plugin = mock(EnthusiaEventsPlugin.class);
        var manager = new EventManager(plugin, null, null, null, null, null, null, null, null);
        var later = mock(BlockState.class); var failed = mock(BlockState.class);
        when(failed.update(true, false)).thenReturn(false, true);
        when(later.update(true, false)).thenReturn(true);
        var reset = tracked(plugin, manager, later, failed);
        manager.services(null, reset, null, null);
        reset.reset();
        assertEquals(2, reset.pendingBlocks()); assertTrue(manager.isArenaResetting());
        verify(later, never()).update(true, false);
        assertFalse(manager.startForcedEvent(EventType.SKYWARS));
        assertFalse(manager.startPrivateEvent(null, EventType.SKYWARS, java.util.List.of()));
        assertFalse(manager.startManualVote(null, null, false)); assertFalse(manager.startScheduledVote());
        assertTrue(manager.availableStartChoices().isEmpty());
        assertThrows(IllegalStateException.class, reset::clear);
        reset.reset();
        assertEquals(0, reset.pendingBlocks()); assertFalse(reset.isResetting()); assertNull(reset.failure());
        verify(later).update(true, false); verify(failed, times(2)).update(true, false);
    }
    @Test void throwingUpdateAlsoSurvivesRetryAtShutdown() throws Exception {
        var plugin = mock(EnthusiaEventsPlugin.class); var state = mock(BlockState.class);
        when(state.update(true, false)).thenThrow(new IllegalStateException("world unavailable")).thenReturn(true);
        var reset = tracked(plugin, mock(EventManager.class), state);
        reset.reset(); assertEquals(1, reset.pendingBlocks()); assertNotNull(reset.failure());
        reset.finishPendingSynchronously(); assertEquals(0, reset.pendingBlocks()); assertFalse(reset.isResetting());
    }
}
