package org.enthusia.events.infrastructure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RecoveryPersistenceTest {
 @TempDir Path root;
 @Test void unfinishedLeaseSurvivesRestartAndRejectsConcurrentReplay() throws Exception {
  UUID player=UUID.randomUUID(), capture=UUID.randomUUID();
  var leases=new RecoveryLeaseStore(root);
  UUID operation=leases.begin(player,capture,capture,"staff","duplicate items reconciled","hash");
  var restarted=new RecoveryLeaseStore(root);
  assertTrue(restarted.blocked(player));
  assertThrows(IOException.class,()->restarted.begin(player,capture,capture,"staff","retry","hash"));
  assertThrows(IOException.class,()->restarted.finish(player,UUID.randomUUID(),"APPLIED","staff","done"));
  restarted.finish(player,operation,"RECONCILED","staff","current inventory accepted");
  assertFalse(new RecoveryLeaseStore(root).blocked(player));
  assertTrue(Files.exists(root.resolve("audit").resolve(operation+".reconciled")));
 }
 @Test void corruptLeaseAndFailedAcknowledgementNeverUnlock() throws Exception {
  UUID player=UUID.randomUUID(), capture=UUID.randomUUID(); var leases=new RecoveryLeaseStore(root);
  UUID operation=leases.begin(player,capture,capture,"staff","reviewed","hash");
  Path result=root.resolve("audit").resolve(operation+".applied"); Files.createDirectories(result); Files.writeString(result.resolve("occupied"),"retained");
  assertThrows(IOException.class,()->leases.finish(player,operation,"APPLIED","staff","done"));
  assertTrue(new RecoveryLeaseStore(root).blocked(player));
  Files.writeString(root.resolve(player+".lease"),"corrupt original");
  assertTrue(leases.blocked(player)); assertEquals("corrupt original",Files.readString(root.resolve(player+".lease")));
 }
 @Test void retentionDeletesOnlyVerifiedResolvedUnprotectedHistory() throws Exception {
  var archive=new PlayerSnapshotArchive(root); UUID player=UUID.randomUUID();
  UUID pending=UUID.randomUUID(),old=UUID.randomUUID(),keep=UUID.randomUUID(),protectedId=UUID.randomUUID(),corrupt=UUID.randomUUID();
  archive.capture(player,pending,1,"pending"); archive.capture(player,old,2,"old"); archive.resolve(player,old);
  archive.capture(player,keep,4,"keep");archive.resolve(player,keep);
  archive.capture(player,protectedId,3,"protected");archive.resolve(player,protectedId);
  archive.capture(player,corrupt,5,"bad");archive.resolve(player,corrupt);
  Files.writeString(root.resolve(player.toString()).resolve(corrupt+".snapshot"),"corrupt");
  archive.prune(player,1,0,id->id.equals(protectedId));
  assertFalse(Files.exists(root.resolve(player.toString()).resolve(old+".snapshot")));
  assertEquals("keep",archive.read(player,keep).payload());assertFalse(archive.read(player,pending).resolved());
  assertEquals("protected",archive.read(player,protectedId).payload());
  assertTrue(Files.exists(root.resolve(player.toString()).resolve(corrupt+".snapshot")));
  assertThrows(IOException.class,()->archive.capture(player,keep,6,"overwrite"));
 }
 @Test void archiveChecksumDetectsPayloadTamperingAndFailedReplacePreservesOriginal() throws Exception {
  var archive=new PlayerSnapshotArchive(root);UUID player=UUID.randomUUID(),id=UUID.randomUUID();archive.capture(player,id,1,"original");
  Path file=root.resolve(player.toString()).resolve(id+".snapshot");Files.writeString(file,Files.readString(file).replace("original","changed"));
  assertThrows(IOException.class,()->archive.read(player,id));
  Path directory=root.resolve("occupied");Files.createDirectories(directory);Files.writeString(directory.resolve("keep"),"original");
  assertThrows(IOException.class,()->AtomicFiles.replace(directory,new byte[]{1}));assertEquals("original",Files.readString(directory.resolve("keep")));
 }
}
