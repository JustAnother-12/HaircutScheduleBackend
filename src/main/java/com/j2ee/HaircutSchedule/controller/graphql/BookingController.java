package com.j2ee.HaircutSchedule.controller.graphql;

import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingController {
    private final BookingService bookingService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @QueryMapping
    public List<BookingResponse> bookings() {
        return bookingService.getAllBookings();
    }
}
