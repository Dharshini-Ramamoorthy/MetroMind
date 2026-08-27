package com.kce.kmrl.dto;

import java.time.Instant;

/**
 * Response DTO for maintenance tickets as seen by the approver panel.
 */
public class TicketResponse {

    private final String id;
    private final String trainNumber;
    private final String description;
    private final String priority;
    private final String status;
    private final String createdBy;
    private final String approverComments;
    private final Instant createdAt;

    public TicketResponse(String id, String trainNumber, String description, String priority,
                          String status, String createdBy, String approverComments, Instant createdAt) {
        this.id = id;
        this.trainNumber = trainNumber;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.createdBy = createdBy;
        this.approverComments = approverComments;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getTrainNumber() { return trainNumber; }
    public String getDescription() { return description; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getApproverComments() { return approverComments; }
    public Instant getCreatedAt() { return createdAt; }
}
