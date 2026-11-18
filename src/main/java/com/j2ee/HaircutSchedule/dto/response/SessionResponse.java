package com.j2ee.HaircutSchedule.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record SessionResponse(
        Integer id,
        Integer userId,
        Instant lastActivityAt,
        Instant expiredAt,
        Instant revokedAt,
        Instant createdAt
) {}
