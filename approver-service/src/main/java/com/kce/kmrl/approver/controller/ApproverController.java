package com.kce.kmrl.approver.controller;

import com.kce.kmrl.approver.dto.ApprovalDecisionRequest;
import com.kce.kmrl.approver.dto.ApprovalHistoryDTO;
import com.kce.kmrl.approver.dto.ApprovalTaskDTO;
import com.kce.kmrl.approver.dto.ApproverStatsResponse;
import com.kce.kmrl.approver.entity.ApprovalTask;
import com.kce.kmrl.approver.service.ApproverService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approver")
public class ApproverController {

    @Autowired
    private ApproverService approverService;

    @PreAuthorize("hasAnyRole('SYSTEM','SADA','OC','MDS','ADMIN')")
    @PostMapping("/tasks/submit")
    public ResponseEntity<ApprovalTaskDTO> submitTask(@RequestBody ApprovalTask task) {
        return ResponseEntity.ok(approverService.createApprovalTask(task));
    }

    @PreAuthorize("hasAnyRole('SADA','ADMIN')")
    @GetMapping("/tasks/pending")
    public ResponseEntity<List<ApprovalTaskDTO>> getPendingTasks(Authentication authentication) {
        String approverRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";
        return ResponseEntity.ok(approverService.getPendingTasks(approverRole));
    }

    @PreAuthorize("hasAnyRole('SADA','ADMIN')")
    @PostMapping("/tasks/{taskId}/decision")
    public ResponseEntity<ApprovalTaskDTO> processDecision(
            @PathVariable String taskId,
            @Valid @RequestBody ApprovalDecisionRequest decision,
            Authentication authentication) {

        String approverUser = authentication != null ? authentication.getName() : "unknown";
        String approverRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";

        return ResponseEntity.ok(approverService.processDecision(taskId, decision, approverUser, approverRole));
    }

    @PreAuthorize("hasAnyRole('SADA','ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<ApproverStatsResponse> getStats(Authentication authentication) {
        String approverRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";
        return ResponseEntity.ok(approverService.getApproverStats(approverRole));
    }

    @PreAuthorize("hasAnyRole('SADA','ADMIN','MDS','OC','SYSTEM')")
    @GetMapping("/history")
    public ResponseEntity<List<ApprovalHistoryDTO>> getHistory(Authentication authentication) {
        String approverUser = authentication != null ? authentication.getName() : "unknown";
        String approverRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";
        return ResponseEntity.ok(approverService.getApprovalHistory(approverRole, approverUser));
    }
}