package com.j2ee.HaircutSchedule.security;

import com.j2ee.HaircutSchedule.dto.request.SocialIdentity;
import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.entity.SocialAccount;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;

import com.j2ee.HaircutSchedule.handler.OAuth2LoginSuccessHandler;
import com.j2ee.HaircutSchedule.repository.SocialAccountRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;

import com.j2ee.HaircutSchedule.service.AuthService;
import com.j2ee.HaircutSchedule.service.OAuthLoginTicketService;
import com.j2ee.HaircutSchedule.service.SocialAuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuth2LoginSuccessHandlerTest {

    private static final String FRONTEND_URL =
            "http://localhost:3000";

    private static final String PROVIDER_USER_ID =
            "google-user-123";

    private static final String EMAIL =
            "customer@example.com";

    @Mock
    private SocialAuthService socialAuthService;

    @Mock
    private AuthService authService;

    @Mock
    private OAuthLoginTicketService ticketService;

    // Repository mocks phục vụ test fallback name
    // với SocialAuthService thật.
    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                handler,
                "frontendUrl",
                FRONTEND_URL
        );
    }

    private User createCustomer() {
        return User.builder()
                .id(1)
                .fullName("Test Customer")
                .email(EMAIL)
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
    }

    private AuthResponse createAuthResponse() {
        return AuthResponse.builder()
                .email(EMAIL)
                .accessToken("access-token-for-test")
                .refreshToken("refresh-token-for-test")
                .tokenType("Bearer")
                .build();
    }

    private OAuth2AuthenticationToken oauthAuthentication(
            String registrationId,
            Map<String, Object> attributes
    ) {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );

        return new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                registrationId
        );
    }

    private Map<String, Object> validGoogleAttributes() {
        Map<String, Object> attributes = new HashMap<>();

        attributes.put("sub", PROVIDER_USER_ID);
        attributes.put("email", EMAIL);
        attributes.put("name", "Test Customer");
        attributes.put("email_verified", true);

        return attributes;
    }

    private void stubSuccessfulLogin(
            User user,
            AuthResponse authResponse
    ) {
        when(socialAuthService.loginOrRegister(any()))
                .thenReturn(user);

        when(authService.loginWithUser(user))
                .thenReturn(authResponse);

        when(ticketService.issue(authResponse))
                .thenReturn("one-time-login-ticket");
    }

    @Test
    void googleLogin_success_redirectsWithTicket() throws Exception {
        // Arrange
        User user = createCustomer();
        AuthResponse authResponse = createAuthResponse();

        stubSuccessfulLogin(user, authResponse);

        OAuth2AuthenticationToken authentication =
                oauthAuthentication(
                        "google",
                        validGoogleAttributes()
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // Execute
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // Assert
        assertThat(response.getRedirectedUrl())
                .isEqualTo(
                        FRONTEND_URL
                                + "/oauth/callback#code=one-time-login-ticket"
                );

        ArgumentCaptor<SocialIdentity> identityCaptor =
                ArgumentCaptor.forClass(SocialIdentity.class);

        verify(socialAuthService)
                .loginOrRegister(identityCaptor.capture());

        SocialIdentity identity = identityCaptor.getValue();

        assertThat(identity.provider()).isEqualTo("GOOGLE");
        assertThat(identity.providerUserId())
                .isEqualTo(PROVIDER_USER_ID);

        assertThat(identity.email()).isEqualTo(EMAIL);
        assertThat(identity.name()).isEqualTo("Test Customer");
        assertThat(identity.emailVerified()).isTrue();

        verify(authService).loginWithUser(user);
        verify(ticketService).issue(authResponse);
    }

    @Test
    void unsupportedProvider_redirectsToFailure() throws Exception {
        // Arrange
        OAuth2AuthenticationToken authentication =
                oauthAuthentication(
                        "github",
                        validGoogleAttributes()
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // Execute
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // Assert
        assertThat(response.getRedirectedUrl())
                .isEqualTo(
                        FRONTEND_URL
                                + "/login?error=social_login_failed"
                );

        verifyNoInteractions(
                socialAuthService,
                authService,
                ticketService
        );
    }

    @Test
    void socialAuthFailure_redirectsToFailure() throws Exception {
        // Arrange
        when(socialAuthService.loginOrRegister(any()))
                .thenThrow(
                        new IllegalStateException(
                                "Google account is not eligible"
                        )
                );

        OAuth2AuthenticationToken authentication =
                oauthAuthentication(
                        "google",
                        validGoogleAttributes()
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // Execute
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // Assert
        assertThat(response.getRedirectedUrl())
                .isEqualTo(
                        FRONTEND_URL
                                + "/login?error=social_login_failed"
                );

        assertThat(response.getRedirectedUrl())
                .doesNotContain("access-token-for-test");

        assertThat(response.getRedirectedUrl())
                .doesNotContain("refresh-token-for-test");

        verify(socialAuthService)
                .loginOrRegister(any());

        verifyNoInteractions(
                authService,
                ticketService
        );
    }

    @Test
    void userNotFound_redirectsToFailure() throws Exception {
        // Arrange
        User user = createCustomer();

        when(socialAuthService.loginOrRegister(any()))
                .thenReturn(user);

        when(authService.loginWithUser(user))
                .thenThrow(
                        new org.springframework.security.core.userdetails.UsernameNotFoundException(
                                "User not found"
                        )
                );

        OAuth2AuthenticationToken authentication =
                oauthAuthentication(
                        "google",
                        validGoogleAttributes()
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // Execute
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // Assert
        assertThat(response.getRedirectedUrl())
                .isEqualTo(
                        FRONTEND_URL
                                + "/login?error=social_login_failed"
                );

        verify(socialAuthService)
                .loginOrRegister(any());

        verify(authService)
                .loginWithUser(user);

        verifyNoInteractions(ticketService);
    }

    @Test
    void successResponseSetsSecurityHeaders() throws Exception {
        // Arrange
        User user = createCustomer();
        AuthResponse authResponse = createAuthResponse();

        stubSuccessfulLogin(user, authResponse);

        OAuth2AuthenticationToken authentication =
                oauthAuthentication(
                        "google",
                        validGoogleAttributes()
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // Execute
        handler.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // Assert
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store");

        assertThat(response.getHeader("Pragma"))
                .isEqualTo("no-cache");

        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("no-referrer");

        assertThat(response.getRedirectedUrl())
                .isEqualTo(
                        FRONTEND_URL
                                + "/oauth/callback#code=one-time-login-ticket"
                );
    }

    @Test
    void missingEmailOrNameHandled() throws Exception {
        // Arrange
        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    saved.setId(25);
                    return saved;
                });

        when(socialAccountRepository.save(any(SocialAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse authResponse = createAuthResponse();

        when(authService.loginWithUser(any(User.class)))
                .thenReturn(authResponse);

        when(ticketService.issue(authResponse))
                .thenReturn("fallback-name-ticket");

        // Use the real SocialAuthService to verify fallback behavior.
        SocialAuthService realSocialAuthService =
                new SocialAuthService(
                        socialAccountRepository,
                        userRepository
                );

        OAuth2LoginSuccessHandler handlerWithRealSocialAuth =
                new OAuth2LoginSuccessHandler(
                        realSocialAuthService,
                        authService,
                        ticketService
                );

        ReflectionTestUtils.setField(
                handlerWithRealSocialAuth,
                "frontendUrl",
                FRONTEND_URL
        );

        Map<String, Object> attributesWithoutName =
                new HashMap<>();

        attributesWithoutName.put("sub", PROVIDER_USER_ID);
        attributesWithoutName.put("email", EMAIL);
        attributesWithoutName.put("email_verified", true);

        OAuth2AuthenticationToken authentication =
                oauthAuthentication(
                        "google",
                        attributesWithoutName
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // Execute
        handlerWithRealSocialAuth.onAuthenticationSuccess(
                request,
                response,
                authentication
        );

        // Assert
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();

        assertThat(createdUser.getFullName())
                .isEqualTo("customer");

        assertThat(createdUser.getEmail())
                .isEqualTo(EMAIL);

        assertThat(createdUser.getRole())
                .isEqualTo(UserRole.CUSTOMER);

        assertThat(createdUser.isActive())
                .isTrue();

        ArgumentCaptor<SocialAccount> accountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);

        verify(socialAccountRepository)
                .save(accountCaptor.capture());

        assertThat(accountCaptor.getValue().getProvider())
                .isEqualTo("GOOGLE");

        assertThat(accountCaptor.getValue().getProviderUserId())
                .isEqualTo(PROVIDER_USER_ID);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(
                        FRONTEND_URL
                                + "/oauth/callback#code=fallback-name-ticket"
                );
    }
}
