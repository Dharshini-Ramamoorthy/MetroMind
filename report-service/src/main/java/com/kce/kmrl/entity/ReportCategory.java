package com.kce.kmrl.entity;

import com.kce.kmrl.exception.InvalidRequestException;

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
            if (clean.equals("ALERT") || clean.equals("ALERTS")) return ALERTS;
            if (clean.equals("SAFETY")) return SAFETY;
            if (clean.equals("ALL")) return OVERALL;
            return OVERALL;
        }
    }
}
