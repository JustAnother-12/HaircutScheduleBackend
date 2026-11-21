package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.request.CreateBookingInput;
import com.j2ee.HaircutSchedule.dto.request.UpdateBookingInput;
import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.dto.response.PageResponse;
import com.j2ee.HaircutSchedule.enums.BookingStatus;
import com.j2ee.HaircutSchedule.security.CustomUserDetails;
import com.j2ee.HaircutSchedule.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.j2ee.HaircutSchedule.util.Helpers.parseId;

@Controller
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<BookingResponse> bookings(
            @Argument Integer page,
            @Argument Integer limit,
            @Argument LocalDate bookingDate,
            @Argument BookingStatus status,
            @Argument String barberId,
            @Argument String customerId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.getAllBookings(
                principal.getUser(),
                page,
                limit,
                bookingDate,
                status,
                barberId == null ? null : parseId(barberId),
                customerId == null ? null : parseId(customerId)
        );
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public BookingResponse booking(
            @Argument String id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.getBookingById(
                principal.getUser(),
                parseId(id)
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public BookingResponse createBooking(
            @Argument String barberId,
            @Argument CreateBookingInput input,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.createBooking(
                principal.getUser(),
                parseId(barberId),
                input.bookingDate(),
                input.startTime(),
                input.serviceIds()
        );
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public BookingResponse cancelBooking(
            @Argument String bookingId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.updateBookingStatus(
                principal.getUser(),
                parseId(bookingId),
                BookingStatus.CANCELLED
        );
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public BookingResponse rescheduleBooking(
            @Argument String bookingId,
            @Argument UpdateBookingInput input,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.updateBookingSchedule(
                principal.getUser(),
                parseId(bookingId),
                input.bookingDate(),
                input.startTime()
        );
    }

    @MutationMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'BARBER')")
    public BookingResponse updateBookingStatus(
            @Argument String bookingId,
            @Argument BookingStatus status,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.updateBookingStatus(
                principal.getUser(),
                parseId(bookingId),
                status
        );
    }
}
