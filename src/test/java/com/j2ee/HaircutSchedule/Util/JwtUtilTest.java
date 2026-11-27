package com.j2ee.HaircutSchedule.Util;

import com.j2ee.HaircutSchedule.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class JwtUtilTest {
    private static final String JWT_SECRET =
            "test-only-secret-for-haircut-schedule-jwt-signing";

    private static final String EMAIL =
            "customer@example.com";

    private JwtUtil jwtUtil;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();

        signingKey = Keys.hmacShaKeyFor(
                JWT_SECRET.getBytes(StandardCharsets.UTF_8)
        );

        ReflectionTestUtils.setField(
                jwtUtil,
                "jwtSecret",
                JWT_SECRET
        );

        ReflectionTestUtils.setField(
                jwtUtil,
                "jwtAccessExpirationHours",
                1L
        );

        ReflectionTestUtils.setField(
                jwtUtil,
                "jwtRefreshExpirationDays",
                1L
        );
    }

    @Test
    void generateAccessToken_hasAccessClaim() {
        // Arrange
        String email = EMAIL;

        // Execute
        String token = jwtUtil.generateAccessToken(email);
        Claims claims = jwtUtil.getAllClaimsFromToken(token);

        // Assert
        assertThat(token).isNotBlank();

        assertThat(claims.getSubject())
                .isEqualTo(email);

        assertThat(claims.get("token_type", String.class))
                .isEqualTo("ACCESS");

        assertThat(claims.getIssuedAt())
                .isNotNull();

        assertThat(claims.getExpiration())
                .isAfter(claims.getIssuedAt());

        assertThat(jwtUtil.validateToken(token))
                .isTrue();
    }

    @Test
    void generateRefreshToken_hasRefreshClaim() {
        // Arrange
        String email = EMAIL;

        // Execute
        String token = jwtUtil.generateRefreshToken(email);
        Claims claims = jwtUtil.getAllClaimsFromToken(token);

        // Assert
        assertThat(token).isNotBlank();

        assertThat(claims.getSubject())
                .isEqualTo(email);

        assertThat(claims.get("token_type", String.class))
                .isEqualTo("REFRESH");

        assertThat(claims.getIssuedAt())
                .isNotNull();

        assertThat(claims.getExpiration())
                .isAfter(claims.getIssuedAt());

        assertThat(jwtUtil.validateToken(token))
                .isTrue();
    }

    @Test
    void validateToken_validToken_true() {
        // Arrange
        String token = jwtUtil.generateAccessToken(EMAIL);

        // Execute
        boolean result = jwtUtil.validateToken(token);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void validateToken_expiredToken_false() {
        // Arrange
        Instant now = Instant.now();

        String expiredToken = Jwts.builder()
                .subject(EMAIL)
                .issuedAt(
                        Date.from(now.minus(2, ChronoUnit.HOURS))
                )
                .expiration(
                        Date.from(now.minus(1, ChronoUnit.HOURS))
                )
                .signWith(signingKey)
                .compact();

        // Execute
        boolean result = jwtUtil.validateToken(expiredToken);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void validateToken_wrongSignature_false() {
        // Arrange
        SecretKey differentKey = Keys.hmacShaKeyFor(
                "different-test-secret-for-jwt-signature-check"
                        .getBytes(StandardCharsets.UTF_8)
        );

        Instant now = Instant.now();

        String tokenWithWrongSignature = Jwts.builder()
                .subject(EMAIL)
                .issuedAt(Date.from(now))
                .expiration(
                        Date.from(now.plus(1, ChronoUnit.HOURS))
                )
                .signWith(differentKey)
                .compact();

        // Execute
        boolean result = jwtUtil.validateToken(
                tokenWithWrongSignature
        );

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void getEmailFromJwt_returnsSubject() {
        // Arrange
        String token = jwtUtil.generateAccessToken(EMAIL);

        // Execute
        String result = jwtUtil.getEmailFromJWT(token);

        // Assert
        assertThat(result).isEqualTo(EMAIL);
    }

    @Test
    void validateToken_malformedToken_false() {
        // Arrange
        String malformedToken = "this-is-not-a-valid-jwt";

        // Execute
        boolean result = jwtUtil.validateToken(malformedToken);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void getAllClaimsFromToken_validToken_returnsClaims() {
        // Arrange
        String token = jwtUtil.generateRefreshToken(EMAIL);

        // Execute
        Claims result = jwtUtil.getAllClaimsFromToken(token);

        // Assert
        assertThat(result).isNotNull();

        assertThat(result.getSubject())
                .isEqualTo(EMAIL);

        assertThat(result.get("token_type", String.class))
                .isEqualTo("REFRESH");

        assertThat(result.getIssuedAt())
                .isNotNull();

        assertThat(result.getExpiration())
                .isAfter(result.getIssuedAt());
    }
}
