package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.dto.response.BookingResponse;

import java.util.List;

public interface BookingService {
    List<BookingResponse> getAllBookings();

    BookingResponse getBookingById(Integer id);
}
