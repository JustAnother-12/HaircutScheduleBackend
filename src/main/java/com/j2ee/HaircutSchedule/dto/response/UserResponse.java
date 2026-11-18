package com.j2ee.HaircutSchedule.dto.response;

import com.j2ee.HaircutSchedule.enums.UserRole;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record UserResponse(
        Integer id,
        String fullName,
        String email,
        String phone,
        UserRole role,
        boolean isActive,
        OffsetDateTime createdAt
) {
}
