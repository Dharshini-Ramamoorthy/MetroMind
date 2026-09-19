package com.kce.kmrl.alert.rules;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.integration.client.MaintenanceServiceClient;
import com.kce.kmrl.alert.integration.client.ServiceResult;
import com.kce.kmrl.alert.integration.dto.MaintenanceTicketDto;
import com.kce.kmrl.alert.rules.state.RuleStateStore;
import com.kce.kmrl.alert.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class MaintenanceRuleEvaluator {

    private final MaintenanceServiceClient maintenanceClient;
    private final RuleAlertPublisher publisher;
    private final RuleStateStore stateStore;
    private final long slaOpenHours;
    private final AlertRepository alertRepository;

    public MaintenanceRuleEvaluator(
            MaintenanceServiceClient maintenanceClient,
            RuleAlertPublisher publisher,
            RuleStateStore stateStore,
            AlertRepository alertRepository,
            @Value("${alert.rules.maintenance-sla.open-hours:24}") long slaOpenHours) {
        this.maintenanceClient = maintenanceClient;
        this.publisher = publisher;
        this.stateStore = stateStore;
        this.alertRepository = alertRepository;
        this.slaOpenHours = slaOpenHours;
    }

    public void evaluate() {
        ServiceResult<List<MaintenanceTicketDto>> result = maintenanceClient.getAllTicketsResult();
        if (!result.isAvailable()) return;
        List<MaintenanceTicketDto> tickets = result.getData();
        java.util.Set<String> currentTicketIds = new java.util.HashSet<>();
        for (MaintenanceTicketDto ticket : tickets) {
            if (ticket != null && ticket.getId() != null) currentTicketIds.add(ticket.getId());
            evaluateNewHighPriority(ticket);
            evaluateOpenPastSla(ticket);
            evaluateCompletedCloseOut(ticket);
        }
        alertRepository.findByIdStartingWith("ALT-SLA-").stream()
                .filter(a -> !currentTicketIds.contains(extractSuffix(a.getId(), "ALT-SLA-")))
                .forEach(a -> publisher.resolveLevel(a.getId()));
    }

    private String extractSuffix(String id, String prefix) {
        return id != null && id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    private void evaluateNewHighPriority(MaintenanceTicketDto ticket) {
        boolean highOrCritical = "HIGH".equalsIgnoreCase(ticket.getPriority())
                || "CRITICAL".equalsIgnoreCase(ticket.getPriority());
        if (!highOrCritical) {
            return;
        }
        String dedupeKey = "seen-high-priority:" + ticket.getId();
        Severity severity = "CRITICAL".equalsIgnoreCase(ticket.getPriority()) ? Severity.SEV3 : Severity.SEV2;

        publisher.publishEventOnce(dedupeKey, "ALT-JOBCARD", severity,
            "New " + ticket.getPriority().toUpperCase() + " priority job card — " + nullToDash(ticket.getTrainNumber()),
            nullToDash(ticket.getDescription()),
            nullToDash(ticket.getTrainNumber()),
            List.of(
                field("Ticket", ticket.getId()),
                field("Train", nullToDash(ticket.getTrainNumber())),
                field("Priority", ticket.getPriority()),
                field("Raised By", nullToDash(ticket.getCreatedBy()))
            ),
            "A new " + ticket.getPriority().toLowerCase() + "-priority job card was raised for " +
            nullToDash(ticket.getTrainNumber()) + ". Review and assign promptly.",
            List.of(1, 1, 1),
            List.of("MDS"),
            null
        );
    }

    private void evaluateOpenPastSla(MaintenanceTicketDto ticket) {
        String alertId = "ALT-SLA-" + ticket.getId();
        boolean openPastSla = "OPEN".equalsIgnoreCase(ticket.getStatus())
                && ticket.getCreatedAt() != null
                && Duration.between(ticket.getCreatedAt(), Instant.now()).toHours() > slaOpenHours;

        if (openPastSla) {
            long hoursOpen = Duration.between(ticket.getCreatedAt(), Instant.now()).toHours();
            publisher.publishLevel(alertId, Severity.SEV2,
                "Job card stuck OPEN past SLA — " + ticket.getId(),
                nullToDash(ticket.getTrainNumber()) + " · open " + hoursOpen + "h",
                nullToDash(ticket.getTrainNumber()),
                List.of(
                    field("Ticket", ticket.getId()),
                    field("Train", nullToDash(ticket.getTrainNumber())),
                    field("Hours Open", String.valueOf(hoursOpen)),
                    field("SLA Window", slaOpenHours + "h")
                ),
                "Ticket " + ticket.getId() + " has sat OPEN for " + hoursOpen + " hours, past the " +
                slaOpenHours + "h SLA window. Needs triage.",
                List.of((int) hoursOpen, (int) hoursOpen, (int) hoursOpen),
                List.of("MDS"),
                null
            );
        } else {
            publisher.resolveLevel(alertId);
        }
    }

    private void evaluateCompletedCloseOut(MaintenanceTicketDto ticket) {
        String watermarkKey = "ticketstatus:" + ticket.getId();
        String previousStatus = stateStore.get(watermarkKey).orElse(null);
        String currentStatus = ticket.getStatus();

        if (previousStatus != null && "COMPLETED".equalsIgnoreCase(currentStatus) && !"COMPLETED".equalsIgnoreCase(previousStatus)) {
            String dedupeKey = "seen-completed:" + ticket.getId();

            publisher.publishEventOnce(dedupeKey, "ALT-CLOSEOUT", Severity.SEV1,
                "Job card completed — " + ticket.getId(),
                nullToDash(ticket.getTrainNumber()),
                nullToDash(ticket.getTrainNumber()),
                List.of(
                    field("Ticket", ticket.getId()),
                    field("Train", nullToDash(ticket.getTrainNumber())),
                    field("Description", nullToDash(ticket.getDescription())),
                    field("Raised By", nullToDash(ticket.getCreatedBy()))
                ),
                "Ticket " + ticket.getId() + " has been marked COMPLETED.",
                List.of(1, 1, 1),
                null,
                ticket.getCreatedBy()
            );
        }

        if (currentStatus != null) {
            stateStore.put(watermarkKey, currentStatus);
        }
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private Alert.AlertField field(String label, String value) {
        return new Alert.AlertField(label, value);
    }
}
