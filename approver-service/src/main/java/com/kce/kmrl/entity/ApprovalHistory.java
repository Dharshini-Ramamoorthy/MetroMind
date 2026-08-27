package com.kce.kmrl.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks every approval decision made in the approver panel.
 * Stored in a separate "approval_history" collection in maintenance_db.
 */
@Document(collection = "approval_history")
public class ApprovalHistory {

    @Id
    private String id;

    private String ticketId;       // references MaintenanceTicket.id
    private String requestId;      // display ID (e.g. REQ-4463)
    private String type;           // Schedule Change, Induction Override, Alert Resolution
    private String decision;       // APPROVED, REJECTED, CHANGES_REQUESTED
    private String approver;       // who made the decision
    private String comments;       // optional approver comments
    private Instant timestamp;

    public ApprovalHistory() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
