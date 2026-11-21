package com.j2ee.HaircutSchedule.dto.request;

import org.springframework.graphql.data.ArgumentValue;

public record UpdateProfileInput(
        ArgumentValue<String> fullName,
        ArgumentValue<String> email,
        ArgumentValue<String> phone
) {
}
