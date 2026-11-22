
package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.entity.Booking;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.mapper.BookingMapper;
import com.j2ee.HaircutSchedule.repository.BarberScheduleRepository;
import com.j2ee.HaircutSchedule.repository.BookingRepository;
import com.j2ee.HaircutSchedule.repository.ServiceRepository;
import com.j2ee.HaircutSchedule.repository.UserRepository;
import com.j2ee.HaircutSchedule.service.impl.BookingServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BarberScheduleRepository scheduleRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingMapper bookingMapper;
    @Mock private Clock clock;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User user(Integer id, UserRole role) {
        return User.builder()
                .id(id)
                .fullName("Test user " + id)
                .email("user" + id + "@example.com")
                .phone("0901234567")
                .hashedPassword("password-hash")
                .role(role)
                .isActive(true)
                .build();
    }

    private Booking booking(BookingStatus status) {
        return Booking.builder()
                .id(100)
                .customer(user(1, UserRole.CUSTOMER))
                .barber(user(2, UserRole.BARBER))
                .bookingDate(LocalDate.of(2026, 12, 1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .status(status)
                .build();
    }

    @Test
    void assignedBarber_canConfirmBooking() {
        User barber = user(2, UserRole.BARBER);
        Booking booking = booking(BookingStatus.PENDING);

        when(bookingRepository.findById(100))
                .thenReturn(Optional.of(booking));

        when(bookingMapper.toResponse(booking))
                .thenReturn(
                        BookingResponse.builder()
                                .id(100)
                                .status(BookingStatus.CONFIRMED)
                                .build()
                );

        bookingService.updateBookingStatus(
                barber,
                100,
                BookingStatus.CONFIRMED
        );

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void admin_canCompleteConfirmedBooking() {
        User admin = user(3, UserRole.ADMIN);
        Booking booking = booking(BookingStatus.CONFIRMED);

        when(bookingRepository.findById(100))
                .thenReturn(Optional.of(booking));

        when(bookingMapper.toResponse(booking))
                .thenReturn(
                        BookingResponse.builder()
                                .id(100)
                                .status(BookingStatus.COMPLETED)
                                .build()
                );

        bookingService.updateBookingStatus(
                admin,
                100,
                BookingStatus.COMPLETED
        );

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void anotherBarber_cannotUpdateBooking() {
        User anotherBarber = user(99, UserRole.BARBER);
        Booking booking = booking(BookingStatus.PENDING);

        when(bookingRepository.findById(100))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() ->
                bookingService.updateBookingStatus(
                        anotherBarber,
                        100,
                        BookingStatus.CONFIRMED
                )
        ).isInstanceOf(AccessDeniedException.class);

        verify(bookingMapper, never()).toResponse(any());
    }

    @Test
    void completedBooking_cannotBeCancelled() {
        User admin = user(3, UserRole.ADMIN);
        Booking booking = booking(BookingStatus.COMPLETED);

        when(bookingRepository.findById(100))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() ->
                bookingService.cancelBooking(
                        admin,
                        100
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }
}

