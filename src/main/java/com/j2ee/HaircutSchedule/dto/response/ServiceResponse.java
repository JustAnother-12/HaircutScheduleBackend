package com.j2ee.HaircutSchedule.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ServiceResponse(
        Integer id,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        boolean isActive
) {
}
