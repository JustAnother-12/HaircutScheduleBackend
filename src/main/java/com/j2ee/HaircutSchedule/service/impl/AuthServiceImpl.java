package com.j2ee.HaircutSchedule.service.impl;

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
import com.j2ee.HaircutSchedule.service.AuthService;
import com.j2ee.HaircutSchedule.service.EmailService;
import com.j2ee.HaircutSchedule.service.RedisActionTokenService;
import com.j2ee.HaircutSchedule.util.ValidationUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtUtil tokenProvider;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    private final RedisActionTokenService actionTokenService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.frontend-url}")
    private String frontendUrl;


    @Override
    public RegistrationResult register(
            String fullName,
            String email,
            String phone,
            String password
    ) {
        String validName = ValidationUtils.requireFullName(fullName);
        String validEmail = ValidationUtils.requireEmail(email);
        String validPhone = ValidationUtils.requirePhone(phone);
        String validPassword = ValidationUtils.requirePassword(password);

        boolean emailExists = userRepository.findByEmailIgnoreCase(validEmail).isPresent();

        boolean phoneExists = userRepository.findByPhone(validPhone).isPresent();

        if (emailExists || phoneExists) {
            throw new ResourceAlreadyExistedException(
                    "Email or phone already exists"
            );
        }

        PendingRegistration pending = new PendingRegistration(
                validName,
                validEmail,
                validPhone,
                passwordEncoder.encode(validPassword)
        );

        String token = actionTokenService.issueRegistrationToken(pending);

        String confirmationUrl = frontendUrl + "/confirmation/" + token;

        try {
            emailService.sendRegistrationConfirmation(
                    validEmail,
                    validName,
                    confirmationUrl
            );

        } catch (RuntimeException ex) {
            actionTokenService.deleteRegistrationToken(token);

            throw new EmailServiceFailureException(
                    "Could not send registration confirmation email",
                    ex
            );
        }

        return new RegistrationResult(
                "A confirmation email has been sent",
                validEmail
        );
    }

    @Override
    @Transactional
    public AuthResponse confirmRegistration(String token) {

        PendingRegistration pending = actionTokenService.consumeRegistrationToken(token);

        // Kiểm tra lại vì có thể tài khoản đã được tạo
        // sau thời điểm người dùng yêu cầu đăng ký.
        if (userRepository.findByEmailIgnoreCase(pending.email()).isPresent()) {
            throw new ResourceAlreadyExistedException(
                    "Email already exists"
            );
        }

        if (userRepository.findByPhone(pending.phone()).isPresent()) {
            throw new ResourceAlreadyExistedException(
                    "Phone already exists"
            );
        }

        User user = User.builder()
                .fullName(pending.fullName())
                .email(pending.email())
                .phone(pending.phone())
                .hashedPassword(pending.encodedPassword())
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        return createTokenPairAndResponse(savedUser);
    }

    @Override
    public AuthResponse login(
            String email,
            String password
    ) {

        String normalizedEmail = ValidationUtils.requireEmail(email);

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                normalizedEmail,
                                password
                        )
                );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return createTokenPairAndResponse(userDetails.getUser());
    }

    @Override
    public AuthResponse loginWithUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("This account has been disabled");
        }

        return createTokenPairAndResponse(user);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        String hashedToken = tokenHasher.hashToken(refreshToken);
        Session session = sessionRepository
                .findSessionByRefreshTokenHash(hashedToken)
                .filter(s -> s.getRevokedAt() == null)
                .orElseThrow(
                        () -> new ResourceNotFoundException("No active session found for the provided refresh token")
                );
        session.setRevokedAt(Instant.now());
        sessionRepository.saveAndFlush(session);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if(tokenProvider.validateToken(refreshToken)) {
            Claims claims = tokenProvider.getAllClaimsFromToken(refreshToken);

            String tokenType = claims.get("token_type", String.class);
            if (!"REFRESH".equals(tokenType)) {
                throw new InvalidOrExpiredTokenException("Invalid token type. Expected a refresh token.");
            }

            String hashedToken = tokenHasher.hashToken(refreshToken);
            Session session = sessionRepository
                    .findSessionByRefreshTokenHash(hashedToken)
                    .filter(s -> s.getRevokedAt() == null)
                    .orElseThrow(
                            () -> new ResourceNotFoundException("No active session with the requested refresh token found in the system")
                    );

            User user = session.getUser();
            String email = user.getEmail();

            // create a new set of refresh and access token
            String newAccessToken = tokenProvider.generateAccessToken(email);
            String newRefreshToken = tokenProvider.generateRefreshToken(email);

            // revoke the old session
            session.setRevokedAt(Instant.now());
            sessionRepository.saveAndFlush(session);

            createSession(session.getUser(), newRefreshToken);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .accessExpiresAt(LocalDateTime.now().plusHours(tokenProvider.jwtAccessExpirationHours))
                    .refreshExpiresAt(LocalDateTime.now().plusDays(tokenProvider.jwtRefreshExpirationDays))
                    .tokenType("Bearer")
                    .build();
        }
        throw new InvalidOrExpiredTokenException("Refresh token has expired or is invalid");
    }

    @Override
    public Session createSession(User user, String refreshToken) {
        Instant now = clock.instant();
        String hashedRefreshToken  = tokenHasher.hashToken(refreshToken);
        Session session = Session.builder()
                .user(user)
                .refreshTokenHash(hashedRefreshToken)
                .createdAt(now)
                .lastActivityAt(now)
                .expiredAt(now.plus(Duration.ofDays(tokenProvider.jwtRefreshExpirationDays)))
                .revokedAt(null)
                .build();

        sessionRepository.saveAndFlush(session);
        return session;
    }

    @Override
    public MessageResponse requestPasswordReset(String email) {
        String validEmail = ValidationUtils.requireEmail(email);

        userRepository.findByEmailIgnoreCase(validEmail)
                .filter(User::isActive)
                .ifPresent(user -> {

                    String token = actionTokenService.issuePasswordResetToken(
                            new PasswordResetPayload(user.getId())
                    );

                    String resetUrl = frontendUrl + "/reset-password/" + token;

                    try {
                        emailService.sendPasswordReset(
                                user.getEmail(),
                                user.getFullName(),
                                resetUrl
                        );

                    } catch (RuntimeException ex) {
                        actionTokenService.deletePasswordResetToken(token);
                        log.error("Could not send password reset email");
                    }
                });

        return new MessageResponse(
                "If the account exists, a password reset email has been sent"
        );
    }

    @Override
    @Transactional
    public Boolean resetPassword(
            String token,
            String newPassword
    ) {
        String validPassword = ValidationUtils.requirePassword(newPassword);

        PasswordResetPayload payload = actionTokenService.consumePasswordResetToken(token);

        User user = userRepository.findById(payload.userId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User associated with reset token no longer exists"
                        )
                );

        user.setHashedPassword(passwordEncoder.encode(validPassword));

        revokeAllSessions(user);

        eventPublisher.publishEvent(
                new PasswordChangedEvent(
                        user.getEmail(),
                        user.getFullName()
                )
        );

        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveDevices(User currentUser) {
        return sessionRepository
                .findAllByUserAndRevokedAtIsNullAndExpiredAtAfter(
                        currentUser,
                        clock.instant()
                )
                .stream()
                .map(sessionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void logoutAll(User currentUser) {
        Instant now = clock.instant();

        sessionRepository.findAllByUserAndRevokedAtIsNull(currentUser)
                .forEach(session ->
                        session.setRevokedAt(now)
                );
    }

    private AuthResponse createTokenPairAndResponse(User user){
        String accessToken = tokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        createSession(user, refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessExpiresAt(LocalDateTime.now().plusHours(tokenProvider.jwtAccessExpirationHours))
                .refreshExpiresAt(LocalDateTime.now().plusDays(tokenProvider.jwtRefreshExpirationDays))
                .tokenType("Bearer")
                .build();
    }

    private void revokeAllSessions(User user) {
        Instant now = clock.instant();

        sessionRepository.findAllByUserAndRevokedAtIsNull(user)
                .forEach(session ->
                        session.setRevokedAt(now)
                );
    }
}
