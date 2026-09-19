package com.kce.kmrl.entity;

import com.kce.kmrl.exception.InvalidRequestException;

import java.util.Arrays;

public enum ReportCategory {
    FLEET,
    SCHEDULE,
    MAINTENANCE,
    ALERTS,
    SAFETY,
    OVERALL;

    public static ReportCategory fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException("Category must not be blank.");
        }
        String clean = value.trim().toUpperCase();
        try {
            return ReportCategory.valueOf(clean);
        } catch (IllegalArgumentException ex) {
            if (clean.equals("ALERT")) return ALERTS;
            if (clean.equals("ALL")) return OVERALL;
            throw new InvalidRequestException("Invalid category: '" + value + "'. Valid categories: " + Arrays.toString(values()));
        }
    }
}
