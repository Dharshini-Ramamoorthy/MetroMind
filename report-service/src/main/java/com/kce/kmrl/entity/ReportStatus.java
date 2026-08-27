package com.kce.kmrl.entity;

import com.kce.kmrl.exception.InvalidRequestException;

public enum ReportStatus {
    FINAL,
    PROCESSING,
    ARCHIVED;

    public static ReportStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException("Status must not be blank.");
        }
        try {
            return ReportStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Invalid status '" + value + "'. Must be one of: FINAL, PROCESSING, ARCHIVED."
            );
        }
    }
}
