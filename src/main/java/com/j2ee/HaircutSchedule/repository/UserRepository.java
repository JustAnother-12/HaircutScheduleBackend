package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
}
