package com.kce.kmrl.alert.integration.client;

import com.kce.kmrl.alert.integration.dto.MaintenanceTicketDto;
import com.kce.kmrl.alert.integration.dto.CreateMaintenanceTicketRequest;
import org.springframework.http.ResponseEntity;
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
public class MaintenanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String maintenanceServiceUrl;

    public MaintenanceServiceClient(
            RestTemplate restTemplate,
            @Value("${maintenance.service.url:http://maintenance-service}") String maintenanceServiceUrl) {
        this.restTemplate = restTemplate;
        this.maintenanceServiceUrl = maintenanceServiceUrl;
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackGetAllTickets")
    public ServiceResult<List<MaintenanceTicketDto>> getAllTicketsResult() {
        String url = maintenanceServiceUrl + "/api/v1/maintenance/tickets";
        MaintenanceTicketDto[] tickets = restTemplate.getForObject(url, MaintenanceTicketDto[].class);
        return ServiceResult.available(tickets == null ? Collections.emptyList() : List.of(tickets));
    }

    private ServiceResult<List<MaintenanceTicketDto>> fallbackGetAllTickets(Throwable ex) {
        log.warn("Could not fetch tickets from {} ({}): {}. Skipping maintenance-based rules this cycle.",
                maintenanceServiceUrl, ex.getClass().getSimpleName(), ex.getMessage());
        return ServiceResult.unavailable();
    }

    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackCreateTicket")
    public MaintenanceTicketDto createTicket(CreateMaintenanceTicketRequest request) {
        String url = maintenanceServiceUrl + "/api/v1/maintenance/tickets";
        ResponseEntity<MaintenanceTicketDto> response =
                restTemplate.postForEntity(url, request, MaintenanceTicketDto.class);
        if (response.getBody() == null || response.getBody().getId() == null) {
            throw new IllegalStateException("Maintenance service returned no work-order id.");
        }
        return response.getBody();
    }

    private MaintenanceTicketDto fallbackCreateTicket(CreateMaintenanceTicketRequest request, Throwable ex) {
        throw new IllegalStateException("Maintenance service is unavailable; dispatch was not completed.", ex);
    }

    public List<MaintenanceTicketDto> getAllTickets() {
        ServiceResult<List<MaintenanceTicketDto>> result = getAllTicketsResult();
        return result.isAvailable() ? result.getData() : Collections.emptyList();
    }
}
