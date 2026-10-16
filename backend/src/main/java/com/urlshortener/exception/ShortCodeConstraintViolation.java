package com.urlshortener.exception;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;
import java.util.Locale;

public final class ShortCodeConstraintViolation {

    private static final String CONSTRAINT_NAME = "uk_urls_short_code";

    private ShortCodeConstraintViolation() {
    }

    public static boolean isDuplicate(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && CONSTRAINT_NAME.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && "23000".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().toLowerCase(Locale.ROOT).contains(CONSTRAINT_NAME)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
