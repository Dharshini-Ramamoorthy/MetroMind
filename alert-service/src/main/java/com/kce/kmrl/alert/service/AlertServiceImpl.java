package com.kce.kmrl.alert.service;

import com.kce.kmrl.alert.dto.AlertResponse;
import com.kce.kmrl.alert.dto.AlertSummaryResponse;
import com.kce.kmrl.alert.dto.LedgerEntryResponse;
import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.AuditLedgerEntry;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.exception.AlertNotFoundException;
import com.kce.kmrl.alert.integration.client.FleetServiceClient;
import com.kce.kmrl.alert.integration.client.MaintenanceServiceClient;
import com.kce.kmrl.alert.integration.client.ScheduleServiceClient;
import com.kce.kmrl.alert.rules.RuleAlertPublisher;
import com.kce.kmrl.alert.integration.dto.CreateMaintenanceTicketRequest;
import com.kce.kmrl.alert.integration.dto.MaintenanceTicketDto;
import com.kce.kmrl.alert.repository.AlertRepository;
import com.kce.kmrl.alert.repository.AuditLedgerRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final AuditLedgerRepository ledgerRepository;
    private final FleetServiceClient fleetClient;
    private final MaintenanceServiceClient maintenanceClient;
    private final ScheduleServiceClient scheduleClient;
    private final RuleAlertPublisher publisher;

    public AlertServiceImpl(AlertRepository alertRepository,
                            AuditLedgerRepository ledgerRepository,
                            FleetServiceClient fleetClient,
                            MaintenanceServiceClient maintenanceClient,
                            ScheduleServiceClient scheduleClient,
                            RuleAlertPublisher publisher) {
        this.alertRepository = alertRepository;
        this.ledgerRepository = ledgerRepository;
        this.fleetClient = fleetClient;
        this.maintenanceClient = maintenanceClient;
        this.scheduleClient = scheduleClient;
        this.publisher = publisher;
    }

    @Override
    public List<AlertResponse> getAllAlerts(String callerRole, String callerUserId) {
        return alertRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> isVisibleTo(a, callerRole, callerUserId))
                .map(AlertResponse::from)
                .toList();
    }

    @Override
    public AlertResponse getAlertById(String id, String callerRole, String callerUserId) {
        Alert alert = findVisibleAlert(id, callerRole, callerUserId);
        return AlertResponse.from(alert);
    }

    private Alert findVisibleAlert(String id, String callerRole, String callerUserId) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
        if (!isVisibleTo(alert, callerRole, callerUserId)) {

            throw new AlertNotFoundException(id);
        }
        return alert;
    }

    private boolean isVisibleTo(Alert alert, String callerRole, String callerUserId) {
        String targetUserId = alert.getTargetUserId();
        if (targetUserId != null && !targetUserId.isBlank()) {
            return callerUserId != null && targetUserId.equals(callerUserId);
        }

        List<String> targetRoles = alert.getTargetRoles();
        if (targetRoles == null || targetRoles.isEmpty()) {
            return callerRole != null;
        }

        return callerRole != null && targetRoles.stream()
                .filter(r -> r != null && !r.isBlank())
                .anyMatch(r -> r.equalsIgnoreCase(callerRole));
    }

    @Override
    public LedgerEntryResponse clearAlert(String alertId, String operatorUserId, String operatorRole) {
        Alert alert = findVisibleAlert(alertId, operatorRole, operatorUserId);

        AuditLedgerEntry entry = buildLedgerEntry(
                alert,
                "CLEAR",
                operatorUserId,
                operatorRole,
                null
        );

        AuditLedgerEntry saved = ledgerRepository.save(entry);
        publisher.suppressLevel(alertId);
        alertRepository.deleteById(alertId);
        return LedgerEntryResponse.from(saved);
    }

    @Override
    public String isolateAlert(String alertId, String operatorUserId, String operatorRole) {
        Alert alert = findVisibleAlert(alertId, operatorRole, operatorUserId);

        String trainId = resolveTrainId(alert);
        if (trainId == null) {
            throw new IllegalArgumentException(
                    "This alert is not associated with a specific fleet train and cannot be isolated.");
        }

        fleetClient.ensureTrainExists(trainId);
        boolean hadActiveTrip = scheduleClient.withdrawTrain(trainId, true);
        fleetClient.updateTrainStatus(trainId, "IN_MAINTENANCE");

        AuditLedgerEntry entry = buildLedgerEntry(
                alert,
                "ISOLATE",
                operatorUserId,
                operatorRole,
                "train=" + trainId + ";activeTrip=" + hadActiveTrip
        );
        ledgerRepository.save(entry);

        return alert.getAsset() + " isolated from the live network.";
    }

    @Override
    public AlertResponse dispatchAlert(String alertId, String operatorUserId, String operatorRole) {
        Alert alert = findVisibleAlert(alertId, operatorRole, operatorUserId);

        if (alert.getMaintenanceWorkOrderId() != null && !alert.getMaintenanceWorkOrderId().isBlank()) {
            return AlertResponse.from(alert);
        }

        String trainNumber = alert.getSourceEntityId() != null && !alert.getSourceEntityId().isBlank()
                ? fleetClient.findTrainNumberById(alert.getSourceEntityId()).orElse(null)
                : alert.getAsset();
        if (trainNumber == null || trainNumber.isBlank()) {
            throw new IllegalArgumentException("This alert has no train asset and cannot be dispatched to maintenance.");
        }

        Severity severity = alert.getSeverity();
        String priority = severity == Severity.SEV3 ? "CRITICAL" : "HIGH";

        CreateMaintenanceTicketRequest request = new CreateMaintenanceTicketRequest(
                trainNumber,
                "Automatic maintenance dispatch for alert " + alert.getId() + ": " + alert.getTitle(),
                priority,
                operatorUserId
        );

        MaintenanceTicketDto ticket = maintenanceClient.createTicket(request);
        alert.setMaintenanceWorkOrderId(ticket.getId());
        Alert updated = alertRepository.save(alert);

        AuditLedgerEntry entry = buildLedgerEntry(
                alert,
                "DISPATCH",
                operatorUserId,
                operatorRole,
                "workOrder=" + ticket.getId()
        );
        ledgerRepository.save(entry);

        return AlertResponse.from(updated);
    }

    private String resolveTrainId(Alert alert) {
        if (alert.getSourceEntityId() != null && !alert.getSourceEntityId().isBlank()) {
            return alert.getSourceEntityId();
        }
        if (alert.getAsset() == null || alert.getAsset().isBlank()) {
            return null;
        }
        return fleetClient.findTrainIdByNumber(alert.getAsset()).orElse(null);
    }

    private AuditLedgerEntry buildLedgerEntry(Alert alert,
                                              String action,
                                              String operatorUserId,
                                              String operatorRole,
                                              String details) {
        Instant now = Instant.now();
        AuditLedgerEntry entry = new AuditLedgerEntry();
        entry.setIncidentId(alert.getId());
        entry.setAction(action);
        entry.setResolvedAt(now);
        entry.setSeverity(alert.getSeverity());
        entry.setOperatorName(operatorUserId == null ? "unknown" : operatorUserId);
        entry.setOperatorId(operatorUserId == null ? "unknown" : operatorUserId);
        entry.setOperatorRole(operatorRole);
        entry.setDetails(details);
        entry.setValidationHash(hash(alert, entry));
        return entry;
    }

    private String hash(Alert alert, AuditLedgerEntry entry) {
        String payload = String.join("|",
                safe(alert.getId()),
                safe(entry.getAction()),
                safe(entry.getOperatorId()),
                safe(entry.getOperatorRole()),
                String.valueOf(entry.getResolvedAt().toEpochMilli()),
                String.valueOf(alert.getSeverity()),
                safe(entry.getDetails())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public List<LedgerEntryResponse> getLedger() {
        return ledgerRepository.findAllByOrderByResolvedAtDesc().stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    @Override
    public AlertSummaryResponse getSummary(String callerRole, String callerUserId) {
        List<Alert> active = alertRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> isVisibleTo(a, callerRole, callerUserId))
                .toList();

        int total = active.size();
        int critical = (int) active.stream().filter(a -> a.getSeverity() == Severity.SEV3).count();
        int warning = (int) active.stream().filter(a -> a.getSeverity() == Severity.SEV2).count();
        int info = (int) active.stream().filter(a -> a.getSeverity() == Severity.SEV1).count();
        return new AlertSummaryResponse(total, critical, warning, info);
    }
}
