package com.j2ee.HaircutSchedule.event;

public record PasswordChangedEvent(
        String email,
        String fullName
) {}
