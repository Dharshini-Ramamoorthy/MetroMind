package com.kce.kmrl.alert.integration.client;

import com.kce.kmrl.alert.integration.dto.ApprovalTaskDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class ApproverServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ApproverServiceClient.class);

    private final RestTemplate restTemplate;
    private final String approverServiceUrl;

    public ApproverServiceClient(
            RestTemplate restTemplate,
            @Value("${approver.service.url:http://approver-service}") String approverServiceUrl) {
        this.restTemplate = restTemplate;
        this.approverServiceUrl = approverServiceUrl;
    }

    @Retry(name = "approverService")
    @CircuitBreaker(name = "approverService", fallbackMethod = "fallbackGetPendingTasks")
    public ServiceResult<List<ApprovalTaskDto>> getPendingTasksResult() {
        String url = approverServiceUrl + "/api/approver/tasks/pending";
        ApprovalTaskDto[] tasks = restTemplate.getForObject(url, ApprovalTaskDto[].class);
        return ServiceResult.available(tasks == null ? Collections.emptyList() : List.of(tasks));
    }

    private ServiceResult<List<ApprovalTaskDto>> fallbackGetPendingTasks(Throwable ex) {
        log.warn("Could not fetch pending tasks from {} ({}): {}. Skipping approver-queue rules this cycle.",
                approverServiceUrl, ex.getClass().getSimpleName(), ex.getMessage());
        return ServiceResult.unavailable();
    }

    public List<ApprovalTaskDto> getPendingTasks() {
        ServiceResult<List<ApprovalTaskDto>> result = getPendingTasksResult();
        return result.isAvailable() ? result.getData() : Collections.emptyList();
    }
}
