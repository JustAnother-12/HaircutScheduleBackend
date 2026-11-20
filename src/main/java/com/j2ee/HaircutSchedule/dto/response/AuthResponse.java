package com.j2ee.HaircutSchedule.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@JsonPropertyOrder({
        "accessToken",
        "refreshToken",
        "accessExpiresAt",
        "refreshExpiresAt",
        "tokenType"
})
public record AuthResponse(
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        String tokenType
) {}
