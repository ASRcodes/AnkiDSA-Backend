import java.net.URI;
import java.net.http.*;
import java.time.Duration;
public class Healthcheck {
  public static void main(String[] args) {
    try {
      var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + System.getenv().getOrDefault("PORT", "8081") + "/health")).timeout(Duration.ofSeconds(5)).GET().build();
      System.exit(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200 ? 0 : 1);
    } catch (Exception e) { System.exit(1); }
  }
}
