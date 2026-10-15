package com.ankidsa;
import com.ankidsa.config.JwtService;
import com.ankidsa.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtSecurityTest {
    private static final String SECRET = "test-only-jwt-security-key-at-least-48-bytes-long-for-fixtures";
    private final Instant now = Instant.parse("2026-09-24T01:00:00Z");
    private JwtService service(Instant instant) { return new JwtService(SECRET, 60000, Clock.fixed(instant, ZoneOffset.UTC)); }
    @Test void issuedSessionsExpireAndHaveDistinctIdentifiers() {
        var user = User.builder().id(42L).email("fixture@example.test").build();
        var jwt = service(now);
        String token = jwt.generateToken(user);
        assertEquals(42, jwt.read(token).userId());
        assertNotEquals(jwt.read(token).sessionId(), jwt.read(jwt.generateToken(user)).sessionId());
        assertNull(service(now.plusSeconds(61)).read(token));
        assertNull(jwt.read(token.substring(0, token.length()-8) + "tampered"));
    }
    @Test void signatureAloneDoesNotPermitMissingExpiryOrWrongIssuer() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String missing = Jwts.builder().issuer("ankidsa").subject("42").id(UUID.randomUUID().toString()).claim("ver",0).issuedAt(Date.from(now)).signWith(key).compact();
        assertNull(service(now).read(missing));
        String wrong = Jwts.builder().issuer("another-app").subject("42").id(UUID.randomUUID().toString()).claim("ver",0).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();
        assertNull(service(now).read(wrong));
    }
}
