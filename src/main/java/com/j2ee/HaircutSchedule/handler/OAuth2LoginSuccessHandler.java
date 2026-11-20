package com.j2ee.HaircutSchedule.handler;

import com.j2ee.HaircutSchedule.dto.request.SocialIdentity;
import com.j2ee.HaircutSchedule.dto.response.AuthResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.service.AuthService;
import com.j2ee.HaircutSchedule.service.OAuthLoginTicketService;
import com.j2ee.HaircutSchedule.service.SocialAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final SocialAuthService socialAuthService;
    private final AuthService authService;
    private final OAuthLoginTicketService ticketService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try{
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

            OAuth2User principal = oauthToken.getPrincipal();

            String provider = oauthToken
                    .getAuthorizedClientRegistrationId()
                    .toUpperCase(Locale.ROOT);

            if (!"GOOGLE".equals(provider)) {
                throw new IllegalArgumentException(
                        "Unsupported provider: " + provider
                );
            }

            Map<String, Object> attributes = principal.getAttributes();

            String providerUserId = principal.getName();

            String email = (String) attributes.get("email");

            String name = (String) attributes.get("name");

            Boolean emailVerified =
                    attributes.get("email_verified") instanceof Boolean value
                            ? value
                            : null;

            SocialIdentity identity = new SocialIdentity(
                    provider,
                    providerUserId,
                    email,
                    name,
                    emailVerified
            );

            User user = socialAuthService.loginOrRegister(identity);

            AuthResponse authResponse = authService.loginWithUser(user);

            String code = ticketService.issue(authResponse);

            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Referrer-Policy", "no-referrer");

            response.sendRedirect(
                    frontendUrl + "/oauth/callback#code=" + code
            );
        } catch (Exception ex) {
            log.error("OAuth2 login processing failed", ex);

            response.sendRedirect(
                    frontendUrl + "/login?error=social_login_failed"
            );
        }
    }
}
