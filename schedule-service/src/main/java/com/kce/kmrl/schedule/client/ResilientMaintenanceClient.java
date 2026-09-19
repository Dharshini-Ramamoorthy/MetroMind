package com.kce.kmrl.schedule.client;

import com.kce.kmrl.schedule.dto.MaintenanceTicketDto;
import com.kce.kmrl.schedule.util.TimeUtil;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ResilientMaintenanceClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientMaintenanceClient.class);

    private static final Set<String> RESOLVED_STATUSES = Set.of("COMPLETED", "CANCELLED", "REJECTED");

    private final MaintenanceClient maintenanceClient;

    @Lazy
    @Autowired
    private ResilientMaintenanceClient self;

    public ResilientMaintenanceClient(MaintenanceClient maintenanceClient) {
        this.maintenanceClient = maintenanceClient;
    }

    public boolean hasOpenTicket(String trainId, String trainNumber, String targetServiceDate) {
        ResilientMaintenanceClient client = (self != null) ? self : this;
        Set<String> blocked = client.findTrainsWithActiveTickets(targetServiceDate);
        if (blocked.isEmpty()) return false;

        String tId = trainId != null ? trainId.trim().toUpperCase() : "";
        String tNum = trainNumber != null ? trainNumber.trim().toUpperCase() : "";
        return blocked.contains(tId) || blocked.contains(tNum);
    }

    public boolean hasOpenTicket(String trainId, String trainNumber) {
        return hasOpenTicket(trainId, trainNumber, TimeUtil.today());
    }

    public Set<String> findTrainsWithActiveTickets() {
        ResilientMaintenanceClient client = (self != null) ? self : this;
        return client.findTrainsWithActiveTickets(TimeUtil.today());
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
                    : TimeUtil.todayDate();
        } catch (Exception e) {
            targetDate = TimeUtil.todayDate();
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

    private Set<String> fallbackFindTrainsWithActiveTicketsWithDate(String targetServiceDate, Throwable ex) {
        log.warn("maintenance-service unreachable while resolving active maintenance tickets: {}", ex.getMessage());
        return Collections.emptySet();
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
