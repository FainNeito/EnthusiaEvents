package org.enthusia.events.infrastructure;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RunnerListenerTest {
    @Test void runnerIsOptInAndDoesNotChangeExistingEnabledList() {
        var stream=Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        var yaml=YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
        assertFalse(yaml.getStringList("events.enabled").contains("RUNNER"));
        assertTrue(yaml.getStringList("events.enabled").contains("SKYWARS"));
    }
    @Test void recordsOriginalBeforeDeletingAndLateTasksCannotModifyEndedSession() {
        var plugin=mock(EnthusiaEventsPlugin.class);var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        var scheduled=new ArrayList<Runnable>();
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(4L))).thenAnswer(call->{scheduled.add(call.getArgument(1));return null;});
        var manager=mock(EventManager.class);var player=mock(Player.class);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        var world=mock(World.class);when(world.getName()).thenReturn("arena");when(world.getUID()).thenReturn(UUID.randomUUID());
        var block=mock(Block.class);when(world.getBlockAt(any(Location.class))).thenReturn(block);when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world,0,69,0));when(block.getType()).thenReturn(Material.STONE);
        var data=mock(BlockData.class);when(block.getBlockData()).thenReturn(data);when(data.clone()).thenReturn(data);
        var session=new EventSession(new EventDefinition(EventType.RUNNER,"Runner",Material.TNT,"",false,false,false),EventPhase.ACTIVE);
        session.participants().add(id);var map=new EventMap(EventType.RUNNER,"floor");
        map.region(new CuboidRegion("arena",-10,60,-10,10,80,10));map.areas().put("breakable-area",new CuboidRegion("arena",-10,69,-10,10,69,10));session.selectedMap(map);when(manager.session()).thenReturn(session);
        var listener=new RunnerListener(plugin,manager);var location=new Location(world,0,70,0);
        listener.move(new PlayerMoveEvent(player,location,location));listener.move(new PlayerMoveEvent(player,location,location));assertEquals(1,scheduled.size());
        scheduled.removeFirst().run();var order=inOrder(manager,block);order.verify(manager).recordArenaResetBlock(block);order.verify(block).setType(Material.AIR,false);
        listener.move(new PlayerMoveEvent(player,location,location));session.phase(EventPhase.TROPHY);scheduled.removeFirst().run();
        verify(block,times(1)).setType(Material.AIR,false);
    }
}
