package com.kce.kmrl.fleet.service;

import com.kce.kmrl.fleet.client.ScheduleServiceClient;
import com.kce.kmrl.fleet.model.TrainAsset;
import com.kce.kmrl.fleet.model.TrainStatus;
import com.kce.kmrl.fleet.repository.TrainAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class NightYardEnforcer {

    private static final Logger log = LoggerFactory.getLogger(NightYardEnforcer.class);

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private final TrainAssetRepository trainRepository;
    private final FleetService fleetService;
    private final ScheduleServiceClient scheduleServiceClient;

    public NightYardEnforcer(TrainAssetRepository trainRepository, FleetService fleetService,
                              ScheduleServiceClient scheduleServiceClient) {
        this.trainRepository = trainRepository;
        this.fleetService = fleetService;
        this.scheduleServiceClient = scheduleServiceClient;
    }

    @Scheduled(fixedRate = 3000)
    public void enforceNightMaintenanceWindow() {
        LocalTime now = LocalTime.now(KOLKATA);
        int currentMinutes = now.getHour() * 60 + now.getMinute();

        boolean isNightMaintenanceWindow = currentMinutes >= 1380 || currentMinutes < 300;
        if (!isNightMaintenanceWindow) {
            return;
        }

        List<TrainAsset> stillInService = trainRepository.findByStatus(TrainStatus.IN_SERVICE);
        if (stillInService.isEmpty()) {
            return;
        }

        Set<String> protectedTrainIds = scheduleServiceClient.findTrainIdsWithInProgressTrip();

        int flipped = 0;
        for (TrainAsset train : stillInService) {
            String trainId = train.getId() != null ? train.getId().trim().toUpperCase() : null;
            if (trainId != null && protectedTrainIds.contains(trainId)) {
                log.info("Night Maintenance Window: leaving train {} IN_SERVICE - schedule-service " +
                        "reports it still has an in-progress trip (ACTIVE/DELAYED). Will be released to " +
                        "STANDBY by schedule-service itself once that trip completes.", train.getId());
                continue;
            }
            fleetService.updateTrainStatus(train.getId(), TrainStatus.STANDBY);
            flipped++;
        }

        if (flipped > 0) {
            log.info("Night Maintenance Window active — returned {} in-service train(s) to Muttom Yard Standby " +
                    "({} left running to finish an in-progress trip).",
                    flipped, stillInService.size() - flipped);
        }
    }
}
