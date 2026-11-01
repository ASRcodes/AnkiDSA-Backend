package com.ankidsa;

import com.ankidsa.service.*;
import com.google.firebase.messaging.*;
import com.google.api.core.ApiFutures;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.net.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class DeliveryAdaptersTest {
 @Test void fcmUsesAdminSdkAndClassifiesPermanentVersusTransientFailures() {
  FirebaseMessaging messaging=mock(FirebaseMessaging.class);
  @SuppressWarnings("unchecked") ObjectProvider<FirebaseMessaging> provider=mock(ObjectProvider.class);
  when(provider.getIfAvailable()).thenReturn(messaging);
  when(messaging.sendAsync(any(Message.class))).thenReturn(ApiFutures.immediateFuture("test-message-id"));
  assertEquals(NotificationService.Delivery.SENT,new FcmNotificationService(provider).sendToToken("test-token","test-delivery"));
  verify(messaging).sendAsync(any(Message.class));
  assertEquals(NotificationService.Delivery.UNREGISTERED,FcmNotificationService.classify(MessagingErrorCode.UNREGISTERED));
  assertEquals(NotificationService.Delivery.RETRY,FcmNotificationService.classify(MessagingErrorCode.QUOTA_EXCEEDED));
  assertEquals(NotificationService.Delivery.FAILED,FcmNotificationService.classify(MessagingErrorCode.SENDER_ID_MISMATCH));
 }
 @Test void fcmDiagnosticsContainOnlyFixedReasonsAndSdkCodes(CapturedOutput output) {
  FirebaseMessaging messaging=mock(FirebaseMessaging.class);
  @SuppressWarnings("unchecked") ObjectProvider<FirebaseMessaging> provider=mock(ObjectProvider.class);
  when(provider.getIfAvailable()).thenReturn(messaging);
  FirebaseMessagingException rejected=mock(FirebaseMessagingException.class);
  when(rejected.getMessagingErrorCode()).thenReturn(MessagingErrorCode.SENDER_ID_MISMATCH);
  when(rejected.getErrorCode()).thenReturn(com.google.firebase.ErrorCode.PERMISSION_DENIED);
  when(rejected.getMessage()).thenReturn("private-provider-payload");
  when(messaging.sendAsync(any(Message.class))).thenReturn(ApiFutures.immediateFailedFuture(rejected));
  var adapter=new FcmNotificationService(provider);
  assertEquals(NotificationService.Delivery.FAILED,adapter.sendToToken("private-device-token","private-delivery-id"));
  assertTrue(output.getAll().contains("reason=SENDER_ID_MISMATCH"));
  assertTrue(output.getAll().contains("providerCode=PERMISSION_DENIED"));
  when(messaging.sendAsync(any(Message.class))).thenReturn(ApiFutures.immediateFailedFuture(new IllegalStateException("private-sdk-error")));
  assertEquals(NotificationService.Delivery.RETRY,adapter.sendToToken("private-device-token","private-delivery-id"));
  assertTrue(output.getAll().contains("reason=SDK_FAILURE"));
  for (String value : List.of("private-provider-payload","private-device-token","private-delivery-id","private-sdk-error"))
   assertFalse(output.getAll().contains(value));
 }
 @Test void brevoPostsOverHttpFixtureAndRetriesExplicitTemporaryFailure() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  List<String> bodies=Collections.synchronizedList(new ArrayList<>());
  AtomicInteger calls=new AtomicInteger();
  server.createContext("/email",exchange->{
   bodies.add(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   int code=calls.incrementAndGet()==1?503:201;
   exchange.sendResponseHeaders(code,-1);exchange.close();
  }); server.start();
  try {
   MailBudget budget=mock(MailBudget.class); when(budget.reserve()).thenReturn(true);
   var mail=new BrevoAccountMailSender("test-key","sender@example.test",budget,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
       URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/email"));
   mail.send("recipient@example.test","Recovery","Test recovery content");
   assertEquals(2,calls.get()); assertEquals(bodies.get(0),bodies.get(1));
   assertTrue(bodies.getFirst().contains("textContent")); assertTrue(bodies.getFirst().contains("idempotencyKey"));
  } finally {server.stop(0);}
 }
 @Test void exhaustedBudgetDoesNotContactProvider() {
  MailBudget budget=mock(MailBudget.class);
  var mail=new BrevoAccountMailSender("test-key","sender@example.test",budget);
  assertThrows(BrevoAccountMailSender.MailUnavailableException.class,()->mail.send("user@example.test","Reset","test"));
 }
}
