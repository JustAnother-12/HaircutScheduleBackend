package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.request.PendingRegistration;
import com.j2ee.HaircutSchedule.dto.response.RegistrationResult;
import com.j2ee.HaircutSchedule.exception.EmailServiceFailureException;
import com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.impl.AuthServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisActionTokenService actionTokenService;
    @Mock private EmailService emailService;

    @Mock private com.j2ee.HaircutSchedule.repository.SessionRepository sessionRepository;
    @Mock private com.j2ee.HaircutSchedule.mapper.SessionMapper sessionMapper;
    @Mock private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Mock private com.j2ee.HaircutSchedule.security.JwtUtil jwtUtil;
    @Mock private com.j2ee.HaircutSchedule.security.TokenHasher tokenHasher;
    @Mock private java.time.Clock clock;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                authService,
                "frontendUrl",
                "http://localhost:3000"
        );
    }

    @Test
    void register_validData_createsRedisTokenAndSendsEmail() {
        when(userRepository.findByEmailIgnoreCase("customera@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByPhone("0901234678"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("customerpassword"))
                .thenReturn("encoded-password");

        when(actionTokenService.issueRegistrationToken(
                any(PendingRegistration.class)
        )).thenReturn("registration-token");

        RegistrationResult result = authService.register(
                "Test customerA",
                "customera@example.com",
                "0901234678",
                "customerpassword"
        );

        ArgumentCaptor<PendingRegistration> captor =
                ArgumentCaptor.forClass(PendingRegistration.class);

        verify(actionTokenService)
                .issueRegistrationToken(captor.capture());

        assertThat(captor.getValue().fullName())
                .isEqualTo("Test customerA");

        assertThat(captor.getValue().encodedPassword())
                .isEqualTo("encoded-password");

        verify(emailService).sendRegistrationConfirmation(
                eq("customera@example.com"),
                eq("Test customerA"),
                eq("http://localhost:3000/confirmation/registration-token")
        );

        assertThat(result.email())
                .isEqualTo("customera@example.com");
    }

    @Test
    void register_duplicateEmail_throwsException() {
        when(userRepository.findByEmailIgnoreCase("customera@example.com"))
                .thenReturn(Optional.of(
                        com.j2ee.HaircutSchedule.entity.User.builder()
                                .id(1)
                                .email("customera@example.com")
                                .build()
                ));

        assertThatThrownBy(() ->
                authService.register(
                        "Test customerA",
                        "customera@example.com",
                        "0901234678",
                        "customerpassword"
                )
        ).isInstanceOf(ResourceAlreadyExistedException.class);

        verifyNoInteractions(actionTokenService, emailService);
    }

    @Test
    void register_emailFailure_deletesIssuedToken() {
        when(userRepository.findByEmailIgnoreCase("customera@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByPhone("0901234678"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("customerpassword"))
                .thenReturn("encoded-password");

        when(actionTokenService.issueRegistrationToken(
                any(PendingRegistration.class)
        )).thenReturn("registration-token");

        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailService)
                .sendRegistrationConfirmation(
                        anyString(),
                        anyString(),
                        anyString()
                );

        assertThatThrownBy(() ->
                authService.register(
                        "Test customerA",
                        "customera@example.com",
                        "0901234678",
                        "customerpassword"
                )
        ).isInstanceOf(EmailServiceFailureException.class);

        verify(actionTokenService)
                .deleteRegistrationToken("registration-token");
    }
}
