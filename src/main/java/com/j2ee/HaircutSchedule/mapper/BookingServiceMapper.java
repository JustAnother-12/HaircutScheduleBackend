package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.BookingServiceResponse;
import com.j2ee.HaircutSchedule.entity.BookingService;
import org.springframework.stereotype.Component;

@Component
public class BookingServiceMapper {
    public BookingServiceResponse toResponse(
            BookingService bookingService
    ) {
        if (bookingService == null) {
            return null;
        }

        return BookingServiceResponse.builder()
                .id(bookingService.getId())
                .serviceId(bookingService.getService().getId())
                .serviceName(bookingService.getService().getName())
                .priceAtBooking(bookingService.getPriceAtBooking())
                .build();
    }
}
