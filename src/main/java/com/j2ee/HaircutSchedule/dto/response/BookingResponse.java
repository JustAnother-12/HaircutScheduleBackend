package com.j2ee.HaircutSchedule.dto.response;

import com.j2ee.HaircutSchedule.enums.BookingStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record BookingResponse(
        Integer id,
        Integer customerId,
        Integer barberId,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal totalPrice,
        BookingStatus status,
        OffsetDateTime createdAt,
        List<BookingServiceResponse> services
) {
}
