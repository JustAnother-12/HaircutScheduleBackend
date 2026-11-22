package com.j2ee.HaircutSchedule.controller;

import com.j2ee.HaircutSchedule.controller.graphql.BookingController;
import com.j2ee.HaircutSchedule.dto.request.CreateBookingInput;
import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.entity.User;
import com.j2ee.HaircutSchedule.enums.UserRole;
import com.j2ee.HaircutSchedule.security.CustomUserDetails;
import com.j2ee.HaircutSchedule.service.BookingService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private CustomUserDetails customerPrincipal() {
        User customer = User.builder()
                .id(1)
                .fullName("Test customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        return new CustomUserDetails(customer);
    }

    @Test
    void createBooking_extractsBarberIdFromInput() {
        CustomUserDetails principal = customerPrincipal();

        CreateBookingInput input = new CreateBookingInput(
                "2",
                LocalDate.of(2026, 12, 1),
                LocalTime.of(9, 0),
                List.of(1, 2)
        );

        BookingResponse expected =
                BookingResponse.builder()
                        .id(100)
                        .build();

        when(bookingService.createBooking(
                principal.getUser(),
                2,
                input.bookingDate(),
                input.startTime(),
                input.serviceIds()
        )).thenReturn(expected);

        BookingResponse actual = bookingController.createBooking(input, principal);

        assertThat(actual.id()).isEqualTo(100);

        verify(bookingService).createBooking(
                principal.getUser(),
                2,
                input.bookingDate(),
                input.startTime(),
                input.serviceIds()
        );
    }

    @Test
    void createBooking_serviceRejectsRequest_propagatesException() {
        CustomUserDetails principal = customerPrincipal();

        CreateBookingInput input = new CreateBookingInput(
                "2",
                LocalDate.of(2026, 12, 1),
                LocalTime.of(9, 0),
                List.of(1)
        );

        when(bookingService.createBooking(
                any(User.class),
                eq(2),
                any(LocalDate.class),
                any(LocalTime.class),
                anyList()
        )).thenThrow(
                new IllegalArgumentException("Invalid booking time")
        );

        assertThatThrownBy(() ->
                bookingController.createBooking(input, principal)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
