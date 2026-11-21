package com.j2ee.HaircutSchedule.dto.request;

public record PendingRegistration(
        String fullName,
        String email,
        String phone,
        String encodedPassword
) {}
