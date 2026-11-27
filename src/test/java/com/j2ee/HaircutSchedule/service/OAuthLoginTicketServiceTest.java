package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.exception.InvalidOrExpiredTokenException;
import com.j2ee.HaircutSchedule.security.TokenHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuthLoginTicketServiceTest {

    private static final String HASHED_CODE = "hashed-oauth-code";
    private static final String JSON_PAYLOAD = "{\"email\":\"customer@example.com\"}";
    private static final String REDIS_KEY = "oauth:login:" + HASHED_CODE;

    private static final String EMAIL = "customer@example.com";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TokenHasher tokenHasher;

    @InjectMocks
    private OAuthLoginTicketService ticketService;

    private void stubValueOperations() {
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
    }

    private AuthResponse createAuthResponse() {
        return AuthResponse.builder()
                .email(EMAIL)
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .accessExpiresAt(LocalDateTime.of(2026, 11, 27, 10, 0))
                .refreshExpiresAt(LocalDateTime.of(2026, 12, 27, 10, 0))
                .tokenType("Bearer")
                .build();
    }

    @Test
    void issueTicket_success() {
        // Arrange
        AuthResponse authResponse = createAuthResponse();

        stubValueOperations();

        when(objectMapper.writeValueAsString(authResponse))
                .thenReturn(JSON_PAYLOAD);

        when(tokenHasher.hashToken(anyString()))
                .thenReturn(HASHED_CODE);

        // Execute
        String code = ticketService.issue(authResponse);

        // Assert
        assertThat(code).isNotBlank();

        // Generated ticket uses URL-safe Base64 without padding.
        assertThat(code)
                .matches("[A-Za-z0-9_-]{43}");

        verify(objectMapper)
                .writeValueAsString(authResponse);

        verify(tokenHasher)
                .hashToken(code);

        verify(valueOperations).set(
                REDIS_KEY,
                JSON_PAYLOAD,
                Duration.ofSeconds(60)
        );
    }

    @Test
    void consumeTicket_success() {
        // Arrange
        String code = "valid-oauth-code";

        AuthResponse expected = createAuthResponse();

        stubValueOperations();

        when(tokenHasher.hashToken(code))
                .thenReturn(HASHED_CODE);

        when(valueOperations.getAndDelete(REDIS_KEY))
                .thenReturn(JSON_PAYLOAD);

        when(objectMapper.readValue(
                JSON_PAYLOAD,
                AuthResponse.class
        )).thenReturn(expected);

        // Execute
        AuthResponse result = ticketService.consume(code);

        // Assert
        assertThat(result).isSameAs(expected);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.accessToken())
                .isEqualTo("test-access-token");
        assertThat(result.refreshToken())
                .isEqualTo("test-refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");

        verify(tokenHasher).hashToken(code);

        verify(valueOperations)
                .getAndDelete(REDIS_KEY);

        verify(objectMapper)
                .readValue(JSON_PAYLOAD, AuthResponse.class);
    }

    @Test
    void consumeTicket_secondTimeRejected() {
        // Arrange
        String code = "one-time-oauth-code";

        AuthResponse expected = createAuthResponse();

        stubValueOperations();

        when(tokenHasher.hashToken(code))
                .thenReturn(HASHED_CODE);

        // First consume succeeds; second consume finds no Redis value.
        when(valueOperations.getAndDelete(REDIS_KEY))
                .thenReturn(JSON_PAYLOAD, null);

        when(objectMapper.readValue(
                JSON_PAYLOAD,
                AuthResponse.class
        )).thenReturn(expected);

        // Execute: first consumption
        AuthResponse firstResult = ticketService.consume(code);

        // Assert: first consumption succeeds
        assertThat(firstResult).isSameAs(expected);

        // Execute & Assert: second consumption is rejected
        assertThatThrownBy(() ->
                ticketService.consume(code)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("OAuth login code is invalid or expired");

        verify(valueOperations, times(2))
                .getAndDelete(REDIS_KEY);

        // JSON only needs to be deserialized on the first consume.
        verify(objectMapper, times(1))
                .readValue(JSON_PAYLOAD, AuthResponse.class);
    }


    @Test
    void consumeTicket_blankCodeRejected() {
        // Arrange
        String blankCode = "   ";

        // Execute & Assert
        assertThatThrownBy(() ->
                ticketService.consume(blankCode)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("OAuth login code is required");

        assertThatThrownBy(() ->
                ticketService.consume(null)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("OAuth login code is required");

        // Invalid codes must be rejected before hashing or Redis access.
        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                tokenHasher,
                objectMapper
        );
    }

    @Test
    void issueTicket_serializationFailure() {
        // Arrange
        AuthResponse authResponse = createAuthResponse();

        when(objectMapper.writeValueAsString(authResponse))
                .thenThrow(new RuntimeException("Serialization failed"));

        // Execute & Assert
        assertThatThrownBy(() ->
                ticketService.issue(authResponse)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not create OAuth login ticket")
                .hasCauseInstanceOf(RuntimeException.class);

        // Serialization fails before Redis key creation.
        verify(objectMapper)
                .writeValueAsString(authResponse);

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                tokenHasher
        );
    }
}
