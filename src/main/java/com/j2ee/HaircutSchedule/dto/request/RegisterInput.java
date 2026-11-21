package com.j2ee.HaircutSchedule.dto.request;

public record RegisterInput(
        String fullName,
        String email,
        String phone,
        String password
) {}
