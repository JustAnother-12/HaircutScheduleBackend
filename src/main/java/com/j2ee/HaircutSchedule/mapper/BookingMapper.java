package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.BookingResponse;
import com.j2ee.HaircutSchedule.entity.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class BookingMapper {
    private final BookingServiceMapper bookingServiceMapper;

    public BookingResponse toResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        return BookingResponse.builder()
                .id(booking.getId())
                .customerId(booking.getCustomer().getId())
                .barberId(booking.getBarber().getId())
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .totalPrice(booking.getTotalPrice())
                .status(booking.getStatus())
                .createdAt(
                        booking.getCreatedAt() == null
                        ? null : booking.getCreatedAt().atOffset(ZoneOffset.UTC)
                )
                .services(
                        booking.getBookingServices()
                        .stream()
                        .map(bookingServiceMapper::toResponse)
                        .toList()
                )
                .build();
    }
}
