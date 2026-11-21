package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.request.PendingRegistration;
import com.j2ee.HaircutSchedule.dto.request.PasswordResetPayload;
import com.j2ee.HaircutSchedule.exception.InvalidOrExpiredTokenException;
import com.j2ee.HaircutSchedule.security.TokenHasher;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RedisActionTokenService {

    private static final String PREFIX = "auth:action:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenHasher tokenHasher;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.registration-token-ttl:PT24H}")
    private Duration registrationTokenTtl;

    @Value("${app.auth.password-reset-token-ttl:PT15M}")
    private Duration passwordResetTokenTtl;

    // REGISTRATION TOKEN
    public String issueRegistrationToken(
            PendingRegistration payload
    ) {
        return issue(
                "registration",
                payload,
                registrationTokenTtl
        );
    }

    public PendingRegistration consumeRegistrationToken(
            String token
    ) {
        return consume(
                "registration",
                token,
                PendingRegistration.class
        );
    }

    public void deleteRegistrationToken(String token) {
        delete("registration", token);
    }

    // PASSWORD RESET TOKEN
    public String issuePasswordResetToken(
            PasswordResetPayload payload
    ) {
        return issue(
                "password-reset",
                payload,
                passwordResetTokenTtl
        );
    }

    public PasswordResetPayload consumePasswordResetToken(
            String token
    ) {
        return consume(
                "password-reset",
                token,
                PasswordResetPayload.class
        );
    }

    public void deletePasswordResetToken(String token) {
        delete("password-reset", token);
    }

    private String issue(
            String purpose,
            Object payload,
            Duration ttl
    ) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "Token TTL must be greater than zero"
            );
        }

        String token = generateToken();

        try {
            String json = objectMapper.writeValueAsString(payload);

            redisTemplate.opsForValue().set(
                    buildKey(purpose, token),
                    json,
                    ttl
            );

            return token;

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not store action token",
                    ex
            );
        }
    }

    private <T> T consume(
            String purpose,
            String token,
            Class<T> type
    ) {
        if (token == null || token.isBlank()) {
            throw new InvalidOrExpiredTokenException(
                    "Token is required"
            );
        }

        String payload = redisTemplate.opsForValue()
                .getAndDelete(buildKey(purpose, token));

        if (payload == null) {
            throw new InvalidOrExpiredTokenException(
                    "Token is invalid, expired or already used"
            );
        }

        try {
            return objectMapper.readValue(payload, type);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not deserialize action token",
                    ex
            );
        }
    }

    private void delete(String purpose, String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        redisTemplate.delete(buildKey(purpose, token));
    }

    private String buildKey(String purpose, String token) {
        return PREFIX
                + purpose
                + ":"
                + tokenHasher.hashToken(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
