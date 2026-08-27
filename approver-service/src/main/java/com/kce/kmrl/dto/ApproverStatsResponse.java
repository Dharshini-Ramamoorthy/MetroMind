package com.kce.kmrl.dto;

/**
 * Dashboard stats for the approver panel header cards.
 */
public class ApproverStatsResponse {

    private final long pendingCount;
    private final long approvedCount;
    private final long rejectedCount;

    public ApproverStatsResponse(long pendingCount, long approvedCount, long rejectedCount) {
        this.pendingCount = pendingCount;
        this.approvedCount = approvedCount;
        this.rejectedCount = rejectedCount;
    }

    public long getPendingCount() { return pendingCount; }
    public long getApprovedCount() { return approvedCount; }
    public long getRejectedCount() { return rejectedCount; }
}
