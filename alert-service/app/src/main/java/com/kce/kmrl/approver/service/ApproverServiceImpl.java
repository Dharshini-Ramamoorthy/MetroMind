package com.kce.kmrl.approver.service;

import com.kce.kmrl.approver.dto.ApprovalDecisionRequest;
import com.kce.kmrl.approver.dto.ApprovalTaskDTO;
import com.kce.kmrl.approver.dto.ApproverStatsResponse;
import com.kce.kmrl.approver.entity.ApprovalHistory;
import com.kce.kmrl.approver.entity.ApprovalStatus;
import com.kce.kmrl.approver.entity.ApprovalTask;
import com.kce.kmrl.approver.entity.RequestType;
import com.kce.kmrl.approver.repository.ApprovalHistoryRepository;
import com.kce.kmrl.approver.repository.ApprovalTaskRepository;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ApproverServiceImpl implements ApproverService {

    private final ApprovalTaskRepository taskRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final RestClient loadBalancedRestClient;
    private final MongoTemplate mongoTemplate;

    private static final Map<RequestType, Set<String>> DECISION_OWNERS = Map.of(
            RequestType.SCHEDULE_PROPOSAL, Set.of("OC", "SADA"),
            RequestType.CERTIFICATE_OF_FITNESS, Set.of("SADA"),
            RequestType.FLEET_OVERRIDE, Set.of("SADA")
    );

    private static final Set<ApprovalStatus> DECISION_STATUSES =
            Set.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED);

    private static final String SYSTEM_USER_ID = "approver-service";
    private static final String SYSTEM_USER_ROLE = "SADA";

    public ApproverServiceImpl(
            ApprovalTaskRepository taskRepository,
            ApprovalHistoryRepository historyRepository,
            @LoadBalanced RestClient.Builder restClientBuilder,
            MongoTemplate mongoTemplate) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.loadBalancedRestClient = restClientBuilder.build();
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ApprovalTaskDTO createApprovalTask(ApprovalTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Approval task must not be null.");
        }
        if (task.getTargetEntityId() == null || task.getTargetEntityId().isBlank()) {
            throw new IllegalArgumentException("targetEntityId must not be blank.");
        }
        if (task.getRequestType() == null) {
            throw new IllegalArgumentException("requestType must not be null.");
        }

        task.setStatus(ApprovalStatus.PENDING);
        task.setCascadeStatus("NOT_STARTED");
        task.setCascadeError(null);
        task.setAssignedApproverRole(resolveDecisionRole(task.getRequestType()));
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        return mapToDTO(taskRepository.save(task));
    }

    private String resolveDecisionRole(RequestType type) {
        Set<String> owners = DECISION_OWNERS.get(type);
        if (owners == null || owners.isEmpty()) {
            return null;
        }
        return owners.size() == 1 ? owners.iterator().next() : null;
    }
        if (owners.contains("SADA") && owners.size() == 1) {
            return "SADA";
        }
        return owners.iterator().next();
    }

    @Override
    public List<ApprovalTaskDTO> getPendingTasks(String approverRole) {
        String bareRole = normalizeRole(approverRole);

        return taskRepository.findByStatus(ApprovalStatus.PENDING).stream()
                .filter(task -> canDecide(task, bareRole))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private void assertCanDecide(ApprovalTask task, String approverRole) {
        if (!canDecide(task, approverRole)) {
            throw new AccessDeniedException(
                    "Role " + approverRole + " cannot decide " + task.getRequestType() + " requests."
            );
        }
    }

    private boolean canDecide(ApprovalTask task, String approverRole) {
        String role = normalizeRole(approverRole);
return DECISION_OWNERS
                .getOrDefault(task.getRequestType(), Set.of())
                .contains(role);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase().replace("ROLE_", "");
    }

    @Override
    public ApprovalTaskDTO processDecision(
            String taskId,
            ApprovalDecisionRequest decision,
            String approverUser,
            String approverRole) {

        if (decision == null || decision.getDecision() == null) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED.");
        }

        ApprovalStatus newStatus = decision.getDecision();
        if (!DECISION_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Only APPROVED or REJECTED decisions are allowed.");
        }

        if (approverUser == null || approverUser.isBlank()) {
            throw new IllegalArgumentException("Approver user must not be blank.");
        }

        ApprovalTask currentTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Approval task not found: " + taskId));
        assertCanDecide(currentTask, approverRole);

        if (currentTask.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException(
                    "Approval task " + taskId + " is not pending. Current status: "
                            + currentTask.getStatus());
        }

        Query claimQuery = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(taskId),
                Criteria.where("status").is(ApprovalStatus.PENDING)
        ));
        Update claimUpdate = new Update()
                .set("status", ApprovalStatus.PROCESSING)
                .set("cascadeStatus", "PROCESSING")
                .set("cascadeError", null)
                .set("updatedAt", LocalDateTime.now());

        ApprovalTask task = mongoTemplate.findAndModify(
                claimQuery,
                claimUpdate,
                ApprovalTask.class
        );

        if (task == null) {
            throw new IllegalStateException(
                    "Approval task " + taskId + " is already being processed or has already been decided.");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean cascadeApplied = false;
        try {

            dispatchCascade(task, newStatus, decision.getComments(), approverUser);
            cascadeApplied = true;

            task.setStatus(newStatus);
            task.setCascadeStatus("SUCCESS");
            task.setCascadeError(null);
            task.setDecidedAt(now);
            task.setDecidedBy(approverUser);
            task.setUpdatedAt(now);
            ApprovalTask saved = taskRepository.save(task);

            saveHistory(
                    taskId,
                    approverUser,
                    newStatus,
                    decision.getComments(),
                    now,
                    "DECISION",
                    "SUCCESS",
                    null
            );

            return mapToDTO(saved);
        } catch (RuntimeException ex) {
            String error = rootMessage(ex);

            if (!cascadeApplied) {
                task.setStatus(ApprovalStatus.PENDING);
                task.setCascadeStatus("FAILED");
                task.setCascadeError(error);
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);
            } else {
                task.setCascadeStatus("FINALIZATION_FAILED");
                task.setCascadeError(error);
                task.setUpdatedAt(LocalDateTime.now());
                try {
                    taskRepository.save(task);
                } catch (RuntimeException ignored) {

                }
            }

            saveHistory(
                    taskId,
                    approverUser,
                    newStatus,
                    decision.getComments(),
                    LocalDateTime.now(),
                    "DECISION",
                    "FAILED",
                    error
            );

            throw new IllegalStateException(
                    cascadeApplied
                            ? "Approval downstream action succeeded, but finalizing the approval task failed. Reconcile task " + taskId + ": " + error
                            : "Approval decision could not be completed. The task remains PENDING for retry. " + error,
                    ex);
        }
    }

    private void saveHistory(
            String taskId,
            String approverUser,
            ApprovalStatus decision,
            String comments,
            LocalDateTime timestamp,
            String eventType,
            String cascadeStatus,
            String errorMessage) {

        ApprovalHistory history = new ApprovalHistory();
        history.setTaskId(taskId);
        history.setApproverUsername(approverUser);
        history.setDecision(decision);
        history.setComments(comments);
        history.setTimestamp(timestamp);
        history.setEventType(eventType);
        history.setCascadeStatus(cascadeStatus);
        history.setErrorMessage(errorMessage);
        historyRepository.save(history);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = throwable.getMessage();
        while (current.getCause() != null) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private void dispatchCascade(
            ApprovalTask task,
            ApprovalStatus status,
            String comments,
            String approverUser) {

        String entityId = task.getTargetEntityId();

        switch (task.getRequestType()) {
            case CERTIFICATE_OF_FITNESS:

                updateMaintenanceTicketStatus(
                        entityId,
                        status == ApprovalStatus.APPROVED
                                ? "COMPLETED"
                                : "IN_PROGRESS",
                        comments
                );
                break;

            case SCHEDULE_PROPOSAL:
                updateScheduleTripStatus(
                        entityId,
                        status == ApprovalStatus.APPROVED
                                ? "PLANNED"
                                : "CANCELLED"
                );
                break;

            case FLEET_OVERRIDE:
                updateFleetStatus(
                        entityId,
                        status == ApprovalStatus.APPROVED
                                ? "STANDBY"
                                : "IN_MAINTENANCE"
                );
                break;

            case MAINTENANCE_DEFECT:

                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported approval request type: " + task.getRequestType());
        }
    }

    private void updateMaintenanceTicketStatus(
            String ticketId,
            String status,
            String comments) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        if (comments != null && !comments.isBlank()) {
            payload.put("comments", comments);
        }

        try {
            loadBalancedRestClient.patch()
                    .uri("http://maintenance-service/api/v1/maintenance/tickets/{ticketId}/status", ticketId)
                    .header("X-User-Id", SYSTEM_USER_ID)
                    .header("X-User-Role", SYSTEM_USER_ROLE)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not apply approval decision to maintenance ticket "
                            + ticketId + ": " + e.getMessage(), e);
        }
    }

    private void updateScheduleTripStatus(String tripId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);

        try {
            loadBalancedRestClient.patch()
                    .uri("http://schedule-service/api/v1/schedule/trips/{tripId}/status", tripId)
                    .header("X-User-Id", SYSTEM_USER_ID)
                    .header("X-User-Role", SYSTEM_USER_ROLE)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not apply approval decision to schedule trip "
                            + tripId + ": " + e.getMessage(), e);
        }
    }

    private void updateFleetStatus(String trainId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);

        try {
            loadBalancedRestClient.put()
                    .uri("http://fleet-service/api/v1/fleet/{trainId}/status", trainId)
                    .header("X-User-Id", SYSTEM_USER_ID)
                    .header("X-User-Role", SYSTEM_USER_ROLE)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not apply fleet override to train "
                            + trainId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public ApproverStatsResponse getApproverStats() {
        ApproverStatsResponse stats = new ApproverStatsResponse();
        stats.setPendingApprovals(taskRepository.countByStatus(ApprovalStatus.PENDING));
        stats.setTotalApproved(historyRepository.countByDecision(ApprovalStatus.APPROVED));
        stats.setTotalRejected(historyRepository.countByDecision(ApprovalStatus.REJECTED));
        return stats;
    }

    @Override
    public List<ApprovalHistory> getApprovalHistory() {
        return historyRepository.findAll();
    }

    private ApprovalTaskDTO mapToDTO(ApprovalTask task) {
        ApprovalTaskDTO dto = new ApprovalTaskDTO();
        dto.setTaskId(task.getId());
        dto.setTargetEntityId(task.getTargetEntityId());
        dto.setRequestType(task.getRequestType());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setPriority(task.getPriority());
        dto.setStatus(task.getStatus());
        dto.setRequestedBy(task.getRequestedBy());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        dto.setDecidedAt(task.getDecidedAt());
        dto.setDecidedBy(task.getDecidedBy());
        dto.setCascadeStatus(task.getCascadeStatus());
        dto.setCascadeError(task.getCascadeError());
        return dto;
    }
}
