package com.kce.kmrl.client;

import com.kce.kmrl.dto.FleetSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "fleet-service")
public interface FleetClient {

    @GetMapping("/api/v1/fleet/summary")
    FleetSummaryDto getSummary();
}
