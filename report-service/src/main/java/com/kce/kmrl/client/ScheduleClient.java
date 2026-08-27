package com.kce.kmrl.client;

import com.kce.kmrl.dto.ScheduleTripDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "schedule-service")
public interface ScheduleClient {

    @GetMapping("/api/v1/schedule/trips/window")
    List<ScheduleTripDto> getTripsInWindow();
}
