package com.j2ee.HaircutSchedule.service;

import com.j2ee.HaircutSchedule.service.impl.SmtpEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmtpEmailServiceTest {

    private static final String FROM = "noreply@example.com";

    private static final String TO = "customer@example.com";

    private static final String FULL_NAME = "Test Customer";

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SmtpEmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                emailService,
                "from",
                FROM
        );
    }

    private SimpleMailMessage captureMessage() {
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        return captor.getValue();
    }

    @Test
    void sendRegistrationConfirmation_success() {
        // Arrange
        String confirmationUrl =
                "http://localhost:3000/confirmation/test-token";

        // Execute
        emailService.sendRegistrationConfirmation(
                TO,
                FULL_NAME,
                confirmationUrl
        );

        // Assert
        SimpleMailMessage message = captureMessage();

        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(TO);

        assertThat(message.getSubject())
                .isEqualTo("Confirm your registration");

        assertThat(message.getText())
                .contains("Hello " + FULL_NAME)
                .contains(confirmationUrl)
                .contains("This link expires in 24 hours.")
                .contains("If you did not request this registration");
    }


    @Test
    void sendPasswordReset_success() {
        // Arrange
        String resetUrl =
                "http://localhost:3000/reset-password/reset-token";

        // Execute
        emailService.sendPasswordReset(
                TO,
                FULL_NAME,
                resetUrl
        );

        // Assert
        SimpleMailMessage message = captureMessage();

        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(TO);

        assertThat(message.getSubject())
                .isEqualTo("Reset your password");

        assertThat(message.getText())
                .contains("Hello " + FULL_NAME)
                .contains(resetUrl)
                .contains("This link expires in 15 minutes.")
                .contains("If you did not request a password reset");

        // The email must not contain an actual password.
        assertThat(message.getText())
                .doesNotContain("NewPassword123");
    }

    @Test
    void sendPasswordChanged_success() {
        // Arrange
        // The notification does not receive a password or token.

        // Execute
        emailService.sendPasswordChanged(
                TO,
                FULL_NAME
        );

        // Assert
        SimpleMailMessage message = captureMessage();

        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(TO);

        assertThat(message.getSubject())
                .isEqualTo("Your password has been changed");

        assertThat(message.getText())
                .contains("Hello " + FULL_NAME)
                .contains("Your account password has been changed successfully.")
                .contains("please contact support");

        assertThat(message.getText())
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }

    @Test
    void sendWithoutFromAddress_throwsException() {
        // Arrange
        ReflectionTestUtils.setField(
                emailService,
                "from",
                "   "
        );

        // Execute & Assert
        assertThatThrownBy(() ->
                emailService.sendRegistrationConfirmation(
                        TO,
                        FULL_NAME,
                        "http://localhost:3000/confirmation/token"
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MAIL_FROM is not configured");

        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailure_propagatesException() {
        // Arrange
        MailSendException smtpException =
                new MailSendException("SMTP server unavailable");

        doThrow(smtpException)
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        // Execute & Assert
        assertThatThrownBy(() ->
                emailService.sendPasswordChanged(
                        TO,
                        FULL_NAME
                )
        )
                .isSameAs(smtpException);

        verify(mailSender)
                .send(any(SimpleMailMessage.class));
    }
}
