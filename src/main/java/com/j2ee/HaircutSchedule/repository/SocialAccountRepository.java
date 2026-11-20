package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Integer> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );

    boolean existsByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );

    List<SocialAccount> findAllByUserId(Integer userId);
}
