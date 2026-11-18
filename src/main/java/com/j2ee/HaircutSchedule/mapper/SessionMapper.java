package com.j2ee.HaircutSchedule.mapper;

import com.j2ee.HaircutSchedule.dto.response.SessionResponse;
import com.j2ee.HaircutSchedule.entity.Session;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class SessionMapper {
    public SessionResponse toResponse(Session session) {
        if (session == null) {
            return null;
        }

        return SessionResponse.builder()
                .id(session.getId())
                .userId(session.getUser().getId())
                .lastActivityAt(session.getLastActivityAt())
                .expiredAt(session.getExpiredAt())
                .revokedAt(session.getRevokedAt())
                .createdAt(session.getCreatedAt())
                .build();
    }

    private OffsetDateTime toUtc(Instant instant) {
        return instant == null
                ? null
                : instant.atOffset(ZoneOffset.UTC);
    }
}
