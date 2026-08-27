package com.kce.kmrl.dto;

import java.time.Instant;

/**
 * Response DTO for the approval history table in the approver panel.
 */
public class ApprovalHistoryResponse {

    private final String id;
    private final String ticketId;
    private final String requestId;
    private final String type;
    private final String decision;
    private final String approver;
    private final String comments;
    private final Instant timestamp;

    public ApprovalHistoryResponse(String id, String ticketId, String requestId, String type,
                                    String decision, String approver, String comments, Instant timestamp) {
        this.id = id;
        this.ticketId = ticketId;
        this.requestId = requestId;
        this.type = type;
        this.decision = decision;
        this.approver = approver;
        this.comments = comments;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getTicketId() { return ticketId; }
    public String getRequestId() { return requestId; }
    public String getType() { return type; }
    public String getDecision() { return decision; }
    public String getApprover() { return approver; }
    public String getComments() { return comments; }
    public Instant getTimestamp() { return timestamp; }
}
