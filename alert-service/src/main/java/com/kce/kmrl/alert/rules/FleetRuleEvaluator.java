package com.kce.kmrl.alert.rules;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.integration.client.FleetServiceClient;
import com.kce.kmrl.alert.integration.client.ServiceResult;
import com.kce.kmrl.alert.integration.dto.TrainAssetDto;
import com.kce.kmrl.alert.rules.state.RuleStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import com.kce.kmrl.alert.repository.AlertRepository;

@Component
public class FleetRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FleetRuleEvaluator.class);
    private static final DateTimeFormatter SERVICE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FleetServiceClient fleetClient;
    private final RuleAlertPublisher publisher;
    private final RuleStateStore stateStore;
    private final AlertRepository alertRepository;
    private final int healthIndexCriticalBelow;
    private final int brakePressureMinKpa;
    private final int brakePressureMaxKpa;
    private final int mileageIntervalKm;
    private final int serviceMaxAgeDays;

    public FleetRuleEvaluator(
            FleetServiceClient fleetClient,
            RuleAlertPublisher publisher,
            RuleStateStore stateStore,
            AlertRepository alertRepository,
            @Value("${alert.rules.health-index.critical-below:40}") int healthIndexCriticalBelow,
            @Value("${alert.rules.brake-pressure.min-kpa:850}") int brakePressureMinKpa,
            @Value("${alert.rules.brake-pressure.max-kpa:950}") int brakePressureMaxKpa,
            @Value("${alert.rules.mileage.interval-km:15000}") int mileageIntervalKm,
            @Value("${alert.rules.service.max-age-days:90}") int serviceMaxAgeDays) {
        this.fleetClient = fleetClient;
        this.publisher = publisher;
        this.stateStore = stateStore;
        this.alertRepository = alertRepository;
        this.healthIndexCriticalBelow = healthIndexCriticalBelow;
        this.brakePressureMinKpa = brakePressureMinKpa;
        this.brakePressureMaxKpa = brakePressureMaxKpa;
        this.mileageIntervalKm = mileageIntervalKm;
        this.serviceMaxAgeDays = serviceMaxAgeDays;
    }

    public void evaluate() {
        ServiceResult<List<TrainAssetDto>> result = fleetClient.getAllTrainsResult();
        if (!result.isAvailable()) {
            return;
        }
        List<TrainAssetDto> trains = result.getData();
        Set<String> currentTrainIds = new HashSet<>();
        for (TrainAssetDto train : trains) {
            if (train == null || train.getId() == null || train.getId().isBlank()) {
                continue;
            }
            currentTrainIds.add(train.getId());
            evaluateHealthIndex(train);
            evaluateBrakePressure(train);
            evaluateUnplannedStatusFlip(train);
            evaluateServiceOverdue(train);
        }
        resolveMissingLevelAlerts(currentTrainIds);
    }

    private void resolveMissingLevelAlerts(Set<String> currentTrainIds) {
        alertRepository.findByIdStartingWith("ALT-HEALTH-").stream()
                .filter(a -> !currentTrainIds.contains(a.getSourceEntityId()))
                .forEach(a -> publisher.resolveLevel(a.getId()));
        alertRepository.findByIdStartingWith("ALT-BRAKE-").stream()
                .filter(a -> !currentTrainIds.contains(a.getSourceEntityId()))
                .forEach(a -> publisher.resolveLevel(a.getId()));
        alertRepository.findByIdStartingWith("ALT-SERVICEDUE-").stream()
                .filter(a -> !currentTrainIds.contains(a.getSourceEntityId()))
                .forEach(a -> publisher.resolveLevel(a.getId()));
    }

    private void evaluateHealthIndex(TrainAssetDto train) {
        String alertId = "ALT-HEALTH-" + train.getId();
        Integer health = train.getHealthIndex();
        if (health != null && health < healthIndexCriticalBelow) {
            publisher.publishLevel(alertId, Severity.SEV3,
                    "Health index critical — " + label(train),
                    "Fleet & Induction health monitor", label(train),
                    List.of(field("Train", label(train)), field("Health Index", String.valueOf(health)),
                            field("Threshold", "< " + healthIndexCriticalBelow), field("Depot", dash(train.getCurrentDepot()))),
                    "Health index has dropped below the critical threshold. Recommend pulling " +
                            label(train) + " for inspection before its next duty assignment.",
                    List.of(health, health, health), List.of("MDS"), null, train.getId());
        } else {
            publisher.resolveLevel(alertId);
        }
    }

    private void evaluateBrakePressure(TrainAssetDto train) {
        String alertId = "ALT-BRAKE-" + train.getId();
        Integer kpa = train.getBrakePressureKpa();
        boolean outOfSpec = kpa != null && (kpa < brakePressureMinKpa || kpa > brakePressureMaxKpa);
        if (outOfSpec) {
            publisher.publishLevel(alertId, Severity.SEV3,
                    "Brake pressure out of spec — " + label(train),
                    "Safe range " + brakePressureMinKpa + "–" + brakePressureMaxKpa + " kPa", label(train),
                    List.of(field("Train", label(train)), field("Brake Pressure", kpa + " kPa"),
                            field("Safe Range", brakePressureMinKpa + "–" + brakePressureMaxKpa + " kPa"),
                            field("Depot", dash(train.getCurrentDepot()))),
                    "Brake pressure is outside the safe operating range. Isolate the train before dispatch.",
                    List.of(kpa, kpa, kpa), List.of("OC", "MDS"), null, train.getId());
        } else {
            publisher.resolveLevel(alertId);
        }
    }

    private void evaluateUnplannedStatusFlip(TrainAssetDto train) {
        String key = "trainstatus:" + train.getId();
        String previous = stateStore.get(key).orElse(null);
        String current = train.getStatus();

        if (previous != null && "IN_SERVICE".equalsIgnoreCase(previous)
                && "IN_MAINTENANCE".equalsIgnoreCase(current)) {
            String dedupeKey = "flip-event:" + train.getId() + ":" + InstantHolder.nowEpochSecond();
            publisher.publishEventOnce(dedupeKey, "ALT-STATUSFLIP", Severity.SEV2,
                    "Unplanned status flip to maintenance — " + label(train),
                    "Train left active service unexpectedly", label(train),
                    List.of(field("Train", label(train)), field("Previous Status", previous),
                            field("Current Status", current), field("Last Trip Code", dash(train.getAssignedTripCode()))),
                    label(train) + " was pulled to maintenance directly out of active service. Verify duty recovery.",
                    List.of(0, 1, 1), List.of("OC"), null);
        }
        if (current != null) {
            stateStore.put(key, current);
        }
    }

    private void evaluateServiceOverdue(TrainAssetDto train) {
        String alertId = "ALT-SERVICEDUE-" + train.getId();
        boolean mileageOverdue = mileageOverdue(train);
        boolean dateOverdue = dateOverdue(train);
        if (mileageOverdue || dateOverdue) {
            int mileageSinceService = mileageSinceService(train);
            publisher.publishLevel(alertId, Severity.SEV2,
                    "Service interval overdue — " + label(train),
                    mileageOverdue ? "Mileage service interval exceeded" : "Last serviced too long ago", label(train),
                    List.of(field("Train", label(train)),
                            field("Total Mileage", train.getTotalMileageKm() == null ? "—" : train.getTotalMileageKm() + " km"),
                            field("Mileage Since Service", mileageSinceService < 0 ? "—" : mileageSinceService + " km"),
                            field("Last Serviced", dash(train.getLastServicedAt())),
                            field("Service Interval", mileageIntervalKm + " km / " + serviceMaxAgeDays + " days")),
                    label(train) + " has crossed its scheduled service window. Recommend a workshop slot before its next duty.",
                    List.of(1, 1, 1), List.of("MDS"), null, train.getId());
        } else {
            publisher.resolveLevel(alertId);
        }
    }

    private boolean mileageOverdue(TrainAssetDto train) {
        Integer total = train.getTotalMileageKm();
        if (total == null) return false;
        int since = mileageSinceService(train);
        return since >= 0 && since >= mileageIntervalKm;
    }

    private int mileageSinceService(TrainAssetDto train) {
        if (train.getTotalMileageKm() == null) return -1;
        int baseline = train.getMileageAtLastServiceKm() == null ? 0 : train.getMileageAtLastServiceKm();
        return Math.max(0, train.getTotalMileageKm() - baseline);
    }

    private boolean dateOverdue(TrainAssetDto train) {
        if (train.getLastServicedAt() == null || train.getLastServicedAt().isBlank()) return false;
        try {
            LocalDate last = LocalDate.parse(train.getLastServicedAt(), SERVICE_DATE_FMT);
            return ChronoUnit.DAYS.between(last, LocalDate.now(ZoneId.of("Asia/Kolkata"))) > serviceMaxAgeDays;
        } catch (Exception e) {
            log.warn("Invalid lastServicedAt '{}' for train {}", train.getLastServicedAt(), train.getId());
            return false;
        }
    }

    private String label(TrainAssetDto train) { return train.getTrainNumber() != null ? train.getTrainNumber() : train.getId(); }
    private String dash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private Alert.AlertField field(String label, String value) { return new Alert.AlertField(label, value); }

    private static final class InstantHolder {
        private static long nowEpochSecond() { return java.time.Instant.now().getEpochSecond(); }
    }
}
