package com.ankidsa;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=INTERVAL}",
 "spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=",
 "jwt.secret=test-only-secret-for-integration-tests-at-least-32-bytes",
 "app.notifications-enabled=false"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired com.ankidsa.repository.ReminderDeviceRepository devices;

 void registerDevice(String session, String token) throws Exception {
  mvc.perform(put("/api/users/me/device").header("Authorization", "Bearer " + session)
    .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
    .andExpect(status().isNoContent());
 }

 @Test void devicesHaveOneOwnerAndLogoutCannotRemoveAnotherAccountsRegistration() throws Exception {
  String first = session(), second = session();
  String phone = "phone-" + UUID.randomUUID(), tablet = "tablet-" + UUID.randomUUID();
  registerDevice(first, phone);
  registerDevice(first, tablet);
  long firstId = devices.findById(com.ankidsa.service.PasswordRecoveryService.fingerprint(phone)).orElseThrow().getUser().getId();
  registerDevice(second, phone);
  var moved = devices.findById(com.ankidsa.service.PasswordRecoveryService.fingerprint(phone)).orElseThrow();
  assertNotEquals(firstId, moved.getUser().getId());
  mvc.perform(delete("/api/users/me/device").header("Authorization", "Bearer " + first)
    .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + phone + "\"}"))
    .andExpect(status().isNoContent());
  assertTrue(devices.existsById(moved.getTokenHash()));
  mvc.perform(delete("/api/users/me/device").header("Authorization", "Bearer " + first)
    .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tablet + "\"}"))
    .andExpect(status().isNoContent());
  assertFalse(devices.existsById(com.ankidsa.service.PasswordRecoveryService.fingerprint(tablet)));
  mvc.perform(put("/api/users/me/device").header("Authorization", "Bearer " + first)
    .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\" \"}"))
    .andExpect(status().isBadRequest());
  mvc.perform(put("/api/users/me/device").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"phone\"}"))
    .andExpect(status().isUnauthorized());
 }

 @Test void simultaneousDeviceRegistrationRetainsOneOwner() throws Exception {
  String first = session(), second = session(), phone = "simultaneous-" + UUID.randomUUID();
  var start = new CountDownLatch(1);
  try (var executor = Executors.newFixedThreadPool(2)) {
   var a = executor.submit(() -> { start.await(); registerDevice(first, phone); return true; });
   var b = executor.submit(() -> { start.await(); registerDevice(second, phone); return true; });
   start.countDown();
   assertTrue(a.get(10, TimeUnit.SECONDS));
   assertTrue(b.get(10, TimeUnit.SECONDS));
  }
  assertTrue(devices.existsById(com.ankidsa.service.PasswordRecoveryService.fingerprint(phone)));
 }
 String session() throws Exception {
  String email=UUID.randomUUID()+"@example.com";
  String body=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
    .content("{\"email\":\""+email+"\",\"password\":\"LongPassword!123\",\"name\":\"Test\"}"))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return JsonPath.read(body,"$.token");
 }
 String add(String token,String slug) throws Exception {
  return mvc.perform(post("/api/problems").header("Authorization","Bearer "+token)
    .contentType(MediaType.APPLICATION_JSON).content(problem(slug)))
    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
 }
 String problem(String slug) { return "{\"leetcodeUrl\":\"https://leetcode.com/problems/"+slug+"/description/?env=demo\",\"title\":\"Two Sum\",\"difficulty\":\"EASY\",\"tags\":\"array, hash-map\",\"notes\":\"Store complements\"}"; }
 String review(int id,String key,long version,int quality) { return "{\"problemId\":"+id+",\"qualityRating\":"+quality+",\"version\":"+version+",\"requestId\":\""+key+"\"}"; }

 @Test void authenticationAndUserPrivacy() throws Exception {
  mvc.perform(get("/api/problems")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/users/1")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
    .content("{\"email\":\"nobody@example.com\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
    .content("{\"email\":\"nobody@example.com\",\"password\":\"WrongPassword123\"}")).andExpect(status().isUnauthorized());
  String token=session();
  mvc.perform(get("/api/auth/verify").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
  mvc.perform(get("/api/users/1").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
  mvc.perform(post("/api/demo/reset").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
  mvc.perform(get("/api/problems").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
 }
 @Test void ownersOnlyAndCanonicalDuplicates() throws Exception {
  String owner=session(),stranger=session();
  int id=JsonPath.read(add(owner,"two-sum"),"$.id");
  mvc.perform(get("/api/problems/"+id).header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
  mvc.perform(post("/api/problems/review").header("Authorization","Bearer "+stranger).contentType(MediaType.APPLICATION_JSON)
    .content(review(id,UUID.randomUUID().toString(),0,4))).andExpect(status().isNotFound());
  mvc.perform(delete("/api/problems/"+id).header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
  mvc.perform(post("/api/problems").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON)
    .content(problem("two-sum").replace("/description/?env=demo","/"))).andExpect(status().isConflict());
  mvc.perform(get("/api/problems/"+id).header("Authorization","Bearer "+owner)).andExpect(jsonPath("$.tags[1]").value("hash-map"));
 }
 @Test void longUnicodePasswordIsRejectedWithoutServerError() throws Exception {
  String password="\u00e9".repeat(40);
  for(String endpoint:List.of("register","login")) {
   mvc.perform(post("/api/auth/"+endpoint).contentType(MediaType.APPLICATION_JSON)
    .content("{\"email\":\"unicode@example.com\",\"password\":\""+password+"\"}"))
    .andExpect(status().isBadRequest());
  }
 }
 @Test void reviewIsIdempotentAndRetentionMeansSuccessfulRecall() throws Exception {
  String token=session(),key=UUID.randomUUID().toString();
  int id=JsonPath.read(add(token,"two-sum"),"$.id");
  for(int i=0;i<2;i++)
   mvc.perform(post("/api/problems/review").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
    .content(review(id,key,0,4))).andExpect(status().isOk()).andExpect(jsonPath("$.interval").value(1));
  mvc.perform(get("/api/stats").header("Authorization","Bearer "+token))
    .andExpect(jsonPath("$.totalReviews").value(1)).andExpect(jsonPath("$.retentionRate").value(100.0)).andExpect(jsonPath("$.dueToday").value(0));
  mvc.perform(post("/api/problems/review").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
    .content(review(id,UUID.randomUUID().toString(),0,4))).andExpect(status().isConflict());
  mvc.perform(post("/api/problems/review").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
    .content(review(id,UUID.randomUUID().toString(),1,4))).andExpect(status().isConflict());
 }
 @Test void concurrentReviewsAdvanceOnlyOnce() throws Exception {
  String token=session(); int id=JsonPath.read(add(token,"two-sum"),"$.id");
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Integer> submit=() -> mvc.perform(post("/api/problems/review").header("Authorization","Bearer "+token)
      .contentType(MediaType.APPLICATION_JSON).content(review(id,UUID.randomUUID().toString(),0,4))).andReturn().getResponse().getStatus();
   var results=pool.invokeAll(List.of(submit,submit));
   var codes=new ArrayList<Integer>(); for(var r:results) codes.add(r.get()); Collections.sort(codes);
   assertEquals(List.of(200,409),codes);
  }
 }
 @Test void editsPreserveScheduleAndDeleteRemovesHistory() throws Exception {
  String token=session(); int id=JsonPath.read(add(token,"two-sum"),"$.id");
  mvc.perform(put("/api/problems/"+id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
    .content(problem("two-sum").replace("\"notes\":", "\"version\":0,\"notes\":")))
    .andExpect(status().isOk()).andExpect(jsonPath("$.interval").value(0));
  mvc.perform(delete("/api/problems/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
  mvc.perform(get("/api/problems/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
 }
 @Test void rejectsMalformedRequestsAndUntrustedCorsOrigin() throws Exception {
  mvc.perform(options("/api/problems").header("Origin","chrome-extension://bafnmcdkpidnpollodebmifggmmpkbje").header("Access-Control-Request-Method","POST"))
    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","chrome-extension://bafnmcdkpidnpollodebmifggmmpkbje"));
  String token=session();
  mvc.perform(post("/api/problems").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{oops")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/problems").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
   .content(problem("two-sum").replace("leetcode.com","evil.example"))).andExpect(status().isBadRequest());
  mvc.perform(options("/api/problems").header("Origin","https://evil.example").header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
 }
}
