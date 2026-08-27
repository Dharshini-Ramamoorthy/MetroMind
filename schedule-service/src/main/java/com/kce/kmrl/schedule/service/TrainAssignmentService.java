package com.kce.kmrl.schedule.service;

import com.kce.kmrl.schedule.client.ResilientFleetClient;
import com.kce.kmrl.schedule.client.ResilientMaintenanceClient;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrainAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TrainAssignmentService.class);

    private final ResilientFleetClient fleetClient;
    private final ResilientMaintenanceClient maintenanceClient;
    private final ScheduleSafetyValidator safetyValidator;

    @Value("${schedule.min-reserve-trains:2}")
    private int minReserveTrains;

    private final ThreadLocal<Map<String, Boolean>> availabilityCache = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Map<String, Boolean>> maintBlockCache = ThreadLocal.withInitial(HashMap::new);

    public TrainAssignmentService(ResilientFleetClient fleetClient, ResilientMaintenanceClient maintenanceClient, ScheduleSafetyValidator safetyValidator) {
        this.fleetClient = fleetClient;
        this.maintenanceClient = maintenanceClient;
        this.safetyValidator = safetyValidator;
    }

    public void clearCache() {
        availabilityCache.get().clear();
        maintBlockCache.get().clear();
    }

    public TrainAssetDto findSafeTrain(ScheduleTrip trip, List<ScheduleTrip> dayTrips, List<TrainAssetDto> candidatePool, StringBuilder resultReason) {
        return findSafeTrain(trip, dayTrips, null, candidatePool, resultReason);
    }

    public TrainAssetDto findSafeTrain(ScheduleTrip trip, List<ScheduleTrip> dayTrips,
                                       Map<String, List<ScheduleTrip>> tripsByTrain,
                                       List<TrainAssetDto> candidatePool, StringBuilder resultReason) {
        if (candidatePool == null || candidatePool.isEmpty()) {
            if (resultReason != null) {
                resultReason.append("Candidate fleet pool is empty.");
            }
            return null;
        }

        StringBuilder routeRejectReason = new StringBuilder();
        if (!safetyValidator.validateHeadways(trip, dayTrips, routeRejectReason)) {
            if (resultReason != null) {
                resultReason.append("Headway violation on route ").append(trip.getRouteName()).append(": ").append(routeRejectReason);
            }
            return null;
        }
        if (!safetyValidator.validateTrackOccupancy(trip, dayTrips, routeRejectReason)) {
            if (resultReason != null) {
                resultReason.append("Track separation violation on route ").append(trip.getRouteName()).append(": ").append(routeRejectReason);
            }
            return null;
        }

        long totalFleet = 25;
        long operational = candidatePool.stream()
                .filter(t -> t != null && !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                .count();
        double maintenancePct = (totalFleet - operational) / (double) totalFleet;

        int startM = trip.getStartMinutes();
        boolean isPeak = (startM >= 7 * 60 && startM < 10 * 60 + 30)
                      || (startM >= 17 * 60 && startM < 20 * 60);

        int effectiveReserve = minReserveTrains;
        if (maintenancePct > 0.20) {
            effectiveReserve--;
        }
        if (isPeak) {
            effectiveReserve--;
        }
        effectiveReserve = Math.max(0, effectiveReserve);

        List<TrainAssetDto> validCandidates = new ArrayList<>();
        Map<String, String> candidateRejectReasons = new HashMap<>();

        for (TrainAssetDto train : candidatePool) {
            if (train == null || train.getId() == null) continue;

            StringBuilder rejectBuilder = new StringBuilder();

            String sDate = trip.getServiceDate();
            String maintKey = train.getId() + "@" + sDate;
            boolean isBlockedByMaint = maintBlockCache.get().computeIfAbsent(maintKey, k ->
                maintenanceClient.hasOpenTicket(train.getId(), train.getTrainNumber(), sDate)
            );
            if (isBlockedByMaint) {
                candidateRejectReasons.put(train.getId(), "Train is reserved for maintenance on " + sDate + ".");
                continue;
            }

            boolean isToday = java.time.LocalDate.now().toString().equals(sDate);
            boolean available;
            if (isToday) {
                available = availabilityCache.get().computeIfAbsent(train.getId(), tid ->
                    fleetClient.checkTrainAvailability(tid, trip.getStartTime(), trip.getEndTime())
                );
            } else {
                available = train.getStatus() == null || !"IN_MAINTENANCE".equalsIgnoreCase(train.getStatus());
            }

            if (!available) {
                candidateRejectReasons.put(train.getId(), "Fleet Service reports train is not operational or has active maintenance tickets.");
                continue;
            }

            List<ScheduleTrip> trainTrips;
            if (tripsByTrain != null) {
                trainTrips = tripsByTrain.getOrDefault(train.getId(), Collections.emptyList());
            } else {
                trainTrips = dayTrips.stream()
                        .filter(t -> !t.getId().equals(trip.getId()))
                        .filter(t -> matchesTrainIdentifier(t, train.getId()))
                        .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                        .collect(Collectors.toList());
            }

            if (!safetyValidator.validateTrainTurnaroundAndOverlap(trip, trainTrips, rejectBuilder)) {
                candidateRejectReasons.put(train.getId(), rejectBuilder.toString());
                continue;
            }

            if (!safetyValidator.validateTrainDirectionContinuity(trip, trainTrips, rejectBuilder)) {
                candidateRejectReasons.put(train.getId(), rejectBuilder.toString());
                continue;
            }

            trip.setAssignedTrainId(train.getId());
            trip.setAssignedTrainName(train.getTrainNumber() != null ? train.getTrainNumber() : train.getId());
            if (!safetyValidator.validatePlatformOccupancy(trip, dayTrips, tripsByTrain, rejectBuilder)) {
                candidateRejectReasons.put(train.getId(), rejectBuilder.toString());

                trip.setAssignedTrainId(null);
                trip.setAssignedTrainName(null);
                continue;
            }

            validCandidates.add(train);
        }

        if (validCandidates.isEmpty()) {
            if (resultReason != null) {
                resultReason.append("No operational train available. Reject reasons: ");
                candidateRejectReasons.forEach((tid, r) -> resultReason.append("[").append(tid).append(": ").append(r).append("] "));
            }
            return null;
        }

        TrainAssetDto selected = selectBestScoredTrain(validCandidates, dayTrips, tripsByTrain, effectiveReserve);
        if (selected != null && resultReason != null) {
            resultReason.append("Assigned because ").append(selected.getTrainNumber() != null ? selected.getTrainNumber() : selected.getId())
                  .append(" is operational, maintenance-free, satisfies direction continuity, platform capacity, and preserves reserve capacity.");
        }
        return selected;
    }

    private TrainAssetDto selectBestScoredTrain(List<TrainAssetDto> candidates, List<ScheduleTrip> dayTrips,
                                                Map<String, List<ScheduleTrip>> tripsByTrain, int reserveCount) {

        Set<String> activeTrainIds;
        if (tripsByTrain != null) {
            activeTrainIds = new HashSet<>();
            for (Map.Entry<String, List<ScheduleTrip>> entry : tripsByTrain.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    activeTrainIds.add(entry.getKey());
                }
            }
        } else {
            activeTrainIds = dayTrips.stream()
                    .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                    .map(ScheduleTrip::getAssignedTrainId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        List<TrainAssetDto> unusedStandby = candidates.stream()
                .filter(t -> !activeTrainIds.contains(t.getId()))
                .collect(Collectors.toList());

        return candidates.stream().max(Comparator.comparingDouble(t -> {
            double score = 0.0;

            long mileage = t.getTotalMileageKm() != null ? t.getTotalMileageKm() : 0;
            score += (1000000.0 - mileage) / 10000.0;

            boolean isAlreadyActive = activeTrainIds.contains(t.getId());
            if (isAlreadyActive) {
                score += 200.0;
            } else if (unusedStandby.size() > reserveCount) {
                score += 50.0;
            }

            return score;
        })).orElse(null);
    }

    private boolean matchesTrainIdentifier(ScheduleTrip trip, String trainId) {
        if (trip == null || trainId == null || trainId.isBlank()) return false;

        String targetDigits = trainId.trim().replaceAll("\\D+", "");
        if (targetDigits.isEmpty()) {
            String rawId = trainId.trim().toUpperCase();
            String assignedId = trip.getAssignedTrainId() != null ? trip.getAssignedTrainId().trim().toUpperCase() : "";
            String assignedName = trip.getAssignedTrainName() != null ? trip.getAssignedTrainName().trim().toUpperCase() : "";
            return assignedId.equalsIgnoreCase(rawId) || assignedName.equalsIgnoreCase(rawId);
        }

        int targetNum = Integer.parseInt(targetDigits);

        String idDigits = trip.getAssignedTrainId() != null ? trip.getAssignedTrainId().replaceAll("\\D+", "") : "";
        if (!idDigits.isEmpty()) {
            try {
                if (Integer.parseInt(idDigits) == targetNum) return true;
            } catch (Exception ignored) {}
        }

        String nameDigits = trip.getAssignedTrainName() != null ? trip.getAssignedTrainName().replaceAll("\\D+", "") : "";
        if (!nameDigits.isEmpty()) {
            try {
                if (Integer.parseInt(nameDigits) == targetNum) return true;
            } catch (Exception ignored) {}
        }

        return false;
    }
}
