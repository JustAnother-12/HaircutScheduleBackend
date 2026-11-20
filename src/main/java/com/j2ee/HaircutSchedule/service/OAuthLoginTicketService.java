package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.exception.InvalidOrExpiredTokenException;
import com.j2ee.HaircutSchedule.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuthLoginTicketService {
    private static final String KEY_PREFIX = "oauth:login:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenHasher tokenHasher;

    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(AuthResponse authResponse) {

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String code = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        try {
            String payload = objectMapper.writeValueAsString(authResponse);

            String key = KEY_PREFIX + tokenHasher.hashToken(code);

            redisTemplate.opsForValue().set(
                    key,
                    payload,
                    TICKET_TTL
            );
            return code;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not create OAuth login ticket",
                    ex
            );
        }
    }

    public AuthResponse consume(String code) {

        if (code == null || code.isBlank()) {
            throw new InvalidOrExpiredTokenException(
                    "OAuth login code is required"
            );
        }

        String key = KEY_PREFIX + tokenHasher.hashToken(code);

        String payload = redisTemplate.opsForValue().getAndDelete(key);

        if (payload == null) {
            throw new InvalidOrExpiredTokenException(
                    "OAuth login code is invalid or expired"
            );
        }

        try {
            return objectMapper.readValue(
                    payload,
                    AuthResponse.class
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not read OAuth login ticket",
                    ex
            );
        }
    }
}
