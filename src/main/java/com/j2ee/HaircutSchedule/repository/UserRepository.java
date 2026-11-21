package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends
        JpaRepository<User, Integer>,
        JpaSpecificationExecutor<User> {
    Optional<User> findByIdAndRole(Integer id, UserRole role);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findAllByRole(UserRole role);
}
