package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.Session;
import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Integer> {
    Optional<Session> findSessionByRefreshTokenHash(String refreshTokenHash);
    Optional<Session> findSessionByUser(User user);
}
