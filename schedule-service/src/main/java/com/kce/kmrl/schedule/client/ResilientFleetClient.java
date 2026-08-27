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

        Set<String> activeMaintenanceTickets = maintenanceClient.findTrainsWithActiveTickets();

        return standby.stream()
                .filter(t -> t != null && t.getStatus() != null)
                .filter(t -> !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                .filter(t -> !matchesMaintenanceSet(t, activeMaintenanceTickets))
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
        List<TrackGroupDto> groups = fleetClient.getYardTracks();
        if (groups == null) return Collections.emptyList();

        Set<String> activeMaintenanceTickets = maintenanceClient.findTrainsWithActiveTickets();

        return groups.stream()
                .filter(g -> g.getTrains() != null)
                .flatMap(g -> g.getTrains().stream())
                .filter(t -> t != null && t.getStatus() != null)
                .filter(t -> !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                .filter(t -> !matchesMaintenanceSet(t, activeMaintenanceTickets))
                .collect(Collectors.toList());
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
        if (train == null || activeTickets == null || activeTickets.isEmpty()) return false;

        String id = train.getId() != null ? train.getId().trim().toUpperCase() : "";
        String number = train.getTrainNumber() != null ? train.getTrainNumber().trim().toUpperCase() : "";

        for (String active : activeTickets) {
            String actUpper = active.trim().toUpperCase();
            if (!id.isEmpty() && (id.equals(actUpper) || id.contains(actUpper) || actUpper.contains(id))) return true;
            if (!number.isEmpty() && (number.equals(actUpper) || number.contains(actUpper) || actUpper.contains(number))) return true;

            String actDigits = actUpper.replaceAll("\\D+", "");
            if (!actDigits.isEmpty()) {
                try {
                    int actNum = Integer.parseInt(actDigits);

                    String idDigits = id.replaceAll("\\D+", "");
                    if (!idDigits.isEmpty() && Integer.parseInt(idDigits) == actNum) return true;

                    String numDigits = number.replaceAll("\\D+", "");
                    if (!numDigits.isEmpty() && Integer.parseInt(numDigits) == actNum) return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
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
        log.warn("fleet-service unreachable while fetching the yard for schedule generation ({}): {}.",
                ex.getClass().getSimpleName(), ex.getMessage());
        return null;
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
