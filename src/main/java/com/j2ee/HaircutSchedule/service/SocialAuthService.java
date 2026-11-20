package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.request.SocialIdentity;
import com.j2ee.HaircutSchedule.entity.SocialAccount;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.repository.SocialAccountRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SocialAuthService {
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    @Transactional
    public User loginOrRegister(SocialIdentity identity) {

        String provider = identity.provider().toUpperCase(Locale.ROOT);

        if (!"GOOGLE".equals(provider)) {
            throw new IllegalArgumentException(
                    "Unsupported social provider: " + provider
            );
        }

        if (identity.providerUserId() == null || identity.providerUserId().isBlank()) {
            throw new IllegalArgumentException(
                    "Provider user ID is required"
            );
        }

        return socialAccountRepository.findByProviderAndProviderUserId(
                provider, identity.providerUserId()
                ).map(
                        socialAccount -> {
                            User user = socialAccount.getUser();
                            if (!user.isActive()) {
                                throw new IllegalStateException(
                                        "This account has been disabled"
                                );
                            }

                            return user;
                        })
                .orElseGet(() -> registerNewSocialUser(identity, provider));
    }

    private User registerNewSocialUser(
            SocialIdentity identity,
            String provider
    ) {

        if (identity.email() == null || identity.email().isBlank()) {
            throw new IllegalStateException(
                    "Google account does not provide an email"
            );
        }

        if (!Boolean.TRUE.equals(identity.emailVerified())) {
            throw new IllegalStateException(
                    "Google email has not been verified"
            );
        }

        String email = identity.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException(
                    "An account with this email already exists."
            );
        }


        String fullName = identity.name();

        if (fullName == null || fullName.isBlank()) {
            fullName = email.substring(0, email.indexOf('@'));
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setHashedPassword(null);
        user.setPhone(null);
        user.setRole(UserRole.CUSTOMER);
        user.setActive(true);

        user = userRepository.save(user);


        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUser(user);
        socialAccount.setProvider(provider);
        socialAccount.setProviderUserId(identity.providerUserId());
        socialAccount.setEmail(email);

        socialAccountRepository.save(socialAccount);

        return user;
    }
}
