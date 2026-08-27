package com.kce.kmrl.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Read-only view of the maintenance tickets stored in the shared
 * "maintenance" collection (same MongoDB collection that maintenance-service
 * writes to). The approver-service reads tickets from here and writes
 * approval decisions back (status + approverComments).
 */
@Document(collection = "maintenance")
public class MaintenanceTicket {

    @Id
    private String id;

    private String trainNumber;
    private String description;
    private String priority;  // LOW, MEDIUM, HIGH, CRITICAL
    private String status;    // OPEN, IN_PROGRESS, COMPLETED, APPROVED, REJECTED
    private String createdBy;
    private String approverComments;
    private Instant createdAt;

    public MaintenanceTicket() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getApproverComments() { return approverComments; }
    public void setApproverComments(String approverComments) { this.approverComments = approverComments; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
