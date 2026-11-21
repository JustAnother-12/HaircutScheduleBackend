package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Integer> {
    List<Session> findAllByUserAndRevokedAtIsNull(User user);
    Optional<Session> findSessionByRefreshTokenHash(String refreshTokenHash);
    List<Session> findAllByUserAndRevokedAtIsNullAndExpiredAtAfter(
            User user,
            Instant now
    );
}
