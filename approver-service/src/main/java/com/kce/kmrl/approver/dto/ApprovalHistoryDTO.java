package com.kce.kmrl.approver.dto;

import com.kce.kmrl.approver.entity.ApprovalStatus;
import com.kce.kmrl.approver.entity.RequestType;
import java.time.LocalDateTime;

public class ApprovalHistoryDTO {

    private String id;
    private String taskId;
    private String title;
    private RequestType requestType;
    private String approverUsername;
    private ApprovalStatus decision;
    private String comments;
    private LocalDateTime timestamp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public RequestType getRequestType() { return requestType; }
    public void setRequestType(RequestType requestType) { this.requestType = requestType; }

    public String getApproverUsername() { return approverUsername; }
    public void setApproverUsername(String approverUsername) { this.approverUsername = approverUsername; }

    public ApprovalStatus getDecision() { return decision; }
    public void setDecision(ApprovalStatus decision) { this.decision = decision; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
