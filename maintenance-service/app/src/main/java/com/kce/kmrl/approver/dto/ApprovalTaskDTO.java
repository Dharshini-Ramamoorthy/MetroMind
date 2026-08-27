package com.kce.kmrl.approver.dto;

import java.time.LocalDateTime;

import com.kce.kmrl.approver.entity.ApprovalStatus;
import com.kce.kmrl.approver.entity.RequestType;

public class ApprovalTaskDTO {
    private String taskId;
    private String targetEntityId;
    private RequestType requestType;
    private String title;
    private String description;
    private String priority;
    private ApprovalStatus status;
    private String requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime decidedAt;
    private String decidedBy;
    private String cascadeStatus;
    private String cascadeError;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }

    public RequestType getRequestType() { return requestType; }
    public void setRequestType(RequestType requestType) { this.requestType = requestType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public String getCascadeStatus() { return cascadeStatus; }
    public void setCascadeStatus(String cascadeStatus) { this.cascadeStatus = cascadeStatus; }

    public String getCascadeError() { return cascadeError; }
    public void setCascadeError(String cascadeError) { this.cascadeError = cascadeError; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}