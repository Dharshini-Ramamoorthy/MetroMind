package com.kce.kmrl.service;

import com.kce.kmrl.dto.*;

import java.util.List;

/**
 * Business logic contract for the approver panel.
 */
public interface ApproverService {

    /** Get all pending tickets (OPEN status) awaiting approval. */
    List<TicketResponse> getPendingTickets();

    /** Get all tickets regardless of status. */
    List<TicketResponse> getAllTickets();

    /** Get a single ticket by id. */
    TicketResponse getTicketById(String id);

    /** Process an approval decision (approve/reject/request changes). */
    TicketResponse processDecision(String ticketId, ApprovalRequest request, String approverName);

    /** Get approval history, newest first. */
    List<ApprovalHistoryResponse> getApprovalHistory();

    /** Get dashboard statistics. */
    ApproverStatsResponse getStats();
}
