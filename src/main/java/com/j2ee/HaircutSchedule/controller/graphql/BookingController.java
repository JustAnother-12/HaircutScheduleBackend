package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.request.CreateBookingInput;
import com.j2ee.HaircutSchedule.dto.request.RescheduleBookingInput;
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

import java.time.LocalDate;

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
            @Argument CreateBookingInput input,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return bookingService.createBooking(
                principal.getUser(),
                parseId(input.barberId()),
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
        return bookingService.cancelBooking(
                principal.getUser(),
                parseId(bookingId)
        );
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public BookingResponse rescheduleBooking(
            @Argument String bookingId,
            @Argument RescheduleBookingInput input,
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
