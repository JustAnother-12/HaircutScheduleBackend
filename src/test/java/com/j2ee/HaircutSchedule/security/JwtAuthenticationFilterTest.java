package com.j2ee.HaircutSchedule.security;

import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {
    private static final String EMAIL = "customer@example.com";

    @Mock
    private JwtUtil tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createCustomer() {
        return User.builder()
                .id(1)
                .fullName("Test Customer")
                .email(EMAIL)
                .hashedPassword("encoded-password")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
    }

    private MockHttpServletRequest requestWithBearerToken(
            String token
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        return request;
    }

    private void setExistingAuthentication() {
        Authentication existing =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("CUSTOMER")
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(existing);
    }

    @Test
    void validAccessToken_setsAuthentication() throws Exception {
        // Arrange
        String token = "valid-access-token";

        Claims claims = mock(Claims.class);

        CustomUserDetails userDetails =
                new CustomUserDetails(createCustomer());

        MockHttpServletRequest request =
                requestWithBearerToken(token);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        when(tokenProvider.validateToken(token))
                .thenReturn(true);

        when(tokenProvider.getAllClaimsFromToken(token))
                .thenReturn(claims);

        when(claims.get("token_type", String.class))
                .thenReturn("ACCESS");

        when(tokenProvider.getEmailFromJWT(token))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(userDetails);

        // Execute
        filter.doFilter(request, response, chain);

        // Assert
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertThat(authentication).isNotNull();

        assertThat(authentication.isAuthenticated())
                .isTrue();

        assertThat(authentication.getPrincipal())
                .isSameAs(userDetails);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("CUSTOMER");

        verify(userDetailsService)
                .loadUserByUsername(EMAIL);

        verify(chain).doFilter(request, response);
    }

    @Test
    void refreshToken_doesNotAuthenticateRequest() throws Exception {
        // Arrange
        String token = "valid-refresh-token";

        Claims claims = mock(Claims.class);

        MockHttpServletRequest request =
                requestWithBearerToken(token);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        when(tokenProvider.validateToken(token))
                .thenReturn(true);

        when(tokenProvider.getAllClaimsFromToken(token))
                .thenReturn(claims);

        when(claims.get("token_type", String.class))
                .thenReturn("REFRESH");

        // Execute
        filter.doFilter(request, response, chain);

        // Assert
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();

        verify(userDetailsService, never())
                .loadUserByUsername(anyString());

        verify(tokenProvider, never())
                .getEmailFromJWT(anyString());

        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidToken_clearsSecurityContext() throws Exception {
        // Arrange
        String invalidToken = "invalid-token";

        setExistingAuthentication();

        MockHttpServletRequest request =
                requestWithBearerToken(invalidToken);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        when(tokenProvider.validateToken(invalidToken))
                .thenReturn(false);

        // Execute
        filter.doFilter(request, response, chain);

        // Assert
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();

        verify(userDetailsService, never())
                .loadUserByUsername(anyString());

        verify(chain).doFilter(request, response);
    }

    @Test
    void userNotFound_clearsContext() throws Exception {
        // Arrange
        String token = "valid-token-for-missing-user";

        Claims claims = mock(Claims.class);

        setExistingAuthentication();

        MockHttpServletRequest request =
                requestWithBearerToken(token);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        when(tokenProvider.validateToken(token))
                .thenReturn(true);

        when(tokenProvider.getAllClaimsFromToken(token))
                .thenReturn(claims);

        when(claims.get("token_type", String.class))
                .thenReturn("ACCESS");

        when(tokenProvider.getEmailFromJWT(token))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(
                        new UsernameNotFoundException("User not found")
                );

        // Execute
        filter.doFilter(request, response, chain);

        // Assert
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();

        verify(userDetailsService)
                .loadUserByUsername(EMAIL);

        verify(chain).doFilter(request, response);
    }

    @Test
    void missingAuthorizationHeader_continuesUnauthenticated()
            throws Exception {

        // Arrange
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        // Execute
        filter.doFilter(request, response, chain);

        // Assert
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();

        verifyNoInteractions(
                tokenProvider,
                userDetailsService
        );

        verify(chain).doFilter(request, response);
    }

    @Test
    void malformedBearerHeader_notAuthenticated() throws Exception {
        // Arrange
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Basic malformed-token"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        // Execute
        filter.doFilter(request, response, chain);

        // Assert
        assertThat(
                SecurityContextHolder.getContext()
                        .getAuthentication()
        ).isNull();

        verifyNoInteractions(
                tokenProvider,
                userDetailsService
        );

        verify(chain).doFilter(request, response);
    }
}
