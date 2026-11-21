package com.j2ee.HaircutSchedule.dto.request;

import org.springframework.graphql.data.ArgumentValue;

public record UpdatePasswordInput(
        ArgumentValue<String> oldPassword,
        ArgumentValue<String> newPassword
) {
}
