package com.j2ee.HaircutSchedule.event;

import com.j2ee.HaircutSchedule.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordChangedEmailListener {

    private final EmailService emailService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(PasswordChangedEvent event) {
        try {
            emailService.sendPasswordChanged(
                    event.email(),
                    event.fullName()
            );

        } catch (RuntimeException ex) {
            log.error(
                    "Could not send password changed notification"
            );
        }
    }
}
