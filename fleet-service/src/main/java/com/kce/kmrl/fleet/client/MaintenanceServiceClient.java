package com.kce.kmrl.fleet.client;

import com.kce.kmrl.fleet.dto.MaintenanceTicketDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class MaintenanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceServiceClient.class);

    private static final Set<String> RESOLVED_STATUSES = Set.of("COMPLETED", "CANCELLED", "REJECTED");

    private final RestTemplate restTemplate;
    private final String maintenanceServiceUrl;

    public MaintenanceServiceClient(
            RestTemplate restTemplate,
            @Value("${maintenance.service.url:http://localhost:8084}") String maintenanceServiceUrl) {
        this.restTemplate = restTemplate;
        this.maintenanceServiceUrl = maintenanceServiceUrl;
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackFindTrainsWithActiveTickets")
    public Set<String> findTrainsWithActiveTickets() {
        String url = maintenanceServiceUrl + "/api/v1/maintenance/tickets";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "fleet-service");
        headers.set("X-User-Role", "SADA");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<MaintenanceTicketDto[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, MaintenanceTicketDto[].class);

            MaintenanceTicketDto[] tickets = response.getBody();
            if (tickets == null) {
                return Collections.emptySet();
            }

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            Set<String> result = new HashSet<>();
            for (MaintenanceTicketDto ticket : tickets) {
                if (ticket.getTrainNumber() == null || ticket.getStatus() == null) {
                    continue;
                }
                String status = ticket.getStatus().trim().toUpperCase();
                if (RESOLVED_STATUSES.contains(status)) {
                    continue;
                }

                String trainNum = ticket.getTrainNumber().trim().toUpperCase();

                if ("SCHEDULED".equals(status) || "ROUTINE_CHECK".equalsIgnoreCase(ticket.getRepairType())) {
                    if (ticket.getPlannedMaintenanceDate() != null && !ticket.getPlannedMaintenanceDate().isBlank()) {
                        try {
                            String rawDate = ticket.getPlannedMaintenanceDate().trim();

                            if (rawDate.length() > 10) rawDate = rawDate.substring(0, 10);
                            LocalDate pDate = LocalDate.parse(rawDate);
                            if (!today.isBefore(pDate)) {
                                result.add(trainNum);
                            }
                        } catch (Exception e) {
                            log.warn("Could not parse plannedMaintenanceDate '{}' for train {}, skipping block.",
                                    ticket.getPlannedMaintenanceDate(), trainNum);
                        }
                    } else {
                        result.add(trainNum);
                    }
                } else {

                    result.add(trainNum);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch maintenance tickets from {}: {}", maintenanceServiceUrl, e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<String> fallbackFindTrainsWithActiveTickets(Throwable ex) {
        log.warn("Could not resolve active maintenance tickets from {} ({}): {}",
                maintenanceServiceUrl, ex.getClass().getSimpleName(), ex.getMessage());
        return Collections.emptySet();
    }
}
