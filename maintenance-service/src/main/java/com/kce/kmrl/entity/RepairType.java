package com.kce.kmrl.entity;

import com.kce.kmrl.exception.InvalidRequestException;

public enum RepairType {
    ROUTINE_CHECK,
    CORRECTIVE,
    PREVENTIVE,
    COMPONENT_REPLACEMENT,
    OVERHAUL,
    EMERGENCY;

    public static RepairType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException("Repair type must not be blank.");
        }
        try {
            return RepairType.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Invalid repair type '" + value + "'. Must be one of: " +
                "ROUTINE_CHECK, CORRECTIVE, PREVENTIVE, COMPONENT_REPLACEMENT, OVERHAUL, EMERGENCY.");
        }
    }

    public boolean requiresImmediateWithdrawal() {
        return this != ROUTINE_CHECK;
    }
}