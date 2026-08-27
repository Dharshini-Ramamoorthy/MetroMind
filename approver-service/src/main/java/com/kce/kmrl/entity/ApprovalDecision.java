package com.kce.kmrl.entity;

import com.kce.kmrl.exception.InvalidRequestException;

/**
 * The three possible decisions an approver can make on a pending ticket.
 */
public enum ApprovalDecision {
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED;

    public static ApprovalDecision fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException("Decision must not be blank.");
        }
        try {
            return ApprovalDecision.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                "Invalid decision '" + value + "'. Must be one of: APPROVED, REJECTED, CHANGES_REQUESTED."
            );
        }
    }
}
