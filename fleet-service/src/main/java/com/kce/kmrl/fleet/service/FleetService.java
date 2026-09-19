package com.kce.kmrl.fleet.service;

import com.kce.kmrl.fleet.client.MaintenanceServiceClient;
import com.kce.kmrl.fleet.dto.FleetOverrideRequest;
import com.kce.kmrl.fleet.dto.FleetSummaryDto;
import com.kce.kmrl.fleet.dto.TrackGroupDto;
import com.kce.kmrl.fleet.model.AuditLedgerEntry;
import com.kce.kmrl.fleet.model.TrainAsset;
import com.kce.kmrl.fleet.model.TrainStatus;
import com.kce.kmrl.fleet.repository.AuditLedgerRepository;
import com.kce.kmrl.fleet.repository.TrainAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FleetService {

    private static final Logger log = LoggerFactory.getLogger(FleetService.class);
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private final TrainAssetRepository trainRepository;
    private final AuditLedgerRepository ledgerRepository;
    private final MaintenanceServiceClient maintenanceServiceClient;
    private final RestTemplate restTemplate;
    private final String approverServiceUrl;

    public FleetService(TrainAssetRepository trainRepository,
                         AuditLedgerRepository ledgerRepository,
                         MaintenanceServiceClient maintenanceServiceClient,
                         RestTemplate restTemplate,
                         @Value("${approver.service.url:http://localhost:8088}") String approverServiceUrl) {
        this.trainRepository = trainRepository;
        this.ledgerRepository = ledgerRepository;
        this.maintenanceServiceClient = maintenanceServiceClient;
        this.restTemplate = restTemplate;
        this.approverServiceUrl = approverServiceUrl;
    }

    private static final Comparator<TrainAsset> LEAST_MILEAGE_FIRST = Comparator
            .comparing(TrainAsset::getTotalMileageKm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TrainAsset::getHealthIndex, Comparator.nullsLast(Comparator.reverseOrder()));

    public FleetSummaryDto getFleetSummary() {
        List<TrainAsset> all = trainRepository.findAll();
        List<TrainAsset> maintenance = resolveTrainsInMaintenance(all);
        Set<String> maintenanceIds = maintenance.stream().map(TrainAsset::getId).collect(Collectors.toSet());

        List<TrainAsset> active = all.stream()
                .filter(t -> !maintenanceIds.contains(t.getId()) && t.getStatus() == TrainStatus.IN_SERVICE)
                .collect(Collectors.toList());

        long total = all.size();
        long activeCount = active.size();
        long maintenanceCount = maintenance.size();
        long standbyCount = Math.max(0, total - activeCount - maintenanceCount);

        return new FleetSummaryDto(activeCount, standbyCount, maintenanceCount, total);
    }

    public List<TrackGroupDto> getYardTrackGroups() {
        List<TrainAsset> all = trainRepository.findAll();
        List<TrainAsset> maintenance = resolveTrainsInMaintenance(all);
        Set<String> maintenanceIds = maintenance.stream().map(TrainAsset::getId).collect(Collectors.toSet());

        List<TrainAsset> active = all.stream()
                .filter(t -> !maintenanceIds.contains(t.getId()) && t.getStatus() == TrainStatus.IN_SERVICE)
                .collect(Collectors.toList());

        Set<String> activeIds = active.stream().map(TrainAsset::getId).collect(Collectors.toSet());

        List<TrainAsset> standby = all.stream()
                .filter(t -> !maintenanceIds.contains(t.getId()) && !activeIds.contains(t.getId()))
                .sorted(LEAST_MILEAGE_FIRST)
                .collect(Collectors.toList());

        return Arrays.asList(
            new TrackGroupDto("Mainline Operational Corridor (Active Sets)", active),
            new TrackGroupDto("Muttom Yard Staging Bays (Standby Reserve Sets)", standby),
            new TrackGroupDto("Muttom Workshop Overhaul Bays (Servicing Sets)", maintenance)
        );
    }

    public List<TrainAsset> getStandbyTrains() {
        List<TrainAsset> all = trainRepository.findAll();
        Set<String> maintenanceIds = resolveTrainsInMaintenance(all).stream()
                .map(TrainAsset::getId)
                .collect(Collectors.toSet());

        return all.stream()
                .filter(t -> !maintenanceIds.contains(t.getId()) && t.getStatus() != TrainStatus.IN_SERVICE && t.getStatus() != TrainStatus.IN_MAINTENANCE)
                .sorted(LEAST_MILEAGE_FIRST)
                .collect(Collectors.toList());
    }

    public TrainAsset getTrainById(String id) {
        return trainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + id));
    }

    public boolean isTrainAvailable(String trainId, String start, String end) {
        try {
            TrainAsset train = resolveTrainByIdOrNumber(trainId);
            List<TrainAsset> maint = resolveTrainsInMaintenance(Collections.singletonList(train));
            if (!maint.isEmpty()) {
                return false;
            }
            return train.getStatus() != TrainStatus.IN_MAINTENANCE;
        } catch (Exception e) {
            log.warn("Error checking train availability for {}: {}", trainId, e.getMessage());
            return false;
        }
    }

    public List<AuditLedgerEntry> getAuditLedger() {
        return ledgerRepository.findAll();
    }

    private TrainAsset resolveTrainByIdOrNumber(String identifier) {
        return trainRepository.findById(identifier)
                .or(() -> trainRepository.findByTrainNumber(identifier))
                .orElseThrow(() -> new IllegalArgumentException("Train set not found: " + identifier));
    }

    public TrainAsset updateTrainStatus(String trainId, TrainStatus newStatus) {
        TrainAsset train = resolveTrainByIdOrNumber(trainId);
        TrainStatus oldStatus = train.getStatus();
        train.setStatus(newStatus);

        if (newStatus == TrainStatus.STANDBY) {
            if (oldStatus == TrainStatus.IN_MAINTENANCE) {
                train.setMileageAtLastServiceKm(train.getTotalMileageKm());
                log.info("Train {} serviced - mileage watermark reset to {} km.", trainId, train.getTotalMileageKm());
            }
            train.setCurrentDepot("Muttom Depot Yard");
            train.setTrack("Yard Staging Bay 1-Track 1");
            train.setAssignedTripCode(null);
            train.setAssignedRoute(null);
        } else if (newStatus == TrainStatus.IN_MAINTENANCE) {
            train.setCurrentDepot("Muttom Central Workshop");
            train.setTrack("Workshop Repair Bay 1");
            train.setAssignedTripCode(null);
            train.setAssignedRoute(null);
        } else if (newStatus == TrainStatus.IN_SERVICE) {
            train.setCurrentDepot("Mainline Corridor");
            train.setTrack("Mainline Interlocking Block");
        }

        TrainAsset updated = trainRepository.save(train);
        logAuditEntry(train.getTrainNumber(), oldStatus.name() + " -> " + newStatus.name(), "AUTOMATED ENGINE");
        return updated;
    }

    public TrainAsset assignTrainDuty(String trainId, String tripCode, String routeName) {
        TrainAsset train = trainRepository.findById(trainId)
                .orElseThrow(() -> new IllegalArgumentException("Train not found: " + trainId));

        train.setAssignedTripCode(tripCode);
        train.setAssignedRoute(routeName);
        return trainRepository.save(train);
    }

    private List<TrainAsset> resolveTrainsInMaintenance(List<TrainAsset> all) {
        Set<String> activeTicketTrains = maintenanceServiceClient.findTrainsWithActiveTickets();

        List<TrainAsset> maintenanceTrains = new ArrayList<>();
        for (TrainAsset train : all) {
            boolean hasActiveTicket = matchesAnyIdentifier(train, activeTicketTrains);
            if (hasActiveTicket) {
                maintenanceTrains.add(train);
            } else if (train.getStatus() == TrainStatus.IN_MAINTENANCE) {

                train.setStatus(TrainStatus.STANDBY);
                train.setCurrentDepot("Muttom Depot Yard");
                train.setTrack("Yard Staging Bay 1-Track 1");
                trainRepository.save(train);
                log.info("Auto-reconciled stale IN_MAINTENANCE train {} -> STANDBY in fleet database.", train.getId());
            }
        }
        return maintenanceTrains;
    }

    private boolean matchesAnyIdentifier(TrainAsset train, Set<String> activeTicketTrains) {
        if (train == null || activeTicketTrains == null || activeTicketTrains.isEmpty()) return false;

        String id = train.getId() != null ? train.getId().trim().toUpperCase() : "";
        String number = train.getTrainNumber() != null ? train.getTrainNumber().trim().toUpperCase() : "";

        for (String active : activeTicketTrains) {
            if (active == null || active.isBlank()) continue;
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

    public Map<String, Object> requestOverride(String trainId, FleetOverrideRequest request) {
        TrainAsset train = getTrainById(trainId);
        if (train == null) {
            throw new IllegalArgumentException("Train " + trainId + " not found");
        }

        String baseUrl = approverServiceUrl != null ? approverServiceUrl.trim() : "http://localhost:8088";
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = baseUrl + "/api/approver/tasks/submit";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "fleet-service");
        headers.set("X-User-Role", "SYSTEM");
        headers.set("Content-Type", "application/json");

        Map<String, Object> payload = new HashMap<>();
        payload.put("targetEntityId", train.getId());
        payload.put("requestType", "FLEET_OVERRIDE");
        payload.put("title", "Fleet Override Request for Train " + (train.getTrainNumber() != null ? train.getTrainNumber() : train.getId()));
        payload.put("description", request.getReason());
        payload.put("priority", "HIGH");
        payload.put("requestedBy", "fleet-service");
        payload.put("assignedApproverRole", "SADA");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            org.springframework.http.ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            log.info("Successfully submitted FLEET_OVERRIDE approval task for train {} to approver-service", trainId);
            logAuditEntry(train.getTrainNumber() != null ? train.getTrainNumber() : trainId, "OVERRIDE_REQUESTED", "APPROVER_TASK");
            return response.getBody() != null ? (Map<String, Object>) response.getBody() : Map.of("status", "SUBMITTED", "trainId", trainId);
        } catch (Exception e) {
            log.error("Failed to submit FLEET_OVERRIDE task for train {} to {}: {}", trainId, url, e.getMessage());
            throw new RuntimeException("Failed to submit fleet override request to approver-service: " + e.getMessage(), e);
        }
    }

    private void logAuditEntry(String trainNumber, String vector, String opCode) {
        String id = "L-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);
        String timestamp = LocalDateTime.now(KOLKATA).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String hash = "0x" + UUID.randomUUID().toString().substring(0, 8);
        ledgerRepository.save(new AuditLedgerEntry(id, timestamp, trainNumber, vector, opCode, hash));
    }
}