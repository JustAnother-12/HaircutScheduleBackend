package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findAllByOrderByBookingDateDescStartTimeAsc();
}
