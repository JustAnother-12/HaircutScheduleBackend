package com.j2ee.HaircutSchedule.service;

public interface EmailService {

    void sendRegistrationConfirmation(
            String email,
            String fullName,
            String confirmationUrl
    );

    void sendPasswordReset(
            String email,
            String fullName,
            String resetUrl
    );

    void sendPasswordChanged(
            String email,
            String fullName
    );
}
