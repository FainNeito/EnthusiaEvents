package org.enthusia.events.infrastructure;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
// Test-only inspection of legacy private state; do not widen the production API for fixtures.
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class CaptureCleanupTest {
    @TempDir Path directory;
    private EventGameplayListener listener(EventManager manager) {
        var plugin=mock(EnthusiaEventsPlugin.class);when(plugin.namespace()).thenReturn("enthusiaevents");
        when(plugin.getDataFolder()).thenReturn(directory.toFile());when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        return new EventGameplayListener(plugin,manager);
    }
    @SuppressWarnings("unchecked")
    @Test void cleanupClearsOwnershipBeforeDismountEventCanCancelIt() throws Exception {
        var manager=mock(EventManager.class);var session=session();when(manager.session()).thenReturn(session);
        var captive=mock(Player.class);var carrier=mock(Player.class);UUID prisonerId=UUID.randomUUID(),carrierId=UUID.randomUUID();
        when(captive.getUniqueId()).thenReturn(prisonerId);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            bukkit.when(()->Bukkit.getPlayer(prisonerId)).thenReturn(captive);
            bukkit.when(()->Bukkit.getPlayer(carrierId)).thenReturn(carrier);
            var listener=listener(manager);
            var owners=(Map<UUID,UUID>)get(listener,"capturePlayerCarrierByPrisoner");owners.put(prisonerId,carrierId);
            ((Map<UUID,List<?>>)get(listener,"capturePlayerStacks")).put(carrierId,new ArrayList<>());
            doAnswer(invocation->{
                var event=new EntityDismountEvent(captive,carrier);listener.onDismount(event);
                assertFalse(event.isCancelled(),"programmatic cleanup must bypass captive dismount lock");return true;
            }).when(carrier).eject();
            invoke(listener,"clearCapturePlayersState",new Class<?>[0]);
            assertTrue(owners.isEmpty());verify(captive).leaveVehicle();verify(captive).setInvulnerable(false);
        }
    }
    @SuppressWarnings("unchecked")
    @Test void roundResetDropsReverseOwnershipEvenWhenCarrierAndCaptiveAreOffline() throws Exception {
        var manager=mock(EventManager.class);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));var listener=listener(manager);
            UUID captive=UUID.randomUUID(),carrier=UUID.randomUUID();
            var owners=(Map<UUID,UUID>)get(listener,"capturePlayerCarrierByPrisoner");owners.put(captive,carrier);
            ((Map<UUID,List<?>>)get(listener,"capturePlayerStacks")).put(carrier,new ArrayList<>());
            invoke(listener,"resetCapturePlayersRound",new Class<?>[]{EventSession.class,EventMap.class},session(),mock(EventMap.class));
            assertTrue(owners.isEmpty());
        }
    }
    private static EventSession session(){return new EventSession(new EventDefinition(EventType.CAPTURE_PLAYERS,"Capture",Material.STICK,"",false,false,false),EventPhase.ACTIVE);}
    private static Object get(Object target,String name)throws Exception {var field=EventGameplayListener.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception {var method=EventGameplayListener.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(target,args);}
}
