package com.kce.kmrl.approver.service;

import com.kce.kmrl.approver.dto.ApprovalDecisionRequest;
import com.kce.kmrl.approver.dto.ApprovalHistoryDTO;
import com.kce.kmrl.approver.dto.ApprovalTaskDTO;
import com.kce.kmrl.approver.dto.ApproverStatsResponse;
import com.kce.kmrl.approver.entity.ApprovalHistory;
import com.kce.kmrl.approver.entity.ApprovalStatus;
import com.kce.kmrl.approver.entity.ApprovalTask;
import com.kce.kmrl.approver.entity.RequestType;
import com.kce.kmrl.approver.repository.ApprovalHistoryRepository;
import com.kce.kmrl.approver.repository.ApprovalTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApproverServiceImpl implements ApproverService {

    private static final Logger log = LoggerFactory.getLogger(ApproverServiceImpl.class);

    private final ApprovalTaskRepository taskRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final RestClient loadBalancedRestClient;

    private static final String SYSTEM_USER_ID = "approver-service";
    private static final String SYSTEM_USER_ROLE = "SYSTEM";

    @Value("${user.internal-secret:local-dev-internal-secret-change-me}")
    private String userServiceInternalSecret;

    public ApproverServiceImpl(
            ApprovalTaskRepository taskRepository,
            ApprovalHistoryRepository historyRepository,
            @LoadBalanced RestClient.Builder restClientBuilder) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.loadBalancedRestClient = restClientBuilder.build();
    }

    private String normalizeRole(String role) {
        if (role == null) return "";
        return role.replace("ROLE_", "").toUpperCase().trim();
    }

    @Override
    public ApprovalTaskDTO createApprovalTask(ApprovalTask task) {
        if (task.getRequestType() == RequestType.SCHEDULE_PROPOSAL && task.getTargetEntityId() != null) {
            List<ApprovalTask> existing = taskRepository.findByTargetEntityId(task.getTargetEntityId());
            if (existing != null && !existing.isEmpty()) {
                taskRepository.deleteAll(existing);
            }
        }

        task.setStatus(ApprovalStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        ApprovalTask saved = taskRepository.save(task);
        return mapToDTO(saved);
    }

    @Override
    public List<ApprovalTaskDTO> getPendingTasks(String approverRole) {
        String role = normalizeRole(approverRole);
        List<ApprovalTask> pending = taskRepository.findByStatus(ApprovalStatus.PENDING);

        return pending.stream()
                .filter(task -> {
                    if ("ADMIN".equals(role)) {
                        return task.getRequestType() == RequestType.USER_REGISTRATION;
                    } else if ("SADA".equals(role) || "APPROVER".equals(role)) {
                        return task.getRequestType() != RequestType.USER_REGISTRATION;
                    }
                    return false;
                })
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private void assertCanDecide(ApprovalTask task, String approverRole) {
        String role = normalizeRole(approverRole);
        if ("ADMIN".equals(role)) {
            if (task.getRequestType() != RequestType.USER_REGISTRATION) {
                throw new AccessDeniedException("ADMIN can only decide User Registration approvals.");
            }
        } else if ("SADA".equals(role) || "APPROVER".equals(role)) {
            if (task.getRequestType() == RequestType.USER_REGISTRATION) {
                throw new AccessDeniedException("SADA cannot decide User Registration approvals.");
            }
        }
    }

    @Override
    public ApprovalTaskDTO processDecision(String taskId, ApprovalDecisionRequest decision, String approverUser, String approverRole) {
        ApprovalTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Approval task not found: " + taskId));

        assertCanDecide(task, approverRole);

        ApprovalStatus newStatus = decision.getDecision();
        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        ApprovalHistory history = new ApprovalHistory();
        history.setTaskId(taskId);
        history.setApproverUsername(approverUser);
        history.setDecision(newStatus);
        history.setComments(decision.getComments());
        history.setTimestamp(LocalDateTime.now());
        historyRepository.save(history);

        if (newStatus == ApprovalStatus.APPROVED) {
            executeApprovalCallback(task);
        } else if (newStatus == ApprovalStatus.REJECTED || newStatus == ApprovalStatus.CHANGES_REQUESTED) {
            executeRejectionOrChangesCallback(task, newStatus, decision.getComments());
        }

        return mapToDTO(task);
    }

    private void executeApprovalCallback(ApprovalTask task) {
        try {
            if (task.getRequestType() == RequestType.SCHEDULE_PROPOSAL && task.getTargetEntityId() != null) {
                if (task.getTargetEntityId().matches("\\d{4}-\\d{2}-\\d{2}")) {
                    loadBalancedRestClient.post()
                            .uri("http://schedule-service/api/v1/schedule/approve-day/" + task.getTargetEntityId())
                            .header("X-User-Id", SYSTEM_USER_ID)
                            .header("X-User-Role", SYSTEM_USER_ROLE)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Approval callback executed for batch SCHEDULE_PROPOSAL targetEntityId={}", task.getTargetEntityId());
                } else {
                    Map<String, String> body = Map.of("status", "PLANNED", "reason", "Approved by SADA");
                    loadBalancedRestClient.patch()
                            .uri("http://schedule-service/api/v1/schedule/trips/" + task.getTargetEntityId() + "/status")
                            .header("X-User-Id", SYSTEM_USER_ID)
                            .header("X-User-Role", SYSTEM_USER_ROLE)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Approval callback executed for single trip SCHEDULE_PROPOSAL tripId={}", task.getTargetEntityId());
                }
            } else if (task.getRequestType() == RequestType.CERTIFICATE_OF_FITNESS && task.getTargetEntityId() != null) {
                Map<String, String> body = Map.of("status", "COMPLETED", "comments", "CoF Approved");
                loadBalancedRestClient.patch()
                        .uri("http://maintenance-service/api/v1/maintenance/tickets/" + task.getTargetEntityId() + "/status")
                        .header("X-User-Id", SYSTEM_USER_ID)
                        .header("X-User-Role", SYSTEM_USER_ROLE)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Approval callback executed for CERTIFICATE_OF_FITNESS ticketId={}: status -> COMPLETED", task.getTargetEntityId());
            } else if (task.getRequestType() == RequestType.USER_REGISTRATION && task.getTargetEntityId() != null) {
                loadBalancedRestClient.post()
                        .uri("http://user-service/api/v1/auth/internal/registrations/" + task.getTargetEntityId() + "/approve")
                        .header("X-Internal-Secret", userServiceInternalSecret)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Approval callback executed for USER_REGISTRATION pendingRegistrationId={}: user account created", task.getTargetEntityId());
            } else if (task.getRequestType() == RequestType.FLEET_OVERRIDE && task.getTargetEntityId() != null) {
                Map<String, String> body = Map.of("status", "STANDBY");
                loadBalancedRestClient.put()
                        .uri("http://fleet-service/api/v1/fleet/" + task.getTargetEntityId() + "/status")
                        .header("X-User-Id", SYSTEM_USER_ID)
                        .header("X-User-Role", SYSTEM_USER_ROLE)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Approval callback executed for FLEET_OVERRIDE trainId={}: status -> STANDBY", task.getTargetEntityId());
            } else if (task.getRequestType() == RequestType.MAINTENANCE_DEFECT) {
                log.warn("No downstream callback configured for MAINTENANCE_DEFECT task {}", task.getId());
            } else {
                log.warn("No downstream callback configured for {} task {}", task.getRequestType(), task.getId());
            }
        } catch (Exception e) {
            log.error("Failed to execute approval callback for task {}: {}", task.getId(), e.getMessage(), e);
        }
    }

    private void executeRejectionOrChangesCallback(ApprovalTask task, ApprovalStatus status, String comments) {
        try {
            String note = (status == ApprovalStatus.CHANGES_REQUESTED ? "[CHANGES_REQUESTED] " : "[REJECTED] ")
                    + (comments != null ? comments : "");

            if (task.getRequestType() == RequestType.SCHEDULE_PROPOSAL && task.getTargetEntityId() != null) {
                if (task.getTargetEntityId().matches("\\d{4}-\\d{2}-\\d{2}")) {
                    if (status == ApprovalStatus.REJECTED) {
                        loadBalancedRestClient.post()
                                .uri("http://schedule-service/api/v1/schedule/reject-day/" + task.getTargetEntityId())
                                .header("X-User-Id", SYSTEM_USER_ID)
                                .header("X-User-Role", SYSTEM_USER_ROLE)
                                .retrieve()
                                .toBodilessEntity();
                        log.info("Rejection callback executed for batch SCHEDULE_PROPOSAL targetEntityId={}", task.getTargetEntityId());
                    } else {
                        log.info("Changes requested for batch SCHEDULE_PROPOSAL targetEntityId={}; keeping trips in draft/editable state for OC revision", task.getTargetEntityId());
                    }
                } else {
                    Map<String, String> body = Map.of("status", "CANCELLED", "reason", note);
                    loadBalancedRestClient.patch()
                            .uri("http://schedule-service/api/v1/schedule/trips/" + task.getTargetEntityId() + "/status")
                            .header("X-User-Id", SYSTEM_USER_ID)
                            .header("X-User-Role", SYSTEM_USER_ROLE)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Rejection/Changes callback executed for single trip SCHEDULE_PROPOSAL tripId={}", task.getTargetEntityId());
                }
            } else if (task.getRequestType() == RequestType.CERTIFICATE_OF_FITNESS && task.getTargetEntityId() != null) {
                Map<String, String> body = Map.of("status", "IN_PROGRESS", "comments", note);
                loadBalancedRestClient.patch()
                        .uri("http://maintenance-service/api/v1/maintenance/tickets/" + task.getTargetEntityId() + "/status")
                        .header("X-User-Id", SYSTEM_USER_ID)
                        .header("X-User-Role", SYSTEM_USER_ROLE)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Rejection/Changes callback executed for CERTIFICATE_OF_FITNESS ticketId={}: status -> IN_PROGRESS with note: {}",
                        task.getTargetEntityId(), note);
            } else if (task.getRequestType() == RequestType.USER_REGISTRATION && task.getTargetEntityId() != null) {
                Map<String, String> body = Map.of("reason", note);
                loadBalancedRestClient.post()
                        .uri("http://user-service/api/v1/auth/internal/registrations/" + task.getTargetEntityId() + "/reject")
                        .header("X-Internal-Secret", userServiceInternalSecret)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Rejection callback executed for USER_REGISTRATION pendingRegistrationId={}", task.getTargetEntityId());
            } else if (task.getRequestType() == RequestType.FLEET_OVERRIDE && task.getTargetEntityId() != null) {
                Map<String, String> body = Map.of("status", "IN_MAINTENANCE");
                loadBalancedRestClient.put()
                        .uri("http://fleet-service/api/v1/fleet/" + task.getTargetEntityId() + "/status")
                        .header("X-User-Id", SYSTEM_USER_ID)
                        .header("X-User-Role", SYSTEM_USER_ROLE)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Rejection/Changes callback executed for FLEET_OVERRIDE trainId={}: status -> IN_MAINTENANCE", task.getTargetEntityId());
            } else if (task.getRequestType() == RequestType.MAINTENANCE_DEFECT) {
                log.warn("No downstream callback configured for MAINTENANCE_DEFECT task {}", task.getId());
            } else {
                log.warn("No downstream callback configured for {} task {}", task.getRequestType(), task.getId());
            }
        } catch (Exception e) {
            log.error("Failed to execute rejection/changes callback for task {}: {}", task.getId(), e.getMessage(), e);
        }
    }


    @Override
    public ApproverStatsResponse getApproverStats(String approverRole) {
        String role = normalizeRole(approverRole);
        List<ApprovalTask> pending = taskRepository.findByStatus(ApprovalStatus.PENDING);
        List<ApprovalHistory> allHistory = historyRepository.findAll();

        long pendingCount = pending.stream().filter(task -> {
            if ("ADMIN".equals(role)) return task.getRequestType() == RequestType.USER_REGISTRATION;
            if ("SADA".equals(role) || "APPROVER".equals(role)) return task.getRequestType() != RequestType.USER_REGISTRATION;
            return false;
        }).count();

        Map<String, RequestType> taskTypeMap = new HashMap<>();
        taskRepository.findAll().forEach(t -> taskTypeMap.put(t.getId(), t.getRequestType()));

        long approvedCount = allHistory.stream().filter(h -> {
            if (h.getDecision() != ApprovalStatus.APPROVED) return false;
            RequestType rt = taskTypeMap.get(h.getTaskId());
            if ("ADMIN".equals(role)) return rt == RequestType.USER_REGISTRATION;
            if ("SADA".equals(role) || "APPROVER".equals(role)) return rt != RequestType.USER_REGISTRATION;
            return false;
        }).count();

        long rejectedCount = allHistory.stream().filter(h -> {
            if (h.getDecision() != ApprovalStatus.REJECTED && h.getDecision() != ApprovalStatus.CHANGES_REQUESTED) return false;
            RequestType rt = taskTypeMap.get(h.getTaskId());
            if ("ADMIN".equals(role)) return rt == RequestType.USER_REGISTRATION;
            if ("SADA".equals(role) || "APPROVER".equals(role)) return rt != RequestType.USER_REGISTRATION;
            return false;
        }).count();

        ApproverStatsResponse stats = new ApproverStatsResponse();
        stats.setPendingApprovals(pendingCount);
        stats.setTotalApproved(approvedCount);
        stats.setTotalRejected(rejectedCount);
        return stats;
    }

    @Override
    public List<ApprovalHistoryDTO> getApprovalHistory(String approverRole, String approverUser) {
        String role = normalizeRole(approverRole);
        List<ApprovalHistory> allHistory = historyRepository.findAll();

        Map<String, ApprovalTask> taskMap = new HashMap<>();
        taskRepository.findAll().forEach(t -> taskMap.put(t.getId(), t));

        return allHistory.stream().filter(h -> {
            ApprovalTask task = taskMap.get(h.getTaskId());
            RequestType rt = task != null ? task.getRequestType() : null;
            if ("ADMIN".equals(role)) {
                return rt == RequestType.USER_REGISTRATION;
            } else if ("SADA".equals(role) || "APPROVER".equals(role)) {
                return rt != RequestType.USER_REGISTRATION;
            } else if ("MDS".equals(role) || "MAINTENANCE".equals(role) || role.contains("MAINT")) {
                return rt == RequestType.CERTIFICATE_OF_FITNESS || rt == RequestType.MAINTENANCE_DEFECT;
            } else if ("OC".equals(role) || role.contains("OPS")) {
                return rt == RequestType.SCHEDULE_PROPOSAL;
            }
            return true;
        }).map(h -> {
            ApprovalHistoryDTO dto = new ApprovalHistoryDTO();
            dto.setId(h.getId());
            dto.setTaskId(h.getTaskId());
            dto.setApproverUsername(h.getApproverUsername());
            dto.setDecision(h.getDecision());
            dto.setComments(h.getComments());
            dto.setTimestamp(h.getTimestamp());
            ApprovalTask task = taskMap.get(h.getTaskId());
            if (task != null) {
                dto.setTitle(task.getTitle());
                dto.setRequestType(task.getRequestType());
            }
            return dto;
        }).collect(Collectors.toList());
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
        return dto;
    }
}