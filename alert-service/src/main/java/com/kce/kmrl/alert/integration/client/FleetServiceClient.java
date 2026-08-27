package com.kce.kmrl.alert.integration.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kce.kmrl.alert.integration.dto.TrainAssetDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class FleetServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FleetServiceClient.class);

    private final RestTemplate restTemplate;
    private final String fleetServiceUrl;

    public FleetServiceClient(
            RestTemplate restTemplate,
            @Value("${fleet.service.url:http://fleet-service}") String fleetServiceUrl) {
        this.restTemplate = restTemplate;
        this.fleetServiceUrl = fleetServiceUrl;
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackGetAllTrains")
    public ServiceResult<List<TrainAssetDto>> getAllTrainsResult() {
        String url = fleetServiceUrl + "/api/v1/fleet/yard";
        TrackGroupDto[] groups = restTemplate.getForObject(url, TrackGroupDto[].class);
        if (groups == null) {
            return ServiceResult.available(Collections.emptyList());
        }
        List<TrainAssetDto> all = new ArrayList<>();
        for (TrackGroupDto group : groups) {
            if (group.getTrains() != null) {
                all.addAll(group.getTrains());
            }
        }
        return ServiceResult.available(all);
    }

    private ServiceResult<List<TrainAssetDto>> fallbackGetAllTrains(Throwable ex) {
        log.warn("Could not fetch fleet roster from {} ({}): {}. Skipping fleet-based rules this cycle.",
                fleetServiceUrl, ex.getClass().getSimpleName(), ex.getMessage());
        return ServiceResult.unavailable();
    }

    public List<TrainAssetDto> getAllTrains() {
        ServiceResult<List<TrainAssetDto>> result = getAllTrainsResult();
        return result.isAvailable() ? result.getData() : Collections.emptyList();
    }

    @Retry(name = "fleetService")
    @CircuitBreaker(name = "fleetService", fallbackMethod = "fallbackUpdateTrainStatus")
    public void updateTrainStatus(String trainId, String status) {
        String url = fleetServiceUrl + "/api/v1/fleet/" + trainId + "/status";
        restTemplate.put(url, java.util.Map.of("status", status));
    }

    private void fallbackUpdateTrainStatus(String trainId, String status, Throwable ex) {
        throw new IllegalStateException("Fleet service is unavailable; train status was not changed.", ex);
    }

    public java.util.Optional<String> findTrainNumberById(String trainId) {
        ServiceResult<List<TrainAssetDto>> result = getAllTrainsResult();
        if (!result.isAvailable()) {
            throw new IllegalStateException("Fleet service is unavailable; train identity cannot be resolved.");
        }
        return result.getData().stream()
                .filter(t -> trainId != null && trainId.equals(t.getId()))
                .map(TrainAssetDto::getTrainNumber)
                .filter(n -> n != null && !n.isBlank())
                .findFirst();
    }

    public void ensureTrainExists(String trainId) {
        if (findTrainById(trainId).isEmpty()) {
            throw new IllegalArgumentException("Train " + trainId + " does not exist.");
        }
    }

    public java.util.Optional<String> findTrainIdByNumber(String trainNumber) {
        ServiceResult<List<TrainAssetDto>> result = getAllTrainsResult();
        if (!result.isAvailable()) {
            throw new IllegalStateException("Fleet service is unavailable; train identity cannot be resolved.");
        }
        return result.getData().stream()
                .filter(t -> trainNumber != null && trainNumber.equalsIgnoreCase(t.getTrainNumber()))
                .map(TrainAssetDto::getId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    private java.util.Optional<TrainAssetDto> findTrainById(String trainId) {
        ServiceResult<List<TrainAssetDto>> result = getAllTrainsResult();
        if (!result.isAvailable()) {
            throw new IllegalStateException("Fleet service is unavailable; train identity cannot be verified.");
        }
        return result.getData().stream().filter(t -> trainId.equals(t.getId())).findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TrackGroupDto {
        private String label;
        private List<TrainAssetDto> trains;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public List<TrainAssetDto> getTrains() { return trains; }
        public void setTrains(List<TrainAssetDto> trains) { this.trains = trains; }
    }
}
