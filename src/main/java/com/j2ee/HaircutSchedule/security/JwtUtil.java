package com.j2ee.HaircutSchedule.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration-hours}")
    public long jwtAccessExpirationHours;

    @Value("${jwt.refresh-expiration-days}")
    public long jwtRefreshExpirationDays;

    private long getAccessExpirationMs() {
        return Duration.ofHours(jwtAccessExpirationHours).toMillis();
    }

    private long getRefreshExpirationMs() {
        return Duration.ofDays(jwtRefreshExpirationDays).toMillis();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + getAccessExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim("token_type", "ACCESS")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String email){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + getRefreshExpirationMs());

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .claim("token_type", "REFRESH")
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getEmailFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try{
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        }catch (io.jsonwebtoken.ExpiredJwtException ex) {
            log.debug("JWT has expired");
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            log.debug("JWT signature verification failed");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT is malformed or invalid: {}", ex.getMessage());
        }
        return false;
    }

    public Claims getAllClaimsFromToken(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
