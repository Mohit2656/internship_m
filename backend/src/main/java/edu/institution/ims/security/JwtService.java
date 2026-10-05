package edu.institution.ims.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationSeconds;
    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-seconds:28800}") long expirationSeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationSeconds = expirationSeconds;
    }
    public String create(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder().subject(principal.id().toString()).claim("role", principal.role()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds))).signWith(key).compact();
    }
    public Long userId(String token) { return Long.valueOf(parse(token).getSubject()); }
    public boolean valid(String token) { try { parse(token); return true; } catch (JwtException | IllegalArgumentException e) { return false; } }
    private Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
