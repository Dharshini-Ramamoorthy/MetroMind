package com.kce.kmrl.approver.controller;

import com.kce.kmrl.approver.dto.ApprovalDecisionRequest;
import com.kce.kmrl.approver.dto.ApprovalTaskDTO;
import com.kce.kmrl.approver.dto.ApproverStatsResponse;
import com.kce.kmrl.approver.entity.ApprovalHistory;
import com.kce.kmrl.approver.entity.ApprovalTask;
import com.kce.kmrl.approver.service.ApproverService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approver")
public class ApproverController {

    @Autowired
    private ApproverService approverService;

    @PostMapping("/tasks/submit")
    public ResponseEntity<ApprovalTaskDTO> submitTask(@RequestBody ApprovalTask task) {
        return ResponseEntity.ok(approverService.createApprovalTask(task));
    }

    @PreAuthorize("hasAnyRole('OC', 'SADA')")
    @GetMapping("/tasks/pending")
    public ResponseEntity<List<ApprovalTaskDTO>> getPendingTasks(Authentication authentication) {
        String approverRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElseThrow(() -> new IllegalStateException("Authenticated approver has no role."));
        return ResponseEntity.ok(approverService.getPendingTasks(approverRole));
    }

    @PreAuthorize("hasAnyRole('OC', 'SADA')")
    @PostMapping("/tasks/{taskId}/decision")
    public ResponseEntity<ApprovalTaskDTO> processDecision(
            @PathVariable String taskId,
            @RequestBody ApprovalDecisionRequest decision,
            Authentication authentication) {

        String approverUser = authentication.getName();
        String approverRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .orElseThrow(() -> new IllegalStateException("Authenticated approver has no role."));

        return ResponseEntity.ok(approverService.processDecision(taskId, decision, approverUser, approverRole));
    }
    @PreAuthorize("hasAnyRole(\'OC\', \'SADA\')")

    @GetMapping("/stats")
    public ResponseEntity<ApproverStatsResponse> getStats() {
        return ResponseEntity.ok(approverService.getApproverStats());
    }
    @PreAuthorize("hasAnyRole(\'OC\', \'SADA\')")

    @GetMapping("/history")
    public ResponseEntity<List<ApprovalHistory>> getHistory() {
        return ResponseEntity.ok(approverService.getApprovalHistory());
    }
}
