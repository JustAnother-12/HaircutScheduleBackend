package com.j2ee.HaircutSchedule.repository;

import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findAllByOrderByBookingDateDescStartTimeAsc();
    List<Booking> findAllByBookingDateAndBarber_IdAndStatusIn(
            LocalDate bookingDate,
            Integer barberId,
            Collection<BookingStatus> statuses
    );
}
