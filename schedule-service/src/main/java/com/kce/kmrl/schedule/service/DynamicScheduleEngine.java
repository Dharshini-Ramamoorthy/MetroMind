package com.kce.kmrl.schedule.service;

import com.kce.kmrl.schedule.client.ResilientFleetClient;
import com.kce.kmrl.schedule.client.ResilientMaintenanceClient;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import com.kce.kmrl.schedule.dto.WithdrawTrainResponse;
import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import com.kce.kmrl.schedule.repository.ScheduleTripRepository;
import com.kce.kmrl.schedule.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicScheduleEngine {

    private static final Logger log = LoggerFactory.getLogger(DynamicScheduleEngine.class);

    private final ScheduleTripRepository tripRepository;
    private final ResilientFleetClient fleetClient;
    private final ResilientMaintenanceClient maintenanceClient;
    private final ScheduleSafetyValidator safetyValidator;

    @Lazy
    @Autowired
    private ScheduleService scheduleService;

    public DynamicScheduleEngine(ScheduleTripRepository tripRepository,
                                  ResilientFleetClient fleetClient,
                                  ResilientMaintenanceClient maintenanceClient,
                                  ScheduleSafetyValidator safetyValidator) {
        this.tripRepository = tripRepository;
        this.fleetClient = fleetClient;
        this.maintenanceClient = maintenanceClient;
        this.safetyValidator = safetyValidator;
    }

    private TrainAssetDto selectBestStandbyTrainForTrip(ScheduleTrip trip, List<TrainAssetDto> standbyTrains) {
        if (standbyTrains == null || standbyTrains.isEmpty()) return null;

        Set<String> activeMaintenanceTickets = maintenanceClient.findTrainsWithActiveTickets(trip.getServiceDate());
        List<ScheduleTrip> todaysTrips = tripRepository.findByServiceDate(trip.getServiceDate());

        List<TrainAssetDto> sortedCandidates = standbyTrains.stream()
                .filter(t -> t != null && t.getId() != null)
                .filter(t -> !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                .filter(t -> !matchesMaintenanceSet(t, activeMaintenanceTickets))
                .sorted(Comparator.comparingLong(t -> t.getTotalMileageKm() != null ? t.getTotalMileageKm().longValue() : 0L))
                .collect(Collectors.toList());

        for (TrainAssetDto candidate : sortedCandidates) {
            boolean hasOverlap = todaysTrips.stream()
                    .filter(t -> matchesTrainIdentifier(t, candidate.getId()) || matchesTrainIdentifier(t, candidate.getTrainNumber()))
                    .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                    .filter(t -> !t.getId().equals(trip.getId()))
                    .anyMatch(t -> !(trip.getEndMinutes() + 3 <= t.getStartMinutes() || trip.getStartMinutes() >= t.getEndMinutes() + 3));

            if (!hasOverlap) {
                return candidate;
            }
        }
        return null;
    }

    public ScheduleTrip generateAndAssignTrip(String tripCode, String routeName,
                                               String startTime, String endTime,
                                               int startMinutes, int endMinutes,
                                               String serviceDate) {
        String resolvedDate = (serviceDate != null && !serviceDate.isBlank()) ? serviceDate : TimeUtil.today();
        String id = "TR-" + resolvedDate + "-" + tripCode + "-" + UUID.randomUUID().toString().substring(0, 8);

        ScheduleTrip trip = new ScheduleTrip();
        trip.setId(id);
        trip.setTripCode(tripCode);
        trip.setRouteName(routeName);
        trip.setStartTime(startTime);
        trip.setEndTime(endTime);
        trip.setStartMinutes(startMinutes);
        trip.setEndMinutes(endMinutes);
        trip.setSeedGenerated(false);
        trip.setServiceDate(resolvedDate);

        List<TrainAssetDto> availableTrains = fleetClient.getAvailableTrains();
        TrainAssetDto best = selectBestStandbyTrainForTrip(trip, availableTrains);

        if (best != null) {
            String trainId = best.getId();
            String trainName = hasText(best.getTrainNumber()) ? best.getTrainNumber() : best.getId();
            trip.setAssignedTrainId(trainId);
            trip.setAssignedTrainName(trainName);
            trip.setAssignmentStatus("ASSIGNED");
            trip.setAssignmentReason("Assigned standby train " + trainName);
        } else {
            trip.setAssignedTrainId(null);
            trip.setAssignedTrainName("Train Not Assigned");
            trip.setAssignmentStatus("NOT_POSSIBLE");
            trip.setAssignmentReason("No feasible standby train found without overlap, maintenance, or continuity conflicts.");
        }

        return trip;
    }

    @Scheduled(fixedRate = 10000)
    public void evaluateDynamicTrips() {
        int currentMinutes = TimeUtil.nowMinutes();
        String today = TimeUtil.today();

        List<ScheduleTrip> todaysTrips = tripRepository.findByServiceDate(today);
        if (todaysTrips == null || todaysTrips.isEmpty()) return;

        List<TrainAssetDto> standbyTrains = null;
        try {
            standbyTrains = fleetClient.getStandbyTrains();
        } catch (Exception e) {
            log.warn("Could not fetch standby trains for dynamic evaluation: {}", e.getMessage());
        }

        for (ScheduleTrip trip : todaysTrips) {
            if ((trip.getStatus() == TripStatus.PLANNED || trip.getStatus() == TripStatus.AWAITING_REPLACEMENT)
                    && currentMinutes >= trip.getStartMinutes()
                    && currentMinutes < trip.getEndMinutes()) {
                handleAutoDispatch(trip, standbyTrains);
            }

            if ((trip.getStatus() == TripStatus.PLANNED || trip.getStatus() == TripStatus.AWAITING_REPLACEMENT)
                    && currentMinutes >= trip.getEndMinutes()) {
                handleMissed(trip);
            }

            if ((trip.getStatus() == TripStatus.ACTIVE || trip.getStatus() == TripStatus.DELAYED)
                    && currentMinutes >= trip.getEndMinutes()) {
                handleAutoComplete(trip);
            }
        }
    }

    private void handleMissed(ScheduleTrip trip) {
        trip.setStatus(TripStatus.MISSED);
        tripRepository.save(trip);
        log.warn("Trip {} passed without dispatch; marked MISSED.", trip.getTripCode());
    }

    private void handleAutoDispatch(ScheduleTrip trip, List<TrainAssetDto> standbyTrains) {
        String trainId = trip.getAssignedTrainId();

        if (trainId == null) {
            TrainAssetDto best = selectBestStandbyTrainForTrip(trip, standbyTrains);
            if (best != null) {
                trainId = best.getId();
                trip.setAssignedTrainId(trainId);
                trip.setAssignedTrainName(hasText(best.getTrainNumber()) ? best.getTrainNumber() : best.getId());
            }
        }

        if (trainId != null) {

            boolean available = false;
            try {
                available = fleetClient.checkTrainAvailability(trainId, trip.getStartTime(), trip.getEndTime());
            } catch (Exception e) {
                log.warn("Could not check train availability before dispatch: {}", e.getMessage());
            }

            if (available) {
                try {
                    fleetClient.updateTrainStatus(trainId, "IN_SERVICE");
                    trip.setStatus(TripStatus.ACTIVE);
                    tripRepository.save(trip);
                    log.info("AUTO DISPATCH: Trip {} dispatched on train {}.", trip.getTripCode(), trainId);
                } catch (Exception e) {
                    log.warn("Failed to dispatch train {} for trip {}: {}", trainId, trip.getTripCode(), e.getMessage());
                }
            } else {
                trip.setStatus(TripStatus.AWAITING_REPLACEMENT);
                tripRepository.save(trip);
                log.warn("AUTO DISPATCH CANCELLED: Train {} is not available or under maintenance; marked trip {} AWAITING_REPLACEMENT.", trainId, trip.getTripCode());
            }
        } else {
            trip.setStatus(TripStatus.AWAITING_REPLACEMENT);
            tripRepository.save(trip);
            log.warn("Trip {} starting now but no train available; marked AWAITING_REPLACEMENT.", trip.getTripCode());
        }
    }

    private void handleAutoComplete(ScheduleTrip trip) {
        String trainId = trip.getAssignedTrainId();
        trip.setStatus(TripStatus.COMPLETED);
        tripRepository.save(trip);

        if (trainId != null) {
            try {
                fleetClient.updateTrainStatus(trainId, "STANDBY");
                log.info("AUTO COMPLETE: Trip {} completed. Train {} returned to STANDBY.", trip.getTripCode(), trainId);
            } catch (Exception e) {
                log.warn("Failed to return train {} to STANDBY for completed trip {}: {}", trainId, trip.getTripCode(), e.getMessage());
            }
        }
    }

    public WithdrawTrainResponse withdrawTrain(String trainId, boolean emergency) {
        if (!hasText(trainId)) {
            return new WithdrawTrainResponse(false, 0, 0);
        }

        List<ScheduleTrip> allTrips = tripRepository.findByServiceDateGreaterThanEqualAndStatusIn(
                TimeUtil.today(),
                List.of(TripStatus.PLANNED, TripStatus.ACTIVE, TripStatus.DELAYED, TripStatus.PROPOSED, TripStatus.AWAITING_REPLACEMENT)
        );

        List<ScheduleTrip> targetTrainTrips = allTrips.stream()
                .filter(t -> matchesTrainIdentifier(t, trainId))
                .sorted(Comparator.comparing(ScheduleTrip::getServiceDate).thenComparingInt(ScheduleTrip::getStartMinutes))
                .collect(Collectors.toList());

        if (targetTrainTrips.isEmpty()) {
            try {
                fleetClient.updateTrainStatus(trainId, "IN_MAINTENANCE");
            } catch (Exception ignored) {}
            return new WithdrawTrainResponse(false, 0, 0);
        }

        boolean hadActiveTrip = targetTrainTrips.stream().anyMatch(t -> t.getStatus() == TripStatus.ACTIVE);

        List<TrainAssetDto> standbyOrAvailable = null;
        try {
            standbyOrAvailable = fleetClient.getStandbyTrains();
            if (standbyOrAvailable == null || standbyOrAvailable.isEmpty()) {
                standbyOrAvailable = fleetClient.getAvailableTrains();
            }
        } catch (Exception e) {
            log.warn("Could not fetch standby/available trains during withdrawal: {}", e.getMessage());
        }
        if (standbyOrAvailable == null) {
            standbyOrAvailable = Collections.emptyList();
        }

        List<ScheduleTrip> tripsToSave = new ArrayList<>();
        Map<String, List<ScheduleTrip>> replacementTripsByTrain = new HashMap<>();
        for (ScheduleTrip t : allTrips) {
            if (hasText(t.getAssignedTrainId()) && !"Train Not Assigned".equals(t.getAssignedTrainId())) {
                replacementTripsByTrain.computeIfAbsent(t.getAssignedTrainId(), k -> new ArrayList<>()).add(t);
            }
        }

        for (ScheduleTrip activeTrip : targetTrainTrips) {
            if (activeTrip.getStatus() == TripStatus.ACTIVE) {
                activeTrip.setStatus(TripStatus.CANCELLED);
                activeTrip.setChangedBy("MAINTENANCE");
                activeTrip.setChangedAt(Instant.now());
                activeTrip.setChangeReason("Train " + trainId + " withdrawn; active trip CANCELLED.");
                tripsToSave.add(activeTrip);
            }
        }

        List<ScheduleTrip> futureTrips = targetTrainTrips.stream()
                .filter(t -> t.getStatus() != TripStatus.ACTIVE && t.getStatus() != TripStatus.CANCELLED)
                .collect(Collectors.toList());

        int futureTripsAffected = futureTrips.size();
        int futureTripsAwaitingReplacement = 0;
        Set<String> assignedReplacementTrainIds = new HashSet<>();

        for (ScheduleTrip trip : futureTrips) {
            String sDate = trip.getServiceDate();
            Set<String> activeMaint = maintenanceClient.findTrainsWithActiveTickets(sDate);

            List<TrainAssetDto> candidates = standbyOrAvailable.stream()
                    .filter(tr -> tr != null && tr.getId() != null)
                    .filter(tr -> !matchesTrainAssetIdentifier(tr, trainId))
                    .filter(tr -> !"IN_MAINTENANCE".equalsIgnoreCase(tr.getStatus()))
                    .filter(tr -> !matchesMaintenanceSet(tr, activeMaint))
                    .sorted(Comparator.comparingLong(tr -> tr.getTotalMileageKm() != null ? tr.getTotalMileageKm().longValue() : 0L))
                    .collect(Collectors.toList());

            TrainAssetDto chosenReplacement = null;
            for (TrainAssetDto candidate : candidates) {
                List<ScheduleTrip> trainDayTrips = replacementTripsByTrain.getOrDefault(candidate.getId(), Collections.emptyList())
                        .stream()
                        .filter(t -> Objects.equals(t.getServiceDate(), sDate))
                        .collect(Collectors.toList());

                StringBuilder reason = new StringBuilder();
                if (safetyValidator.validateTrainTurnaroundAndOverlap(trip, trainDayTrips, reason)
                        && safetyValidator.validateTrainDirectionContinuity(trip, trainDayTrips, reason)) {
                    chosenReplacement = candidate;
                    break;
                }
            }

            if (chosenReplacement != null) {
                String repName = hasText(chosenReplacement.getTrainNumber()) ? chosenReplacement.getTrainNumber() : chosenReplacement.getId();
                trip.setAssignedTrainId(chosenReplacement.getId());
                trip.setAssignedTrainName(repName);
                trip.setAssignmentStatus("ASSIGNED");
                trip.setAssignmentReason("Assigned replacement train " + repName + " after withdrawal of " + trainId);
                trip.setStatus(trip.getStatus() == TripStatus.PROPOSED ? TripStatus.PROPOSED : TripStatus.PLANNED);
                if (trip.getTripCode() != null && !trip.getTripCode().endsWith("-R")) {
                    trip.setTripCode(trip.getTripCode() + "-R");
                }
                trip.setChangedBy("MAINTENANCE");
                trip.setChangedAt(Instant.now());
                trip.setChangeReason("Re-assigned to train " + repName + " following withdrawal of " + trainId);

                replacementTripsByTrain.computeIfAbsent(chosenReplacement.getId(), k -> new ArrayList<>()).add(trip);
                assignedReplacementTrainIds.add(chosenReplacement.getId());
                tripsToSave.add(trip);
            } else {
                trip.setAssignedTrainId(null);
                trip.setAssignedTrainName("Train Not Assigned");
                trip.setAssignmentStatus("NOT_POSSIBLE");
                trip.setAssignmentReason("Train " + trainId + " withdrawn; no safe replacement train available.");
                trip.setStatus(TripStatus.AWAITING_REPLACEMENT);
                trip.setChangedBy("MAINTENANCE");
                trip.setChangedAt(Instant.now());
                trip.setChangeReason("Train " + trainId + " withdrawn; awaiting replacement.");
                futureTripsAwaitingReplacement++;
                tripsToSave.add(trip);
            }
        }

        tripRepository.saveAll(tripsToSave);

        try {
            fleetClient.updateTrainStatus(trainId, "IN_MAINTENANCE");
        } catch (Exception e) {
            log.warn("Failed to set train {} status to IN_MAINTENANCE on fleet-service: {}", trainId, e.getMessage());
        }

        for (String repId : assignedReplacementTrainIds) {
            try {
                fleetClient.updateTrainStatus(repId, "IN_SERVICE");
                log.info("YARD REPLACEMENT: Train {} set to IN_SERVICE to cover withdrawn train {}.", repId, trainId);
            } catch (Exception ignored) {}
        }

        return new WithdrawTrainResponse(hadActiveTrip, futureTripsAffected, futureTripsAwaitingReplacement);
    }

    private TrainAssetDto findFirstYardReplacementTrain(String withdrawnTrainId, List<TrainAssetDto> availableTrains, List<ScheduleTrip> targetTrainTrips, List<ScheduleTrip> allTrips) {
        if (targetTrainTrips == null || targetTrainTrips.isEmpty()) return null;
        ScheduleTrip firstTrip = targetTrainTrips.get(0);
        String serviceDate = firstTrip.getServiceDate();
        boolean isToday = TimeUtil.today().equals(serviceDate);
        int nowM = isToday ? TimeUtil.nowMinutes() : 0;

        List<TrainAssetDto> fetchedStandby = null;
        try {
            fetchedStandby = fleetClient.getStandbyTrains();
        } catch (Exception e) {
            log.warn("Could not fetch standby trains from fleet-service: {}", e.getMessage());
        }

        final List<TrainAssetDto> pool = (fetchedStandby != null && !fetchedStandby.isEmpty()) ? fetchedStandby : availableTrains;
        if (pool == null || pool.isEmpty()) return null;

        final Set<String> activeMaintenanceTickets = maintenanceClient.findTrainsWithActiveTickets(serviceDate);
        final boolean isTrueStandbyPool = (fetchedStandby != null && !fetchedStandby.isEmpty());

        String targetLocation = firstTrip.getRouteName().contains("Aluva to Thrippunithura") ? "ALUVA" : "THRIPPUNITHURA";

        class CandidateScore {
            TrainAssetDto train;
            double score;
            CandidateScore(TrainAssetDto train, double score) {
                this.train = train;
                this.score = score;
            }
        }

        List<CandidateScore> candidates = new ArrayList<>();

        for (TrainAssetDto train : pool) {
            if (train == null || train.getId() == null) continue;
            if ("IN_MAINTENANCE".equalsIgnoreCase(train.getStatus())) continue;
            if (!isTrueStandbyPool && !"STANDBY".equalsIgnoreCase(train.getStatus())) continue;
            if (matchesMaintenanceSet(train, activeMaintenanceTickets)) continue;
            if (matchesTrainAssetIdentifier(train, withdrawnTrainId)) continue;

            List<ScheduleTrip> trainTrips = allTrips.stream()
                    .filter(t -> serviceDate.equals(t.getServiceDate()))
                    .filter(t -> matchesTrainIdentifier(t, train.getId()))
                    .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                    .collect(Collectors.toList());

            int lastEndMinutes = 0;
            String currentLocation = "MUTTOM";

            ScheduleTrip lastTrip = null;
            for (ScheduleTrip t : trainTrips) {
                if (t.getEndMinutes() <= firstTrip.getStartMinutes()) {
                    if (lastTrip == null || t.getEndMinutes() > lastTrip.getEndMinutes()) {
                        lastTrip = t;
                    }
                }
            }

            if (lastTrip != null) {
                lastEndMinutes = lastTrip.getEndMinutes();
                currentLocation = lastTrip.getRouteName().contains("Aluva to Thrippunithura") ? "THRIPPUNITHURA" : "ALUVA";
            }

            int repositionTime = 0;
            if (!currentLocation.equalsIgnoreCase(targetLocation)) {
                if ("MUTTOM".equalsIgnoreCase(currentLocation)) {
                    repositionTime = "ALUVA".equalsIgnoreCase(targetLocation) ? 15 : 30;
                } else {
                    repositionTime = 45;
                }
            }

            int minReadyTime = Math.max(lastEndMinutes, nowM) + repositionTime + 3;
            if (minReadyTime > firstTrip.getStartMinutes()) {
                continue;
            }

            boolean hasOverlapConflict = false;
            for (ScheduleTrip targetTrip : targetTrainTrips) {
                for (ScheduleTrip candTrip : trainTrips) {
                    boolean overlap = !(candTrip.getEndMinutes() + 3 <= targetTrip.getStartMinutes() ||
                                        candTrip.getStartMinutes() >= targetTrip.getEndMinutes() + 3);
                    if (overlap) {
                        hasOverlapConflict = true;
                        break;
                    }
                }
                if (hasOverlapConflict) break;
            }

            if (hasOverlapConflict) {
                continue;
            }

            double score = repositionTime;

            double mileage = train.getTotalMileageKm() != null ? train.getTotalMileageKm() : 0.0;
            score += mileage / 10000.0;

            if (!trainTrips.isEmpty()) {
                score += 200.0;
            }

            candidates.add(new CandidateScore(train, score));
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(c -> c.score));
        return candidates.get(0).train;
    }

    private boolean matchesMaintenanceSet(TrainAssetDto train, Set<String> activeTickets) {
        return com.kce.kmrl.schedule.util.TrainIdUtil.matchesMaintenanceSet(train, activeTickets);
    }

    private boolean matchesTrainAssetIdentifier(TrainAssetDto train, String targetId) {
        return com.kce.kmrl.schedule.util.TrainIdUtil.matchesTrainAssetIdentifier(train, targetId);
    }

    private boolean matchesTrainIdentifier(ScheduleTrip trip, String trainId) {
        return com.kce.kmrl.schedule.util.TrainIdUtil.matchesTrainIdentifier(trip, trainId);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}