package com.kce.kmrl.service;

import com.kce.kmrl.dto.CertificateOfFitnessRequest;
import com.kce.kmrl.dto.CreateTicketRequest;
import com.kce.kmrl.dto.MaintenanceResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface MaintenanceService {
    MaintenanceResponse createTicket(CreateTicketRequest request);
    MaintenanceResponse beginRoutineMaintenance(String ticketId);
    MaintenanceResponse applyApproverDecision(String ticketId, String newStatus, String comments);
    MaintenanceResponse submitCertificateOfFitness(String ticketId, CertificateOfFitnessRequest request, MultipartFile document);
    Resource getCofDocument(String ticketId);
    List<MaintenanceResponse> getAllTickets();
    MaintenanceResponse getTicketById(String ticketId);
    void deleteTicket(String ticketId);
    void markTrainPulled(String ticketId, String trainNumber);
    MaintenanceResponse retryApprovalSubmission(String ticketId);
    MaintenanceResponse retryWithdrawal(String ticketId);
}