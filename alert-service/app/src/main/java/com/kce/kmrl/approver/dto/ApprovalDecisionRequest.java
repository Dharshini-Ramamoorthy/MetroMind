package com.kce.kmrl.approver.dto;

import com.kce.kmrl.approver.entity.ApprovalStatus;

public class ApprovalDecisionRequest {
    private ApprovalStatus decision;
    private String comments;

    public ApprovalStatus getDecision() { return decision; }
    public void setDecision(ApprovalStatus decision) { this.decision = decision; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}