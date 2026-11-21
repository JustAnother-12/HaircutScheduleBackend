package com.j2ee.HaircutSchedule.dto.request;

import com.j2ee.HaircutSchedule.enums.UserRole;
import org.springframework.graphql.data.ArgumentValue;

public record UpdateUserInput(
        ArgumentValue<String> fullName,
        ArgumentValue<String> email,
        ArgumentValue<String> phone,
        ArgumentValue<UserRole> role
) {
}
