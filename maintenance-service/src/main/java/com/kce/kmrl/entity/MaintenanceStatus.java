package com.kce.kmrl.entity;

import com.kce.kmrl.exception.InvalidRequestException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public enum MaintenanceStatus {
    OPEN,
    SCHEDULED,
    IN_PROGRESS,
    PENDING_CLOSURE,
    COMPLETED,
    REJECTED,
    CANCELLED;

    private static final Map<MaintenanceStatus, Set<MaintenanceStatus>> VALID_TRANSITIONS =
            new EnumMap<>(MaintenanceStatus.class);

    static {
        VALID_TRANSITIONS.put(OPEN,            Set.of(IN_PROGRESS, REJECTED));
        VALID_TRANSITIONS.put(SCHEDULED,       Set.of(IN_PROGRESS, CANCELLED));
        VALID_TRANSITIONS.put(IN_PROGRESS,     Set.of(PENDING_CLOSURE, CANCELLED));
        VALID_TRANSITIONS.put(PENDING_CLOSURE, Set.of(COMPLETED, IN_PROGRESS));
        VALID_TRANSITIONS.put(COMPLETED,       Set.of());
        VALID_TRANSITIONS.put(REJECTED,        Set.of());
        VALID_TRANSITIONS.put(CANCELLED,       Set.of());
    }

    public static MaintenanceStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException("Status must not be blank.");
        }
        try {
            return MaintenanceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Invalid status '" + value + "'. Must be one of: " +
                "OPEN, SCHEDULED, IN_PROGRESS, PENDING_CLOSURE, COMPLETED, REJECTED, CANCELLED.");
        }
    }

    public static void assertValidTransition(MaintenanceStatus current, MaintenanceStatus next) {
        if (current == next) return;
        if (!VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new InvalidRequestException(
                "Illegal status transition: " + current + " -> " + next);
        }
    }
}