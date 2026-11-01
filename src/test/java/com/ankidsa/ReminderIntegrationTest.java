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
 @Autowired com.ankidsa.config.JwtService jwt;
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
  db.update("delete from job_leases where name='reminders-last-success'");
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
  var cancelled=job.runBatch();
  assertEquals(1,cancelled.skipped()); assertEquals(0,cancelled.failed()); assertEquals(0,cancelled.remaining());
  assertEquals("INELIGIBLE",db.queryForObject("select failure_code from reminder_deliveries where user_id=?",String.class,user));
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
  var removed=job.runBatch();
  assertEquals(1,removed.skipped()); assertEquals(0,removed.failed()); assertEquals(0,removed.remaining());
  verify(push,times(1)).sendReminderNotification(anyString(),anyString());
 }
 @Test void invalidTokenIsRemovedAndExpiredWorkIsNotSent() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.UNREGISTERED);
  var expired=job.runBatch();
  assertEquals(1,expired.skipped()); assertEquals(0,expired.failed()); assertEquals(0,expired.remaining());
  assertEquals("UNREGISTERED",db.queryForObject("select failure_code from reminder_deliveries where user_id=?",String.class,user));
  assertEquals(0,job.runBatch().skipped());
  assertEquals(0,db.queryForObject("select count(*) from reminder_devices where token_hash=?",Integer.class,hash));
  clock.advance(6*3600); assertEquals("outside_window",job.runBatch().status());
 }
 @Test void expiredDeviceDoesNotPreventOtherDevicesFromReceivingTheirReminder() {
  String validToken="valid-phone-"+UUID.randomUUID();
  String validHash=PasswordRecoveryService.fingerprint(validToken);
  db.update("insert into reminder_devices(token_hash,user_id,token,session_version,updated_at) values (?,?,?,0,?)",validHash,user,validToken,Timestamp.from(clock.instant()));
  when(push.sendReminderNotification(eq(token),anyString())).thenReturn(NotificationService.Delivery.UNREGISTERED);
  var result=job.runBatch();
  assertEquals(1,result.skipped()); assertEquals(1,result.sent()); assertEquals(0,result.failed()); assertEquals(0,result.remaining());
  assertEquals(1,db.queryForObject("select count(*) from reminder_devices where user_id=?",Integer.class,user));
  var repeated=job.runBatch();
  assertEquals(0,repeated.sent()); assertEquals(0,repeated.skipped());
  verify(push,times(1)).sendReminderNotification(eq(validToken),anyString());
 }
 @Test void rejectedDeviceDoesNotPreventOtherDevicesFromReceivingTheirReminder() {
  String validToken="valid-phone-"+UUID.randomUUID();
  String validHash=PasswordRecoveryService.fingerprint(validToken);
  db.update("insert into reminder_devices(token_hash,user_id,token,session_version,updated_at) values (?,?,?,0,?)",validHash,user,validToken,Timestamp.from(clock.instant()));
  when(push.sendReminderNotification(eq(token),anyString())).thenReturn(NotificationService.Delivery.FAILED);
  var result=job.runBatch();
  assertEquals(1,result.failed()); assertEquals(1,result.sent()); assertEquals(0,result.skipped()); assertEquals(0,result.remaining());
  assertEquals("PROVIDER_REJECTED",db.queryForObject("select failure_code from reminder_deliveries where token_hash=?",String.class,hash));
  assertEquals(0,job.runBatch().sent());
  verify(push,times(1)).sendReminderNotification(eq(validToken),anyString());
 }
 @Test void jobEndpointRequiresDedicatedSecret() throws Exception {
  mvc.perform(post("/internal/reminders").header("X-Reminder-Secret","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(post("/internal/reminders").header("X-Reminder-Secret","test-only-reminder-secret-at-least-43-characters-long"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("disabled"));
 }
 @Test void auditRequiresDedicatedSecretEvenForAnAuthenticatedAccount() throws Exception {
  mvc.perform(get("/internal/reminders")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/reminders").header("X-Reminder-Secret","wrong")).andExpect(status().isUnauthorized());
  String userJwt=jwt.generateToken(com.ankidsa.entity.User.builder().id(user).tokenVersion(0L).build());
  mvc.perform(get("/internal/reminders").header("Authorization","Bearer "+userJwt)).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/reminders").header("X-Reminder-Secret","test-only-reminder-secret-at-least-43-characters-long"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("disabled"))
   .andExpect(jsonPath("$.day").exists()).andExpect(jsonPath("$.ranToday").isBoolean())
   .andExpect(jsonPath("$.pending").value(0)).andExpect(jsonPath("$.lastBatchRemaining").value(0))
   .andExpect(jsonPath("$.token").doesNotExist());
  assertEquals(0,db.queryForObject("select count(*) from reminder_deliveries",Integer.class));
  verifyNoInteractions(push);
 }
 @Test void auditDetectsMissingMorningCycleWithoutSendingOrStaging() {
  clock.advance(6*3600);
  var audit=job.audit();
  assertTrue(audit.windowClosed()); assertFalse(audit.ranToday());
  assertEquals("audit",audit.status()); assertEquals(LocalDate.now(clock),audit.day());
  assertEquals(0,audit.sent()); assertEquals(0,audit.failed()); assertEquals(0,audit.pending());
  assertEquals(0,db.queryForObject("select count(*) from reminder_deliveries",Integer.class));
  verifyNoInteractions(push);
 }
 @Test void auditAcceptsACompletedEmptyMorningCycleAndDoesNotMistakeYesterdayForToday() {
  db.update("update problems set next_review_date=? where user_id=?",LocalDate.now(clock).plusDays(4),user);
  job.runBatch(); clock.advance(6*3600);
  var healthy=job.audit();
  assertTrue(healthy.ranToday()); assertTrue(healthy.windowClosed()); assertEquals(0,healthy.lastBatchRemaining());
  assertEquals("remaining:0",db.queryForObject("select owner from job_leases where name='reminders-last-success'",String.class));
  assertEquals(0,healthy.sent()); assertEquals(0,healthy.failed()); assertEquals(0,healthy.pending());
  clock.advance(86400);
  assertFalse(job.audit().ranToday());
  verifyNoInteractions(push);
 }
 @Test void auditSeparatesProviderFailureFromExpectedDeviceExpiry() {
  String expiredToken="expired-phone-"+UUID.randomUUID();
  db.update("insert into reminder_devices(token_hash,user_id,token,session_version,updated_at) values (?,?,?,0,?)",
   PasswordRecoveryService.fingerprint(expiredToken),user,expiredToken,Timestamp.from(clock.instant()));
  when(push.sendReminderNotification(eq(token),anyString())).thenReturn(NotificationService.Delivery.FAILED);
  when(push.sendReminderNotification(eq(expiredToken),anyString())).thenReturn(NotificationService.Delivery.UNREGISTERED);
  job.runBatch(); clock.advance(6*3600);
  var audit=job.audit();
  assertTrue(audit.ranToday()); assertEquals(1,audit.failed()); assertEquals(1,audit.skipped()); assertEquals(0,audit.pending());
  verify(push,times(2)).sendReminderNotification(anyString(),anyString());
 }
 @Test void auditCountsPendingWorkOnlyWhileTheDeviceAndReviewsRemainEligible() {
  when(push.sendReminderNotification(anyString(),anyString())).thenReturn(NotificationService.Delivery.RETRY);
  job.runBatch(); clock.advance(6*3600);
  assertEquals(1,job.audit().pending());
  db.update("update problems set next_review_date=? where user_id=?",LocalDate.now(clock).plusDays(3),user);
  assertEquals(0,job.audit().pending());
  db.update("update problems set next_review_date=? where user_id=?",LocalDate.now(clock),user);
  db.update("delete from reminder_devices where token_hash=?",hash);
  assertEquals(0,job.audit().pending());
  verify(push,times(1)).sendReminderNotification(anyString(),anyString());
 }
 @Test void auditDoesNotCountADispatchLeaseOrAfternoonOrFutureSuccessMarker() {
  clock.advance(6*3600);
  db.update("update job_leases set owner=null,lease_until=? where name='reminders'",Timestamp.from(clock.instant().minusSeconds(3600)));
  assertFalse(job.audit().ranToday());
  db.update("insert into job_leases(name,lease_until,owner) values ('reminders-last-success',?,'remaining:0')",Timestamp.from(clock.instant()));
  assertFalse(job.audit().ranToday());
  clock.advance(-6*3600);
  db.update("update job_leases set lease_until=? where name='reminders-last-success'",Timestamp.from(clock.instant().plusSeconds(60)));
  assertFalse(job.audit().ranToday());
 }
 @Test void auditPreservesUnstagedBacklogAndTheNextCompletedBatchClearsIt() {
  String lateToken="late-phone-"+UUID.randomUUID();
  String lateHash=PasswordRecoveryService.fingerprint(lateToken);
  when(push.sendReminderNotification(eq(token),anyString())).thenAnswer(invocation -> {
   db.update("insert into reminder_devices(token_hash,user_id,token,session_version,updated_at) values (?,?,?,0,?)",
    lateHash,user,lateToken,Timestamp.from(clock.instant()));
   return NotificationService.Delivery.SENT;
  });
  var initial=job.runBatch();
  assertEquals(1,initial.sent()); assertEquals(1,initial.remaining());
  var incomplete=job.audit();
  assertTrue(incomplete.ranToday()); assertEquals(0,incomplete.pending()); assertEquals(1,incomplete.lastBatchRemaining());
  assertEquals("remaining:1",db.queryForObject("select owner from job_leases where name='reminders-last-success'",String.class));
  var completed=job.runBatch();
  assertEquals(1,completed.sent()); assertEquals(0,completed.remaining());
  clock.advance(6*3600);
  var healthy=job.audit();
  assertTrue(healthy.ranToday()); assertEquals(0,healthy.pending()); assertEquals(0,healthy.lastBatchRemaining());
  assertEquals("remaining:0",db.queryForObject("select owner from job_leases where name='reminders-last-success'",String.class));
 }
 @Test void afternoonRegistrationsDoNotCreateHistoricalMorningBacklog() {
  job.runBatch(); clock.advance(6*3600);
  String afternoonToken="afternoon-phone-"+UUID.randomUUID();
  db.update("insert into reminder_devices(token_hash,user_id,token,session_version,updated_at) values (?,?,?,0,?)",
   PasswordRecoveryService.fingerprint(afternoonToken),user,afternoonToken,Timestamp.from(clock.instant()));
  var audit=job.audit();
  assertTrue(audit.ranToday()); assertEquals(0,audit.pending()); assertEquals(0,audit.lastBatchRemaining());
  verify(push,times(1)).sendReminderNotification(anyString(),anyString());
 }
 @Test void malformedSuccessMarkersCannotReportAHealthyMorning() {
  db.update("insert into job_leases(name,lease_until) values ('reminders-last-success',?)",Timestamp.from(clock.instant()));
  assertFalse(job.audit().ranToday());
  for(String marker : List.of("", "remaining:-1", "remaining:01", "remaining:2147483648", "remaining:1 extra", "unrelated")) {
   db.update("update job_leases set owner=? where name='reminders-last-success'",marker);
   var audit=job.audit();
   assertFalse(audit.ranToday()); assertEquals(0,audit.lastBatchRemaining());
  }
 }
 @Test void failedBatchReleasesItsLeaseWithoutRecordingSuccessfulCycle() {
  JdbcTemplate failingDb=spy(db);
  doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic database failure"))
   .when(failingDb).update(eq("update reminder_deliveries set status='FAILED',failure_code='EXPIRED' where study_date<? and status in ('PENDING','PROCESSING','RETRYABLE')"),eq(LocalDate.now(clock)));
  var failingJob=new SchedulerService(failingDb,manager,push,clock,true,"06:30","12:00",10,3,180);
  assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,failingJob::runBatch);
  assertNull(db.queryForObject("select owner from job_leases where name='reminders'",String.class));
  assertEquals(0,db.queryForObject("select count(*) from job_leases where name='reminders-last-success'",Integer.class));
  clock.advance(6*3600); assertFalse(job.audit().ranToday());
  verifyNoInteractions(push);
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
