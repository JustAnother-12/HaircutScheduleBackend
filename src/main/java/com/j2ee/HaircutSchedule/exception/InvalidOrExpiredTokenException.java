package com.j2ee.HaircutSchedule.exception;

public class InvalidOrExpiredTokenException extends RuntimeException {
    public InvalidOrExpiredTokenException(String message) {
        super(message);
    }
}
