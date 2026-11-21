package com.j2ee.HaircutSchedule.exception;

public class ResourceAlreadyExistedException extends RuntimeException {
    public ResourceAlreadyExistedException(String message) {
        super(message);
    }
}
