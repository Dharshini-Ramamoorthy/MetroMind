package com.kce.kmrl.service;

import com.kce.kmrl.dto.*;
import com.kce.kmrl.entity.ApprovalDecision;
import com.kce.kmrl.entity.ApprovalHistory;
import com.kce.kmrl.entity.MaintenanceTicket;
import com.kce.kmrl.exception.TicketNotFoundException;
import com.kce.kmrl.repository.ApprovalHistoryRepository;
import com.kce.kmrl.repository.MaintenanceTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Core business logic for the approver panel. Reads maintenance tickets
 * from the shared "maintenance" collection and writes approval decisions
 * both back to the ticket (status + approverComments) and to the
 * "approval_history" collection for audit trail.
 */
@Service
public class ApproverServiceImpl implements ApproverService {

    private final MaintenanceTicketRepository ticketRepository;
    private final ApprovalHistoryRepository historyRepository;

    @Autowired
    public ApproverServiceImpl(MaintenanceTicketRepository ticketRepository,
                                ApprovalHistoryRepository historyRepository) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public List<TicketResponse> getPendingTickets() {
        // Pending tickets are those with OPEN status (submitted by MDS,
        // not yet acted on by admin)
        return ticketRepository.findByStatusIn(List.of("OPEN", "IN_PROGRESS"))
            .stream()
            .map(this::toTicketResponse)
            .toList();
    }

    @Override
    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAll()
            .stream()
            .map(this::toTicketResponse)
            .toList();
    }

    @Override
    public TicketResponse getTicketById(String id) {
        MaintenanceTicket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
        return toTicketResponse(ticket);
    }

    @Override
    public TicketResponse processDecision(String ticketId, ApprovalRequest request, String approverName) {
        // Validate decision enum
        ApprovalDecision decision = ApprovalDecision.fromString(request.getDecision());

        // Find the ticket
        MaintenanceTicket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException(ticketId));

        // Update ticket status based on decision
        switch (decision) {
            case APPROVED -> {
                ticket.setStatus("COMPLETED");
                ticket.setApproverComments(
                    request.getComments() != null ? request.getComments() : "Approved by " + approverName);
            }
            case REJECTED -> {
                ticket.setStatus("OPEN");
                ticket.setApproverComments(
                    request.getComments() != null ? request.getComments() : "Rejected by " + approverName);
            }
            case CHANGES_REQUESTED -> {
                ticket.setStatus("OPEN");
                ticket.setApproverComments(
                    request.getComments() != null ? request.getComments() : "Changes requested by " + approverName);
            }
        }

        ticketRepository.save(ticket);

        // Record in approval history
        ApprovalHistory history = new ApprovalHistory();
        history.setTicketId(ticketId);
        history.setRequestId(ticket.getId());
        history.setType(categorizeTicket(ticket));
        history.setDecision(decision.name());
        history.setApprover(approverName);
        history.setComments(request.getComments());
        history.setTimestamp(Instant.now());
        historyRepository.save(history);

        return toTicketResponse(ticket);
    }

    @Override
    public List<ApprovalHistoryResponse> getApprovalHistory() {
        return historyRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))
            .stream()
            .map(this::toHistoryResponse)
            .toList();
    }

    @Override
    public ApproverStatsResponse getStats() {
        long pending = ticketRepository.countByStatus("OPEN") + ticketRepository.countByStatus("IN_PROGRESS");
        long approved = historyRepository.countByDecision("APPROVED");
        long rejected = historyRepository.countByDecision("REJECTED");
        return new ApproverStatsResponse(pending, approved, rejected);
    }

    /**
     * Categorize a maintenance ticket into the type categories used by the
     * approver panel frontend (Schedule Change, Induction Override, Alert Resolution).
     * Based on keywords in the ticket description.
     */
    private String categorizeTicket(MaintenanceTicket ticket) {
        String desc = (ticket.getDescription() != null ? ticket.getDescription() : "").toLowerCase();
        if (desc.contains("schedule") || desc.contains("headway") || desc.contains("timetable")) {
            return "Schedule Change";
        } else if (desc.contains("induction") || desc.contains("override") || desc.contains("fitness")) {
            return "Induction Override";
        } else if (desc.contains("alert") || desc.contains("critical") || desc.contains("breaker") || desc.contains("traction")) {
            return "Alert Resolution";
        }
        return "Schedule Change"; // default
    }

    private TicketResponse toTicketResponse(MaintenanceTicket ticket) {
        return new TicketResponse(
            ticket.getId(),
            ticket.getTrainNumber(),
            ticket.getDescription(),
            ticket.getPriority(),
            ticket.getStatus(),
            ticket.getCreatedBy(),
            ticket.getApproverComments(),
            ticket.getCreatedAt()
        );
    }

    private ApprovalHistoryResponse toHistoryResponse(ApprovalHistory h) {
        return new ApprovalHistoryResponse(
            h.getId(),
            h.getTicketId(),
            h.getRequestId(),
            h.getType(),
            h.getDecision(),
            h.getApprover(),
            h.getComments(),
            h.getTimestamp()
        );
    }
}
