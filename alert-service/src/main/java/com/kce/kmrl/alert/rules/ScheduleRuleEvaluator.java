package com.kce.kmrl.alert.rules;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.integration.client.FleetServiceClient;
import com.kce.kmrl.alert.integration.client.ScheduleServiceClient;
import com.kce.kmrl.alert.integration.client.ServiceResult;
import com.kce.kmrl.alert.integration.dto.ScheduleTripDto;
import com.kce.kmrl.alert.integration.dto.TrainAssetDto;
import com.kce.kmrl.alert.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ScheduleRuleEvaluator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String CONFLICT_PREFIX = "ALT-CONFLICT-";

    private final ScheduleServiceClient scheduleClient;
    private final FleetServiceClient fleetClient;
    private final AlertRepository alertRepository;
    private final RuleAlertPublisher publisher;
    private final int delaySev3Minutes;
    private final int unassignedLeadMinutes;

    public ScheduleRuleEvaluator(
            ScheduleServiceClient scheduleClient,
            FleetServiceClient fleetClient,
            AlertRepository alertRepository,
            RuleAlertPublisher publisher,
            @Value("${alert.rules.trip-delay.sev3-minutes:15}") int delaySev3Minutes,
            @Value("${alert.rules.trip-unassigned.lead-minutes:15}") int unassignedLeadMinutes) {
        this.scheduleClient = scheduleClient;
        this.fleetClient = fleetClient;
        this.alertRepository = alertRepository;
        this.publisher = publisher;
        this.delaySev3Minutes = delaySev3Minutes;
        this.unassignedLeadMinutes = unassignedLeadMinutes;
    }

    public void evaluate() {
        ServiceResult<List<ScheduleTripDto>> result = scheduleClient.getAllTripsResult();
        if (!result.isAvailable()) return;

        LocalDate today = LocalDate.now(IST);
        int nowMinutes = java.time.LocalTime.now(IST).getHour() * 60 + java.time.LocalTime.now(IST).getMinute();
        List<ScheduleTripDto> trips = result.getData().stream()
                .filter(t -> t != null && t.getId() != null)
                .filter(t -> t.getServiceDate() == null || today.toString().equals(t.getServiceDate()))
                .toList();

        Set<String> currentTripIds = new HashSet<>();
        for (ScheduleTripDto trip : trips) {
            currentTripIds.add(trip.getId());
            evaluateDelayed(trip, nowMinutes);
            evaluateUnassignedCloseToStart(trip, nowMinutes);
        }
        evaluateTrackConflicts(trips);
        resolveMissingTripAlerts(currentTripIds);
    }

    private void resolveMissingTripAlerts(Set<String> currentTripIds) {
        alertRepository.findByIdStartingWith("ALT-DELAY-").stream()
                .filter(a -> !currentTripIds.contains(a.getId().substring("ALT-DELAY-".length())))
                .forEach(a -> publisher.resolveLevel(a.getId()));
        alertRepository.findByIdStartingWith("ALT-UNASSIGNED-").stream()
                .filter(a -> !currentTripIds.contains(a.getId().substring("ALT-UNASSIGNED-".length())))
                .forEach(a -> publisher.resolveLevel(a.getId()));
    }

    private void evaluateDelayed(ScheduleTripDto trip, int nowMinutes) {
        String alertId = "ALT-DELAY-" + trip.getId();
        if (!"DELAYED".equalsIgnoreCase(trip.getStatus()) || trip.getStartMinutes() == null) {
            publisher.resolveLevel(alertId);
            return;
        }

        int delayMinutes = Math.max(0, nowMinutes - trip.getStartMinutes());
        Severity severity = delayMinutes >= delaySev3Minutes ? Severity.SEV3 : Severity.SEV2;
        publisher.publishLevel(alertId, severity,
                "Trip delayed — " + label(trip),
                "Route: " + dash(trip.getRouteName()), label(trip),
                List.of(field("Trip", label(trip)), field("Route", dash(trip.getRouteName())),
                        field("Scheduled Start", dash(trip.getStartTime())), field("Elapsed Delay", delayMinutes + " min"),
                        field("Assigned Train", dash(trip.getAssignedTrainName()))),
                label(trip) + " remains DELAYED and is " + delayMinutes + " minutes past its scheduled start.",
                List.of(delayMinutes, delayMinutes, delayMinutes), List.of("OC"), null, trip.getAssignedTrainId());
    }

    private void evaluateUnassignedCloseToStart(ScheduleTripDto trip, int nowMinutes) {
        String alertId = "ALT-UNASSIGNED-" + trip.getId();
        boolean unassigned = trip.getAssignedTrainId() == null || trip.getAssignedTrainId().isBlank();
        boolean planned = "PLANNED".equalsIgnoreCase(trip.getStatus());
        boolean today = trip.getStartMinutes() != null && trip.getStartMinutes() >= 0;
        int minutesToStart = today ? trip.getStartMinutes() - nowMinutes : Integer.MAX_VALUE;
        boolean withinWindow = minutesToStart >= 0 && minutesToStart <= unassignedLeadMinutes;

        if (unassigned && planned && withinWindow) {
            publisher.publishLevel(alertId, Severity.SEV2,
                    "Trip unassigned close to start — " + label(trip),
                    "No train bound with " + minutesToStart + " min to go", label(trip),
                    List.of(field("Trip", label(trip)), field("Route", dash(trip.getRouteName())),
                            field("Scheduled Start", dash(trip.getStartTime())), field("Minutes To Start", String.valueOf(minutesToStart))),
                    label(trip) + " has no train assigned with only " + minutesToStart + " minutes until start.",
                    List.of(minutesToStart, minutesToStart, minutesToStart), List.of("OC"), null, trip.getAssignedTrainId());
        } else {
            publisher.resolveLevel(alertId);
        }
    }

    private void evaluateTrackConflicts(List<ScheduleTripDto> trips) {
        ServiceResult<List<TrainAssetDto>> fleetResult = fleetClient.getAllTrainsResult();
        if (!fleetResult.isAvailable()) return;

        Map<String, String> trackByTrainId = new HashMap<>();
        for (TrainAssetDto train : fleetResult.getData()) {
            if (train.getId() != null) trackByTrainId.put(train.getId(), train.getTrack());
        }

        List<ScheduleTripDto> live = trips.stream()
                .filter(t -> t.getStartMinutes() != null && t.getEndMinutes() != null)
                .filter(t -> isLive(t.getStatus()))
                .toList();
        Set<String> activeIds = new HashSet<>();

        for (int i = 0; i < live.size(); i++) {
            for (int j = i + 1; j < live.size(); j++) {
                ScheduleTripDto a = live.get(i);
                ScheduleTripDto b = live.get(j);
                if (!sameServiceDate(a, b) || !overlaps(a, b)) continue;

                boolean sameTrain = a.getAssignedTrainId() != null && a.getAssignedTrainId().equals(b.getAssignedTrainId());
                String trackA = a.getAssignedTrainId() == null ? null : trackByTrainId.get(a.getAssignedTrainId());
                String trackB = b.getAssignedTrainId() == null ? null : trackByTrainId.get(b.getAssignedTrainId());
                boolean sameTrack = trackA != null && trackB != null && trackA.equalsIgnoreCase(trackB);

                if (!sameTrain && !sameTrack) continue;

                String pairId = conflictAlertId(a, b);
                activeIds.add(pairId);
                publisher.publishLevel(pairId, Severity.SEV3,
                        "Track/headway conflict — " + dash(a.getRouteName()),
                        label(a) + " overlaps " + label(b), dash(a.getRouteName()),
                        List.of(field("Route", dash(a.getRouteName())),
                                field("Trip A", label(a) + " (" + dash(a.getStartTime()) + "–" + dash(a.getEndTime()) + ")"),
                                field("Trip B", label(b) + " (" + dash(b.getStartTime()) + "–" + dash(b.getEndTime()) + ")"),
                                field("Reason", sameTrain ? "Same assigned train" : "Same assigned track")),
                        "Two live trips overlap on the same train/track resource. Verify headway before departure.",
                        List.of(1, 1, 1), List.of("OC"), null);
            }
        }

        for (Alert existing : alertRepository.findByIdStartingWith(CONFLICT_PREFIX)) {
            if (!activeIds.contains(existing.getId())) publisher.resolveLevel(existing.getId());
        }
    }

    private boolean sameServiceDate(ScheduleTripDto a, ScheduleTripDto b) {
        if (a.getServiceDate() == null || b.getServiceDate() == null) return true;
        return a.getServiceDate().equals(b.getServiceDate());
    }

    private boolean isLive(String status) {
        return "PLANNED".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status) || "DELAYED".equalsIgnoreCase(status);
    }

    private boolean overlaps(ScheduleTripDto a, ScheduleTripDto b) {
        return a.getStartMinutes() < b.getEndMinutes() && b.getStartMinutes() < a.getEndMinutes();
    }

    private String conflictAlertId(ScheduleTripDto a, ScheduleTripDto b) {
        String first = a.getId().compareTo(b.getId()) <= 0 ? a.getId() : b.getId();
        String second = a.getId().compareTo(b.getId()) <= 0 ? b.getId() : a.getId();
        return CONFLICT_PREFIX + first + "_" + second;
    }

    private String label(ScheduleTripDto trip) { return trip.getTripCode() != null ? trip.getTripCode() : trip.getId(); }
    private String dash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private Alert.AlertField field(String label, String value) { return new Alert.AlertField(label, value); }
}
