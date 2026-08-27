package com.kce.kmrl.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/approver/tickets/{id}/decide.
 * The admin sends a decision (APPROVED / REJECTED / CHANGES_REQUESTED)
 * and optional comments.
 */
public class ApprovalRequest {

    @NotBlank(message = "decision is required")
    private String decision;

    private String comments;

    public ApprovalRequest() {}

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}
