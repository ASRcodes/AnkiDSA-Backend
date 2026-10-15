package com.ankidsa;

import com.ankidsa.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:reminders;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=INTERVAL}",
 "spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=",
 "jwt.secret=test-only-secret-for-integration-tests-at-least-32-bytes",
 "app.notifications-enabled=false", "app.reminders.trigger-secret=test-only-reminder-secret-at-least-43-characters-long"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
class ReminderIntegrationTest {
 @Autowired JdbcTemplate db;
 @Autowired PlatformTransactionManager manager;
 @Autowired MockMvc mvc;
 NotificationService push;
 MutableClock clock;
 SchedulerService job;
 String token, hash;
 long user;
 static class MutableClock extends Clock {
  final AtomicReference<Instant> time=new AtomicReference<>(Instant.parse("2026-09-24T01:05:00Z"));
  public ZoneId getZone(){return ZoneId.of("Asia/Kolkata");}
  public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
  public Instant instant(){return time.get();}
  void advance(long seconds){time.updateAndGet(t->t.plusSeconds(seconds));}
 }
 @BeforeEach void prepare() {
  // TestDatabaseGuard rejects remote URLs; this cleanup is confined to disposable fixtures.
  db.update("delete from reminder_deliveries"); db.update("delete from reminder_devices");
  db.update("delete from mail_reservations");
  db.update("update mail_budget set blocked_until=? where id=1",Timestamp.from(Instant.EPOCH));
  db.update("update job_leases set owner=null,lease_until=? where name='reminders'",Timestamp.from(Instant.EPOCH));
  clock=new MutableClock(); push=mock(NotificationService.class);
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.SENT);
  job=new SchedulerService(db,manager,push,clock,true,"06:30","12:00",10,3,180);
  String email=UUID.randomUUID()+"@reminder.test";
  db.update("insert into users(email,token_version) values (?,0)",email);
  user=db.queryForObject("select id from users where email=?",Long.class,email);
  token="test-phone-"+UUID.randomUUID(); hash=PasswordRecoveryService.fingerprint(token);
  db.update("insert into reminder_devices(token_hash,user_id,token,session_version,updated_at) values (?,?,?,0,?)",hash,user,token,Timestamp.from(clock.instant()));
  db.update("insert into problems(user_id,leetcode_url,title,difficulty,next_review_date) values (?,?,?,'EASY',?)",user,"https://leetcode.com/problems/two-sum/","Two Sum",LocalDate.now(clock));
 }
 @Test void repeatedTriggersSendOneReminderAndRecordSuccess() {
  assertEquals(1,job.runBatch().sent()); assertEquals(0,job.runBatch().sent());
  verify(push,times(1)).sendReminderNotification(eq(token),anyString());
  assertEquals("SENT",db.queryForObject("select status from reminder_deliveries where user_id=?",String.class,user));
  assertNotNull(db.queryForObject("select sent_at from reminder_deliveries where user_id=?",Timestamp.class,user));
 }
 @Test void noReminderWhenAllQuestionsAreScheduledForLater() {
  db.update("update problems set next_review_date=? where user_id=?",LocalDate.now(clock).plusDays(1),user);
  assertEquals(0,job.runBatch().sent());
  verifyNoInteractions(push);
 }
 @Test void overdueQuestionsCanTriggerOneNewReminderTheFollowingDay() {
  assertEquals(1,job.runBatch().sent());
  clock.advance(86400);
  assertEquals(1,job.runBatch().sent());
  assertEquals(0,job.runBatch().sent());
  verify(push,times(2)).sendReminderNotification(eq(token),anyString());
 }
 @Test void finishingReviewsBeforeRetryStopsThePendingReminder() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.RETRY);
  assertEquals(1,job.runBatch().retryable());
  db.update("update problems set next_review_date=? where user_id=?",LocalDate.now(clock).plusDays(3),user);
  clock.advance(61);
  assertEquals(1,job.runBatch().failed());
  verify(push,times(1)).sendReminderNotification(eq(token),anyString());
 }
 @Test void concurrentTriggerDoesNotSendTwice() throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  when(push.sendReminderNotification(anyString(),anyString())).thenAnswer(a->{entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));return NotificationService.Delivery.SENT;});
  try(var pool=Executors.newFixedThreadPool(2)) {
   var first=pool.submit(job::runBatch); assertTrue(entered.await(5,TimeUnit.SECONDS));
   assertEquals("busy",job.runBatch().status()); release.countDown(); assertEquals(1,first.get(5,TimeUnit.SECONDS).sent());
  }
  verify(push,times(1)).sendReminderNotification(anyString(),anyString());
 }
 @Test void transientFailureIsNotMarkedSentAndRetriesAfterBackoff() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.RETRY,NotificationService.Delivery.SENT);
  assertEquals(1,job.runBatch().retryable()); assertEquals(0,job.runBatch().sent());
  assertNull(db.queryForObject("select sent_at from reminder_deliveries where user_id=?",Timestamp.class,user));
  clock.advance(61); assertEquals(1,job.runBatch().sent());
  verify(push,times(2)).sendReminderNotification(anyString(),anyString());
 }
 @Test void expiredProcessingLeaseRecoversAfterCrash() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.RETRY);
  job.runBatch();
  db.update("update reminder_deliveries set status='PROCESSING',lease_until=? where user_id=?",Timestamp.from(clock.instant().minusSeconds(1)),user);
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.SENT);
  assertEquals(1,job.runBatch().sent());
 }
 @Test void removedOrMovedDeviceCannotReceiveAnOldPendingReminder() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.RETRY);
  job.runBatch(); db.update("delete from reminder_devices where token_hash=?",hash); clock.advance(61);
  assertEquals(1,job.runBatch().failed()); verify(push,times(1)).sendReminderNotification(anyString(),anyString());
 }
 @Test void invalidTokenIsRemovedAndExpiredWorkIsNotSent() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.UNREGISTERED);
  assertEquals(1,job.runBatch().failed());
  assertEquals(0,db.queryForObject("select count(*) from reminder_devices where token_hash=?",Integer.class,hash));
  clock.advance(6*3600); assertEquals("outside_window",job.runBatch().status());
 }
 @Test void jobEndpointRequiresDedicatedSecret() throws Exception {
  mvc.perform(post("/internal/reminders").header("X-Reminder-Secret","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(post("/internal/reminders").header("X-Reminder-Secret","test-only-reminder-secret-at-least-43-characters-long"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("disabled"));
 }
 @Test void rollingMailBudgetIsAtomicAndSurvivesNewServiceInstance() throws Exception {
  MailBudget budget=new MailBudget(db,manager,clock,2);
  try(var pool=Executors.newFixedThreadPool(4)) {
   var results=pool.invokeAll(Collections.nCopies(8,(Callable<Boolean>)budget::reserve));
   int accepted=0;for(var result:results) if(result.get())accepted++;
   assertEquals(2,accepted);
  }
  assertFalse(new MailBudget(db,manager,clock,2).available());
  clock.advance(86401); assertTrue(budget.reserve());
 }
}
