package com.kce.kmrl.schedule.client;

import com.kce.kmrl.schedule.dto.MaintenanceTicketDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ResilientMaintenanceClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientMaintenanceClient.class);

    private static final Set<String> RESOLVED_STATUSES = Set.of("COMPLETED", "CANCELLED", "REJECTED");

    private final MaintenanceClient maintenanceClient;

    public ResilientMaintenanceClient(MaintenanceClient maintenanceClient) {
        this.maintenanceClient = maintenanceClient;
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackHasOpenTicket")
    public boolean hasOpenTicket(String trainId, String trainNumber, String targetServiceDate) {
        Set<String> blocked = findTrainsWithActiveTickets(targetServiceDate);
        if (blocked.isEmpty()) return false;

        String tId = trainId != null ? trainId.trim().toUpperCase() : "";
        String tNum = trainNumber != null ? trainNumber.trim().toUpperCase() : "";
        return blocked.contains(tId) || blocked.contains(tNum);
    }

    public boolean hasOpenTicket(String trainId, String trainNumber) {
        return hasOpenTicket(trainId, trainNumber, LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
    }

    private boolean fallbackHasOpenTicket(String trainId, String trainNumber, String targetServiceDate, Throwable ex) {
        log.warn("maintenance-service unreachable while checking open tickets for train {} / {} on {}: {}",
                trainId, trainNumber, targetServiceDate, ex.getMessage());
        return false;
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackFindTrainsWithActiveTicketsNoArgs")
    public Set<String> findTrainsWithActiveTickets() {
        return findTrainsWithActiveTickets(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackFindTrainsWithActiveTicketsWithDate")
    public Set<String> findTrainsWithActiveTickets(String targetServiceDate) {
        List<MaintenanceTicketDto> tickets = maintenanceClient.getAllTickets();
        if (tickets == null) return Collections.emptySet();

        LocalDate targetDate;
        try {
            targetDate = (targetServiceDate != null && !targetServiceDate.isBlank())
                    ? LocalDate.parse(targetServiceDate.trim())
                    : LocalDate.now(ZoneId.of("Asia/Kolkata"));
        } catch (Exception e) {
            targetDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        }

        Set<String> result = new HashSet<>();
        for (MaintenanceTicketDto ticket : tickets) {
            if (ticket.getTrainNumber() == null || ticket.getStatus() == null) continue;
            String status = ticket.getStatus().trim().toUpperCase();
            if (RESOLVED_STATUSES.contains(status)) continue;

            String trainNum = ticket.getTrainNumber().trim().toUpperCase();

            if ("SCHEDULED".equals(status) || "ROUTINE_CHECK".equalsIgnoreCase(ticket.getRepairType())) {
                if (ticket.getPlannedMaintenanceDate() != null && !ticket.getPlannedMaintenanceDate().isBlank()) {
                    try {
                        String rawDate = ticket.getPlannedMaintenanceDate().trim();

                        if (rawDate.length() > 10) rawDate = rawDate.substring(0, 10);
                        LocalDate pDate = LocalDate.parse(rawDate);

                        if (!targetDate.isBefore(pDate)) {
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
    }

    private Set<String> fallbackFindTrainsWithActiveTicketsNoArgs(Throwable ex) {
        log.warn("maintenance-service unreachable while resolving active maintenance tickets: {}", ex.getMessage());
        return Collections.emptySet();
    }

    private Set<String> fallbackFindTrainsWithActiveTicketsWithDate(String targetServiceDate, Throwable ex) {
        return fallbackFindTrainsWithActiveTicketsNoArgs(ex);
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackMarkTrainPulled")
    public void markTrainPulled(String trainNumber) {
        maintenanceClient.markTrainPulled(trainNumber);
    }

    private void fallbackMarkTrainPulled(String trainNumber, Throwable ex) {
        log.warn("maintenance-service unreachable while marking train {} pulled: {}",
                trainNumber, ex.getMessage());
    }
}
