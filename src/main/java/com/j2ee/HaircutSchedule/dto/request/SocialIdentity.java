package com.j2ee.HaircutSchedule.dto.request;

public record SocialIdentity(
        String provider,
        String providerUserId,
        String email,
        String name,
        Boolean emailVerified
) {}
