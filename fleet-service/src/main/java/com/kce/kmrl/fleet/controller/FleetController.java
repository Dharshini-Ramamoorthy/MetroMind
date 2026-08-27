package com.kce.kmrl.fleet.controller;

import com.kce.kmrl.fleet.dto.FleetSummaryDto;
import com.kce.kmrl.fleet.dto.StatusUpdateRequest;
import com.kce.kmrl.fleet.dto.TrackGroupDto;
import com.kce.kmrl.fleet.model.TrainAsset;
import com.kce.kmrl.fleet.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fleet")
public class FleetController {

    private final FleetService service;

    public FleetController(FleetService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<FleetSummaryDto> getSummary() {
        return ResponseEntity.ok(service.getFleetSummary());
    }

    @GetMapping("/yard")
    public ResponseEntity<List<TrackGroupDto>> getYardTracks() {
        return ResponseEntity.ok(service.getYardTrackGroups());
    }

    @GetMapping("/standby")
    public ResponseEntity<List<TrainAsset>> getStandbyTrains() {
        return ResponseEntity.ok(service.getStandbyTrains());
    }

    @GetMapping("/{trainId}")
    public ResponseEntity<TrainAsset> getTrainById(@PathVariable String trainId) {
        return ResponseEntity.ok(service.getTrainById(trainId));
    }

    @GetMapping("/{trainId}/availability")
    public ResponseEntity<java.util.Map<String, Object>> checkTrainAvailability(
            @PathVariable String trainId,
            @RequestParam String start,
            @RequestParam String end) {
        boolean available = service.isTrainAvailable(trainId, start, end);
        String reason = available ? "Train is operational and not in maintenance" : "Train is currently in maintenance or has active maintenance tickets";
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("trainId", trainId);
        response.put("available", available);
        response.put("reason", reason);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('MDS','SADA','SYSTEM')")
    @PutMapping("/{trainId}/status")
    public ResponseEntity<TrainAsset> updateTrainStatus(
            @PathVariable String trainId,
            @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(service.updateTrainStatus(trainId, request.getStatus()));
    }

    @PreAuthorize("hasAnyRole('MDS','SADA','SYSTEM')")
    @PutMapping("/{trainId}/duty")
    public ResponseEntity<TrainAsset> assignTrainDuty(
            @PathVariable String trainId,
            @RequestParam String tripCode,
            @RequestParam String routeName) {
        return ResponseEntity.ok(service.assignTrainDuty(trainId, tripCode, routeName));
    }
}
