package com.j2ee.HaircutSchedule.util;

import org.springframework.graphql.data.ArgumentValue;

public class Helpers {
    public static Integer parseId(String id) {

        try {
            Integer parsedId = Integer.valueOf(id);
            if (parsedId <= 0) {
                throw new IllegalArgumentException(
                        "ID must be greater than zero"
                );
            }
            return parsedId;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid ID: " + id
            );
        }
    }

    public static void validateArgumentStringNotBlankOrNull(String argument, String message){
        if (argument == null || argument.isBlank()) {
            throw new IllegalArgumentException(
                    message + " cannot be blank"
            );
        }
    }
}
