package com.kce.kmrl.service;

import com.kce.kmrl.dto.CertificateOfFitnessRequest;
import com.kce.kmrl.dto.CreateTicketRequest;
import com.kce.kmrl.dto.MaintenanceResponse;
import com.kce.kmrl.entity.Maintenance;
import com.kce.kmrl.entity.MaintenanceStatus;
import com.kce.kmrl.entity.RepairType;
import com.kce.kmrl.entity.TrainWithdrawalStatus;
import com.kce.kmrl.exception.InvalidRequestException;
import com.kce.kmrl.exception.TicketNotFoundException;
import com.kce.kmrl.exception.WithdrawalFailedException;
import com.kce.kmrl.repository.MaintenanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaintenanceServiceImpl implements MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceServiceImpl.class);
    private static final String SYSTEM_USER_ID   = "maintenance-service";
    private static final String SYSTEM_USER_ROLE = "SYSTEM";

    private static final Set<MaintenanceStatus> RELEASES_TO_STANDBY =
            EnumSet.of(MaintenanceStatus.COMPLETED, MaintenanceStatus.CANCELLED);

    private static final Set<MaintenanceStatus> UNRESOLVED =
            EnumSet.of(MaintenanceStatus.OPEN, MaintenanceStatus.SCHEDULED,
                       MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.PENDING_CLOSURE);

    private static final Set<String> ALLOWED_COF_EXTENSIONS =
            Set.of(".pdf", ".png", ".jpg", ".jpeg");

    private final MaintenanceRepository maintenanceRepository;
    private final RestClient loadBalancedRestClient;
    private final Path uploadDir;

    public MaintenanceServiceImpl(
            MaintenanceRepository maintenanceRepository,
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${maintenance.cof.upload-dir:./cof-uploads}") String uploadDirPath) {
        this.maintenanceRepository = maintenanceRepository;
        this.loadBalancedRestClient = restClientBuilder.build();
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try { Files.createDirectories(this.uploadDir); }
        catch (IOException e) { throw new IllegalStateException("Could not create CoF upload directory: " + this.uploadDir, e); }
    }

    @Override
    public MaintenanceResponse createTicket(CreateTicketRequest request) {
        List<Maintenance> existing = maintenanceRepository.findByTrainNumber(request.getTrainNumber());
        boolean hasUnresolved = existing.stream().anyMatch(m -> UNRESOLVED.contains(m.getStatus()));
        if (hasUnresolved) {
            throw new InvalidRequestException("Train " + request.getTrainNumber() + " already has an unresolved maintenance ticket.");
        }
        verifyTrainExistsOnFleet(request.getTrainNumber());
        if (request.getRepairType() == RepairType.ROUTINE_CHECK) {
            return createRoutineCheckTicket(request);
        }
        return createImmediateWithdrawalTicket(request);
    }

    private MaintenanceResponse createRoutineCheckTicket(CreateTicketRequest request) {
        LocalDate plannedDate = request.getPlannedMaintenanceDate();
        if (plannedDate == null) {
            throw new InvalidRequestException("plannedMaintenanceDate is required for ROUTINE_CHECK tickets.");
        }
        if (!plannedDate.isAfter(LocalDate.now())) {
            throw new InvalidRequestException("plannedMaintenanceDate must be a future date (received: " + plannedDate + ").");
        }
        Maintenance m = new Maintenance();
        m.setTrainNumber(request.getTrainNumber());
        m.setDescription(request.getDescription());
        m.setRepairType(RepairType.ROUTINE_CHECK);
        m.setPlannedMaintenanceDate(plannedDate);
        m.setStatus(MaintenanceStatus.SCHEDULED);
        m.setCreatedBy(request.getCreatedBy());
        m.setCreatedAt(Instant.now());
        m.setTrainPulled(false);
        m.setWithdrawalStatus(TrainWithdrawalStatus.NOT_REQUIRED);
        Maintenance saved = maintenanceRepository.save(m);
        log.info("ROUTINE_CHECK ticket {} created for train {} — planned date: {}.", saved.getId(), saved.getTrainNumber(), plannedDate);
        return mapToResponse(saved);
    }

    private MaintenanceResponse createImmediateWithdrawalTicket(CreateTicketRequest request) {
        if (request.getPlannedMaintenanceDate() != null) {
            throw new InvalidRequestException("plannedMaintenanceDate must not be set for repair type " + request.getRepairType() + ".");
        }
        Maintenance m = new Maintenance();
        m.setTrainNumber(request.getTrainNumber());
        m.setDescription(request.getDescription());
        m.setRepairType(request.getRepairType());
        m.setStatus(MaintenanceStatus.OPEN);
        m.setCreatedBy(request.getCreatedBy());
        m.setCreatedAt(Instant.now());
        m.setTrainPulled(false);
        m.setWithdrawalStatus(TrainWithdrawalStatus.WITHDRAWAL_PENDING);
        Maintenance saved = maintenanceRepository.save(m);
        try {
            withdrawTrainFromSchedule(saved.getTrainNumber(), saved.getId());
        } catch (WithdrawalFailedException e) {
            saved.setWithdrawalStatus(TrainWithdrawalStatus.WITHDRAWAL_FAILED);
            saved = maintenanceRepository.save(saved);
            log.error("{}", e.getMessage());
            return mapToResponse(saved);
        }
        saved.setTrainPulled(true);
        saved.setWithdrawalStatus(TrainWithdrawalStatus.PULLED);
        saved.setTrainPulledAt(Instant.now());
        saved.setStatus(MaintenanceStatus.IN_PROGRESS);
        saved = maintenanceRepository.save(saved);
        updateFleetTrainStatus(saved.getTrainNumber(), "IN_MAINTENANCE");
        log.info("Ticket {} ({}) created for train {}. Train is IN_MAINTENANCE.", saved.getId(), saved.getRepairType(), saved.getTrainNumber());
        return mapToResponse(saved);
    }

    @Override
    public MaintenanceResponse beginRoutineMaintenance(String ticketId) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.getRepairType() != RepairType.ROUTINE_CHECK) {
            throw new InvalidRequestException("begin-routine-maintenance is only valid for ROUTINE_CHECK tickets. This ticket has repairType: " + ticket.getRepairType());
        }
        if (ticket.getStatus() != MaintenanceStatus.SCHEDULED) {
            throw new InvalidRequestException("begin-routine-maintenance requires status SCHEDULED. Current: " + ticket.getStatus());
        }
        LocalDate plannedDate = ticket.getPlannedMaintenanceDate();
        if (LocalDate.now().isBefore(plannedDate)) {
            throw new InvalidRequestException("Cannot begin routine maintenance before planned date " + plannedDate + " (today is " + LocalDate.now() + ").");
        }
        withdrawTrainFromSchedule(ticket.getTrainNumber(), ticketId);
        ticket.setTrainPulled(true);
        ticket.setWithdrawalStatus(TrainWithdrawalStatus.PULLED);
        ticket.setTrainPulledAt(Instant.now());
        ticket.setStatus(MaintenanceStatus.IN_PROGRESS);
        Maintenance saved = maintenanceRepository.save(ticket);
        updateFleetTrainStatus(saved.getTrainNumber(), "IN_MAINTENANCE");
        log.info("Routine maintenance begun for ticket {} (train {}). Train is IN_MAINTENANCE.", ticketId, ticket.getTrainNumber());
        return mapToResponse(saved);
    }

    @Scheduled(fixedRate = 20000)
    public void autoActivateScheduledMaintenance() {
        LocalDate today = LocalDate.now();
        List<Maintenance> scheduled = maintenanceRepository.findByStatusAndPlannedMaintenanceDateLessThanEqual(
                MaintenanceStatus.SCHEDULED, today);
        for (Maintenance ticket : scheduled) {
            try {
                log.info("Auto-activating scheduled routine maintenance on date {} for ticket {} (train {})",
                        ticket.getPlannedMaintenanceDate(), ticket.getId(), ticket.getTrainNumber());
                try {
                    withdrawTrainFromSchedule(ticket.getTrainNumber(), ticket.getId());
                } catch (Exception e) {
                    log.warn("Withdrawal notification for scheduled train {} had exception: {}", ticket.getTrainNumber(), e.getMessage());
                }
                ticket.setTrainPulled(true);
                ticket.setWithdrawalStatus(TrainWithdrawalStatus.PULLED);
                ticket.setTrainPulledAt(Instant.now());
                ticket.setStatus(MaintenanceStatus.IN_PROGRESS);
                maintenanceRepository.save(ticket);
                updateFleetTrainStatus(ticket.getTrainNumber(), "IN_MAINTENANCE");
            } catch (Exception e) {
                log.error("Failed to auto-activate scheduled ticket {}: {}", ticket.getId(), e.getMessage());
            }
        }
    }

    @Override
    public MaintenanceResponse applyApproverDecision(String ticketId, String newStatus, String comments) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        MaintenanceStatus target = MaintenanceStatus.fromString(newStatus);
        if (target != MaintenanceStatus.COMPLETED && target != MaintenanceStatus.IN_PROGRESS) {
            throw new InvalidRequestException("Approver decision must be COMPLETED or IN_PROGRESS. Received: " + newStatus);
        }
        MaintenanceStatus.assertValidTransition(ticket.getStatus(), target);
        ticket.setStatus(target);
        if (comments != null && !comments.isBlank()) { ticket.setApproverComments(comments); }
        if (target == MaintenanceStatus.COMPLETED) {
            Maintenance saved = maintenanceRepository.save(ticket);
            updateFleetTrainStatus(saved.getTrainNumber(), "STANDBY");
            log.info("CoF approved for ticket {} (train {}). Train returned to STANDBY.", ticketId, saved.getTrainNumber());
            return mapToResponse(saved);
        } else {
            if (ticket.getCofDocumentStoredFileName() != null && !ticket.getCofDocumentStoredFileName().isBlank()) {
                try {
                    Files.deleteIfExists(uploadDir.resolve(ticket.getCofDocumentStoredFileName()));
                } catch (IOException e) {
                    log.warn("Failed to delete rejected CoF document {}: {}", ticket.getCofDocumentStoredFileName(), e.getMessage());
                }
            }
            ticket.setCofDocumentStoredFileName(null);
            ticket.setCofDocumentOriginalFileName(null);
            ticket.setCofSubmittedBy(null);
            ticket.setCofSubmittedAt(null);
            Maintenance saved = maintenanceRepository.save(ticket);
            log.info("CoF rejected for ticket {} (train {}). Back to IN_PROGRESS.", ticketId, saved.getTrainNumber());
            return mapToResponse(saved);
        }
    }

    private void withdrawTrainFromSchedule(String trainId, String maintenanceTicketId) {
        try {
            Map<String, Object> payload = Map.of("trainId", trainId, "maintenanceTicketId", maintenanceTicketId, "emergency", true);
            loadBalancedRestClient.post()
                    .uri("http://schedule-service/api/v1/schedule/trips/withdraw-train")
                    .header("X-User-Id", SYSTEM_USER_ID).header("X-User-Role", SYSTEM_USER_ROLE)
                    .body(payload).retrieve().toBodilessEntity();
            log.info("Schedule-service withdrawal succeeded for train {} (ticket {})", trainId, maintenanceTicketId);
        } catch (Exception e) {
            log.error("Schedule-service withdrawal FAILED for train {} (ticket {}): {}", trainId, maintenanceTicketId, e.getMessage());
            throw new WithdrawalFailedException(maintenanceTicketId, trainId, e.getMessage());
        }
    }

    @Override
    public MaintenanceResponse submitCertificateOfFitness(String ticketId, CertificateOfFitnessRequest request, MultipartFile document) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new InvalidRequestException("Certificate of Fitness can only be submitted for tickets in IN_PROGRESS status. Current status: " + ticket.getStatus());
        }
        if (document == null || document.isEmpty()) {
            throw new InvalidRequestException("A Certificate of Fitness document is required. Please attach a non-empty file.");
        }
        String engineer = (request != null && request.getEngineerName() != null && !request.getEngineerName().isBlank())
                ? request.getEngineerName() : "MaintenanceEngineer";
        String storedFileName = storeCofDocument(document, ticketId);
        ticket.setStatus(MaintenanceStatus.PENDING_CLOSURE);
        ticket.setCofSubmittedBy(engineer);
        ticket.setCofSubmittedAt(Instant.now());
        ticket.setCofDocumentStoredFileName(storedFileName);
        ticket.setCofDocumentOriginalFileName(document.getOriginalFilename());
        ticket.setApprovalSubmissionFailed(false);
        Maintenance saved = maintenanceRepository.save(ticket);
        String remarks = (request != null && request.getRemarks() != null) ? request.getRemarks() : "";
        String description = "Maintenance work completed by " + engineer + " for train " + saved.getTrainNumber() + ". Requesting operational clearance." + (!remarks.isBlank() ? " Notes: " + remarks : "");
        try {
            doSubmitApprovalTask(saved.getId(), "CERTIFICATE_OF_FITNESS", "Certificate of Fitness (CoF): Train " + saved.getTrainNumber(), description, engineer);
        } catch (Exception e) {
            log.error("Approval task submission FAILED for ticket {} (train {}). Retry via POST /tickets/{}/retry-approval. Cause: {}", saved.getId(), saved.getTrainNumber(), saved.getId(), e.getMessage());
            saved.setApprovalSubmissionFailed(true);
            saved = maintenanceRepository.save(saved);
        }
        return mapToResponse(saved);
    }

    @Override
    public MaintenanceResponse retryApprovalSubmission(String ticketId) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.getStatus() != MaintenanceStatus.PENDING_CLOSURE) {
            throw new InvalidRequestException("Approval retry is only valid for PENDING_CLOSURE tickets. Current: " + ticket.getStatus());
        }
        if (!ticket.isApprovalSubmissionFailed()) {
            throw new InvalidRequestException("Ticket " + ticketId + " does not have a pending approval submission failure.");
        }
        String engineer = ticket.getCofSubmittedBy() != null ? ticket.getCofSubmittedBy() : "MaintenanceEngineer";
        doSubmitApprovalTask(ticket.getId(), "CERTIFICATE_OF_FITNESS", "Certificate of Fitness (CoF): Train " + ticket.getTrainNumber(),
                "Retry: Maintenance work completed by " + engineer + " for train " + ticket.getTrainNumber() + ". Requesting operational clearance.", engineer);
        ticket.setApprovalSubmissionFailed(false);
        Maintenance saved = maintenanceRepository.save(ticket);
        log.info("Approval task successfully re-submitted for ticket {} after prior failure.", ticketId);
        return mapToResponse(saved);
    }

    @Override
    public MaintenanceResponse retryWithdrawal(String ticketId) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.getWithdrawalStatus() != TrainWithdrawalStatus.WITHDRAWAL_FAILED) {
            throw new InvalidRequestException("Withdrawal retry is only valid for tickets with WITHDRAWAL_FAILED status. Current: " + ticket.getWithdrawalStatus());
        }
        try {
            withdrawTrainFromSchedule(ticket.getTrainNumber(), ticket.getId());
            ticket.setTrainPulled(true);
            ticket.setWithdrawalStatus(TrainWithdrawalStatus.PULLED);
            ticket.setTrainPulledAt(Instant.now());
            ticket.setStatus(MaintenanceStatus.IN_PROGRESS);
            Maintenance saved = maintenanceRepository.save(ticket);
            updateFleetTrainStatus(saved.getTrainNumber(), "IN_MAINTENANCE");
            return mapToResponse(saved);
        } catch (WithdrawalFailedException e) {
            ticket.setWithdrawalStatus(TrainWithdrawalStatus.WITHDRAWAL_FAILED);
            maintenanceRepository.save(ticket);
            throw e;
        }
    }

    private String storeCofDocument(MultipartFile document, String ticketId) {
        String orig = document.getOriginalFilename();
        if (orig == null || !orig.contains(".")) {
            throw new InvalidRequestException("Invalid document extension. Allowed types are: .pdf, .png, .jpg, .jpeg.");
        }
        String ext = orig.substring(orig.lastIndexOf('.')).toLowerCase();
        if (!ALLOWED_COF_EXTENSIONS.contains(ext)) {
            throw new InvalidRequestException("Invalid document extension. Allowed types are: .pdf, .png, .jpg, .jpeg.");
        }
        String stored = ticketId + "_" + UUID.randomUUID() + ext;
        Path target = uploadDir.resolve(stored);
        if (!target.normalize().startsWith(uploadDir)) {
            throw new InvalidRequestException("Invalid document filename.");
        }
        try { Files.copy(document.getInputStream(), target); log.info("CoF document for ticket {} stored as {}", ticketId, stored); return stored; }
        catch (IOException e) { throw new InvalidRequestException("Failed to store the CoF document: " + e.getMessage()); }
    }

    @Override
    public Resource getCofDocument(String ticketId) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        String stored = ticket.getCofDocumentStoredFileName();
        if (stored == null || stored.isBlank()) { throw new InvalidRequestException("No CoF document has been stored for ticket " + ticketId + "."); }
        Path filePath = uploadDir.resolve(stored);
        if (!Files.exists(filePath)) { throw new InvalidRequestException("CoF document for ticket " + ticketId + " is recorded but the file is missing on disk."); }
        return new FileSystemResource(filePath);
    }

    private void doSubmitApprovalTask(String targetId, String type, String title, String desc, String requestedBy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetEntityId", targetId); payload.put("requestType", type);
        payload.put("title", title); payload.put("description", desc);
        payload.put("requestedBy", requestedBy != null ? requestedBy : "MaintenanceEngineer");
        loadBalancedRestClient.post().uri("http://approver-service/api/approver/tasks/submit")
                .header("X-User-Id", SYSTEM_USER_ID).header("X-User-Role", SYSTEM_USER_ROLE)
                .body(payload).retrieve().toBodilessEntity();
        log.info("Submitted {} approval task to approver-service for ticket {}", type, targetId);
    }

    @Override
    public void markTrainPulled(String ticketId, String trainNumber) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElse(null);
        if (ticket != null && !ticket.getTrainNumber().equals(trainNumber)) {
            throw new InvalidRequestException("Ticket train number '" + ticket.getTrainNumber() + "' does not match provided train number '" + trainNumber + "'.");
        }
        if (ticket == null || ticket.isTrainPulled()) return;
        ticket.setTrainPulled(true);
        ticket.setWithdrawalStatus(TrainWithdrawalStatus.PULLED);
        ticket.setTrainPulledAt(Instant.now());
        maintenanceRepository.save(ticket);
    }

    @Override
    public List<MaintenanceResponse> getAllTickets() {
        return maintenanceRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public MaintenanceResponse getTicketById(String ticketId) {
        return mapToResponse(maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId)));
    }

    @Override
    public void deleteTicket(String ticketId) {
        Maintenance ticket = maintenanceRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        MaintenanceStatus status = ticket.getStatus();
        if (status == MaintenanceStatus.PENDING_CLOSURE || status == MaintenanceStatus.COMPLETED
                || status == MaintenanceStatus.REJECTED || status == MaintenanceStatus.CANCELLED) {
            throw new InvalidRequestException("Ticket " + ticketId + " cannot be deleted in state " + status + ". These are audit records.");
        }
        maintenanceRepository.deleteById(ticketId);
        log.info("Ticket {} (train {}, status {}) deleted.", ticketId, ticket.getTrainNumber(), status);
        if (ticket.isTrainPulled()) {
            List<Maintenance> remaining = maintenanceRepository.findByTrainNumber(ticket.getTrainNumber());
            boolean hasOtherUnresolved = remaining.stream().anyMatch(m -> UNRESOLVED.contains(m.getStatus()));
            if (!hasOtherUnresolved) { updateFleetTrainStatus(ticket.getTrainNumber(), "STANDBY"); }
        }
    }

    private void verifyTrainExistsOnFleet(String trainId) {
        try {
            loadBalancedRestClient.get().uri("http://fleet-service/api/v1/fleet/{trainId}", trainId)
                    .header("X-User-Id", SYSTEM_USER_ID).header("X-User-Role", SYSTEM_USER_ROLE)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidRequestException("Train '" + trainId + "' does not exist on fleet-service.");
        } catch (Exception e) {
            log.warn("Fleet-service unreachable during train verification for {}: {}", trainId, e.getMessage());
        }
    }

    private void updateFleetTrainStatus(String trainNumber, String status) {
        try {
            loadBalancedRestClient.put().uri("http://fleet-service/api/v1/fleet/{trainId}/status", trainNumber)
                    .header("X-User-Id", SYSTEM_USER_ID).header("X-User-Role", SYSTEM_USER_ROLE)
                    .body(Map.of("status", status)).retrieve().toBodilessEntity();
            log.info("Updated train {} status to {} on fleet-service", trainNumber, status);
        } catch (Exception e) { log.warn("Failed to set train {} to {} on fleet-service: {}", trainNumber, status, e.getMessage()); }
    }

    private MaintenanceResponse mapToResponse(Maintenance m) {
        MaintenanceResponse res = new MaintenanceResponse();
        res.setId(m.getId()); res.setTrainNumber(m.getTrainNumber());
        res.setDescription(m.getDescription()); res.setRepairType(m.getRepairType());
        res.setPlannedMaintenanceDate(m.getPlannedMaintenanceDate());
        res.setStatus(m.getStatus()); res.setCreatedBy(m.getCreatedBy());
        res.setApproverComments(m.getApproverComments()); res.setCreatedAt(m.getCreatedAt());
        res.setTrainPulled(m.isTrainPulled()); res.setWithdrawalStatus(m.getWithdrawalStatus());
        res.setTrainPulledAt(m.getTrainPulledAt()); res.setCofSubmittedBy(m.getCofSubmittedBy());
        res.setCofSubmittedAt(m.getCofSubmittedAt()); res.setCofDocumentOriginalFileName(m.getCofDocumentOriginalFileName());
        res.setApprovalSubmissionFailed(m.isApprovalSubmissionFailed());
        if (m.getCofDocumentStoredFileName() != null && !m.getCofDocumentStoredFileName().isBlank()) {
            res.setCofDocumentUrl("/api/v1/maintenance/tickets/" + m.getId() + "/certificate-of-fitness/document");
        }
        return res;
    }
}