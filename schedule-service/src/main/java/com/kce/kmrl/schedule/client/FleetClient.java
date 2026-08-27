package com.kce.kmrl.schedule.client;

import com.kce.kmrl.schedule.dto.StatusUpdateRequest;
import com.kce.kmrl.schedule.dto.TrackGroupDto;
import com.kce.kmrl.schedule.dto.TrainAssetDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "fleet-service", url = "${fleet.service.url:http://localhost:8085}")
public interface FleetClient {

    @GetMapping("/api/v1/fleet/standby")
    List<TrainAssetDto> getStandbyTrains();

    @GetMapping("/api/v1/fleet/yard")
    List<TrackGroupDto> getYardTracks();

    @GetMapping("/api/v1/fleet/{trainId}")
    TrainAssetDto getTrainById(@PathVariable("trainId") String trainId);

    @GetMapping("/api/v1/fleet/{trainId}/availability")
    java.util.Map<String, Object> checkTrainAvailability(
            @PathVariable("trainId") String trainId,
            @RequestParam("start") String start,
            @RequestParam("end") String end
    );

    @PutMapping("/api/v1/fleet/{trainId}/status")
    TrainAssetDto updateTrainStatus(
            @PathVariable("trainId") String trainId,
            @RequestBody StatusUpdateRequest request
    );

    @PutMapping("/api/v1/fleet/{trainId}/duty")
    TrainAssetDto assignTrainDuty(
            @PathVariable("trainId") String trainId,
            @RequestParam("tripCode") String tripCode,
            @RequestParam("routeName") String routeName
    );
}
