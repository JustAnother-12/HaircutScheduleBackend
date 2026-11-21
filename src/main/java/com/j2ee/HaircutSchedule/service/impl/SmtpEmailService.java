package com.j2ee.HaircutSchedule.service.impl;

import com.j2ee.HaircutSchedule.service.EmailService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Override
    public void sendRegistrationConfirmation(
            String email,
            String fullName,
            String confirmationUrl
    ) {
        String subject = "Confirm your registration";

        String body = """
                Hello %s,

                Thank you for registering.

                Please open the following link to confirm your account:

                %s

                This link expires in 24 hours.

                If you did not request this registration, please ignore this email.
                """.formatted(fullName, confirmationUrl);

        send(email, subject, body);
    }

    @Override
    public void sendPasswordReset(
            String email,
            String fullName,
            String resetUrl
    ) {
        String subject = "Reset your password";

        String body = """
                Hello %s,

                We received a request to reset your password.

                Please open the following link:

                %s

                This link expires in 15 minutes.

                If you did not request a password reset, please ignore this email.
                """.formatted(fullName, resetUrl);

        send(email, subject, body);
    }

    @Override
    public void sendPasswordChanged(
            String email,
            String fullName
    ) {
        String subject = "Your password has been changed";

        String body = """
                Hello %s,

                Your account password has been changed successfully.

                If you did not perform this action, please contact support.
                """.formatted(fullName);

        send(email, subject, body);
    }

    private void send(
            String recipient,
            String subject,
            String body
    ) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "MAIL_FROM is not configured"
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}
