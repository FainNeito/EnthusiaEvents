package org.enthusia.events.infrastructure;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlayerRecoveryTest {
 private static final String SNAPSHOT_SUFFIX = ".snapshot";
 @TempDir Path root;
 org.mockito.MockedStatic<PlayerSnapshot> captures;
 EnthusiaEventsPlugin plugin; Player player; PlayerInventory inventory; World world; CommandSender actor;
 @BeforeEach void setup() {
  plugin=mock(EnthusiaEventsPlugin.class);player=mock(Player.class);inventory=mock(PlayerInventory.class);world=mock(World.class);
  var config=new YamlConfiguration();config.set("recovery.player-history.enabled",true);when(plugin.getConfig()).thenReturn(config);
  when(plugin.getDataFolder()).thenReturn(root.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
  var server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(plugin.eventManager()).thenReturn(mock(EventManager.class));
  when(world.getName()).thenReturn("world");when(world.getUID()).thenReturn(UUID.randomUUID());when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
  when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(server.getWorld("world")).thenReturn(world);
  UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(player.isOnline()).thenReturn(true);when(player.getName()).thenReturn("Target");
  when(server.getPlayer(id)).thenReturn(player);when(player.getInventory()).thenReturn(inventory);
  when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[41]);when(inventory.getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[4]);
  when(player.getLocation()).thenReturn(new Location(world,1,64,1));when(player.getHealth()).thenReturn(20.0);when(player.getMaxHealth()).thenReturn(20.0);
  when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);when(player.getActivePotionEffects()).thenReturn(List.of());when(player.teleport(any(Location.class))).thenReturn(true);
  captures=mockStatic(PlayerSnapshot.class);
  var snapshot=new PlayerSnapshot(new Location(world,1,64,1),new org.bukkit.inventory.ItemStack[41],new org.bukkit.inventory.ItemStack[4],null,20,20,20,5,0,0,0,0,GameMode.SURVIVAL,List.of(),false,false,0.2F,0.1F);
  captures.when(()->PlayerSnapshot.capture(player)).thenReturn(snapshot);
  actor=mock(CommandSender.class);when(actor.getName()).thenReturn("Staff");when(actor.hasPermission(anyString())).thenReturn(true);
 }
 @AfterEach void close() { captures.close(); }
 @Test void failedCaptureDoesNotAcknowledgeOrLeavePendingEntry() throws Exception {
  Path file=root.resolve("snapshots.yml");Files.createDirectories(file);Files.writeString(file.resolve("keep"),"original");
  var service=new PlayerSnapshotService(plugin);assertFalse(service.capture(player));assertFalse(service.hasSnapshot(player.getUniqueId()));
  verify(inventory,never()).setContents(any());assertEquals("original",Files.readString(file.resolve("keep")));
 }
 @Test void failedPostApplicationSaveBlocksReplayAcrossRestart() throws Exception {
  var service=spy(new PlayerSnapshotService(plugin));doNothing().when(service).restoreMaxHealth(any(),anyDouble());assertTrue(service.capture(player));
  doAnswer(call->{Path file=root.resolve("snapshots.yml");Files.delete(file);Files.createDirectories(file);Files.writeString(file.resolve("keep"),"original");return null;}).when(inventory).setContents(any());
  assertFalse(service.restoreSynchronously(player,false));assertTrue(service.recoveryBlocked(player.getUniqueId()));
  assertFalse(service.restoreSynchronously(player,false));verify(inventory,times(1)).setContents(any());
  assertTrue(new PlayerSnapshotService(plugin).recoveryBlocked(player.getUniqueId()));
 }
 @Test void resolvedHistoryRequiresBoundConfirmationAndAcknowledgesUndoBeforeApplying() throws Exception {
  var service=spy(new PlayerSnapshotService(plugin));doNothing().when(service).restoreMaxHealth(any(),anyDouble());assertTrue(service.capture(player));
  assertTrue(service.restoreSynchronously(player,false));
  UUID archived;
  try(var files=Files.list(root.resolve("recovery/players").resolve(player.getUniqueId().toString()))) {archived=UUID.fromString(files.filter(f->f.toString().endsWith(SNAPSHOT_SUFFIX)).findFirst().orElseThrow().getFileName().toString().replace(SNAPSHOT_SUFFIX,""));}
  var preview=service.previewHistorical(actor,player,archived,"spent items accounted for",false);
  UUID token=UUID.fromString(preview.getLast().substring(preview.getLast().lastIndexOf(' ')+1));
  assertTrue(service.confirmHistorical(actor,token).contains("undo capture="));assertFalse(service.recoveryBlocked(player.getUniqueId()));
  assertThrows(IllegalArgumentException.class,()->service.confirmHistorical(actor,token));verify(inventory,times(2)).setContents(any());
 }
 @Test void changedCurrentStateAndRevokedPermissionRejectConfirmation() throws Exception {
  var service=spy(new PlayerSnapshotService(plugin));doNothing().when(service).restoreMaxHealth(any(),anyDouble());assertTrue(service.capture(player));assertTrue(service.restoreSynchronously(player,false));
  UUID archived;try(var files=Files.list(root.resolve("recovery/players").resolve(player.getUniqueId().toString()))) {archived=UUID.fromString(files.filter(f->f.toString().endsWith(SNAPSHOT_SUFFIX)).findFirst().orElseThrow().getFileName().toString().replace(SNAPSHOT_SUFFIX,""));}
  var preview=service.previewHistorical(actor,player,archived,"reviewed",false);UUID token=UUID.fromString(preview.getLast().substring(preview.getLast().lastIndexOf(' ')+1));
  when(actor.hasPermission("enthusia.events.recovery.apply")).thenReturn(false);assertThrows(IllegalArgumentException.class,()->service.confirmHistorical(actor,token));
  when(actor.hasPermission("enthusia.events.recovery.apply")).thenReturn(true);
  captures.when(()->PlayerSnapshot.capture(player)).thenReturn(new PlayerSnapshot(new Location(world,2,64,1),new org.bukkit.inventory.ItemStack[41],new org.bukkit.inventory.ItemStack[4],null,20,20,20,5,0,0,0,0,GameMode.SURVIVAL,List.of(),false,false,0.2F,0.1F));
  assertThrows(IllegalArgumentException.class,()->service.confirmHistorical(actor,token));verify(inventory,times(1)).setContents(any());
 }
 @Test void malformedHistoricalHealthMustBeRejectedBeforeConfirmationOrApplication() throws Exception {
  var service=new PlayerSnapshotService(plugin);UUID id=UUID.randomUUID();YamlConfiguration payload=new YamlConfiguration();String path="players."+player.getUniqueId();
  payload.set("archive-schema",1);payload.set(path+".capture-id",id.toString());payload.set(path+".captured-at",1L);
  payload.set(path+".location","world,1.0,64.0,1.0,0.0,0.0");payload.set(path+".world-uuid",world.getUID().toString());payload.set(path+".health",Double.NaN);
  payload.set(path+".inventory",Arrays.asList(new org.bukkit.inventory.ItemStack[41]));payload.set(path+".armor",Arrays.asList(new org.bukkit.inventory.ItemStack[4]));
  var archive=new PlayerSnapshotArchive(root.resolve("recovery/players"));archive.capture(player.getUniqueId(),id,1,payload.saveToString());archive.resolve(player.getUniqueId(),id);
  assertThrows(IOException.class,()->service.previewHistorical(actor,player,id,"transfers reconciled",false));verify(inventory,never()).setContents(any());
 }
}
