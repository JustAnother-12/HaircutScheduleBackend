package com.j2ee.HaircutSchedule.dto.request;

import java.math.BigDecimal;

public record CreateServiceInput(
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes
) {}
