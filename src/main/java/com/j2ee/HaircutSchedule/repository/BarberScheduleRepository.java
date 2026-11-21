package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.BarberSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BarberScheduleRepository extends JpaRepository<BarberSchedule, Integer> {
    List<BarberSchedule> findByBarber_IdOrderByDateOfWeekAscStartTimeAsc(
            Integer barberId
    );

    List<BarberSchedule> findByBarber_IdAndDateOfWeekOrderByStartTimeAsc(
            Integer barberId,
            Integer dateOfWeek
    );

    List<BarberSchedule> findByBarber_IdAndDateOfWeekAndIsOffOrderByStartTimeAsc(
            Integer barberId,
            Integer dateOfWeek,
            boolean isOff
    );
}
