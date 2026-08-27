package com.kce.kmrl.approver.dto;

public class ApproverStatsResponse {
    private long pendingApprovals;
    private long totalApproved;
    private long totalRejected;

    public long getPendingApprovals() { return pendingApprovals; }
    public void setPendingApprovals(long pendingApprovals) { this.pendingApprovals = pendingApprovals; }

    public long getTotalApproved() { return totalApproved; }
    public void setTotalApproved(long totalApproved) { this.totalApproved = totalApproved; }

    public long getTotalRejected() { return totalRejected; }
    public void setTotalRejected(long totalRejected) { this.totalRejected = totalRejected; }
}