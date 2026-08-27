package com.kce.kmrl.client;

import com.kce.kmrl.dto.FleetSummaryDto;
import com.kce.kmrl.dto.MaintenanceTicketDto;
import com.kce.kmrl.dto.ScheduleTripDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResilientReportSourceClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientReportSourceClient.class);

    private final FleetClient fleetClient;
    private final MaintenanceClient maintenanceClient;
    private final ScheduleClient scheduleClient;

    public ResilientReportSourceClient(FleetClient fleetClient,
                                        MaintenanceClient maintenanceClient,
                                        ScheduleClient scheduleClient) {
        this.fleetClient = fleetClient;
        this.maintenanceClient = maintenanceClient;
        this.scheduleClient = scheduleClient;
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackGetFleetSummary")
    public FleetSummaryDto getFleetSummary() {
        return fleetClient.getSummary();
    }

    @Retry(name = "maintenanceService")
    @CircuitBreaker(name = "maintenanceService", fallbackMethod = "fallbackGetTickets")
    public List<MaintenanceTicketDto> getMaintenanceTickets() {
        return maintenanceClient.getTickets();
    }

    @Retry(name = "scheduleService")
    @CircuitBreaker(name = "scheduleService", fallbackMethod = "fallbackGetTrips")
    public List<ScheduleTripDto> getScheduleTrips() {
        return scheduleClient.getTripsInWindow();
    }

    private FleetSummaryDto fallbackGetFleetSummary(Throwable ex) {
        log.warn("fleet-service unreachable while building a report ({}): {}. " +
                "Fleet section will be marked unavailable.",
                ex.getClass().getSimpleName(), ex.getMessage());
        return null;
    }

    private List<MaintenanceTicketDto> fallbackGetTickets(Throwable ex) {
        log.warn("maintenance-service unreachable while building a report ({}): {}. " +
                "Maintenance section will be marked unavailable.",
                ex.getClass().getSimpleName(), ex.getMessage());
        return null;
    }

    private List<ScheduleTripDto> fallbackGetTrips(Throwable ex) {
        log.warn("schedule-service unreachable while building a report ({}): {}. " +
                "Schedule section will be marked unavailable.",
                ex.getClass().getSimpleName(), ex.getMessage());
        return null;
    }
}
