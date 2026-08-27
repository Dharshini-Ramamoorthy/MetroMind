package com.kce.kmrl.alert.rules;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.integration.client.ApproverServiceClient;
import com.kce.kmrl.alert.integration.client.ServiceResult;
import com.kce.kmrl.alert.integration.dto.ApprovalTaskDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kce.kmrl.alert.repository.AlertRepository;

@Component
public class ApproverRuleEvaluator {

    private static final Map<String, Set<String>> DECISION_OWNERS = Map.of(
        "SCHEDULE_PROPOSAL", Set.of("OC", "SADA"),
        "MAINTENANCE_DEFECT", Set.of("MDS", "SADA"),
        "CERTIFICATE_OF_FITNESS", Set.of("SADA")
    );
    private static final Set<String> DEFAULT_OWNERS = Set.of("SADA");

    private final ApproverServiceClient approverClient;
    private final RuleAlertPublisher publisher;
    private final long slaHours;
    private final AlertRepository alertRepository;

    public ApproverRuleEvaluator(
            ApproverServiceClient approverClient,
            RuleAlertPublisher publisher,
            AlertRepository alertRepository,
            @Value("${alert.rules.approver-sla.hours:4}") long slaHours) {
        this.approverClient = approverClient;
        this.publisher = publisher;
        this.alertRepository = alertRepository;
        this.slaHours = slaHours;
    }

    public void evaluate() {
        ServiceResult<List<ApprovalTaskDto>> result = approverClient.getPendingTasksResult();
        if (!result.isAvailable()) return;
        List<ApprovalTaskDto> pending = result.getData();
        Set<String> currentTaskIds = new java.util.HashSet<>();
        for (ApprovalTaskDto task : pending) {
            if (task != null && task.getTaskId() != null) currentTaskIds.add(task.getTaskId());
            evaluateNewPendingItem(task);
            evaluatePendingTooLong(task);
        }
        alertRepository.findByIdStartingWith("ALT-QUEUESLA-").stream()
                .filter(a -> !currentTaskIds.contains(extractSuffix(a.getId(), "ALT-QUEUESLA-")))
                .forEach(a -> publisher.resolveLevel(a.getId()));
    }

    private String extractSuffix(String id, String prefix) {
        return id != null && id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    private void evaluateNewPendingItem(ApprovalTaskDto task) {
        String dedupeKey = "seen-pending-task:" + task.getTaskId();
        List<String> owners = owningRoles(task.getRequestType());

        publisher.publishEventOnce(dedupeKey, "ALT-QUEUE", Severity.SEV2,
            "New item awaiting sign-off — " + nullToDash(task.getTitle()),
            nullToDash(task.getRequestType()),
            nullToDash(task.getTargetEntityId()),
            List.of(
                field("Task", task.getTaskId()),
                field("Type", nullToDash(task.getRequestType())),
                field("Priority", nullToDash(task.getPriority())),
                field("Submitted By", nullToDash(task.getRequestedBy()))
            ),
            "A new " + nullToDash(task.getRequestType()) + " request is awaiting sign-off.",
            List.of(1, 1, 1),
            owners,
            null
        );
    }

    private void evaluatePendingTooLong(ApprovalTaskDto task) {
        String alertId = "ALT-QUEUESLA-" + task.getTaskId();
        boolean pastSla = task.getCreatedAt() != null
                && Duration.between(task.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toInstant(), Instant.now()).toHours() > slaHours;

        if (pastSla) {
            long hoursPending = Duration.between(task.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toInstant(), Instant.now()).toHours();
            List<String> owners = withAdmin(owningRoles(task.getRequestType()));

            publisher.publishLevel(alertId, Severity.SEV3,
                "Approval pending too long — " + nullToDash(task.getTitle()),
                nullToDash(task.getRequestType()) + " · pending " + hoursPending + "h",
                nullToDash(task.getTargetEntityId()),
                List.of(
                    field("Task", task.getTaskId()),
                    field("Type", nullToDash(task.getRequestType())),
                    field("Hours Pending", String.valueOf(hoursPending)),
                    field("SLA Window", slaHours + "h")
                ),
                "This request has been pending " + hoursPending + " hours, past the " +
                slaHours + "h SLA window. Needs a decision.",
                List.of((int) hoursPending, (int) hoursPending, (int) hoursPending),
                owners,
                null
            );
        } else {
            publisher.resolveLevel(alertId);
        }
    }

    private List<String> owningRoles(String requestType) {
        Set<String> owners = requestType != null
                ? DECISION_OWNERS.getOrDefault(requestType.toUpperCase(), DEFAULT_OWNERS)
                : DEFAULT_OWNERS;
        return new ArrayList<>(owners);
    }

    private List<String> withAdmin(List<String> owners) {
        if (owners.contains("SADA")) {
            return owners;
        }
        List<String> withAdmin = new ArrayList<>(owners);
        withAdmin.add("SADA");
        return withAdmin;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private Alert.AlertField field(String label, String value) {
        return new Alert.AlertField(label, value);
    }
}
