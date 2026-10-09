package org.enthusia.events.infrastructure;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.EnthusiaEventsPlugin;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SeasonProgressTest {
 @TempDir Path root;
 EnthusiaEventsPlugin plugin; YamlConfiguration config; MutableClock clock; UUID id; Player player; World world;
 @BeforeEach void setup() {
  plugin=mock(EnthusiaEventsPlugin.class);config=new YamlConfiguration();clock=new MutableClock();id=UUID.randomUUID();player=mock(Player.class);world=mock(World.class);
  config.set("seasons.enabled",true);config.set("seasons.environment","production");config.set("seasons.namespace","test-proof");config.set("seasons.minimum-active-seconds",2);config.set("seasons.idle-seconds",1);
  config.set("seasons.definitions.first.start","2026-01-01T00:00:00Z");config.set("seasons.definitions.first.end","2026-01-01T00:00:10Z");
  config.set("seasons.cosmetics.champion.title","Champion");config.set("seasons.cosmetics.champion.completed",1);config.set("seasons.cosmetics.champion.wins",1);
  when(plugin.getConfig()).thenReturn(config);when(plugin.getDataFolder()).thenReturn(root.toFile());when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
  var server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.getPlayer(id)).thenReturn(player);when(player.isOnline()).thenReturn(true);
  when(player.getLocation()).thenAnswer(call->new Location(world,clock.millis()/1000.0,64,1));
 }
 EventSession session(boolean privateSession) { var session=new EventSession(new EventDefinition(EventType.PARKOUR,"Test",Material.STICK,"",false,false,false),EventPhase.ACTIVE);session.privateSession(privateSession);session.participants().add(id);session.finalRankings().add(id);return session; }
 void participate(SeasonProgress service,EventSession session) { clock.add(1000);service.sample(session);clock.add(1000);service.sample(session);service.finished(session,id); }
 @Test void completionIdempotencyRestartPersonalBestAndCosmeticOwnership() throws Exception {
  var service=new SeasonProgress(plugin,clock);var session=session(false);service.pin(session,List.of(id));participate(service,session);service.complete(session,Set.of(id));
  assertTrue(String.join(";",service.view(id)).contains("Completed eligible events: 1; wins: 1"));assertTrue(String.join(";",service.view(id)).contains("PARKOUR/unconfigured: 2000ms"));
  service.complete(session,Set.of(id));assertTrue(service.equip(id,"champion").contains("champion"));
  var restarted=new SeasonProgress(plugin,clock);assertTrue(String.join(";",restarted.view(id)).contains("Selected cosmetic: champion"));
  restarted.pin(session,List.of(id));participate(restarted,session);restarted.complete(session,Set.of(id));
  assertTrue(String.join(";",restarted.view(id)).contains("Completed eligible events: 1; wins: 1"));
 }
 @Test void privateTestAfkAndCancelledSessionsCannotUnlock() throws Exception {
  var service=new SeasonProgress(plugin,clock);var privateSession=session(true);service.pin(privateSession,List.of(id));participate(service,privateSession);service.complete(privateSession,Set.of(id));
  var publicSession=session(false);service.pin(publicSession,List.of(id));service.cancel(publicSession.id());participate(service,publicSession);service.complete(publicSession,Set.of(id));
  var idle=session(false);service.pin(idle,List.of(id));when(player.isOnline()).thenReturn(false);participate(service,idle);service.complete(idle,Set.of(id));
  assertThrows(IllegalArgumentException.class,()->service.equip(id,"champion"));assertTrue(String.join(";",service.view(id)).contains("Completed eligible events: 0"));
  config.set("seasons.environment","test");assertTrue(new SeasonProgress(plugin,clock).view(id).getFirst().contains("excluded"));
 }
 @Test void graceRetainsOriginalSeasonAndChangedPolicyOrCorruptLedgerFreezes() throws Exception {
  config.set("seasons.closing-grace-seconds",5);clock.add(8000);var service=new SeasonProgress(plugin,clock);var session=session(false);service.pin(session,List.of(id));participate(service,session);clock.add(1000);service.complete(session,Set.of(id));
  assertEquals(1,Files.list(root.resolve("seasons/test-proof/first")).filter(p->p.toString().endsWith(".credit")).count());
  config.set("seasons.cosmetics.champion.wins",2);assertTrue(new SeasonProgress(plugin,clock).view(id).getFirst().contains("unavailable"));
  config.set("seasons.cosmetics.champion.wins",1);Files.writeString(root.resolve("seasons/test-proof/first").resolve(session.id()+".credit"),"corrupt original");
  assertTrue(new SeasonProgress(plugin,clock).view(id).getFirst().contains("unavailable"));assertEquals("corrupt original",Files.readString(root.resolve("seasons/test-proof/first").resolve(session.id()+".credit")));
 }
 @Test void stationaryOnlinePlayerCannotReceiveParticipationDuringIdleGrace() throws Exception {
  config.set("seasons.idle-seconds",60);
  when(player.getLocation()).thenReturn(new Location(world,1,64,1));
  var service=new SeasonProgress(plugin,clock);var idle=session(false);service.pin(idle,List.of(id));
  participate(service,idle);service.complete(idle,Set.of(id));
  assertTrue(String.join(";",service.view(id)).contains("Completed eligible events: 0"));
  assertThrows(IllegalArgumentException.class,()->service.equip(id,"champion"));
 }
 static final class MutableClock extends Clock {
  long now=Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();void add(long value){now+=value;}
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(now);}
 }
}
