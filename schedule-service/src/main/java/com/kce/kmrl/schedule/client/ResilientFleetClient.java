package com.kce.kmrl.schedule.client;

import com.kce.kmrl.schedule.dto.StatusUpdateRequest;
import com.kce.kmrl.schedule.dto.TrackGroupDto;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ResilientFleetClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientFleetClient.class);

    private final FleetClient fleetClient;

    @Lazy
    @Autowired
    private ResilientMaintenanceClient maintenanceClient;

    public ResilientFleetClient(FleetClient fleetClient) {
        this.fleetClient = fleetClient;
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackGetStandbyTrains")
    public List<TrainAssetDto> getStandbyTrains() {
        List<TrainAssetDto> standby = fleetClient.getStandbyTrains();
        if (standby == null) return Collections.emptyList();

        return standby.stream()
                .filter(t -> t != null && t.getStatus() != null)
                .filter(t -> !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackGetTrainById")
    public TrainAssetDto getTrainById(String trainId) {
        return fleetClient.getTrainById(trainId);
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackCheckTrainAvailability")
    public boolean checkTrainAvailability(String trainId, String start, String end) {
        java.util.Map<String, Object> resp = fleetClient.checkTrainAvailability(trainId, start, end);
        if (resp == null || !resp.containsKey("available")) return false;
        return Boolean.TRUE.equals(resp.get("available"));
    }

    private boolean fallbackCheckTrainAvailability(String trainId, String start, String end, Throwable ex) {
        log.warn("fleet-service unreachable checking availability for train {} ({}): {}. Falling back to false.",
                trainId, ex.getClass().getSimpleName(), ex.getMessage());
        return false;
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackGetAvailableTrains")
    public List<TrainAssetDto> getAvailableTrains() {
        try {
            List<TrackGroupDto> groups = fleetClient.getYardTracks();
            if (groups != null && !groups.isEmpty()) {
                List<TrainAssetDto> trains = groups.stream()
                        .filter(g -> g.getTrains() != null)
                        .flatMap(g -> g.getTrains().stream())
                        .filter(t -> t != null && t.getStatus() != null)
                        .filter(t -> !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                        .collect(Collectors.toList());
                if (!trains.isEmpty()) {
                    return trains;
                }
            }
        } catch (Exception e) {
            log.warn("getYardTracks failed ({}), falling back to standby trains list.", e.getMessage());
        }
        return getStandbyTrains();
    }

    public List<TrainAssetDto> filterOutMaintenance(List<TrainAssetDto> trains, Set<String> activeMaintenanceTickets) {
        if (trains == null || trains.isEmpty()) return Collections.emptyList();
        if (activeMaintenanceTickets == null || activeMaintenanceTickets.isEmpty()) return trains;
        return trains.stream()
                .filter(t -> !matchesMaintenanceSet(t, activeMaintenanceTickets))
                .collect(Collectors.toList());
    }

    public List<TrainAssetDto> filterOutMaintenance(List<TrainAssetDto> trains) {
        if (trains == null || trains.isEmpty()) return Collections.emptyList();
        Set<String> activeTickets = maintenanceClient.findTrainsWithActiveTickets();
        return filterOutMaintenance(trains, activeTickets);
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackUpdateTrainStatus")
    public void updateTrainStatus(String trainId, String status) {
        fleetClient.updateTrainStatus(trainId, new StatusUpdateRequest(status));
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackAssignTrainDuty")
    public void assignTrainDuty(String trainId, String tripCode, String routeName) {
        fleetClient.assignTrainDuty(trainId, tripCode, routeName);
    }

    private boolean matchesMaintenanceSet(TrainAssetDto train, Set<String> activeTickets) {
        return com.kce.kmrl.schedule.util.TrainIdUtil.matchesMaintenanceSet(train, activeTickets);
    }

    private List<TrainAssetDto> fallbackGetStandbyTrains(Throwable ex) {
        log.warn("fleet-service unreachable while fetching standby trains ({}): {}. " +
                "Treating standby list as empty for this cycle.",
                ex.getClass().getSimpleName(), ex.getMessage());
        return Collections.emptyList();
    }

    private TrainAssetDto fallbackGetTrainById(String trainId, Throwable ex) {
        log.warn("fleet-service unreachable while fetching train {} ({}): {}.",
                trainId, ex.getClass().getSimpleName(), ex.getMessage());
        return null;
    }

    private List<TrainAssetDto> fallbackGetAvailableTrains(Throwable ex) {
        log.warn("fleet-service unreachable while fetching the yard for schedule generation ({}): {}. Falling back to standby trains list.",
                ex.getClass().getSimpleName(), ex.getMessage());
        return getStandbyTrains();
    }

    private void fallbackUpdateTrainStatus(String trainId, String status, Throwable ex) {
        log.warn("fleet-service unreachable while updating train {} to status {} ({}): {}.",
                trainId, status, ex.getClass().getSimpleName(), ex.getMessage());
    }

    private void fallbackAssignTrainDuty(String trainId, String tripCode, String routeName, Throwable ex) {
        log.warn("fleet-service unreachable while assigning duty {} ({}) to train {} ({}): {}.",
                tripCode, routeName, trainId, ex.getClass().getSimpleName(), ex.getMessage());
    }
}
