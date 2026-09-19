package com.kce.kmrl.schedule.service;

import com.kce.kmrl.schedule.client.ResilientFleetClient;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import com.kce.kmrl.schedule.repository.ScheduleTripRepository;
import com.kce.kmrl.schedule.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataInitializerService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializerService.class);

    private static final int TRIP_DURATION_MIN = 45;

    private static final class Band {
        final int startMin;
        final int endMin;
        final double headwayMin;
        final int extraPeakTrips;

        Band(int startMin, int endMin, double headwayMin, int extraPeakTrips) {
            this.startMin = startMin;
            this.endMin = endMin;
            this.headwayMin = headwayMin;
            this.extraPeakTrips = extraPeakTrips;
        }
    }

    private static final List<Band> SERVICE_BANDS = List.of(
        new Band(6 * 60, 8 * 60, 10.0, 0),
        new Band(8 * 60, 10 * 60, 6.75, 8),
        new Band(10 * 60, 16 * 60, 11.0, 0),
        new Band(16 * 60, 19 * 60, 6.75, 8),
        new Band(19 * 60, 23 * 60, 12.5, 0)
    );

    private final ScheduleTripRepository tripRepository;
    private final ResilientFleetClient fleetClient;

    public DataInitializerService(ScheduleTripRepository tripRepository, ResilientFleetClient fleetClient) {
        this.tripRepository = tripRepository;
        this.fleetClient = fleetClient;
    }

    private static final String[] RIVER_NAMES = {
        "Periyar", "Pamba", "Kabani", "Bhavani", "Chaliyar", "Bharathapuzha", "Meenachil", "Kaveri"
    };

    @Override
    public void run(String... args) {
        String today = TimeUtil.today();

        List<ScheduleTrip> aiTrips = tripRepository.findByServiceDate(today).stream()
                .filter(t -> t.getTripCode() != null && t.getTripCode().startsWith("RUN-AI-"))
                .collect(Collectors.toList());

        if (!aiTrips.isEmpty()) {
            log.info("AI-generated schedule for {} already exists ({} trips) — reconciling elapsed trips only.",
                    today, aiTrips.size());
            reconcileAfterRestart(aiTrips);
            return;
        }

        if (tripRepository.existsByServiceDateAndSeedGeneratedTrue(today)) {
            List<ScheduleTrip> seedTrips = tripRepository.findByServiceDateAndSeedGeneratedTrue(today);
            log.info("Legacy seed schedule for {} exists ({} trips) — reconciling elapsed trips only.",
                    today, seedTrips.size());
            reconcileAfterRestart(seedTrips);
            return;
        }

        log.info("No schedule found for {} on startup — seeding today-only baseline with assigned trains.", today);
        generateFullDayScheduleForToday();
    }

    public synchronized void generateFullDayScheduleForToday() {
        String today = TimeUtil.today();

        List<ScheduleTrip> stale = tripRepository.findByServiceDateAndSeedGeneratedTrue(today);
        if (!stale.isEmpty()) {
            tripRepository.deleteAll(stale);
            log.info("Cleared {} stale seed trips for {} before re-seeding.", stale.size(), today);
        }

        int currentMinutes = TimeUtil.nowMinutes();
        List<TrainAssetDto> availableTrains = fetchAvailableTrainsForSeeding();
        List<Integer> startTimes = buildStartTimes();

        List<ScheduleTrip> trips = new ArrayList<>();
        int trainCursor = 0;
        int seq = 1;

        for (int startM : startTimes) {
            int endM = Math.min(startM + TRIP_DURATION_MIN, 1439);

            String startTimeStr = TimeUtil.formatMinutesToTime(startM);
            String endTimeStr = TimeUtil.formatMinutesToTime(endM);

            String tripId = "TR-" + today + "-" + seq;
            String tripCode = "RUN-" + String.format("%03d", seq);
            String routeName = (seq % 2 != 0) ? "Aluva to Thrippunithura" : "Thrippunithura to Aluva";

            TripStatus tripStatus;
            if (currentMinutes >= endM) {
                tripStatus = TripStatus.COMPLETED;
            } else if (currentMinutes >= startM) {
                tripStatus = TripStatus.ACTIVE;
            } else {
                tripStatus = TripStatus.PLANNED;
            }

            String trainId;
            String trainName;

            if (!availableTrains.isEmpty()) {
                TrainAssetDto train = availableTrains.get(trainCursor % availableTrains.size());
                trainCursor++;
                trainId = train.getId();
                trainName = hasText(train.getTrainNumber()) ? train.getTrainNumber() : train.getId();
                if (tripStatus == TripStatus.ACTIVE) {
                    syncActiveTrainWithFleet(trainId, tripCode, routeName);
                }
            } else {
                int trainNum = ((seq - 1) % 15) + 1;
                trainId = String.format("TS-%02d", trainNum);
                trainName = "KMRL Set " + String.format("%02d", trainNum) + " (" + RIVER_NAMES[(trainNum - 1) % RIVER_NAMES.length] + ")";
                trainCursor++;
            }

            trips.add(new ScheduleTrip(
                tripId, tripCode, routeName, trainId, trainName,
                tripStatus, startTimeStr, endTimeStr, startM, endM, true, today
            ));

            seq++;
        }

        tripRepository.saveAll(trips);
        log.info("Seeded {}'s baseline timetable with 100% train assignments: {} trips.", today, trips.size());
    }

    private List<Integer> buildStartTimes() {
        Set<Integer> startTimes = new LinkedHashSet<>();

        for (Band band : SERVICE_BANDS) {
            double cursor = band.startMin;
            while (cursor < band.endMin) {
                startTimes.add((int) Math.round(cursor));
                cursor += band.headwayMin;
            }

            if (band.extraPeakTrips > 0) {
                double duration = band.endMin - band.startMin;
                double step = duration / (band.extraPeakTrips + 1);
                for (int i = 1; i <= band.extraPeakTrips; i++) {
                    int t = (int) Math.round(band.startMin + step * i);
                    if (t < band.endMin) {
                        startTimes.add(t);
                    }
                }
            }
        }

        return startTimes.stream().sorted().collect(Collectors.toList());
    }

    private void reconcileAfterRestart(List<ScheduleTrip> todaysTrips) {
        int currentMinutes = TimeUtil.nowMinutes();
        List<ScheduleTrip> missed = todaysTrips.stream()
                .filter(t -> (t.getStatus() == TripStatus.PLANNED || t.getStatus() == TripStatus.ACTIVE || t.getStatus() == TripStatus.DELAYED)
                        && t.getEndMinutes() <= currentMinutes)
                .collect(Collectors.toList());
        if (missed.isEmpty()) {
            return;
        }
        for (ScheduleTrip t : missed) {
            t.setStatus(TripStatus.COMPLETED);
        }
        tripRepository.saveAll(missed);
        log.info("Restart reconciliation: {} trip(s) whose window elapsed marked COMPLETED.",
                missed.size());
    }

    private List<TrainAssetDto> fetchAvailableTrainsForSeeding() {
        List<TrainAssetDto> standby = fleetClient.getStandbyTrains();
        if (!standby.isEmpty()) {
            log.info("Fetched {} standby trains from fleet-service for schedule seeding.", standby.size());
            return standby;
        }
        log.warn("fleet-service returned no standby trains; falling back to local train pool for seeding.");
        return new ArrayList<>();
    }

    private void syncActiveTrainWithFleet(String trainId, String tripCode, String routeName) {
        fleetClient.updateTrainStatus(trainId, "IN_SERVICE");
        fleetClient.assignTrainDuty(trainId, tripCode, routeName);
        log.info("Synced fleet-service: train {} marked IN_SERVICE on trip {}.", trainId, tripCode);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}