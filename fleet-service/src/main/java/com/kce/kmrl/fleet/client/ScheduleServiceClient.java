package com.kce.kmrl.fleet.client;

import com.kce.kmrl.fleet.dto.ScheduleTripDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class ScheduleServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ScheduleServiceClient.class);

    private static final Set<String> IN_PROGRESS_STATUSES =
            new HashSet<>(Arrays.asList("ACTIVE", "DELAYED"));

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private final RestTemplate restTemplate;
    private final String scheduleServiceUrl;

    public ScheduleServiceClient(
            RestTemplate restTemplate,
            @Value("${schedule.service.url:http://localhost:8086}") String scheduleServiceUrl) {
        this.restTemplate = restTemplate;
        this.scheduleServiceUrl = scheduleServiceUrl;
    }

    @Retry(name = "scheduleService")
    @CircuitBreaker(name = "scheduleService", fallbackMethod = "fallbackFindTrainIdsInProgress")
    public Set<String> findTrainIdsWithInProgressTrip() {
        String today = LocalDate.now(KOLKATA).toString();
        String url = scheduleServiceUrl + "/api/v1/schedule/trips/date/" + today;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "fleet-service");
        headers.set("X-User-Role", "SYSTEM");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<ScheduleTripDto[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ScheduleTripDto[].class);

            ScheduleTripDto[] trips = response.getBody();
            if (trips == null) {
                return Collections.emptySet();
            }

            Set<String> result = new HashSet<>();
            for (ScheduleTripDto trip : trips) {
                if (trip.getAssignedTrainId() == null || trip.getStatus() == null) {
                    continue;
                }
                if (IN_PROGRESS_STATUSES.contains(trip.getStatus().trim().toUpperCase())) {
                    result.add(trip.getAssignedTrainId().trim().toUpperCase());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to retrieve trips from schedule-service: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<String> fallbackFindTrainIdsInProgress(Throwable ex) {
        log.warn("Could not resolve in-progress trips from schedule-service ({}): {}", scheduleServiceUrl, ex.getMessage());
        return Collections.emptySet();
    }
}
