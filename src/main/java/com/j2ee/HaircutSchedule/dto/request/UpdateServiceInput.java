package com.j2ee.HaircutSchedule.dto.request;

import org.springframework.graphql.data.ArgumentValue;

import java.math.BigDecimal;

public record UpdateServiceInput(
        ArgumentValue<String> name,
        ArgumentValue<String> description,
        ArgumentValue<BigDecimal> price,
        ArgumentValue<Integer> durationMinutes,
        ArgumentValue<Boolean> isActive
) {
}
