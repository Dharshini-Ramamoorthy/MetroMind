package com.kce.kmrl.controller;

import com.kce.kmrl.dto.*;
import com.kce.kmrl.service.ApproverService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the Approver Panel — admin-only.
 *
 * All endpoints require SADA (System Admin) role. The approver panel
 * allows admins to:
 *  - View pending maintenance tickets that need approval
 *  - Approve, reject, or request changes on tickets
 *  - View approval history
 *  - View dashboard statistics
 */
@RestController
@RequestMapping("/api/v1/approver")
public class ApproverController {

    private final ApproverService approverService;

    @Autowired
    public ApproverController(ApproverService approverService) {
        this.approverService = approverService;
    }

    /**
     * GET /api/v1/approver/tickets/pending
     * Returns all tickets awaiting admin approval (OPEN + IN_PROGRESS).
     */
    @PreAuthorize("hasRole('SADA')")
    @GetMapping("/tickets/pending")
    public ResponseEntity<List<TicketResponse>> getPendingTickets() {
        return ResponseEntity.ok(approverService.getPendingTickets());
    }

    /**
     * GET /api/v1/approver/tickets
     * Returns all tickets regardless of status.
     */
    @PreAuthorize("hasRole('SADA')")
    @GetMapping("/tickets")
    public ResponseEntity<List<TicketResponse>> getAllTickets() {
        return ResponseEntity.ok(approverService.getAllTickets());
    }

    /**
     * GET /api/v1/approver/tickets/{id}
     * Returns a single ticket by id.
     */
    @PreAuthorize("hasRole('SADA')")
    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable String id) {
        return ResponseEntity.ok(approverService.getTicketById(id));
    }

    /**
     * POST /api/v1/approver/tickets/{id}/decide
     * Process an approval decision on a ticket.
     * Body: { "decision": "APPROVED|REJECTED|CHANGES_REQUESTED", "comments": "..." }
     */
    @PreAuthorize("hasRole('SADA')")
    @PostMapping("/tickets/{id}/decide")
    public ResponseEntity<TicketResponse> processDecision(
            @PathVariable String id,
            @Valid @RequestBody ApprovalRequest request,
            Authentication authentication) {
        String approverName = authentication != null ? authentication.getName() : "Admin";
        TicketResponse result = approverService.processDecision(id, request, approverName);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/approver/history
     * Returns the approval history, newest first.
     */
    @PreAuthorize("hasRole('SADA')")
    @GetMapping("/history")
    public ResponseEntity<List<ApprovalHistoryResponse>> getApprovalHistory() {
        return ResponseEntity.ok(approverService.getApprovalHistory());
    }

    /**
     * GET /api/v1/approver/stats
     * Returns dashboard statistics for the approver panel header cards.
     */
    @PreAuthorize("hasRole('SADA')")
    @GetMapping("/stats")
    public ResponseEntity<ApproverStatsResponse> getStats() {
        return ResponseEntity.ok(approverService.getStats());
    }
}
