package com.j2ee.HaircutSchedule.util;

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
}
