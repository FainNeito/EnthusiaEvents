package org.enthusia.events.domain;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class BuildScoreTest {
 @Test void scorePenalizesExtrasAndSeparatesMissingAndWrongCanonicalStates() {
  var template=Map.of(1,"stone",2,"oak[facing=east]",3,"glass");
  var score=BuildScore.compare(template,Map.of(1,"stone",2,"oak[facing=west]",4,"glass"));
  assertEquals(new BuildScore(3,1,1,1,1),score);assertEquals(0,score.points());
  assertEquals(3,BuildScore.compare(template,template).points());
  assertThrows(IllegalArgumentException.class,()->BuildScore.compare(Map.of(),Map.of()));
 }
 @Test void seasonBoundsAreStartInclusiveEndExclusiveAndIdsCannotEscapePaths() {
  Instant now=Instant.parse("2026-01-01T00:00:00Z");var season=new SeasonDefinition("spring",now,now.plusSeconds(60));
  assertTrue(season.contains(now));assertFalse(season.contains(now.plusSeconds(60)));
  assertThrows(IllegalArgumentException.class,()->new SeasonDefinition("../escape",now,now.plusSeconds(60)));
 }
}
