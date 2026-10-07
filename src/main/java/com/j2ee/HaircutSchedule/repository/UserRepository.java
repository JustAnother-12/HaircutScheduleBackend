package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
