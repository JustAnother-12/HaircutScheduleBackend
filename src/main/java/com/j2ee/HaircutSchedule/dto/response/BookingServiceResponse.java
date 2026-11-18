package com.j2ee.HaircutSchedule.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BookingServiceResponse(
        Integer id,
        Integer serviceId,
        String serviceName,
        BigDecimal priceAtBooking
) {
}
