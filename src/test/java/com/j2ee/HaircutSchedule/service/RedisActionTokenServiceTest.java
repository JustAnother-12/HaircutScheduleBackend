package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.request.PasswordResetPayload;
import com.j2ee.HaircutSchedule.dto.request.PendingRegistration;
import com.j2ee.HaircutSchedule.exception.InvalidOrExpiredTokenException;
import com.j2ee.HaircutSchedule.security.TokenHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisActionTokenServiceTest {

    private static final String HASHED_TOKEN = "hashed-action-token";

    private static final String REGISTRATION_KEY =
            "auth:action:registration:" + HASHED_TOKEN;

    private static final String PASSWORD_RESET_KEY =
            "auth:action:password-reset:" + HASHED_TOKEN;

    private static final String REGISTRATION_JSON =
            "{\"fullName\":\"Test Customer\",\"email\":\"customer@example.com\"}";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TokenHasher tokenHasher;

    @InjectMocks
    private RedisActionTokenService actionTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                actionTokenService,
                "registrationTokenTtl",
                Duration.ofHours(24)
        );

        ReflectionTestUtils.setField(
                actionTokenService,
                "passwordResetTokenTtl",
                Duration.ofMinutes(15)
        );
    }

    private void stubValueOperations() {
        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
    }

    private PendingRegistration createPendingRegistration() {
        return new PendingRegistration(
                "Test Customer",
                "customer@example.com",
                "0901234567",
                "encoded-password"
        );
    }

    @Test
    void issueRegistrationToken_storesPayloadWithTtl() {
        // Arrange
        PendingRegistration payload = createPendingRegistration();

        stubValueOperations();

        when(objectMapper.writeValueAsString(payload))
                .thenReturn(REGISTRATION_JSON);

        when(tokenHasher.hashToken(anyString()))
                .thenReturn(HASHED_TOKEN);

        // Execute
        String token =
                actionTokenService.issueRegistrationToken(payload);

        // Assert
        assertThat(token).isNotBlank();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");

        verify(objectMapper)
                .writeValueAsString(payload);

        verify(tokenHasher)
                .hashToken(token);

        verify(valueOperations).set(
                REGISTRATION_KEY,
                REGISTRATION_JSON,
                Duration.ofHours(24)
        );
    }

    @Test
    void consumeRegistrationToken_returnsPayload() {
        // Arrange
        String token = "registration-token";

        PendingRegistration expected = createPendingRegistration();

        stubValueOperations();

        when(tokenHasher.hashToken(token))
                .thenReturn(HASHED_TOKEN);

        when(valueOperations.getAndDelete(REGISTRATION_KEY))
                .thenReturn(REGISTRATION_JSON);

        when(objectMapper.readValue(
                REGISTRATION_JSON,
                PendingRegistration.class
        )).thenReturn(expected);

        // Execute
        PendingRegistration result =
                actionTokenService.consumeRegistrationToken(token);

        // Assert
        assertThat(result).isSameAs(expected);

        assertThat(result.fullName())
                .isEqualTo("Test Customer");

        assertThat(result.email())
                .isEqualTo("customer@example.com");

        assertThat(result.phone())
                .isEqualTo("0901234567");

        assertThat(result.encodedPassword())
                .isEqualTo("encoded-password");

        verify(tokenHasher).hashToken(token);

        verify(valueOperations)
                .getAndDelete(REGISTRATION_KEY);

        verify(objectMapper)
                .readValue(
                        REGISTRATION_JSON,
                        PendingRegistration.class
                );
    }

    @Test
    void consumeRegistrationToken_cannotBeReused() {
        // Arrange
        String token = "one-time-registration-token";

        PendingRegistration expected = createPendingRegistration();

        stubValueOperations();

        when(tokenHasher.hashToken(token))
                .thenReturn(HASHED_TOKEN);

        when(valueOperations.getAndDelete(REGISTRATION_KEY))
                .thenReturn(REGISTRATION_JSON, null);

        when(objectMapper.readValue(
                REGISTRATION_JSON,
                PendingRegistration.class
        )).thenReturn(expected);

        // Execute: first consumption
        PendingRegistration result =
                actionTokenService.consumeRegistrationToken(token);

        // Assert: first consumption succeeds
        assertThat(result).isSameAs(expected);

        // Execute & Assert: second consumption is rejected
        assertThatThrownBy(() ->
                actionTokenService.consumeRegistrationToken(token)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("Token is invalid, expired or already used");

        verify(valueOperations, times(2))
                .getAndDelete(REGISTRATION_KEY);

        verify(objectMapper, times(1))
                .readValue(
                        REGISTRATION_JSON,
                        PendingRegistration.class
                );
    }

    @Test
    void registrationTokenCannotBeUsedAsPasswordResetToken() {
        // Arrange
        String registrationToken = "registration-token";

        stubValueOperations();

        when(tokenHasher.hashToken(registrationToken))
                .thenReturn(HASHED_TOKEN);

        // Execute & Assert
        assertThatThrownBy(() ->
                actionTokenService.consumePasswordResetToken(
                        registrationToken
                )
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("Token is invalid, expired or already used");

        // The token is looked up under the password-reset namespace.
        verify(valueOperations)
                .getAndDelete(PASSWORD_RESET_KEY);

        // It must not be looked up under registration namespace.
        verify(valueOperations, never())
                .getAndDelete(REGISTRATION_KEY);

        verify(objectMapper, never())
                .readValue(anyString(), eq(PasswordResetPayload.class));
    }

    @Test
    void consumeBlankToken_rejected() {
        // Arrange
        String blankToken = "   ";

        // Execute & Assert: blank token
        assertThatThrownBy(() ->
                actionTokenService.consumeRegistrationToken(blankToken)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("Token is required");

        // Execute & Assert: null token
        assertThatThrownBy(() ->
                actionTokenService.consumeRegistrationToken(null)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("Token is required");

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                tokenHasher,
                objectMapper
        );
    }

    @Test
    void issueToken_invalidTtl_rejected() {
        // Arrange
        PendingRegistration payload = createPendingRegistration();

        ReflectionTestUtils.setField(
                actionTokenService,
                "registrationTokenTtl",
                Duration.ZERO
        );

        // Execute & Assert: zero TTL
        assertThatThrownBy(() ->
                actionTokenService.issueRegistrationToken(payload)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token TTL must be greater than zero");

        ReflectionTestUtils.setField(
                actionTokenService,
                "registrationTokenTtl",
                Duration.ofSeconds(-1)
        );

        // Execute & Assert: negative TTL
        assertThatThrownBy(() ->
                actionTokenService.issueRegistrationToken(payload)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token TTL must be greater than zero");

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                tokenHasher,
                objectMapper
        );
    }

    @Test
    void issueToken_serializationFailure_throwsException() {
        // Arrange
        PendingRegistration payload = createPendingRegistration();

        when(objectMapper.writeValueAsString(payload))
                .thenThrow(new RuntimeException("JSON serialization failed"));

        // Execute & Assert
        assertThatThrownBy(() ->
                actionTokenService.issueRegistrationToken(payload)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not store action token")
                .hasCauseInstanceOf(RuntimeException.class);

        verify(objectMapper)
                .writeValueAsString(payload);

        verifyNoInteractions(
                redisTemplate,
                valueOperations,
                tokenHasher
        );
    }

    @Test
    void deleteToken_removesRedisKey() {
        // Arrange
        String token = "password-reset-token";

        when(tokenHasher.hashToken(token))
                .thenReturn(HASHED_TOKEN);

        // Execute
        actionTokenService.deletePasswordResetToken(token);

        // Assert
        verify(tokenHasher).hashToken(token);

        verify(redisTemplate)
                .delete(PASSWORD_RESET_KEY);

        verify(redisTemplate, never()).opsForValue();
    }
}
