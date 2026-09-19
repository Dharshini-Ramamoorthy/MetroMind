package com.kce.kmrl.alert.integration.client;

import com.kce.kmrl.alert.integration.dto.ScheduleTripDto;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Component
public class ScheduleServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ScheduleServiceClient.class);

    private final RestTemplate restTemplate;
    private final String scheduleServiceUrl;
    private final String tripsPath;

    public ScheduleServiceClient(
            RestTemplate restTemplate,
            @Value("${schedule.service.url:http://schedule-service}") String scheduleServiceUrl,
            @Value("${schedule.service.trips-path:/api/v1/schedule/trips/window}") String tripsPath) {
        this.restTemplate = restTemplate;
        this.scheduleServiceUrl = scheduleServiceUrl;
        this.tripsPath = tripsPath;
    }

    @Retry(name = "scheduleService")
    @CircuitBreaker(name = "scheduleService", fallbackMethod = "fallbackGetAllTrips")
    public ServiceResult<List<ScheduleTripDto>> getAllTripsResult() {
        String url = scheduleServiceUrl + tripsPath;
        ScheduleTripDto[] trips = restTemplate.getForObject(url, ScheduleTripDto[].class);
        return ServiceResult.available(trips == null ? Collections.emptyList() : List.of(trips));
    }

    private ServiceResult<List<ScheduleTripDto>> fallbackGetAllTrips(Throwable ex) {
        log.warn("Could not fetch trips from {}{} ({}): {}. Skipping schedule-based rules this cycle. " +
                "If schedule-service's real endpoint path differs from {}, set schedule.service.trips-path.",
                scheduleServiceUrl, tripsPath, ex.getClass().getSimpleName(), ex.getMessage(), tripsPath);
        return ServiceResult.unavailable();
    }

    @CircuitBreaker(name = "scheduleService", fallbackMethod = "fallbackWithdrawTrain")
    public boolean withdrawTrain(String trainId, boolean emergency) {
        String url = scheduleServiceUrl + "/api/v1/schedule/trips/withdraw-train";
        ResponseEntity<WithdrawTrainResponse> response = restTemplate.postForEntity(
                url, Map.of("trainId", trainId, "emergency", emergency), WithdrawTrainResponse.class);
        return response.getBody() != null && response.getBody().hadActiveTrip();
    }

    private boolean fallbackWithdrawTrain(String trainId, boolean emergency, Throwable ex) {
        throw new IllegalStateException("Schedule service is unavailable; isolation was not completed.", ex);
    }

    public record WithdrawTrainResponse(boolean hadActiveTrip) {}

    public List<ScheduleTripDto> getAllTrips() {
        ServiceResult<List<ScheduleTripDto>> result = getAllTripsResult();
        return result.isAvailable() ? result.getData() : Collections.emptyList();
    }
}
