package com.kce.kmrl.alert.entity;

public enum Severity {
    SEV3, SEV2, SEV1;

    public static Severity fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Severity must not be null");
        }
        try {
            return Severity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown severity '" + value + "'. Must be one of: SEV3, SEV2, SEV1");
        }
    }
}
