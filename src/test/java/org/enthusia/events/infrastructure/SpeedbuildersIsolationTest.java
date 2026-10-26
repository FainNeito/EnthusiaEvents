package org.enthusia.events.infrastructure;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class SpeedbuildersIsolationTest {
 @Test void unrelatedBuildOutsideArenaIsNotCancelledDuringPrivateMatch() {
  var plugin=mock(EnthusiaEventsPlugin.class);var manager=mock(EventManager.class);var service=new Speedbuilders(plugin,manager);
  var world=mock(World.class);when(world.getName()).thenReturn("world");var map=new EventMap(EventType.SPEEDBUILDERS,"arena");map.region(new CuboidRegion("world",0,60,0,10,80,10));
  var session=new EventSession(new EventDefinition(EventType.SPEEDBUILDERS,"Speed",Material.STICK,"",false,false,false),EventPhase.ACTIVE);session.selectedMap(map);when(manager.session()).thenReturn(session);
  var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());var block=mock(Block.class);when(block.getLocation()).thenReturn(new Location(world,100,64,100));
  var event=mock(BlockPlaceEvent.class);when(event.getPlayer()).thenReturn(player);when(event.getBlock()).thenReturn(block);service.place(event);verify(event,never()).setCancelled(true);
 }
 @Test void staleRoundCallbackCannotCancelReplacementTask() throws Exception {
  var plugin=mock(EnthusiaEventsPlugin.class);var manager=mock(EventManager.class);var service=new Speedbuilders(plugin,manager);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
  var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
  List<Runnable> callbacks=new ArrayList<>();List<BukkitTask> tasks=new ArrayList<>();
  when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(call->{callbacks.add(call.getArgument(1));var task=mock(BukkitTask.class);tasks.add(task);return task;});
  var session=new EventSession(new EventDefinition(EventType.SPEEDBUILDERS,"Speed",Material.STICK,"",false,false,false),EventPhase.ACTIVE);when(manager.session()).thenReturn(session);
  var tracked=Speedbuilders.class.getDeclaredField("tracked");tracked.setAccessible(true);tracked.set(service,session);
  var countdown=Speedbuilders.class.getDeclaredMethod("countdown",int.class,Runnable.class);countdown.setAccessible(true);countdown.invoke(service,30,(Runnable)()->{});
  service.stop();tracked.set(service,session);countdown.invoke(service,30,(Runnable)()->{});callbacks.getFirst().run();verify(tasks.get(1),never()).cancel();
 }
}
