package com.ankidsa;

import com.ankidsa.service.*;
import com.google.firebase.messaging.*;
import com.google.api.core.ApiFutures;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

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
