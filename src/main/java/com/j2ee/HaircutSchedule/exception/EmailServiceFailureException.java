package com.j2ee.HaircutSchedule.exception;

public class EmailServiceFailureException extends RuntimeException {
    public EmailServiceFailureException(String message, Exception e) {
        super(message);
    }
}
