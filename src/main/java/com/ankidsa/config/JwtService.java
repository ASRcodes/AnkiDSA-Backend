package com.ankidsa.config;

import com.ankidsa.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    public record Identity(long userId, long tokenVersion) {}

    public String generateToken(User user) {
        var now = clock.instant();
        return Jwts.builder().subject(user.getId().toString())
            .claim("ver", user.getTokenVersion())
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(signingKey).compact();
    }

    public Identity read(String token) {
        try {
            var claims = Jwts.parser().verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant())).build()
                .parseSignedClaims(token).getPayload();
            long id = Long.parseLong(claims.getSubject());
            Number version = claims.get("ver", Number.class);
            return id > 0 && version != null && version.longValue() >= 0
                ? new Identity(id, version.longValue()) : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
