package org.enthusia.events.infrastructure;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.*;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArenaRecoveryTest {
 private static final String WORLD_NAME = "world";
 @TempDir Path root;
 EnthusiaEventsPlugin plugin;World world;Block block;EventMap map;EventSession session;List<Step> steps;
 BlockData original, changed; AtomicReference<BlockData> current;
 @BeforeEach void setup() {
  plugin=mock(EnthusiaEventsPlugin.class);world=mock(World.class);block=mock(Block.class);steps=new ArrayList<>();
  var config=new YamlConfiguration();config.set("recovery.arena.enabled",true);when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(root.toFile());
  var server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.getWorld(WORLD_NAME)).thenReturn(world);
  UUID worldId=UUID.randomUUID();when(world.getUID()).thenReturn(worldId);when(world.getName()).thenReturn(WORLD_NAME);when(server.getWorld(worldId)).thenReturn(world);
  when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
  original=mock(BlockData.class);changed=mock(BlockData.class);when(original.getAsString()).thenReturn("minecraft:stone");when(changed.getAsString()).thenReturn("minecraft:air");
  current=new AtomicReference<>(original);when(block.getBlockData()).thenAnswer(call->current.get());when(block.getState()).thenReturn(mock(BlockState.class));
  when(server.createBlockData("minecraft:stone")).thenReturn(original);doAnswer(call->{current.set(call.getArgument(0));return null;}).when(block).setBlockData(any(),eq(false));
  var scheduler=mock(BukkitScheduler.class);when(server.getScheduler()).thenReturn(scheduler);
  when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(call->{var step=new Step(call.getArgument(1));steps.add(step);return step.task;});
  when(scheduler.runTaskAsynchronously(eq(plugin),any(Runnable.class))).thenAnswer(call->{((Runnable)call.getArgument(1)).run();return mock(BukkitTask.class);});
  when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call->{((Runnable)call.getArgument(1)).run();return mock(BukkitTask.class);});
  map=new EventMap(EventType.RUNNER,"arena");map.worldName(WORLD_NAME);map.region(new CuboidRegion(WORLD_NAME,0,64,0,0,64,0));
  var maps=mock(MapSetupService.class);when(plugin.maps()).thenReturn(maps);when(maps.find(EventType.RUNNER,"arena")).thenReturn(Optional.of(map));
  session=new EventSession(new EventDefinition(EventType.RUNNER,"Test",Material.STICK,"",false,false,false),EventPhase.PRESTART);session.selectedMap(map);
 }
 private void drain() { for(int n=0;n<100;n++){var live=steps.stream().filter(s->!s.cancelled).toList();if(live.isEmpty())return;for(Step step:live)step.action.run();}fail("work exceeded bounded fixture"); }
 Path pending(){return root.resolve("recovery/arena/pending.bin");}
 ArenaRecovery capture() {var service=new ArenaRecovery(plugin);service.prepare(session,()->{},failure->fail(failure));drain();assertTrue(service.prepared(session));return service;}
 @Test void restartQuarantinesBeforeReplayAndVerifiesOriginalBeforeCompleting() throws Exception {
  ArenaRecovery first=capture();assertTrue(Files.exists(pending()));first.shutdown();current.set(changed);
  var restarted=new ArenaRecovery(plugin);assertTrue(restarted.quarantine());assertFalse(restarted.prepared(session));restarted.retry();drain();
  assertSame(original,current.get());assertFalse(restarted.blocked());assertFalse(Files.exists(pending()));
  assertTrue(Files.exists(pending().resolveSibling(session.id()+".complete.bin")));
 }
 @Test void unloadedChunkFailureRetainsBaselineAndSupportsRepeatableRetry() throws Exception {
  capture().shutdown();current.set(changed);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);var recovery=new ArenaRecovery(plugin);recovery.retry();drain();
  assertTrue(recovery.blocked());assertTrue(Files.exists(pending()));assertSame(changed,current.get());
  when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);recovery.retry();drain();assertFalse(recovery.blocked());assertSame(original,current.get());
 }
 @Test void corruptBytesAndChangedArenaBoundsNeverReplayOrDiscard() throws Exception {
  capture().shutdown();map.region(new CuboidRegion(WORLD_NAME,0,64,0,1,64,0));var recovery=new ArenaRecovery(plugin);recovery.retry();drain();assertTrue(recovery.blocked());verify(block,never()).setBlockData(any(),anyBoolean());
  Files.writeString(pending(),"corrupt original");var corrupted=new ArenaRecovery(plugin);corrupted.retry();assertTrue(corrupted.quarantine());assertEquals("corrupt original",Files.readString(pending()));
 }
 @Test void unsupportedTileRejectsPreparationWithoutPublishedBaseline() throws Exception {
  when(block.getState()).thenReturn(mock(Sign.class));var recovery=new ArenaRecovery(plugin);List<String> failures=new ArrayList<>();recovery.prepare(session,()->fail("activation must not happen"),failures::add);drain();
  assertEquals(1,failures.size());assertTrue(recovery.blocked());assertFalse(Files.exists(pending()));recovery.retry();assertFalse(recovery.blocked());verify(block,never()).setBlockData(any(),anyBoolean());
 }
 static final class Step {final Runnable action;final BukkitTask task=mock(BukkitTask.class);boolean cancelled;Step(Runnable action){this.action=action;doAnswer(c->{cancelled=true;return null;}).when(task).cancel();}}
}
