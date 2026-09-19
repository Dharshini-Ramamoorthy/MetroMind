package com.kce.kmrl.schedule.service;

import com.kce.kmrl.schedule.client.ResilientFleetClient;
import com.kce.kmrl.schedule.client.ForecastClient;
import com.kce.kmrl.schedule.client.ResilientMaintenanceClient;
import com.kce.kmrl.schedule.dto.ForecastScheduleResponse;
import com.kce.kmrl.schedule.dto.AdjustTripRequest;
import com.kce.kmrl.schedule.dto.ProposeTripRequest;
import com.kce.kmrl.schedule.dto.StatusUpdateRequest;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import com.kce.kmrl.schedule.dto.WithdrawTrainResponse;
import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import com.kce.kmrl.schedule.repository.ScheduleTripRepository;
import com.kce.kmrl.schedule.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleTripRepository tripRepository;
    private final DynamicScheduleEngine scheduleEngine;
    private final ResilientFleetClient fleetClient;
    private final ResilientMaintenanceClient maintenanceClient;
    private final RestClient loadBalancedRestClient;
    private final ForecastClient forecastClient;
    private final ServiceDayProfileResolver dayProfileResolver;

    @org.springframework.beans.factory.annotation.Autowired
    private ScheduleSafetyValidator safetyValidator;

    @org.springframework.beans.factory.annotation.Autowired
    private TrainAssignmentService trainAssignmentService;

    private final java.util.concurrent.locks.ReentrantLock scheduleLock = new java.util.concurrent.locks.ReentrantLock();

    public static final String ROUTE_A = "Aluva to Thrippunithura";
    public static final String ROUTE_B = "Thrippunithura to Aluva";

    public static final int REAL_KMRL_PEAK_HEADWAY_SECONDS = 480;
    public static final int REAL_KMRL_OFFPEAK_HEADWAY_SECONDS = 720;
    public static final int REAL_KMRL_LATE_HEADWAY_SECONDS = 900;
    public static final int MIN_HEADWAY_SECONDS = 420;

    @Value("${schedule.default-weather:Clear}")
    private String defaultWeather;

    @Value("${internal.service-secret:}")
    private String internalServiceSecret;

    public ScheduleService(ScheduleTripRepository tripRepository,
                           DynamicScheduleEngine scheduleEngine,
                           ResilientFleetClient fleetClient,
                           ResilientMaintenanceClient maintenanceClient,
                           ForecastClient forecastClient,
                           ServiceDayProfileResolver dayProfileResolver,
                           @LoadBalanced RestClient.Builder restClientBuilder) {
        this.tripRepository = tripRepository;
        this.scheduleEngine = scheduleEngine;
        this.fleetClient = fleetClient;
        this.maintenanceClient = maintenanceClient;
        this.forecastClient = forecastClient;
        this.dayProfileResolver = dayProfileResolver;
        this.loadBalancedRestClient = restClientBuilder.build();
    }

    public List<ScheduleTrip> generateFullDayScheduleForDate(String serviceDate, String triggeredBy) {
        long totalStart = System.currentTimeMillis();
        if (!hasText(serviceDate)) throw new IllegalArgumentException("serviceDate is required.");
        trainAssignmentService.clearCache();

        LocalDate date;
        try {
            date = LocalDate.parse(serviceDate);
        } catch (Exception ex) {
            throw new IllegalArgumentException("serviceDate must be yyyy-MM-dd.");
        }

        if (date.isBefore(TimeUtil.todayDate())) {
            throw new IllegalArgumentException("Cannot generate schedule for past date: " + serviceDate);
        }

        boolean isToday = date.equals(TimeUtil.todayDate());
        int nowM = isToday ? TimeUtil.nowMinutes() : TimeUtil.SERVICE_START_MIN;

        List<ScheduleTrip> existing = tripRepository.findByServiceDate(serviceDate);
        List<ScheduleTrip> preservedTrips = new ArrayList<>();

        if (existing != null && !existing.isEmpty()) {
            if (isToday) {
                List<ScheduleTrip> toDelete = new ArrayList<>();
                for (ScheduleTrip t : existing) {
                    if (t.getStatus() == TripStatus.ACTIVE || t.getStatus() == TripStatus.COMPLETED || t.getEndMinutes() <= nowM) {
                        if (t.getEndMinutes() <= nowM && t.getStatus() != TripStatus.COMPLETED) {
                            t.setStatus(TripStatus.COMPLETED);
                        }
                        preservedTrips.add(t);
                    } else {
                        toDelete.add(t);
                    }
                }
                if (!toDelete.isEmpty()) {
                    tripRepository.deleteAll(toDelete);
                    log.info("Regenerate {}: deleted {} stale PROPOSED/PLANNED trips, preserved {} ACTIVE/COMPLETED.",
                            serviceDate, toDelete.size(), preservedTrips.size());
                }
            } else {
                tripRepository.deleteAll(existing);
                log.info("Regenerate {}: deleted all {} existing trips for a clean re-generation.", serviceDate, existing.size());
            }
        }

        final int TRIP_DURATION_MIN = 45;
        final int DAY_START = TimeUtil.SERVICE_START_MIN;
        final int DAY_END = TimeUtil.SERVICE_END_MIN;

        long t = System.currentTimeMillis();
        List<TrainAssetDto> rawFleet = fleetClient.getAvailableTrains();
        if (rawFleet == null || rawFleet.isEmpty()) {
            log.warn("getAvailableTrains() returned empty; attempting fallback to getStandbyTrains().");
            rawFleet = fleetClient.getStandbyTrains();
        }
        Set<String> blockedForServiceDate = maintenanceClient.findTrainsWithActiveTickets(serviceDate);

        List<TrainAssetDto> candidatePool = (rawFleet != null ? rawFleet : new ArrayList<TrainAssetDto>()).stream()
                .filter(tr -> tr != null && tr.getId() != null)
                .filter(tr -> !"IN_MAINTENANCE".equalsIgnoreCase(tr.getStatus()))
                .filter(tr -> {
                    String tid = tr.getId().trim().toUpperCase();
                    String tnum = tr.getTrainNumber() != null ? tr.getTrainNumber().trim().toUpperCase() : "";
                    boolean isBlocked = blockedForServiceDate.contains(tid) || blockedForServiceDate.contains(tnum);
                    if (isBlocked) {
                        log.info("Train {} excluded from candidate pool for date {} because of scheduled/active maintenance.",
                                hasText(tnum) ? tnum : tid, serviceDate);
                    }
                    return !isBlocked;
                })
                .collect(Collectors.toList());

        long inServiceCount = (rawFleet != null ? rawFleet : Collections.<TrainAssetDto>emptyList()).stream()
                .filter(tr -> tr != null && "IN_SERVICE".equalsIgnoreCase(tr.getStatus()))
                .count();
        long standbyCount = (rawFleet != null ? rawFleet : Collections.<TrainAssetDto>emptyList()).stream()
                .filter(tr -> tr != null && "STANDBY".equalsIgnoreCase(tr.getStatus()))
                .count();
        long excludedCount = (rawFleet != null ? rawFleet : Collections.<TrainAssetDto>emptyList()).size() - (candidatePool != null ? candidatePool.size() : 0);

        log.info("[SCHEDULE] fleet-service returned {} trains for serviceDate={}", rawFleet != null ? rawFleet.size() : 0, serviceDate);
        log.info("[SCHEDULE] Total fleet = {}, Maintenance excluded = {}, IN_SERVICE = {}, STANDBY = {}, Candidate pool = {}",
                rawFleet != null ? rawFleet.size() : 0, excludedCount, inServiceCount, standbyCount, candidatePool != null ? candidatePool.size() : 0);
        log.info("FLEET TIME = {} ms ({} candidate trains)", System.currentTimeMillis() - t, candidatePool != null ? candidatePool.size() : 0);

        if (candidatePool == null || candidatePool.isEmpty()) {
            throw new IllegalStateException("No available trains returned from fleet-service. Cannot generate schedule.");
        }

        candidatePool.sort(Comparator.comparingLong(train -> train.getTotalMileageKm() != null ? train.getTotalMileageKm().longValue() : 0L));

        ServiceDayProfileResolver.DayProfile dayProfile = dayProfileResolver.resolve(date);
        boolean holiday = dayProfile.dayType() == ServiceDayProfileResolver.DayType.PUBLIC_HOLIDAY;
        boolean festival = dayProfile.dayType() == ServiceDayProfileResolver.DayType.FESTIVAL;
        String weather = hasText(defaultWeather) ? defaultWeather : "Clear";

        t = System.currentTimeMillis();
        Map<Integer, ForecastScheduleResponse> forecastCache = forecastClient.getDailyProfile(date, holiday, festival, weather);
        if (forecastCache == null) {
            forecastCache = Collections.emptyMap();
        }

        int maxDemandFleet = forecastCache.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(ForecastScheduleResponse::getRecommendedFleetSize)
                .max().orElse(0);
        log.info("FORECAST TIME = {} ms ({} 30-min slots, peak fleet={})", System.currentTimeMillis() - t, forecastCache.size(), maxDemandFleet);

        int peakFleetCount = (maxDemandFleet > 0) ? Math.min(maxDemandFleet, candidatePool.size()) : Math.min(20, candidatePool.size());

        int reserveCount = Math.min(3, Math.max(2, candidatePool.size() - peakFleetCount));
        reserveCount = Math.min(reserveCount, Math.max(0, candidatePool.size() - 1));

        List<TrainAssetDto> activePool = new ArrayList<>(candidatePool.subList(0, candidatePool.size() - reserveCount));
        if (activePool.isEmpty()) {
            throw new IllegalStateException("Not enough operational trains to generate a schedule: " + candidatePool.size() + " available");
        }
        List<TrainAssetDto> yardReserveFleet = candidatePool.subList(candidatePool.size() - reserveCount, candidatePool.size());

        if (isToday) {
            for (TrainAssetDto train : activePool) {
                try { fleetClient.updateTrainStatus(train.getId(), "IN_SERVICE"); } catch (Exception ignored) {}
            }
            for (TrainAssetDto train : yardReserveFleet) {
                try { fleetClient.updateTrainStatus(train.getId(), "STANDBY"); } catch (Exception ignored) {}
            }
        }

        Map<String, Integer> trainLastEnd      = new LinkedHashMap<>();
        Map<String, String>  trainNextRoute    = new HashMap<>();
        Map<String, Double>  trainAccumMileage = new HashMap<>();
        Map<String, Integer> trainParkedUntil  = new HashMap<>();
        Map<String, String>  trainParkedAt     = new HashMap<>();
        Set<String>          trainAtDepot      = new HashSet<>();

        final int ALUVA_POCKET_CAPACITY         = 2;
        final int THRIPPUNITHURA_POCKET_CAPACITY = 3;
        final int IDLE_TIMEOUT_MIN              = 60;

        for (TrainAssetDto train : activePool) {
            trainLastEnd.put(train.getId(), -1);
            trainNextRoute.put(train.getId(), "MUTTOM");
            trainAccumMileage.put(train.getId(), train.getTotalMileageKm() != null ? train.getTotalMileageKm().doubleValue() : 0.0);
        }

        for (ScheduleTrip preserved : preservedTrips) {
            String tid = preserved.getAssignedTrainId();
            if (hasText(tid) && !"Train Not Assigned".equals(tid)) {
                trainLastEnd.merge(tid, preserved.getEndMinutes(), Math::max);
                trainAccumMileage.merge(tid, 25.6, Double::sum);
                String route = preserved.getRouteName();
                trainNextRoute.put(tid, ROUTE_A.equals(route) || (route != null && route.contains("Aluva")) ? ROUTE_B : ROUTE_A);
            }
        }

        scheduleLock.lock();
        t = System.currentTimeMillis();
        try {
            List<ScheduleTrip> trips = new ArrayList<>(preservedTrips);
            Map<String, List<ScheduleTrip>> tripsByTrain = new HashMap<>();
            for (ScheduleTrip pt : preservedTrips) {
                if (pt.getAssignedTrainId() != null && !pt.getAssignedTrainId().equals("Train Not Assigned")) {
                    tripsByTrain.computeIfAbsent(pt.getAssignedTrainId(), k -> new ArrayList<>()).add(pt);
                }
            }
            int seq = preservedTrips.size() + 1;
            double cursor = DAY_START;

            int lastDepartureMin = (festival || holiday) ? (23 * 60) : (22 * 60 + 30);
            while (cursor <= lastDepartureMin) {
                int startM = (int) Math.round(cursor);
                if (startM > lastDepartureMin) break;

                int slotHour = startM / 60;
                LocalTime slotTime = LocalTime.of(slotHour, startM % 60);

                int slotKey = (startM / 30) * 30;
                ForecastScheduleResponse forecast = forecastCache.getOrDefault(slotKey,
                        forecastCache.getOrDefault(slotKey - 30,
                        forecastCache.getOrDefault(slotKey + 30, null)));

                boolean isAiForecastAvailable = (forecast != null && forecast.getRecommendedHeadwaySeconds() > 0);
                boolean isPeakHour = isAiForecastAvailable
                        ? (forecast.getPredictedSurgeMultiplier() >= 1.2 || forecast.getRecommendedFleetSize() >= 11)
                        : ((startM >= 7 * 60 && startM < 10 * 60 + 30) || (startM >= 17 * 60 && startM < 20 * 60));
                boolean isEarlyMorning = startM < 7 * 60;
                boolean isOffPeak = !isPeakHour && (startM >= 10 * 60 + 30 && startM < 17 * 60);

                int baseHeadwaySec = isAiForecastAvailable
                        ? forecast.getRecommendedHeadwaySeconds()
                        : (isPeakHour ? REAL_KMRL_PEAK_HEADWAY_SECONDS
                          : isEarlyMorning ? REAL_KMRL_OFFPEAK_HEADWAY_SECONDS
                          : isOffPeak ? REAL_KMRL_OFFPEAK_HEADWAY_SECONDS
                          : REAL_KMRL_LATE_HEADWAY_SECONDS);

                int headwaySec = baseHeadwaySec;
                headwaySec = Math.max(MIN_HEADWAY_SECONDS, headwaySec);
                double headwayMin = headwaySec / 60.0;

                double dispatchIntervalMin = Math.max(3.5, headwayMin / 2.0);

                int forecastFleet = (forecast != null && forecast.getRecommendedFleetSize() > 0)
                        ? forecast.getRecommendedFleetSize() : 0;
                int slotFleetCeiling = (forecastFleet > 0)
                        ? Math.min(forecastFleet, activePool.size())
                        : (isPeakHour ? Math.min(14, activePool.size()) : Math.min(8, activePool.size()));

                int endM = startM + TRIP_DURATION_MIN;
                if (endM > 24 * 60) break;

                long concurrentNow = trips.stream()
                        .filter(tr -> tr.getStartMinutes() < endM && tr.getEndMinutes() > startM)
                        .count();

                if (concurrentNow >= slotFleetCeiling) {
                    cursor += dispatchIntervalMin;
                    continue;
                }

                for (String tid : new ArrayList<>(trainNextRoute.keySet())) {
                    if (trainAtDepot.contains(tid)) continue;
                    if (trainParkedUntil.containsKey(tid)) continue;
                    int lastEnd = trainLastEnd.getOrDefault(tid, -1);
                    if (lastEnd < 0) continue;
                    int idleMinutes = startM - lastEnd;
                    if (idleMinutes > IDLE_TIMEOUT_MIN) {
                        trainAtDepot.add(tid);

                        trainParkedAt.remove(tid);
                        trainParkedUntil.remove(tid);
                        log.debug("Train {} idle {}min at terminal — returning to Muttom depot.", tid, idleMinutes);
                    }
                }

                if (isPeakHour && !trainAtDepot.isEmpty()) {
                    long neededMore = slotFleetCeiling - concurrentNow;
                    if (neededMore > 0) {
                        List<String> depotByMileage = trainAtDepot.stream()
                                .sorted(Comparator.comparingDouble(tid ->
                                        trainAccumMileage.getOrDefault(tid, 0.0)))
                                .collect(Collectors.toList());
                        for (String tid : depotByMileage) {
                            if (neededMore <= 0) break;
                            trainAtDepot.remove(tid);

                            trainLastEnd.put(tid, startM);
                            neededMore--;
                            log.debug("Train {} re-deployed from Muttom depot for peak demand at {}:{}",
                                    tid, startM / 60, String.format("%02d", startM % 60));
                        }
                    }
                }

                if (isOffPeak) {

                    long activeRunning = trainNextRoute.keySet().stream()
                            .filter(tid -> !trainParkedUntil.containsKey(tid))
                            .filter(tid -> trainLastEnd.getOrDefault(tid, -1) >= 0)
                            .count();

                    if (activeRunning > slotFleetCeiling) {

                        List<String> parkCandidates = trainNextRoute.keySet().stream()
                                .filter(tid -> !trainParkedUntil.containsKey(tid))
                                .filter(tid -> trainLastEnd.getOrDefault(tid, -1) >= 0)
                                .filter(tid -> trainLastEnd.getOrDefault(tid, -1) <= startM)
                                .sorted(Comparator.comparingDouble((String tid) ->
                                        trainAccumMileage.getOrDefault(tid, 0.0)).reversed())
                                .collect(Collectors.toList());

                        for (String tid : parkCandidates) {
                            if (activeRunning <= slotFleetCeiling) break;
                            String lastRoute = trainNextRoute.getOrDefault(tid, "MUTTOM");
                            String parkAt    = ROUTE_B.equals(lastRoute) ? "ALUVA" : "THRIPPUNITHURA";
                            long aluvaParked = trainParkedAt.values().stream().filter("ALUVA"::equals).count();
                            long tptParked   = trainParkedAt.values().stream().filter("THRIPPUNITHURA"::equals).count();

                            if ("ALUVA".equals(parkAt) && aluvaParked < ALUVA_POCKET_CAPACITY) {
                                trainParkedAt.put(tid, "ALUVA");
                                trainParkedUntil.put(tid, 17 * 60);
                                activeRunning--;
                                log.debug("Train {} (high mileage {}) parked Aluva pocket until 17:00", tid,
                                        String.format("%.0f", trainAccumMileage.getOrDefault(tid, 0.0)));
                            } else if ("THRIPPUNITHURA".equals(parkAt) && tptParked < THRIPPUNITHURA_POCKET_CAPACITY) {
                                trainParkedAt.put(tid, "THRIPPUNITHURA");
                                trainParkedUntil.put(tid, 17 * 60);
                                activeRunning--;
                                log.debug("Train {} (high mileage {}) parked Thrippunithura pocket until 17:00", tid,
                                        String.format("%.0f", trainAccumMileage.getOrDefault(tid, 0.0)));
                            }
                        }
                    }
                }

                if (!isOffPeak) {
                    for (String tid : new ArrayList<>(trainParkedUntil.keySet())) {
                        if (startM >= trainParkedUntil.get(tid)) {
                            trainParkedUntil.remove(tid);
                            trainParkedAt.remove(tid);
                            trainLastEnd.put(tid, startM);
                            log.debug("Train {} un-parked from pocket — peak demand resumed at {}:{}",
                                    tid, startM / 60, String.format("%02d", startM % 60));
                        }
                    }
                }

                List<TrainAssetDto> eligiblePool;
                if (isOffPeak) {
                    eligiblePool = activePool.stream()
                            .filter(tr -> !trainParkedUntil.containsKey(tr.getId()))
                            .filter(tr -> !trainAtDepot.contains(tr.getId()))
                            .filter(tr -> trainLastEnd.getOrDefault(tr.getId(), -1) >= 0)
                            .collect(Collectors.toList());

                    if (eligiblePool.isEmpty()) {
                        eligiblePool = activePool.stream()
                                .filter(tr -> !trainParkedUntil.containsKey(tr.getId()))
                                .filter(tr -> !trainAtDepot.contains(tr.getId()))
                                .collect(Collectors.toList());
                    }
                } else {

                    eligiblePool = activePool.stream()
                            .filter(tr -> !trainParkedUntil.containsKey(tr.getId()))
                            .filter(tr -> !trainAtDepot.contains(tr.getId()))
                            .collect(Collectors.toList());
                }

                String preferredRoute = (seq % 2 != 0) ? ROUTE_A : ROUTE_B;
                String otherRoute     = preferredRoute.equals(ROUTE_A) ? ROUTE_B : ROUTE_A;
                String routeName      = preferredRoute;
                String tripCode       = "RUN-AI-" + String.format("%03d", seq);
                String tripId         = "TR-" + serviceDate + "-AI-" + String.format("%03d", seq) + "-" + UUID.randomUUID().toString().substring(0, 6);

                ScheduleTrip trip = new ScheduleTrip();
                trip.setId(tripId); trip.setTripCode(tripCode);
                trip.setRouteName(routeName);
                trip.setStartMinutes(startM); trip.setEndMinutes(endM);
                trip.setStartTime(TimeUtil.formatMinutesToTime(startM));
                trip.setEndTime(TimeUtil.formatMinutesToTime(endM));
                trip.setServiceDate(serviceDate);
                trip.setSource("AI_FORECAST");
                trip.setChangedBy(hasText(triggeredBy) ? triggeredBy : "SADA");
                trip.setChangedAt(Instant.now());
                if (forecast != null) {
                    trip.setPeakStation(forecast.getPeakStation());
                    trip.setPeakDemand(forecast.getPeakDemand());
                    trip.setPredictedSurgeMultiplier(forecast.getPredictedSurgeMultiplier());
                    trip.setEstimatedCapacityPct(forecast.getEstimatedCapacityPct());
                }

                StringBuilder assignmentReason = new StringBuilder();
                TrainAssetDto bestTrain = trainAssignmentService.findSafeTrain(trip, trips, tripsByTrain, eligiblePool, assignmentReason);

                if (bestTrain == null) {
                    trip.setRouteName(otherRoute);
                    StringBuilder altReason = new StringBuilder();
                    TrainAssetDto altTrain = trainAssignmentService.findSafeTrain(trip, trips, tripsByTrain, eligiblePool, altReason);
                    if (altTrain != null) {
                        bestTrain = altTrain;
                        assignmentReason = altReason;
                        routeName = otherRoute;
                    } else {
                        trip.setRouteName(preferredRoute);
                    }
                }

                if (bestTrain == null) {

                    int[] offsets = {-30, 30, -60, 60, -90, 90, -120, 120};
                    for (int offsetSeconds : offsets) {
                        int tempStart = (int) Math.round(startM + offsetSeconds / 60.0);
                        int tempEnd   = (int) Math.round(endM   + offsetSeconds / 60.0);
                        if (tempStart < DAY_START || tempEnd > 24 * 60) continue;

                        ScheduleTrip tempTrip = new ScheduleTrip();
                        tempTrip.setId(tripId); tempTrip.setTripCode(tripCode);
                        tempTrip.setRouteName(routeName);
                        tempTrip.setStartMinutes(tempStart); tempTrip.setEndMinutes(tempEnd);
                        tempTrip.setStartTime(TimeUtil.formatMinutesToTime(tempStart));
                        tempTrip.setEndTime(TimeUtil.formatMinutesToTime(tempEnd));
                        tempTrip.setServiceDate(serviceDate);

                        if (safetyValidator.validateHeadways(tempTrip, trips, null) &&
                            safetyValidator.validateTrackOccupancy(tempTrip, trips, null)) {
                            StringBuilder shiftReason = new StringBuilder();
                            TrainAssetDto shifted = trainAssignmentService.findSafeTrain(tempTrip, trips, tripsByTrain, eligiblePool, shiftReason);
                            if (shifted != null) {
                                trip.setStartMinutes(tempStart); trip.setEndMinutes(tempEnd);
                                trip.setStartTime(tempTrip.getStartTime()); trip.setEndTime(tempTrip.getEndTime());
                                bestTrain = shifted; assignmentReason = shiftReason;
                                log.info("Shifted trip {} by {}s to resolve conflict. New start: {}", tripCode, offsetSeconds, trip.getStartTime());
                                break;
                            }
                        }
                    }
                }

                if (bestTrain == null) {
                    log.debug("No eligible train for slot {}:{} (concurrent={}, ceiling={}) — skipping slot.",
                            startM / 60, String.format("%02d", startM % 60), concurrentNow, slotFleetCeiling);

                    if (startM >= 21 * 60) break;

                    cursor += dispatchIntervalMin;
                    seq++;
                    continue;
                }

                trip.setAssignedTrainId(bestTrain.getId());
                trip.setAssignedTrainName(bestTrain.getTrainNumber() != null ? bestTrain.getTrainNumber() : bestTrain.getId());
                trip.setAssignmentStatus("ASSIGNED");
                trip.setAssignmentReason(assignmentReason.toString());
                trip.setOccupancyStart(trip.getStartMinutes());
                trip.setOccupancyEnd(trip.getEndMinutes());

                if (routeName.contains("Aluva to Thrippunithura")) {
                    trip.setTrackId("T1");
                    trip.setSectionIds("ALU_EDP_UP,EDP_JLN_UP,JLN_MGR_UP,MGR_TPT_UP");
                    trip.setPlatformId("ALU_P" + ((seq % 2) + 1));
                } else {
                    trip.setTrackId("T2");
                    trip.setSectionIds("TPT_MGR_DN,MGR_JLN_DN,JLN_EDP_DN,EDP_ALU_DN");
                    trip.setPlatformId("TPT_P" + ((seq % 3) + 1));
                }

                trainLastEnd.put(bestTrain.getId(), trip.getEndMinutes());
                trainAccumMileage.merge(bestTrain.getId(), 25.6, Double::sum);
                trainNextRoute.put(bestTrain.getId(), ROUTE_A.equals(routeName) ? ROUTE_B : ROUTE_A);

                TripStatus status = TripStatus.PROPOSED;
                if (isToday) {
                    if (trip.getEndMinutes() <= nowM) {
                        status = TripStatus.COMPLETED;
                    } else if (trip.getStartMinutes() <= nowM && trip.getEndMinutes() > nowM) {
                        status = TripStatus.ACTIVE;
                    }
                }
                trip.setStatus(status);
                trips.add(trip);
                tripsByTrain.computeIfAbsent(bestTrain.getId(), k -> new ArrayList<>()).add(trip);

                cursor = Math.max(cursor + dispatchIntervalMin, startM + dispatchIntervalMin);
                seq++;
            }

            log.info("SCHEDULING LOOP TIME = {} ms", System.currentTimeMillis() - t);

            List<ScheduleTrip> finalTrips = trips.stream()
                    .filter(tr -> preservedTrips.contains(tr) || "ASSIGNED".equals(tr.getAssignmentStatus()))
                    .collect(Collectors.toList());

            t = System.currentTimeMillis();
            tripRepository.saveAll(finalTrips);
            log.info("DB SAVE TIME = {} ms ({} trips)", System.currentTimeMillis() - t, finalTrips.size());

            int newTripsCount = finalTrips.size() - preservedTrips.size();
            log.info("Generated {} operational trips for {} (peak-fleet={}, yard-reserve={}, preserved={}).",
                    newTripsCount, serviceDate, peakFleetCount, reserveCount, preservedTrips.size());

            t = System.currentTimeMillis();
            pushBatchApprovalTask(serviceDate, finalTrips.size(), triggeredBy);
            log.info("APPROVER TIME = {} ms", System.currentTimeMillis() - t);

            log.info("TOTAL GENERATION TIME = {} ms", System.currentTimeMillis() - totalStart);
            return finalTrips;
        } finally {
            trainAssignmentService.clearCache();
            scheduleLock.unlock();
        }
    }

    private void pushBatchApprovalTask(String serviceDate, int tripCount, String triggeredBy) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetEntityId", serviceDate);
            payload.put("requestType", "SCHEDULE_PROPOSAL");
            payload.put("title", "AI Schedule Proposal — " + serviceDate);
            payload.put("description", "AI-generated full-day schedule for " + serviceDate + ": " + tripCount + " trips proposed (05:00 – 23:00).");
            payload.put("priority", "HIGH");
            payload.put("requestedBy", triggeredBy != null ? triggeredBy : "AI_CRON");

            var requestSpec = loadBalancedRestClient.post()
                    .uri("http://approver-service/api/approver/tasks/submit")
                    .header("X-User-Id", "schedule-service")
                    .header("X-User-Role", "SADA");
            if (internalServiceSecret != null && !internalServiceSecret.isBlank()) {
                requestSpec.header("X-Gateway-Secret", internalServiceSecret);
            }
            requestSpec.body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Pushed batch SCHEDULE_PROPOSAL approval task for {} ({} trips).", serviceDate, tripCount);
        } catch (Exception e) {
            log.error("Failed to push batch approval task to approver-service: {}", e.getMessage());
        }
    }

    private void pushSingleTripApprovalTask(ScheduleTrip trip, String triggeredBy) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetEntityId", trip.getId());
            payload.put("requestType", "SCHEDULE_PROPOSAL");
            payload.put("title", "Trip Proposal — " + trip.getTripCode() + " (" + trip.getServiceDate() + ")");
            payload.put("description", "OC proposed " + trip.getTripCode() + " on " + trip.getRouteName() + " (" + trip.getStartTime() + " – " + trip.getEndTime() + ") with train " + trip.getAssignedTrainName() + " [" + trip.getAssignedTrainId() + "].");
            payload.put("priority", "HIGH");
            payload.put("requestedBy", hasText(triggeredBy) ? triggeredBy : "OC");

            var requestSpec = loadBalancedRestClient.post()
                    .uri("http://approver-service/api/approver/tasks/submit")
                    .header("X-User-Id", "schedule-service")
                    .header("X-User-Role", "SADA");
            if (internalServiceSecret != null && !internalServiceSecret.isBlank()) {
                requestSpec.header("X-Gateway-Secret", internalServiceSecret);
            }
            requestSpec.body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Pushed single SCHEDULE_PROPOSAL approval task for trip {}", trip.getId());
        } catch (Exception e) {
            log.error("Failed to push single trip approval task to approver-service: {}", e.getMessage());
        }
    }

    public List<ScheduleTrip> getTripsByDate(String serviceDate) {
        List<ScheduleTrip> list = tripRepository.findByServiceDate(serviceDate);
        if (list == null || list.isEmpty()) return new ArrayList<>();
        return list;
    }

    public List<ScheduleTrip> getTripsInCurrentTimeWindow() {
        return getTripsByDate(TimeUtil.today());
    }

    public List<ScheduleTrip> getTripsForDate(String date) {
        return getTripsByDate(date);
    }

    public List<ScheduleTrip> saveTrips(List<ScheduleTrip> trips) {
        return tripRepository.saveAll(trips);
    }

    public ScheduleTrip getTripById(String tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip schedule not found: " + tripId));
    }

    public WithdrawTrainResponse withdrawTrain(String trainId, boolean emergency) {
        return scheduleEngine.withdrawTrain(trainId, emergency);
    }

    public ScheduleTrip proposeTrip(ProposeTripRequest request) {
        scheduleLock.lock();
        try {
            trainAssignmentService.clearCache();
            if (!hasText(request.getServiceDate())) {
                throw new IllegalArgumentException("serviceDate cannot be null or blank");
            }

            String serviceDate = request.getServiceDate().trim();
            LocalDate requestedServiceDate = LocalDate.parse(serviceDate);
            if (requestedServiceDate.isBefore(TimeUtil.todayDate())) {
                throw new IllegalArgumentException("serviceDate cannot be in the past");
            }

            if (!hasText(request.getAssignedTrainId())) {
                throw new IllegalArgumentException("Cannot schedule trip: A specific Train Set ID must be provided.");
            }

            int fallbackStartMinutes = TimeUtil.nowMinutes();
            String rawStartTime = hasText(request.getStartTime()) ? request.getStartTime() : TimeUtil.formatMinutesToTime(fallbackStartMinutes);
            int startMinutes = parseTimeToMinutes(rawStartTime, fallbackStartMinutes);
            int fallbackEndMinutes = startMinutes + 45;
            String rawEndTime = hasText(request.getEndTime()) ? request.getEndTime() : TimeUtil.formatMinutesToTime(fallbackEndMinutes);
            int endMinutes = parseTimeToMinutes(rawEndTime, fallbackEndMinutes);
            String startTime = TimeUtil.formatMinutesToTime(startMinutes);
            String endTime = TimeUtil.formatMinutesToTime(endMinutes);

            if (endMinutes - startMinutes > 60) {
                throw new IllegalArgumentException("Cannot schedule trip: Maximum allowable trip duration is 60 minutes. Proposed duration: " + (endMinutes - startMinutes) + " minutes.");
            }
            if (endMinutes <= startMinutes) {
                throw new IllegalArgumentException("Cannot schedule trip: End time must be strictly after start time.");
            }
            if (startMinutes < TimeUtil.SERVICE_START_MIN || endMinutes > TimeUtil.SERVICE_END_MIN) {
                throw new IllegalArgumentException("Cannot schedule trip: Time falls outside of operating hours (05:00 - 23:00).");
            }

            String routeName = hasText(request.getRouteName()) ? request.getRouteName().trim() : ROUTE_A;

            List<ScheduleTrip> dayTrips = tripRepository.findByServiceDate(serviceDate);
            List<TrainAssetDto> rawFleet = fleetClient.getAvailableTrains();
        if (rawFleet == null || rawFleet.isEmpty()) {
            log.warn("getAvailableTrains() returned empty; attempting fallback to getStandbyTrains().");
            rawFleet = fleetClient.getStandbyTrains();
        }
            Set<String> blockedForServiceDate = maintenanceClient.findTrainsWithActiveTickets(serviceDate);

            List<TrainAssetDto> candidatePool = (rawFleet != null ? rawFleet : new ArrayList<TrainAssetDto>()).stream()
                    .filter(tr -> tr != null && tr.getId() != null)
                    .filter(tr -> !"IN_MAINTENANCE".equalsIgnoreCase(tr.getStatus()))
                    .filter(tr -> {
                        String tid = tr.getId().trim().toUpperCase();
                        String tnum = tr.getTrainNumber() != null ? tr.getTrainNumber().trim().toUpperCase() : "";
                        boolean isBlocked = blockedForServiceDate.contains(tid) || blockedForServiceDate.contains(tnum);
                        return !isBlocked;
                    })
                    .collect(Collectors.toList());

            String targetTrainId = normalizeTrainId(request.getAssignedTrainId());
            TrainAssetDto foundTrain = candidatePool.stream()
                    .filter(t -> t.getId().equalsIgnoreCase(targetTrainId))
                    .findFirst()
                    .orElse(null);

            if (foundTrain == null) {
                throw new IllegalArgumentException("Cannot schedule trip: Requested train " + targetTrainId + " is unavailable, in maintenance, or does not exist.");
            }
            final TrainAssetDto requestedTrain = foundTrain;

            String id = "TR-" + serviceDate + "-" + request.getTripCode() + "-" + UUID.randomUUID().toString().substring(0, 8);
            ScheduleTrip trip = new ScheduleTrip();
            trip.setId(id);
            trip.setTripCode(request.getTripCode());
            trip.setRouteName(routeName);
            trip.setStartTime(startTime);
            trip.setEndTime(endTime);
            trip.setStartMinutes(startMinutes);
            trip.setEndMinutes(endMinutes);
            trip.setServiceDate(serviceDate);
            trip.setSource(hasText(request.getSource()) ? request.getSource().toUpperCase() : "MANUAL");
            trip.setChangedBy("MANUAL");
            trip.setChangedAt(Instant.now());

            StringBuilder timingRejectReason = new StringBuilder();
            boolean timingSafe = safetyValidator.validateHeadways(trip, dayTrips, timingRejectReason) &&
                                 safetyValidator.validateTrackOccupancy(trip, dayTrips, timingRejectReason);

            if (!timingSafe) {
                throw new IllegalArgumentException("Cannot schedule trip: " + timingRejectReason.toString());
            }

            StringBuilder trainRejectReason = new StringBuilder();
            boolean available = fleetClient.checkTrainAvailability(requestedTrain.getId(), startTime, endTime);
            if (!available) {
                throw new IllegalArgumentException("Cannot schedule trip: Fleet Service reports train " + targetTrainId + " is not operational or has active maintenance tickets.");
            }

            List<ScheduleTrip> trainTrips = dayTrips.stream()
                    .filter(t -> matchesTrainIdentifier(t, requestedTrain.getId()))
                    .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                    .collect(Collectors.toList());

            if (!safetyValidator.validateTrainTurnaroundAndOverlap(trip, trainTrips, trainRejectReason)) {
                throw new IllegalArgumentException("Cannot schedule trip: " + trainRejectReason.toString());
            }

            if (!safetyValidator.validateTrainDirectionContinuity(trip, trainTrips, trainRejectReason)) {
                throw new IllegalArgumentException("Cannot schedule trip: " + trainRejectReason.toString());
            }

            trip.setAssignedTrainId(requestedTrain.getId());
            trip.setAssignedTrainName(requestedTrain.getTrainNumber() != null ? requestedTrain.getTrainNumber() : requestedTrain.getId());

            if (!safetyValidator.validatePlatformOccupancy(trip, dayTrips, trainRejectReason)) {
                throw new IllegalArgumentException("Cannot schedule trip: " + trainRejectReason.toString());
            }

            trip.setAssignmentStatus("ASSIGNED");
            trip.setAssignmentReason("Manually assigned train " + requestedTrain.getTrainNumber() + " is safe.");
            trip.setOccupancyStart(trip.getStartMinutes());
            trip.setOccupancyEnd(trip.getEndMinutes());
            int seq = dayTrips.size() + 1;
            if (routeName.contains("Aluva to Thrippunithura")) {
                trip.setTrackId("T1");
                trip.setSectionIds("ALU_EDP_UP,EDP_JLN_UP,JLN_MGR_UP,MGR_TPT_UP");
                trip.setPlatformId("ALU_P" + ((seq % 2) + 1));
            } else {
                trip.setTrackId("T2");
                trip.setSectionIds("TPT_MGR_DN,MGR_JLN_DN,JLN_EDP_DN,EDP_ALU_DN");
                trip.setPlatformId("TPT_P" + ((seq % 3) + 1));
            }

            trip.setStatus(TripStatus.PROPOSED);
            ScheduleTrip saved = tripRepository.save(trip);
            pushSingleTripApprovalTask(saved, hasText(request.getSource()) ? request.getSource() : "OC");
            return saved;
        } finally {
            trainAssignmentService.clearCache();
            scheduleLock.unlock();
        }
    }

    public void deleteProposedTrip(String tripId, String callerRole, String callerName, String reason) {
        ScheduleTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
        if (trip.getStatus() != TripStatus.PROPOSED) {
            throw new IllegalStateException("Only PROPOSED trips may be deleted.");
        }
        String actor = hasText(callerName) ? callerName.trim() : (hasText(callerRole) ? callerRole.trim() : "unknown");
        String auditReason = hasText(reason) ? reason.trim() : "Deleted by " + actor;
        trip.setChangedBy(actor);
        trip.setChangedAt(Instant.now());
        trip.setChangeReason(auditReason);
        tripRepository.save(trip);
        tripRepository.deleteById(tripId);
        log.info("Deleted proposed trip {} by {} (role: {}): {}", tripId, actor, callerRole, auditReason);
    }

    public void validateFullSchedule(List<ScheduleTrip> dayTrips) {
        String sDate = (dayTrips != null && !dayTrips.isEmpty()) ? dayTrips.get(0).getServiceDate() : TimeUtil.today();
        List<TrainAssetDto> rawFleet = fleetClient.getAvailableTrains();
        if (rawFleet == null || rawFleet.isEmpty()) {
            log.warn("getAvailableTrains() returned empty; attempting fallback to getStandbyTrains().");
            rawFleet = fleetClient.getStandbyTrains();
        }
        Set<String> blockedForServiceDate = maintenanceClient.findTrainsWithActiveTickets(sDate);

        List<TrainAssetDto> candidatePool = (rawFleet != null ? rawFleet : new ArrayList<TrainAssetDto>()).stream()
                .filter(tr -> tr != null && tr.getId() != null)
                .filter(tr -> !"IN_MAINTENANCE".equalsIgnoreCase(tr.getStatus()))
                .filter(tr -> {
                    String tid = tr.getId().trim().toUpperCase();
                    String tnum = tr.getTrainNumber() != null ? tr.getTrainNumber().trim().toUpperCase() : "";
                    boolean isBlocked = blockedForServiceDate.contains(tid) || blockedForServiceDate.contains(tnum);
                    if (isBlocked) {
                        log.info("Train {} excluded from candidate pool for date {} because of scheduled/active maintenance.",
                                hasText(tnum) ? tnum : tid, sDate);
                    }
                    return !isBlocked;
                })
                .collect(Collectors.toList());
        List<ScheduleTrip> activeTrips = dayTrips.stream()
                .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                .collect(Collectors.toList());

        for (ScheduleTrip trip : activeTrips) {

            StringBuilder timingError = new StringBuilder();
            if (!safetyValidator.validateHeadways(trip, activeTrips, timingError)) {
                throw new IllegalArgumentException("Schedule validation failed for trip " + trip.getTripCode() + ": " + timingError.toString());
            }
            if (!safetyValidator.validateTrackOccupancy(trip, activeTrips, timingError)) {
                throw new IllegalArgumentException("Schedule validation failed for trip " + trip.getTripCode() + ": " + timingError.toString());
            }

            if (trip.getAssignmentStatus() != null && "NOT_POSSIBLE".equalsIgnoreCase(trip.getAssignmentStatus())) {
                throw new IllegalArgumentException("Schedule contains unassigned/impossible duties. Trip " + trip.getTripCode() + " has status NOT_POSSIBLE: " + trip.getAssignmentReason());
            }

            if (trip.getAssignedTrainId() != null && !"Train Not Assigned".equalsIgnoreCase(trip.getAssignedTrainName())) {

                boolean available = fleetClient.checkTrainAvailability(trip.getAssignedTrainId(), trip.getStartTime(), trip.getEndTime());
                if (!available) {
                    throw new IllegalArgumentException("Train " + trip.getAssignedTrainId() + " assigned to trip " + trip.getTripCode() + " is not operational or in maintenance.");
                }

                List<ScheduleTrip> trainTrips = activeTrips.stream()
                        .filter(t -> matchesTrainIdentifier(t, trip.getAssignedTrainId()))
                        .collect(Collectors.toList());

                StringBuilder trainError = new StringBuilder();
                if (!safetyValidator.validateTrainTurnaroundAndOverlap(trip, trainTrips, trainError)) {
                    throw new IllegalArgumentException("Conflict detected for train " + trip.getAssignedTrainId() + ": " + trainError.toString());
                }
                if (!safetyValidator.validateTrainDirectionContinuity(trip, trainTrips, trainError)) {
                    throw new IllegalArgumentException("Continuity error for train " + trip.getAssignedTrainId() + ": " + trainError.toString());
                }
            }
        }

        StringBuilder platformError = new StringBuilder();
        for (ScheduleTrip trip : activeTrips) {
            if (!safetyValidator.validatePlatformOccupancy(trip, activeTrips, platformError)) {
                throw new IllegalArgumentException("Platform capacity conflict: " + platformError.toString());
            }
        }
    }

    public int approveDaySchedule(String serviceDate) {
        scheduleLock.lock();
        try {
            List<ScheduleTrip> allTrips = tripRepository.findByServiceDate(serviceDate);

            List<ScheduleTrip> proposed = allTrips.stream()
                    .filter(t -> t.getStatus() == TripStatus.PROPOSED)
                    .collect(Collectors.toList());

            if (proposed.isEmpty()) {
                return 0;
            }

            List<ScheduleTrip> tripsToValidate = allTrips.stream()
                    .filter(t -> t.getStatus() == TripStatus.PROPOSED
                            || t.getStatus() == TripStatus.PLANNED
                            || t.getStatus() == TripStatus.ACTIVE)
                    .collect(Collectors.toList());

            validateFullSchedule(tripsToValidate);

            proposed.forEach(t -> {
                t.setStatus(TripStatus.PLANNED);
                t.setChangedAt(Instant.now());
                t.setChangedBy("APPROVER");
            });
            tripRepository.saveAll(proposed);
            return proposed.size();
        } finally {
            scheduleLock.unlock();
        }
    }

    public int rejectDaySchedule(String serviceDate) {
        scheduleLock.lock();
        try {
            List<ScheduleTrip> proposed = tripRepository.findByServiceDate(serviceDate).stream()
                    .filter(t -> t.getStatus() == TripStatus.PROPOSED)
                    .collect(Collectors.toList());
            proposed.forEach(t -> {
                t.setStatus(TripStatus.REJECTED);
                t.setChangedAt(Instant.now());
                t.setChangedBy("APPROVER");
                t.setChangeReason("Schedule proposal rejected by SADA");
            });
            tripRepository.saveAll(proposed);
            return proposed.size();
        } finally {
            scheduleLock.unlock();
        }
    }

    public Map<String, Integer> decideSelectedTrips(String serviceDate, List<String> approvedTripIds, List<String> rejectedTripIds, String callerRole, String reason) {
        scheduleLock.lock();
        try {
            List<ScheduleTrip> proposed = tripRepository.findByServiceDate(serviceDate).stream()
                    .filter(t -> t.getStatus() == TripStatus.PROPOSED)
                    .collect(Collectors.toList());
            if (proposed.isEmpty()) {
                return Map.of("approved", 0, "rejected", 0);
            }

            Set<String> appSet = approvedTripIds != null ? new HashSet<>(approvedTripIds) : Set.of();
            Set<String> rejSet = rejectedTripIds != null ? new HashSet<>(rejectedTripIds) : Set.of();

            int approvedCount = 0;
            int rejectedCount = 0;

            List<ScheduleTrip> modified = new ArrayList<>();
            for (ScheduleTrip t : proposed) {
                if (appSet.contains(t.getId())) {
                    t.setStatus(TripStatus.PLANNED);
                    t.setChangedAt(Instant.now());
                    t.setChangedBy(hasText(callerRole) ? callerRole.replace("ROLE_", "") : "APPROVER");
                    t.setChangeReason(hasText(reason) ? reason : "Selected trip approved");
                    modified.add(t);
                    approvedCount++;
                } else if (rejSet.contains(t.getId())) {
                    t.setStatus(TripStatus.REJECTED);
                    t.setChangedAt(Instant.now());
                    t.setChangedBy(hasText(callerRole) ? callerRole.replace("ROLE_", "") : "APPROVER");
                    t.setChangeReason(hasText(reason) ? reason : "Selected trip rejected");
                    modified.add(t);
                    rejectedCount++;
                }
            }

            if (!modified.isEmpty()) {
                tripRepository.saveAll(modified);
            }
            log.info("Granular decision for {}: approved {}, rejected {}.", serviceDate, approvedCount, rejectedCount);
            return Map.of("approved", approvedCount, "rejected", rejectedCount);
        } finally {
            scheduleLock.unlock();
        }
    }


    public int resolveScheduleCollisions(List<ScheduleTrip> trips) {
        if (trips == null || trips.isEmpty()) return 0;

        Map<String, List<ScheduleTrip>> byTrain = trips.stream()
                .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                .filter(t -> hasText(t.getAssignedTrainId()) || hasText(t.getAssignedTrainName()))
                .collect(Collectors.groupingBy(t -> {
                    String norm = normalizeTrainId(t.getAssignedTrainId());
                    return hasText(norm) ? norm : normalizeTrainId(t.getAssignedTrainName());
                }));

        int resolvedCount = 0;
        for (List<ScheduleTrip> trainTrips : byTrain.values()) {
            if (trainTrips.size() < 2) continue;

            boolean changed = true;
            int passes = 0;
            while (changed && passes < 10) {
                changed = false;
                passes++;
                trainTrips.sort(Comparator.comparingInt(ScheduleTrip::getStartMinutes));

                for (int i = 0; i < trainTrips.size() - 1; i++) {
                    ScheduleTrip t1 = trainTrips.get(i);
                    ScheduleTrip t2 = trainTrips.get(i + 1);
                    int minAllowedStart = t1.getEndMinutes() + 3;
                    if (t2.getStartMinutes() < minAllowedStart) {
                        int duration = Math.max(45, t2.getEndMinutes() - t2.getStartMinutes());
                        int adjustedStart = minAllowedStart;
                        if (adjustedStart + duration > TimeUtil.SERVICE_END_MIN) {
                            t2.setAssignmentStatus("NOT_POSSIBLE");
                            t2.setChangeReason("Cannot resolve timing overlap with " + t1.getTripCode() + ": trip cannot be shifted within service hours (exceeds " + TimeUtil.SERVICE_END_MIN + " min).");
                            log.warn("resolveScheduleCollisions: Trip {} cannot be shifted within service hours (end would be {} > {}).",
                                    t2.getTripCode(), adjustedStart + duration, TimeUtil.SERVICE_END_MIN);
                        } else {
                            int adjustedEnd = adjustedStart + duration;
                            t2.setStartMinutes(adjustedStart);
                            t2.setEndMinutes(adjustedEnd);
                            t2.setStartTime(TimeUtil.formatMinutesToTime(adjustedStart));
                            t2.setEndTime(TimeUtil.formatMinutesToTime(adjustedEnd));
                            t2.setChangeReason("Anti-Collision Engine: Resolved timing overlap with " + t1.getTripCode());
                            resolvedCount++;
                            changed = true;
                        }
                    }
                }
            }
        }
        return resolvedCount;
    }

    public ScheduleTrip adjustTrip(String tripId, AdjustTripRequest request, String callerRole) {
        scheduleLock.lock();
        try {
            ScheduleTrip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

            if (trip.getStatus() != TripStatus.PROPOSED && trip.getStatus() != TripStatus.PLANNED) {
                throw new IllegalStateException("Only PROPOSED or PLANNED trips can be adjusted. Current status: " + trip.getStatus());
            }

            String targetTrainId = hasText(request.getAssignedTrainId()) ? request.getAssignedTrainId().trim() : trip.getAssignedTrainId();
            String targetRoute = hasText(request.getRouteName()) ? request.getRouteName().trim() : trip.getRouteName();

            int targetStartM = trip.getStartMinutes();
            if (hasText(request.getStartTime())) {
                targetStartM = parseTimeToMinutes(request.getStartTime(), trip.getStartMinutes());
            }

            int targetEndM = trip.getEndMinutes();
            if (hasText(request.getEndTime())) {
                targetEndM = parseTimeToMinutes(request.getEndTime(), trip.getEndMinutes());
            }

            if (targetEndM - targetStartM > 60) {
                throw new IllegalArgumentException("Cannot modify schedule: Maximum allowable trip duration is 60 minutes. Proposed duration: " + (targetEndM - targetStartM) + " minutes.");
            }
            if (targetEndM <= targetStartM) {
                throw new IllegalArgumentException("Cannot modify schedule: End time must be strictly after start time.");
            }
            if (targetStartM < TimeUtil.SERVICE_START_MIN || targetEndM > TimeUtil.SERVICE_END_MIN) {
                throw new IllegalArgumentException("Cannot modify schedule: Time falls outside of operating hours (05:00 - 23:00).");
            }

            List<ScheduleTrip> dayTrips = tripRepository.findByServiceDate(trip.getServiceDate());
            String sDate = trip.getServiceDate();
            List<TrainAssetDto> rawFleet = fleetClient.getAvailableTrains();
        if (rawFleet == null || rawFleet.isEmpty()) {
            log.warn("getAvailableTrains() returned empty; attempting fallback to getStandbyTrains().");
            rawFleet = fleetClient.getStandbyTrains();
        }
        Set<String> blockedForServiceDate = maintenanceClient.findTrainsWithActiveTickets(sDate);

        List<TrainAssetDto> candidatePool = (rawFleet != null ? rawFleet : new ArrayList<TrainAssetDto>()).stream()
                .filter(tr -> tr != null && tr.getId() != null)
                .filter(tr -> !"IN_MAINTENANCE".equalsIgnoreCase(tr.getStatus()))
                .filter(tr -> {
                    String tid = tr.getId().trim().toUpperCase();
                    String tnum = tr.getTrainNumber() != null ? tr.getTrainNumber().trim().toUpperCase() : "";
                    boolean isBlocked = blockedForServiceDate.contains(tid) || blockedForServiceDate.contains(tnum);
                    if (isBlocked) {
                        log.info("Train {} excluded from candidate pool for date {} because of scheduled/active maintenance.",
                                hasText(tnum) ? tnum : tid, sDate);
                    }
                    return !isBlocked;
                })
                .collect(Collectors.toList());

            ScheduleTrip tempTrip = new ScheduleTrip();
            tempTrip.setId(trip.getId());
            tempTrip.setTripCode(trip.getTripCode());
            tempTrip.setRouteName(targetRoute);
            tempTrip.setStartMinutes(targetStartM);
            tempTrip.setEndMinutes(targetEndM);
            tempTrip.setStartTime(TimeUtil.formatMinutesToTime(targetStartM));
            tempTrip.setEndTime(TimeUtil.formatMinutesToTime(targetEndM));
            tempTrip.setServiceDate(trip.getServiceDate());

            StringBuilder timingRejectReason = new StringBuilder();
            boolean timingSafe = safetyValidator.validateHeadways(tempTrip, dayTrips, timingRejectReason) &&
                                 safetyValidator.validateTrackOccupancy(tempTrip, dayTrips, timingRejectReason);

            if (!timingSafe) {
                throw new IllegalArgumentException("Cannot modify schedule: " + timingRejectReason.toString());
            }

            TrainAssetDto assignedTrain = null;
            StringBuilder assignmentReason = new StringBuilder();

            if (!hasText(targetTrainId) || "Train Not Assigned".equalsIgnoreCase(targetTrainId)) {
                throw new IllegalArgumentException("Cannot modify schedule: A specific Train Set ID must be provided.");
            }

            String normTrainId = normalizeTrainId(targetTrainId);
            TrainAssetDto foundTrain = candidatePool.stream()
                    .filter(t -> t.getId().equalsIgnoreCase(normTrainId))
                    .findFirst()
                    .orElse(null);

            if (foundTrain == null) {
                throw new IllegalArgumentException("Cannot modify schedule: Requested train " + targetTrainId + " is unavailable, in maintenance, or does not exist.");
            }
            final TrainAssetDto requestedTrain = foundTrain;

            StringBuilder trainRejectReason = new StringBuilder();
            boolean available = fleetClient.checkTrainAvailability(requestedTrain.getId(), tempTrip.getStartTime(), tempTrip.getEndTime());
            if (!available) {
                throw new IllegalArgumentException("Cannot modify schedule: Fleet Service reports train " + targetTrainId + " is not operational or has active maintenance tickets.");
            }

            List<ScheduleTrip> trainTrips = dayTrips.stream()
                    .filter(t -> !t.getId().equals(trip.getId()))
                    .filter(t -> matchesTrainIdentifier(t, requestedTrain.getId()))
                    .filter(t -> t.getStatus() != TripStatus.CANCELLED && t.getStatus() != TripStatus.MISSED)
                    .collect(Collectors.toList());

            if (!safetyValidator.validateTrainTurnaroundAndOverlap(tempTrip, trainTrips, trainRejectReason)) {
                throw new IllegalArgumentException("Cannot modify schedule: " + trainRejectReason.toString());
            }

            if (!safetyValidator.validateTrainDirectionContinuity(tempTrip, trainTrips, trainRejectReason)) {
                throw new IllegalArgumentException("Cannot modify schedule: " + trainRejectReason.toString());
            }

            tempTrip.setAssignedTrainId(requestedTrain.getId());
            tempTrip.setAssignedTrainName(requestedTrain.getTrainNumber() != null ? requestedTrain.getTrainNumber() : requestedTrain.getId());
            if (!safetyValidator.validatePlatformOccupancy(tempTrip, dayTrips, trainRejectReason)) {
                throw new IllegalArgumentException("Cannot modify schedule: " + trainRejectReason.toString());
            }

            assignedTrain = requestedTrain;
            assignmentReason.append("Assigned train ").append(requestedTrain.getTrainNumber()).append(" is safe.");

            if (assignedTrain != null) {
                trip.setAssignedTrainId(assignedTrain.getId());
                trip.setAssignedTrainName(assignedTrain.getTrainNumber() != null ? assignedTrain.getTrainNumber() : assignedTrain.getId());
                trip.setAssignmentStatus("ASSIGNED");
                trip.setAssignmentReason(assignmentReason.toString());

                trip.setOccupancyStart(targetStartM);
                trip.setOccupancyEnd(targetEndM);
                if (targetRoute.contains("Aluva to Thrippunithura")) {
                    trip.setTrackId("T1");
                    trip.setSectionIds("ALU_EDP_UP,EDP_JLN_UP,JLN_MGR_UP,MGR_TPT_UP");
                    trip.setPlatformId("ALU_P" + ((dayTrips.size() % 2) + 1));
                } else {
                    trip.setTrackId("T2");
                    trip.setSectionIds("TPT_MGR_DN,MGR_JLN_DN,JLN_EDP_DN,EDP_ALU_DN");
                    trip.setPlatformId("TPT_P" + ((dayTrips.size() % 3) + 1));
                }
            }

            trip.setRouteName(targetRoute);
            trip.setStartTime(tempTrip.getStartTime());
            trip.setStartMinutes(targetStartM);
            trip.setEndTime(tempTrip.getEndTime());
            trip.setEndMinutes(targetEndM);

            trip.setChangedBy(callerRole != null ? callerRole.replace("ROLE_", "") : "OC");
            trip.setChangedAt(Instant.now());
            if (hasText(request.getReason())) trip.setChangeReason(request.getReason().trim());

            return tripRepository.save(trip);
        } finally {
            scheduleLock.unlock();
        }
    }

    private static boolean matchesTrainIdentifier(ScheduleTrip trip, String targetTrainId) {
        return com.kce.kmrl.schedule.util.TrainIdUtil.matchesTrainIdentifier(trip, targetTrainId);
    }

    private static String normalizeTrainId(String input) {
        if (input == null || input.isBlank()) return "";
        String clean = input.trim();
        String digits = clean.replaceAll("\\D+", "");
        if (!digits.isEmpty()) {
            try {
                int num = Integer.parseInt(digits);
                return String.format("TS-%02d", num);
            } catch (Exception ignored) {}
        }
        return clean.toUpperCase();
    }

    private static final Map<TripStatus, Set<TripStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            TripStatus.PROPOSED, Set.of(TripStatus.PLANNED, TripStatus.REJECTED, TripStatus.CANCELLED),
            TripStatus.PLANNED, Set.of(TripStatus.ACTIVE, TripStatus.DELAYED, TripStatus.CANCELLED, TripStatus.MISSED, TripStatus.AWAITING_REPLACEMENT),
            TripStatus.ACTIVE, Set.of(TripStatus.COMPLETED, TripStatus.DELAYED, TripStatus.CANCELLED),
            TripStatus.DELAYED, Set.of(TripStatus.ACTIVE, TripStatus.COMPLETED, TripStatus.CANCELLED, TripStatus.MISSED),
            TripStatus.AWAITING_REPLACEMENT, Set.of(TripStatus.PLANNED, TripStatus.ACTIVE, TripStatus.CANCELLED, TripStatus.MISSED)
    );

    public ScheduleTrip updateTripStatus(String tripId, StatusUpdateRequest request, String callerRole) {
        ScheduleTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("Status must not be blank.");
        }

        TripStatus newStatus;
        try {
            newStatus = TripStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Unknown trip status: '" + request.getStatus() + "'. Allowed statuses: " + Arrays.toString(TripStatus.values()));
        }

        TripStatus currentStatus = trip.getStatus();
        if (currentStatus == newStatus) {
            return trip;
        }

        Set<TripStatus> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowed.contains(newStatus)) {
            throw new IllegalArgumentException("Disallowed status transition: cannot move trip from " + currentStatus + " to " + newStatus);
        }

        String role = (callerRole != null) ? callerRole.trim().toUpperCase(Locale.ENGLISH).replace("ROLE_", "") : "OC";
        if (currentStatus == TripStatus.PROPOSED && (newStatus == TripStatus.PLANNED || newStatus == TripStatus.REJECTED)) {
            if (!"SADA".equals(role) && !"SYSTEM".equals(role)) {
                throw new IllegalStateException("Only SADA or SYSTEM callers can transition a trip from PROPOSED to " + newStatus + ". Current caller role: " + role);
            }
        }

        trip.setStatus(newStatus);
        trip.setChangedBy(role);
        trip.setChangedAt(Instant.now());
        return tripRepository.save(trip);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static int parseTimeToMinutes(String timeStr, int fallback) {
        if (!hasText(timeStr)) return fallback;
        String clean = timeStr.trim().toUpperCase(Locale.ENGLISH);

        try {
            boolean isPm = clean.contains("PM");
            boolean isAm = clean.contains("AM");
            if (isPm || isAm) {
                String numericPart = clean.replace("AM", "").replace("PM", "").trim();
                String[] parts = numericPart.split(":");
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                if (isPm && hours < 12) hours += 12;
                if (isAm && hours == 12) hours = 0;
                return hours * 60 + minutes;
            }
        } catch (Exception ignored) {}

        try {
            String[] parts = clean.split(":");
            if (parts.length >= 2) {
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                return hours * 60 + minutes;
            }
        } catch (Exception ignored) {}

        try {
            LocalTime parsed = LocalTime.parse(clean, DateTimeFormatter.ISO_LOCAL_TIME);
            return parsed.getHour() * 60 + parsed.getMinute();
        } catch (Exception ignored) {}

        return fallback;
    }
}