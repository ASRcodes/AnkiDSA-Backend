package com.ankidsa;
import com.ankidsa.service.SrsService;
import com.ankidsa.entity.ProblemStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SrsTest {
 @Test void successfulGoodReviewsReachMastery() {
  var s=SrsService.calculate(2.5,0,0,4);
  assertEquals(1,s.interval());
  s=SrsService.calculate(s.ef(),s.interval(),s.repetitions(),4); assertEquals(6,s.interval());
  s=SrsService.calculate(s.ef(),s.interval(),s.repetitions(),4); assertEquals(15,s.interval());
  s=SrsService.calculate(s.ef(),s.interval(),s.repetitions(),4); assertEquals(38,s.interval());
  assertEquals(ProblemStatus.MASTERED,s.status());
 }
 @Test void forgettingResetsAndEasinessHasAFloor() {
  var s=SrsService.calculate(1.3,120,6,1);
  assertEquals(1.3,s.ef()); assertEquals(1,s.interval()); assertEquals(0,s.repetitions());
  assertEquals(ProblemStatus.LEARNING,s.status());
 }
 @Test void intervalsStayBounded() {
  assertEquals(3650,SrsService.calculate(5,3650,20,5).interval());
  assertThrows(IllegalArgumentException.class,() -> SrsService.calculate(2.5,0,0,6));
 }
}
