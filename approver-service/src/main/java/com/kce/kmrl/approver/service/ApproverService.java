package com.kce.kmrl.approver.service;

import com.kce.kmrl.approver.dto.ApprovalDecisionRequest;
import com.kce.kmrl.approver.dto.ApprovalHistoryDTO;
import com.kce.kmrl.approver.dto.ApprovalTaskDTO;
import com.kce.kmrl.approver.dto.ApproverStatsResponse;
import com.kce.kmrl.approver.entity.ApprovalTask;

import java.util.List;

public interface ApproverService {
    ApprovalTaskDTO createApprovalTask(ApprovalTask task);
    List<ApprovalTaskDTO> getPendingTasks(String approverRole);
    ApprovalTaskDTO processDecision(String taskId, ApprovalDecisionRequest decision, String approverUser, String approverRole);
    ApproverStatsResponse getApproverStats(String approverRole);
    List<ApprovalHistoryDTO> getApprovalHistory(String approverRole, String approverUser);
}