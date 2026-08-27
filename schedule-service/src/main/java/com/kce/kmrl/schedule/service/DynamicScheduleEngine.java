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

    @Lazy
    @Autowired
    private ScheduleService scheduleService;

    public DynamicScheduleEngine(ScheduleTripRepository tripRepository,
                                  ResilientFleetClient fleetClient,
                                  ResilientMaintenanceClient maintenanceClient) {
        this.tripRepository = tripRepository;
        this.fleetClient = fleetClient;
        this.maintenanceClient = maintenanceClient;
    }

    private TrainAssetDto selectBestStandbyTrainForTrip(ScheduleTrip trip, List<TrainAssetDto> standbyTrains) {
        if (standbyTrains == null || standbyTrains.isEmpty()) return null;

        Set<String> activeMaintenanceTickets = maintenanceClient.findTrainsWithActiveTickets(trip.getServiceDate());
        List<ScheduleTrip> todaysTrips = tripRepository.findByServiceDate(trip.getServiceDate());

        List<TrainAssetDto> sortedCandidates = standbyTrains.stream()
                .filter(t -> t != null && t.getId() != null)
                .filter(t -> !"IN_MAINTENANCE".equalsIgnoreCase(t.getStatus()))
                .filter(t -> !matchesMaintenanceSet(t, activeMaintenanceTickets))
                .sorted(Comparator.comparingLong(TrainAssetDto::getTotalMileageKm))
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

        String trainId = null;
        String trainName = "Train Not Assigned";

        if (best != null) {
            trainId = best.getId();
            trainName = hasText(best.getTrainNumber()) ? best.getTrainNumber() : best.getId();
            trip.setAssignedTrainId(trainId);
            trip.setAssignedTrainName(trainName);
        } else if (availableTrains != null && !availableTrains.isEmpty()) {
            TrainAssetDto fallback = availableTrains.get(0);
            trip.setAssignedTrainId(fallback.getId());
            trip.setAssignedTrainName(hasText(fallback.getTrainNumber()) ? fallback.getTrainNumber() : fallback.getId());
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

        List<ScheduleTrip> allTrips = tripRepository.findAll();

        List<ScheduleTrip> targetTrainTrips = allTrips.stream()
                .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.COMPLETED && t.getStatus() != TripStatus.MISSED)
                .filter(t -> matchesTrainIdentifier(t, trainId))
                .sorted(Comparator.comparingInt(ScheduleTrip::getStartMinutes))
                .collect(Collectors.toList());

        if (targetTrainTrips.isEmpty()) {
            try {
                fleetClient.updateTrainStatus(trainId, "IN_MAINTENANCE");
            } catch (Exception ignored) {}
            return new WithdrawTrainResponse(false, 0, 0);
        }

        List<TrainAssetDto> availableTrains = null;
        try {
            availableTrains = fleetClient.getAvailableTrains();
        } catch (Exception e) {
            log.warn("Could not fetch available trains during withdrawal: {}", e.getMessage());
        }

        TrainAssetDto replacement = findFirstYardReplacementTrain(trainId, availableTrains, targetTrainTrips, allTrips);

        List<ScheduleTrip> tripsToSave = new ArrayList<>();
        int affectedCount = 0;

        for (ScheduleTrip originalTrip : targetTrainTrips) {

            originalTrip.setStatus(TripStatus.CANCELLED);
            originalTrip.setChangedBy("MAINTENANCE");
            originalTrip.setChangedAt(Instant.now());
            originalTrip.setChangeReason("Train " + trainId + " pulled for maintenance; trip CANCELLED.");
            tripsToSave.add(originalTrip);
            affectedCount++;

            if (replacement != null) {
                String repName = hasText(replacement.getTrainNumber()) ? replacement.getTrainNumber() : replacement.getId();

                ScheduleTrip repTrip = new ScheduleTrip();
                repTrip.setId("TR-" + originalTrip.getServiceDate() + "-REP-" + UUID.randomUUID().toString().substring(0, 8));
                repTrip.setTripCode(originalTrip.getTripCode());
                repTrip.setRouteName(originalTrip.getRouteName());
                repTrip.setStartTime(originalTrip.getStartTime());
                repTrip.setEndTime(originalTrip.getEndTime());
                repTrip.setStartMinutes(originalTrip.getStartMinutes());
                repTrip.setEndMinutes(originalTrip.getEndMinutes());
                repTrip.setServiceDate(originalTrip.getServiceDate());
                repTrip.setAssignedTrainId(replacement.getId());
                repTrip.setAssignedTrainName(repName);
                repTrip.setStatus(TripStatus.PLANNED);
                repTrip.setSource("YARD_REPLACEMENT");
                repTrip.setChangedBy("MAINTENANCE");
                repTrip.setChangedAt(Instant.now());
                repTrip.setChangeReason("Assigned from Muttom Yard to cover pulled train " + trainId);

                tripsToSave.add(repTrip);
            }
        }

        tripRepository.saveAll(tripsToSave);

        try {
            fleetClient.updateTrainStatus(trainId, "IN_MAINTENANCE");
        } catch (Exception e) {
            log.warn("Failed to set train {} status to IN_MAINTENANCE on fleet-service: {}", trainId, e.getMessage());
        }

        if (replacement != null) {
            try {
                fleetClient.updateTrainStatus(replacement.getId(), "IN_SERVICE");
                log.info("YARD REPLACEMENT: Train {} assigned to cover {} trips for pulled train {}.", replacement.getId(), affectedCount, trainId);
            } catch (Exception ignored) {}
        }

        return new WithdrawTrainResponse(!targetTrainTrips.isEmpty(), affectedCount, affectedCount);
    }

    private TrainAssetDto findFirstYardReplacementTrain(String withdrawnTrainId, List<TrainAssetDto> availableTrains, List<ScheduleTrip> targetTrainTrips, List<ScheduleTrip> allTrips) {
        if (targetTrainTrips == null || targetTrainTrips.isEmpty()) return null;
        ScheduleTrip firstTrip = targetTrainTrips.get(0);
        String serviceDate = firstTrip.getServiceDate();
        boolean isToday = java.time.LocalDate.now().toString().equals(serviceDate);
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
        if (train == null || activeTickets == null || activeTickets.isEmpty()) return false;

        String id = train.getId() != null ? train.getId().trim().toUpperCase() : "";
        String number = train.getTrainNumber() != null ? train.getTrainNumber().trim().toUpperCase() : "";

        for (String active : activeTickets) {
            if (active == null || active.isBlank()) continue;
            String actUpper = active.trim().toUpperCase();

            if (!id.isEmpty() && id.equals(actUpper)) return true;
            if (!number.isEmpty() && number.equals(actUpper)) return true;

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

    private boolean matchesTrainAssetIdentifier(TrainAssetDto train, String targetId) {
        if (train == null || targetId == null || targetId.isBlank()) return false;

        String targetDigits = targetId.trim().replaceAll("\\D+", "");
        if (targetDigits.isEmpty()) {
            String rawId = targetId.trim().toUpperCase();
            String trainId = train.getId() != null ? train.getId().trim().toUpperCase() : "";
            String trainNum = train.getTrainNumber() != null ? train.getTrainNumber().trim().toUpperCase() : "";
            return trainId.equalsIgnoreCase(rawId) || trainNum.equalsIgnoreCase(rawId);
        }

        int targetNum = Integer.parseInt(targetDigits);

        String idDigits = train.getId() != null ? train.getId().replaceAll("\\D+", "") : "";
        if (!idDigits.isEmpty()) {
            try {
                if (Integer.parseInt(idDigits) == targetNum) return true;
            } catch (Exception ignored) {}
        }

        String numDigits = train.getTrainNumber() != null ? train.getTrainNumber().replaceAll("\\D+", "") : "";
        if (!numDigits.isEmpty()) {
            try {
                if (Integer.parseInt(numDigits) == targetNum) return true;
            } catch (Exception ignored) {}
        }

        return false;
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

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}