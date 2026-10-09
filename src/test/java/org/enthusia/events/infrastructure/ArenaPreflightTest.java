package org.enthusia.events.infrastructure;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArenaPreflightTest {
 @Test void absentWorldAndUnloadedPointsStayBlockedWithoutWorldCreation() {
  var plugin=mock(EnthusiaEventsPlugin.class);var server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
  var maps=mock(MapSetupService.class);var map=new EventMap(EventType.RUNNER,"arena");map.worldName("world");when(maps.validate(map)).thenReturn(List.of());var service=new ArenaPreflight(plugin,maps);
  assertFalse(ArenaPreflight.ready(service.inspect(map)));verify(server,never()).createWorld(any());
  var world=mock(World.class);when(world.getName()).thenReturn("world");when(world.getUID()).thenReturn(java.util.UUID.randomUUID());when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(server.getWorld("world")).thenReturn(world);
  map.spawns().put("one",new Location(world,0,64,0));map.spectatorSpawn(new Location(world,1,64,1));
  assertTrue(service.inspect(map).stream().anyMatch(f->f.severity()==org.enthusia.events.domain.PreflightFinding.Severity.UNKNOWN));verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
 }
 @Test void exhaustedPointBudgetCannotReportReady() {
  var plugin=mock(EnthusiaEventsPlugin.class);var server=mock(Server.class);var world=mock(World.class);var config=new YamlConfiguration();config.set("preflight.max-points",1);when(plugin.getConfig()).thenReturn(config);when(plugin.getServer()).thenReturn(server);when(server.getWorld("world")).thenReturn(world);
  when(world.getName()).thenReturn("world");when(world.getUID()).thenReturn(java.util.UUID.randomUUID());when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
  var block=mock(Block.class);when(block.isPassable()).thenReturn(true);when(block.getType()).thenReturn(Material.STONE);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);when(world.getBlockAt(any(Location.class))).thenReturn(block);
  var maps=mock(MapSetupService.class);var map=new EventMap(EventType.ELYTRA_RACE,"arena");map.worldName("world");when(maps.validate(map)).thenReturn(List.of());map.spawns().put("one",new Location(world,0,64,0));map.spawns().put("two",new Location(world,1,64,1));
  var findings=new ArenaPreflight(plugin,maps).inspect(map);assertFalse(ArenaPreflight.ready(findings));assertTrue(findings.stream().anyMatch(f->f.problem().contains("budget")));
 }
}
