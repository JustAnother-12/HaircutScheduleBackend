package com.j2ee.HaircutSchedule.service.impl;

import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.exception.InvalidOrExpiredTokenException;
import com.j2ee.HaircutSchedule.exception.ResourceNotFoundException;
import com.j2ee.HaircutSchedule.repository.SessionRepository;
import com.j2ee.HaircutSchedule.security.CustomUserDetails;
import com.j2ee.HaircutSchedule.security.JwtUtil;
import com.j2ee.HaircutSchedule.security.TokenHasher;
import com.j2ee.HaircutSchedule.service.AuthService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SessionRepository sessionRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil tokenProvider;
    private final TokenHasher tokenHasher;
    private final Clock clock;

    @Override
    public AuthResponse login(
            String username,
            String password
    ) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        username,
                        password
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return createTokenPair(userDetails.getUser());
    }

    @Override
    public AuthResponse loginWithUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("This account has been disabled");
        }

        return createTokenPair(user);
    }

    @Override
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
        String hashedRefreshToken  = tokenHasher.hashToken(refreshToken);
        Session session = Session.builder()
                .user(user)
                .refreshTokenHash(hashedRefreshToken)
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .expiredAt(Instant.now().plusMillis(Duration.ofDays(tokenProvider.jwtRefreshExpirationDays).toMillis()))
                .revokedAt(null)
                .build();

        sessionRepository.saveAndFlush(session);
        return session;
    }

    private AuthResponse createTokenPair(User user){
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
}
