package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.request.SocialIdentity;
import com.j2ee.HaircutSchedule.entity.SocialAccount;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.SocialAccountRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SocialAuthServiceTest {

    private static final String PROVIDER = "google";

    private static final String PROVIDER_USER_ID = "google-user-123";

    private static final String EMAIL = "customer@example.com";

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SocialAuthService socialAuthService;

    private User createActiveUser() {
        return User.builder()
                .id(1)
                .fullName("Test Customer")
                .email(EMAIL)
                .hashedPassword("encoded-password")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
    }

    private SocialIdentity createIdentity(
            String provider,
            String providerUserId,
            String email,
            String name,
            Boolean emailVerified
    ) {
        return new SocialIdentity(
                provider,
                providerUserId,
                email,
                name,
                emailVerified
        );
    }

    private SocialAccount createSocialAccount(User user) {
        return SocialAccount.builder()
                .id(1)
                .user(user)
                .provider("GOOGLE")
                .providerUserId(PROVIDER_USER_ID)
                .email(EMAIL)
                .build();
    }

    @Test
    void loginExistingSocialAccount_returnsLinkedUser() {
        // Arrange
        User user = createActiveUser();

        SocialAccount account = createSocialAccount(user);

        SocialIdentity identity = createIdentity(
                "google",
                PROVIDER_USER_ID,
                EMAIL,
                "Test Customer",
                true
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.of(account));

        // Execute
        User result = socialAuthService.loginOrRegister(identity);

        // Assert
        assertThat(result).isSameAs(user);
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.isActive()).isTrue();

        verify(socialAccountRepository)
                .findByProviderAndProviderUserId(
                        "GOOGLE",
                        PROVIDER_USER_ID
                );

        // Existing linked account must not create another user.
        verifyNoInteractions(userRepository);

        verify(socialAccountRepository, never())
                .save(any(SocialAccount.class));
    }

    @Test
    void loginExistingDisabledUser_rejected() {
        // Arrange
        User disabledUser = createActiveUser();
        disabledUser.setActive(false);

        SocialAccount account = createSocialAccount(disabledUser);

        SocialIdentity identity = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                EMAIL,
                "Test Customer",
                true
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.of(account));

        // Execute & Assert
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identity)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This account has been disabled");

        verifyNoInteractions(userRepository);

        verify(socialAccountRepository, never())
                .save(any(SocialAccount.class));
    }

    @Test
    void unsupportedProvider_rejected() {
        // Arrange
        SocialIdentity identity = createIdentity(
                "github",
                "github-user-123",
                EMAIL,
                "Test Customer",
                true
        );

        // Execute & Assert
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identity)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported social provider: GITHUB");

        verifyNoInteractions(
                socialAccountRepository,
                userRepository
        );
    }

    @Test
    void missingProviderUserId_rejected() {
        // Arrange
        SocialIdentity identityWithNullId = createIdentity(
                PROVIDER,
                null,
                EMAIL,
                "Test Customer",
                true
        );

        SocialIdentity identityWithBlankId = createIdentity(
                PROVIDER,
                "   ",
                EMAIL,
                "Test Customer",
                true
        );

        // Execute & Assert: null provider ID
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identityWithNullId)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider user ID is required");

        // Execute & Assert: blank provider ID
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identityWithBlankId)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider user ID is required");

        verifyNoInteractions(
                socialAccountRepository,
                userRepository
        );
    }

    @Test
    void missingEmail_rejected() {
        // Arrange
        SocialIdentity identityWithNullEmail = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                null,
                "Test Customer",
                true
        );

        SocialIdentity identityWithBlankEmail = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                "   ",
                "Test Customer",
                true
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.empty());

        // Execute & Assert: null email
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identityWithNullEmail)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google account does not provide an email");

        // Execute & Assert: blank email
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identityWithBlankEmail)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google account does not provide an email");

        verify(socialAccountRepository, times(2))
                .findByProviderAndProviderUserId(
                        "GOOGLE",
                        PROVIDER_USER_ID
                );

        verifyNoInteractions(userRepository);

        verify(socialAccountRepository, never())
                .save(any(SocialAccount.class));
    }

    @Test
    void unverifiedEmail_rejected() {
        // Arrange
        SocialIdentity unverifiedIdentity = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                EMAIL,
                "Test Customer",
                false
        );

        SocialIdentity identityWithNullVerification = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                EMAIL,
                "Test Customer",
                null
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.empty());

        // Execute & Assert: emailVerified = false
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(unverifiedIdentity)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google email has not been verified");

        // Execute & Assert: emailVerified = null
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(
                        identityWithNullVerification
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Google email has not been verified");

        verifyNoInteractions(userRepository);

        verify(socialAccountRepository, never())
                .save(any(SocialAccount.class));
    }

    @Test
    void emailAlreadyRegistered_rejected() {
        // Arrange
        User existingUser = createActiveUser();

        SocialIdentity identity = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                "  CUSTOMER@EXAMPLE.COM  ",
                "Test Customer",
                true
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(existingUser));

        // Execute & Assert
        assertThatThrownBy(() ->
                socialAuthService.loginOrRegister(identity)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("An account with this email already exists.");

        verify(userRepository)
                .findByEmailIgnoreCase(EMAIL);

        verify(userRepository, never()).save(any(User.class));

        verify(socialAccountRepository, never())
                .save(any(SocialAccount.class));
    }

    @Test
    void newSocialUser_createdAndLinked() {
        // Arrange
        String rawEmail = "  NewCustomer@Example.com  ";
        String normalizedEmail = "newcustomer@example.com";

        SocialIdentity identity = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                rawEmail,
                "New Customer",
                true
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmailIgnoreCase(normalizedEmail))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User savedUser = invocation.getArgument(0);
                    savedUser.setId(10);
                    return savedUser;
                });

        when(socialAccountRepository.save(any(SocialAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        User result = socialAuthService.loginOrRegister(identity);

        // Assert: new User
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(result).isSameAs(savedUser);

        assertThat(savedUser.getId()).isEqualTo(10);
        assertThat(savedUser.getFullName()).isEqualTo("New Customer");
        assertThat(savedUser.getEmail()).isEqualTo(normalizedEmail);
        assertThat(savedUser.getHashedPassword()).isNull();
        assertThat(savedUser.getPhone()).isNull();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(savedUser.isActive()).isTrue();

        // Assert: linked SocialAccount
        ArgumentCaptor<SocialAccount> accountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);

        verify(socialAccountRepository).save(accountCaptor.capture());

        SocialAccount savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getUser()).isSameAs(savedUser);
        assertThat(savedAccount.getProvider()).isEqualTo("GOOGLE");
        assertThat(savedAccount.getProviderUserId())
                .isEqualTo(PROVIDER_USER_ID);
        assertThat(savedAccount.getEmail()).isEqualTo(normalizedEmail);
    }

    @Test
    void missingName_fallbackUsesEmailPrefix() {
        // Arrange
        String normalizedEmail = "newcustomer@example.com";

        SocialIdentity identity = createIdentity(
                PROVIDER,
                PROVIDER_USER_ID,
                "  NewCustomer@Example.com  ",
                null,
                true
        );

        when(socialAccountRepository.findByProviderAndProviderUserId(
                "GOOGLE",
                PROVIDER_USER_ID
        )).thenReturn(Optional.empty());

        when(userRepository.findByEmailIgnoreCase(normalizedEmail))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User savedUser = invocation.getArgument(0);
                    savedUser.setId(11);
                    return savedUser;
                });

        when(socialAccountRepository.save(any(SocialAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        User result = socialAuthService.loginOrRegister(identity);

        // Assert
        assertThat(result.getFullName())
                .isEqualTo("newcustomer");

        assertThat(result.getEmail())
                .isEqualTo(normalizedEmail);

        assertThat(result.getRole())
                .isEqualTo(UserRole.CUSTOMER);

        assertThat(result.isActive()).isTrue();

        verify(userRepository)
                .findByEmailIgnoreCase(normalizedEmail);

        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }
}
