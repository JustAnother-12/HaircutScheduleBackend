package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.request.PasswordResetPayload;
import com.j2ee.HaircutSchedule.dto.request.PendingRegistration;
import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.dto.response.MessageResponse;
import com.j2ee.HaircutSchedule.dto.response.RegistrationResult;
import com.j2ee.HaircutSchedule.dto.response.SessionResponse;
import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.event.PasswordChangedEvent;
import com.j2ee.HaircutSchedule.exception.EmailServiceFailureException;
import com.j2ee.HaircutSchedule.exception.InvalidOrExpiredTokenException;
import com.j2ee.HaircutSchedule.exception.ResourceAlreadyExistedException;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.mapper.SessionMapper;
import com.j2ee.HaircutSchedule.repository.SessionRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.security.CustomUserDetails;
import com.j2ee.HaircutSchedule.security.JwtUtil;
import com.j2ee.HaircutSchedule.security.TokenHasher;
import com.j2ee.HaircutSchedule.service.impl.AuthServiceImpl;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-12-25T10:00:00Z");
    private static final String ACCESS_TOKEN = "mock-access-token";
    private static final String REFRESH_TOKEN = "mock-refresh-token";
    private static final String HASHED_REFRESH_TOKEN = "hashed-refresh-token";

    private static final long ACCESS_EXPIRATION_HOURS = 1;
    private static final long REFRESH_EXPIRATION_DAYS = 30;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisActionTokenService actionTokenService;
    @Mock private EmailService emailService;
    @Mock private StringRedisTemplate redisTemplate;

    @Mock private SessionRepository sessionRepository;
    @Mock private SessionMapper sessionMapper;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private TokenHasher tokenHasher;
    @Mock private java.time.Clock clock;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                authService,
                "frontendUrl",
                "http://localhost:3000"
        );

        jwtUtil.jwtAccessExpirationHours = ACCESS_EXPIRATION_HOURS;
        jwtUtil.jwtRefreshExpirationDays = REFRESH_EXPIRATION_DAYS;
    }

    private User createActiveUser() {
        return User.builder()
                .id(1)
                .fullName("Test Customer")
                .email("customera@example.com")
                .phone("0901234678")
                .hashedPassword("encoded-password")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
    }

    private void mockTokenGeneration(User user) {
        when(jwtUtil.generateAccessToken(user.getEmail()))
                .thenReturn(ACCESS_TOKEN);

        when(jwtUtil.generateRefreshToken(user.getEmail()))
                .thenReturn(REFRESH_TOKEN);

        when(tokenHasher.hashToken(REFRESH_TOKEN))
                .thenReturn(HASHED_REFRESH_TOKEN);

        when(clock.instant())
                .thenReturn(FIXED_NOW);
    }

    private Session captureCreatedSession() {
        ArgumentCaptor<Session> captor =
                ArgumentCaptor.forClass(Session.class);

        verify(sessionRepository, times(1))
                .saveAndFlush(captor.capture());

        return captor.getValue();
    }

    private void verifySession(Session session, User user){
        assertThat(session.getUser()).isSameAs(user);

        assertThat(session.getRefreshTokenHash())
                .isEqualTo(HASHED_REFRESH_TOKEN);

        assertThat(session.getRefreshTokenHash())
                .isNotEqualTo(REFRESH_TOKEN);

        assertThat(session.getCreatedAt())
                .isEqualTo(FIXED_NOW);

        assertThat(session.getLastActivityAt())
                .isEqualTo(FIXED_NOW);

        assertThat(session.getExpiredAt())
                .isEqualTo(FIXED_NOW.plus(
                        Duration.ofDays(REFRESH_EXPIRATION_DAYS)
                ));

        assertThat(session.getRevokedAt()).isNull();
    }

    private void verifyAuthResponse(AuthResponse response, User user){
        // Verify AuthResponse
        assertThat(response).isNotNull();

        assertThat(response.email())
                .isEqualTo(user.getEmail());

        assertThat(response.accessToken())
                .isEqualTo(ACCESS_TOKEN);

        assertThat(response.refreshToken())
                .isEqualTo(REFRESH_TOKEN);

        assertThat(response.tokenType())
                .isEqualTo("Bearer");

        assertThat(response.accessExpiresAt())
                .isEqualTo(
                        LocalDateTime.ofInstant(
                                FIXED_NOW.plus(
                                        Duration.ofHours(ACCESS_EXPIRATION_HOURS)
                                ),
                                ZoneOffset.UTC
                        )
                );

        assertThat(response.refreshExpiresAt())
                .isEqualTo(
                        LocalDateTime.ofInstant(
                                FIXED_NOW.plus(
                                        Duration.ofDays(REFRESH_EXPIRATION_DAYS)
                                ),
                                ZoneOffset.UTC
                        )
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
    void confirmRegistration_validToken_success(){
        User activeUser = createActiveUser();
        PendingRegistration pending = new PendingRegistration(
                activeUser.getFullName(),
                activeUser.getEmail(),
                activeUser.getPhone(),
                activeUser.getHashedPassword()
        );

        when(actionTokenService.consumeRegistrationToken("confirm_token"))
                .thenReturn(pending);

        when(userRepository.findByEmailIgnoreCase(activeUser.getEmail()))
                .thenReturn(Optional.empty());

        when(userRepository.findByPhone(activeUser.getPhone()))
                .thenReturn(Optional.empty());

        // Giả lập việc lưu User thành công.
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // tạo access và refresh token
        mockTokenGeneration(activeUser);

        AuthResponse response = authService.confirmRegistration("confirm_token");

        // Verify user
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.isActive())
                .isTrue();

        assertThat(capturedUser.getRole())
                .isEqualTo(UserRole.CUSTOMER);

        assertThat(capturedUser.getHashedPassword())
                .isEqualTo(pending.encodedPassword());

        assertThat(response.email())
                .isEqualTo(pending.email());

        // Verify session
        Session capturedSession = captureCreatedSession();
        verifySession(capturedSession, capturedUser);

        // Verify AuthResponse
        verifyAuthResponse(response, capturedUser);
    }

    @Test
    void confirmRegistration_duplicateEmail_throwsException(){
        PendingRegistration pending = new PendingRegistration(
                "Test Customer",
                "customera@example.com",
                "0901234678",
                "encode_password"
        );

        when(actionTokenService.consumeRegistrationToken("confirm_token"))
                .thenReturn(pending);

        when(userRepository.findByEmailIgnoreCase("customera@example.com"))
                .thenReturn(Optional.of(
                        User.builder()
                                .id(1)
                                .email("customera@example.com")
                                .build()
                ));

        assertThatThrownBy(() ->
                authService.confirmRegistration("confirm_token")
        ).isInstanceOf(ResourceAlreadyExistedException.class);

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(
                jwtUtil,
                sessionRepository
        );
    }

    @Test
    void confirmRegistration_duplicatePhone_throwsException(){
        PendingRegistration pending = new PendingRegistration(
                "Test Customer",
                "customera@example.com",
                "0901234678",
                "encode_password"
        );

        when(actionTokenService.consumeRegistrationToken("confirm_token"))
                .thenReturn(pending);

        when(userRepository.findByPhone("0901234678"))
                .thenReturn(Optional.of(
                        User.builder()
                                .id(1)
                                .phone("0901234678")
                                .build()
                ));

        assertThatThrownBy(() ->
                authService.confirmRegistration("confirm_token")
        ).isInstanceOf(ResourceAlreadyExistedException.class);

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verifyNoInteractions(
                jwtUtil,
                sessionRepository
        );
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
    void register_duplicatePhone_throwsException() {
        when(userRepository.findByPhone("0901234678"))
                .thenReturn(Optional.of(
                        User.builder()
                                .id(1)
                                .phone("0901234678")
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

    @Test
    void login_validCredentials_success(){
        // Arrange
        User user = createActiveUser();

        CustomUserDetails principal =
                new CustomUserDetails(user);

        Authentication authenticated =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        when(authenticationManager.authenticate(
                any(Authentication.class)
        )).thenReturn(authenticated);

        mockTokenGeneration(user);

        // Execute
        AuthResponse response = authService.login(
                "  CustomerA@Example.com  ",
                "customerpassword"
        );


        // Verify Authentication
        ArgumentCaptor<Authentication> authCaptor =
                ArgumentCaptor.forClass(Authentication.class);

        verify(authenticationManager)
                .authenticate(authCaptor.capture());

        Authentication authRequest = authCaptor.getValue();

        assertThat(authRequest)
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);

        assertThat(authRequest.getPrincipal())
                .isEqualTo("customera@example.com");

        assertThat(authRequest.getCredentials())
                .isEqualTo("customerpassword");

        // Verify JWT generation
        verify(jwtUtil).generateAccessToken(user.getEmail());
        verify(jwtUtil).generateRefreshToken(user.getEmail());

        Session capturedSession = captureCreatedSession();
        verifySession(capturedSession, user);

        verifyAuthResponse(response, user);

        verifyNoInteractions(userRepository);
    }

    @Test
    void login_invalidCredentials_throwsException() {
        // Arrange
        when(authenticationManager.authenticate(
                any(Authentication.class)
        )).thenThrow(
                new BadCredentialsException("Invalid email or password")
        );

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.login(
                        "customera@example.com",
                        "wrongpassword"
                )
        )
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");


        verify(authenticationManager).authenticate(any(Authentication.class));

        verifyNoInteractions(
                jwtUtil,
                tokenHasher,
                sessionRepository
        );
    }

    @Test
    void loginWithUser_activeUser_success() {
        // Arrange
        User user = createActiveUser();

        mockTokenGeneration(user);

        // Execute
        AuthResponse response = authService.loginWithUser(user);

        // Verify JWT generation
        verify(jwtUtil).generateAccessToken(user.getEmail());
        verify(jwtUtil).generateRefreshToken(user.getEmail());

        // Verify Session
        Session capturedSession = captureCreatedSession();
        verifySession(capturedSession, user);

        // Verify AuthResponse
        verifyAuthResponse(response, user);

        // loginWithUser không thực hiện username/password authentication.
        verifyNoInteractions(
                authenticationManager,
                userRepository
        );
    }

    @Test
    void loginWithUser_disabledUser_throwsException() {
        // Arrange
        User disabledUser = User.builder()
                .id(2)
                .fullName("Disabled Customer")
                .email("disabled@example.com")
                .phone("0901234567")
                .hashedPassword("encoded-password")
                .role(UserRole.CUSTOMER)
                .isActive(false)
                .build();

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.loginWithUser(disabledUser)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This account has been disabled");


        // Không được tạo JWT hoặc session.
        verifyNoInteractions(
                authenticationManager,
                jwtUtil,
                tokenHasher,
                sessionRepository
        );
    }

    @Test
    void logout_validRefreshToken_success() {
        // Arrange
        User user = createActiveUser();

        Session session = Session.builder()
                .id(1)
                .refreshTokenHash(tokenHasher.hashToken(REFRESH_TOKEN))
                .user(user)
                .expiredAt(FIXED_NOW.plusSeconds(10))
                .lastActivityAt(FIXED_NOW)
                .revokedAt(null)
                .createdAt(FIXED_NOW)
                .build();

        when(sessionRepository.findSessionByRefreshTokenHash(tokenHasher.hashToken(REFRESH_TOKEN)))
                .thenReturn(Optional.of(
                        session
                ));

        when(clock.instant())
                .thenReturn(FIXED_NOW);

        // Execute & Assert
        authService.logout(REFRESH_TOKEN);

        assertThat(session.getRevokedAt())
                .isEqualTo(FIXED_NOW);

        verify(sessionRepository)
                .saveAndFlush(any(Session.class));
    }

    @Test
    void logout_unknownOrRevokedToken_throwsException() {
        // Arrange
        User user = createActiveUser();



        when(sessionRepository.findSessionByRefreshTokenHash(tokenHasher.hashToken(REFRESH_TOKEN)))
                .thenReturn(Optional.of(
                        Session.builder()
                                .id(1)
                                .user(user)
                                .revokedAt(FIXED_NOW)
                                .build()
                        )
                );
        // Execute & Assert
        assertThatThrownBy(() ->
                authService.logout(REFRESH_TOKEN)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No active session found for the provided refresh token");

        verify(sessionRepository, never())
                .saveAndFlush(any(Session.class));
    }


    @Test
    void refreshToken_validRefreshToken_rotatesTokens() {
        // Arrange
        String oldRefreshToken = "old-refresh-token";
        String oldHashedToken = "old-hashed-refresh-token";

        User user = createActiveUser();

        Claims claims = mock(Claims.class);

        Session oldSession = Session.builder()
                .id(10)
                .user(user)
                .refreshTokenHash(oldHashedToken)
                .createdAt(FIXED_NOW.minus(Duration.ofDays(1)))
                .lastActivityAt(FIXED_NOW.minus(Duration.ofHours(1)))
                .expiredAt(FIXED_NOW.plus(Duration.ofDays(1)))
                .revokedAt(null)
                .build();

        when(jwtUtil.validateToken(oldRefreshToken))
                .thenReturn(true);

        when(jwtUtil.getAllClaimsFromToken(oldRefreshToken))
                .thenReturn(claims);

        when(claims.get("token_type", String.class))
                .thenReturn("REFRESH");

        when(tokenHasher.hashToken(oldRefreshToken))
                .thenReturn(oldHashedToken);

        when(sessionRepository.findSessionByRefreshTokenHash(oldHashedToken))
                .thenReturn(Optional.of(oldSession));

        mockTokenGeneration(user);

        // Execute
        AuthResponse response =
                authService.refreshToken(oldRefreshToken);

        // Verify old token
        verify(jwtUtil).validateToken(oldRefreshToken);

        verify(jwtUtil).getAllClaimsFromToken(oldRefreshToken);

        verify(claims).get("token_type", String.class);


        // Verify old session is revoked
        assertThat(oldSession.getRevokedAt())
                .isEqualTo(FIXED_NOW);


        // Verify new JWT generation
        verify(jwtUtil).generateAccessToken(user.getEmail());
        verify(jwtUtil).generateRefreshToken(user.getEmail());


        // Verify old and new sessions
        ArgumentCaptor<Session> sessionCaptor =
                ArgumentCaptor.forClass(Session.class);

        verify(sessionRepository, times(2))
                .saveAndFlush(sessionCaptor.capture());

        List<Session> savedSessions = sessionCaptor.getAllValues();

        // First save: old session after revocation.
        Session revokedSession = savedSessions.get(0);

        assertThat(revokedSession).isSameAs(oldSession);
        assertThat(revokedSession.getRevokedAt())
                .isEqualTo(FIXED_NOW);

        // Second save: newly created session.
        Session newSession = savedSessions.get(1);
        verifySession(newSession, user);

        // Verify AuthResponse
        assertThat(response).isNotNull();

        assertThat(response.email())
                .isEqualTo(user.getEmail());
    }


    @Test
    void refreshToken_invalidJwt_throwsException() {
        // Arrange
        String invalidRefreshToken = "invalid-refresh-token";

        when(jwtUtil.validateToken(invalidRefreshToken))
                .thenReturn(false);

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.refreshToken(invalidRefreshToken)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("Refresh token has expired or is invalid");

        // Không đọc claims nếu JWT không hợp lệ.
        verify(jwtUtil).validateToken(invalidRefreshToken);

        verify(jwtUtil, never())
                .getAllClaimsFromToken(anyString());

        // Không được hash token hoặc truy vấn Session.
        verifyNoInteractions(
                tokenHasher,
                sessionRepository
        );

        // Không được tạo JWT mới.
        verify(jwtUtil, never())
                .generateAccessToken(anyString());

        verify(jwtUtil, never())
                .generateRefreshToken(anyString());
    }

    @Test
    void refreshToken_accessToken_throwsException() {
        // Arrange
        String accessToken = "mock-access-token";

        Claims claims = mock(Claims.class);

        when(jwtUtil.validateToken(accessToken))
                .thenReturn(true);

        when(jwtUtil.getAllClaimsFromToken(accessToken))
                .thenReturn(claims);

        when(claims.get("token_type", String.class))
                .thenReturn("ACCESS");

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.refreshToken(accessToken)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage(
                        "Invalid token type. Expected a refresh token."
                );

        // Đã validate và đọc claim.
        verify(jwtUtil).validateToken(accessToken);

        verify(jwtUtil).getAllClaimsFromToken(accessToken);

        verify(claims).get("token_type", String.class);

        // Phải từ chối trước khi hash hoặc truy vấn Session.
        verifyNoInteractions(
                tokenHasher,
                sessionRepository
        );

        // Không tạo token mới.
        verify(jwtUtil, never())
                .generateAccessToken(anyString());

        verify(jwtUtil, never())
                .generateRefreshToken(anyString());
    }

    @Test
    void refreshToken_sessionNotFound_throwsException() {
        // Arrange
        String refreshToken = "unknown-refresh-token";
        String hashedToken = "unknown-hashed-token";

        Claims claims = mock(Claims.class);

        when(jwtUtil.validateToken(refreshToken))
                .thenReturn(true);

        when(jwtUtil.getAllClaimsFromToken(refreshToken))
                .thenReturn(claims);

        when(claims.get("token_type", String.class))
                .thenReturn("REFRESH");

        when(tokenHasher.hashToken(refreshToken))
                .thenReturn(hashedToken);

        when(sessionRepository.findSessionByRefreshTokenHash(hashedToken))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.refreshToken(refreshToken)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(
                        "No active session with the requested refresh token found in the system"
                );

        // Xác nhận token được validate và hash.
        verify(jwtUtil).validateToken(refreshToken);

        verify(jwtUtil).getAllClaimsFromToken(refreshToken);

        verify(tokenHasher).hashToken(refreshToken);

        // Xác nhận truy vấn Session.
        verify(sessionRepository)
                .findSessionByRefreshTokenHash(hashedToken);

        // Không tạo JWT mới.
        verify(jwtUtil, never())
                .generateAccessToken(anyString());

        verify(jwtUtil, never())
                .generateRefreshToken(anyString());

        // Không lưu hoặc revoke Session.
        verify(sessionRepository, never())
                .saveAndFlush(any(Session.class));
    }

    @Test
    void requestPasswordReset_unknownEmail_returnsGenericMessage() {
        // Arrange
        String inputEmail = "UnknownEmail@gmail.com";
        String normalizedEmail = "unknownemail@gmail.com";

        when(userRepository.findByEmailIgnoreCase(normalizedEmail))
                .thenReturn(Optional.empty());

        // Execute
        MessageResponse response =
                authService.requestPasswordReset(inputEmail);

        // Assert
        assertThat(response.message())
                .isEqualTo(
                        "If the account exists, a password reset email has been sent"
                );

        verify(userRepository)
                .findByEmailIgnoreCase(normalizedEmail);

        verifyNoInteractions(
                actionTokenService,
                emailService
        );
    }

    @Test
    void requestPasswordReset_emailFailure_deletesToken() {
        // Arrange
        User user = createActiveUser();
        String resetToken = "password-reset-token";

        when(userRepository.findByEmailIgnoreCase(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(actionTokenService.issuePasswordResetToken(
                any(PasswordResetPayload.class)
        )).thenReturn(resetToken);

        doThrow(new RuntimeException("SMTP server unavailable"))
                .when(emailService)
                .sendPasswordReset(
                        eq(user.getEmail()),
                        eq(user.getFullName()),
                        anyString()
                );

        // Execute
        MessageResponse response =
                authService.requestPasswordReset(user.getEmail());

        // Assert
        assertThat(response.message())
                .isEqualTo(
                        "If the account exists, a password reset email has been sent"
                );

        verify(emailService).sendPasswordReset(
                eq(user.getEmail()),
                eq(user.getFullName()),
                anyString()
        );

        verify(actionTokenService)
                .deletePasswordResetToken(resetToken);
    }

    @Test
    void resetPassword_validToken_updatesPassword() {
        // Arrange
        User user = createActiveUser();

        Session session = Session.builder()
                .user(user)
                .revokedAt(null)
                .build();

        when(actionTokenService.consumePasswordResetToken("password-token"))
                .thenReturn(new PasswordResetPayload(user.getId()));

        when(userRepository.findById(user.getId()))
                .thenReturn(Optional.of(user));

        when(sessionRepository.findAllByUserAndRevokedAtIsNull(user))
                .thenReturn(List.of(session));

        when(passwordEncoder.encode("new-password"))
                .thenReturn("hashed-new-password");

        when(clock.instant())
                .thenReturn(FIXED_NOW);

        // Execute
        authService.resetPassword(
                "password-token",
                "new-password"
        );

        // Assert password
        assertThat(user.getHashedPassword())
                .isEqualTo("hashed-new-password");

        // Assert session revocation
        assertThat(session.getRevokedAt())
                .isEqualTo(FIXED_NOW);

        // Verify password change event
        verify(eventPublisher)
                .publishEvent(
                        new PasswordChangedEvent(
                                user.getEmail(),
                                user.getFullName()
                        )
                );

        verify(sessionRepository)
                .findAllByUserAndRevokedAtIsNull(user);

        verify(passwordEncoder)
                .encode("new-password");
    }

    @Test
    void resetPassword_invalidToken_throwsException() {
        // Arrange
        String invalidToken = "invalid-password-reset-token";
        String validPassword = "NewPassword123";

        when(actionTokenService.consumePasswordResetToken(invalidToken))
                .thenThrow(new InvalidOrExpiredTokenException(
                        "Token is invalid, expired or already used"
                ));

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.resetPassword(invalidToken, validPassword)
        )
                .isInstanceOf(InvalidOrExpiredTokenException.class)
                .hasMessage("Token is invalid, expired or already used");

        // Verify
        verify(actionTokenService)
                .consumePasswordResetToken(invalidToken);

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void getActiveDevices_returnsOnlyRepositoryResults() {
        // Arrange
        User user = createActiveUser();

        Session session1 = Session.builder()
                .id(1)
                .user(user)
                .build();

        Session session2 = Session.builder()
                .id(2)
                .user(user)
                .build();

        SessionResponse response1 = SessionResponse.builder()
                .id(1)
                .userId(user.getId())
                .build();

        SessionResponse response2 = SessionResponse.builder()
                .id(2)
                .userId(user.getId())
                .build();

        when(clock.instant()).thenReturn(FIXED_NOW);

        when(sessionRepository.findAllByUserAndRevokedAtIsNullAndExpiredAtAfter(
                user,
                FIXED_NOW
        )).thenReturn(List.of(session1, session2));

        when(sessionMapper.toResponse(session1)).thenReturn(response1);
        when(sessionMapper.toResponse(session2)).thenReturn(response2);

        // Execute
        List<SessionResponse> result = authService.getActiveDevices(user);

        // Assert
        assertThat(result)
                .containsExactly(response1, response2);

        verify(sessionRepository)
                .findAllByUserAndRevokedAtIsNullAndExpiredAtAfter(
                        user,
                        FIXED_NOW
                );

        verify(sessionMapper).toResponse(session1);
        verify(sessionMapper).toResponse(session2);
    }

    @Test
    void logoutAll_revokesAllActiveSessions() {
        // Arrange
        User user = createActiveUser();

        Session session1 = Session.builder()
                .id(1)
                .user(user)
                .revokedAt(null)
                .build();

        Session session2 = Session.builder()
                .id(2)
                .user(user)
                .revokedAt(null)
                .build();

        List<Session> sessions = List.of(session1, session2);

        when(clock.instant()).thenReturn(FIXED_NOW);

        when(sessionRepository.findAllByUserAndRevokedAtIsNull(user))
                .thenReturn(sessions);

        // Execute
        authService.logoutAll(user);

        // Assert
        assertThat(session1.getRevokedAt()).isEqualTo(FIXED_NOW);
        assertThat(session2.getRevokedAt()).isEqualTo(FIXED_NOW);

        verify(clock).instant();
        verify(sessionRepository).findAllByUserAndRevokedAtIsNull(user);
    }

    @Test
    void loginWithUser_nullUser_throwsException() {
        // Execute & Assert
        assertThatThrownBy(() ->
                authService.loginWithUser(null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User is required");

        verifyNoInteractions(
                jwtUtil,
                tokenHasher,
                sessionRepository
        );
    }

    @Test
    void requestPasswordReset_existingActiveUser_sendsEmail() {
        // Arrange
        User user = createActiveUser();
        String resetToken = "password-reset-token";

        when(userRepository.findByEmailIgnoreCase(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(actionTokenService.issuePasswordResetToken(
                any(PasswordResetPayload.class)
        )).thenReturn(resetToken);

        // Execute
        MessageResponse response =
                authService.requestPasswordReset(user.getEmail());

        // Assert
        assertThat(response.message())
                .isEqualTo(
                        "If the account exists, a password reset email has been sent"
                );

        ArgumentCaptor<PasswordResetPayload> payloadCaptor =
                ArgumentCaptor.forClass(PasswordResetPayload.class);

        verify(actionTokenService)
                .issuePasswordResetToken(payloadCaptor.capture());

        assertThat(payloadCaptor.getValue().userId())
                .isEqualTo(user.getId());

        verify(emailService).sendPasswordReset(
                eq(user.getEmail()),
                eq(user.getFullName()),
                eq("http://localhost:3000/reset-password/" + resetToken)
        );
    }

    @Test
    void requestPasswordReset_disabledUser_doesNotSendEmail() {
        // Arrange
        User user = createActiveUser();
        user.setActive(false);

        when(userRepository.findByEmailIgnoreCase(user.getEmail()))
                .thenReturn(Optional.of(user));

        // Execute
        MessageResponse response =
                authService.requestPasswordReset(user.getEmail());

        // Assert
        assertThat(response.message())
                .isEqualTo(
                        "If the account exists, a password reset email has been sent"
                );

        verify(userRepository)
                .findByEmailIgnoreCase(user.getEmail());

        verifyNoInteractions(
                actionTokenService,
                emailService
        );
    }

    @Test
    void resetPassword_userNotFound_throwsException() {
        // Arrange
        String resetToken = "password-reset-token";
        Integer missingUserId = 999;

        when(actionTokenService.consumePasswordResetToken(resetToken))
                .thenReturn(new PasswordResetPayload(missingUserId));

        when(userRepository.findById(missingUserId))
                .thenReturn(Optional.empty());

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.resetPassword(
                        resetToken,
                        "NewPassword123"
                )
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User associated with reset token no longer exists");

        verify(actionTokenService)
                .consumePasswordResetToken(resetToken);

        verify(userRepository).findById(missingUserId);

        verifyNoInteractions(
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

    @Test
    void resetPassword_invalidPassword_throwsException() {
        // Arrange
        String resetToken = "password-reset-token";
        String invalidPassword = "short";

        // Execute & Assert
        assertThatThrownBy(() ->
                authService.resetPassword(
                        resetToken,
                        invalidPassword
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Password must contain between 8 and 32 characters"
                );

        verifyNoInteractions(
                actionTokenService,
                userRepository,
                passwordEncoder,
                sessionRepository,
                eventPublisher
        );
    }

}
