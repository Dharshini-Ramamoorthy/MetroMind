package com.kce.kmrl.schedule.controller;

import com.kce.kmrl.schedule.dto.AdjustTripRequest;
import com.kce.kmrl.schedule.dto.DeleteTripRequest;
import com.kce.kmrl.schedule.dto.ProposeTripRequest;
import com.kce.kmrl.schedule.dto.StatusUpdateRequest;
import com.kce.kmrl.schedule.dto.WithdrawTrainRequest;
import com.kce.kmrl.schedule.dto.WithdrawTrainResponse;
import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/trips/window")
    public ResponseEntity<List<ScheduleTrip>> getTripsInWindow() {
        return ResponseEntity.ok(service.getTripsInCurrentTimeWindow());
    }

    @PreAuthorize("hasRole('SYSTEM')")
    @PostMapping("/trips/withdraw-train")
    public ResponseEntity<WithdrawTrainResponse> withdrawTrain(@RequestBody WithdrawTrainRequest request) {
        if (request.getTrainId() == null || request.getTrainId().isBlank()) {
            throw new IllegalArgumentException("trainId is required.");
        }
        WithdrawTrainResponse response = service.withdrawTrain(request.getTrainId(), request.isEmergency());
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('OC')")
    @PostMapping("/propose")
    public ResponseEntity<ScheduleTrip> proposeTrip(@RequestBody ProposeTripRequest request) {
        return ResponseEntity.ok(service.proposeTrip(request));
    }

    @GetMapping("/trips/date/{serviceDate}")
    public ResponseEntity<List<ScheduleTrip>> getTripsByDate(@PathVariable String serviceDate) {
        return ResponseEntity.ok(service.getTripsByDate(serviceDate));
    }

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<ScheduleTrip> getTripById(@PathVariable String tripId) {
        return ResponseEntity.ok(service.getTripById(tripId));
    }

    @PreAuthorize("hasRole('OC')")
    @PatchMapping("/trips/{tripId}/adjust")
    public ResponseEntity<ScheduleTrip> adjustTrip(
            @PathVariable String tripId,
            @RequestBody AdjustTripRequest request,
            Authentication authentication) {
        String callerRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";
        return ResponseEntity.ok(service.adjustTrip(tripId, request, callerRole));
    }

    @PreAuthorize("hasAnyRole('OC','SADA','SYSTEM')")
    @PatchMapping("/trips/{tripId}/status")
    public ResponseEntity<ScheduleTrip> updateTripStatus(
            @PathVariable String tripId,
            @RequestBody StatusUpdateRequest request,
            Authentication authentication) {
        String callerRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";
        return ResponseEntity.ok(service.updateTripStatus(tripId, request, callerRole));
    }


    @PreAuthorize("hasRole('OC')")
    @DeleteMapping("/trips/{tripId}")
    public ResponseEntity<Map<String, String>> deleteProposedTrip(
            @PathVariable String tripId,
            @RequestBody(required = false) DeleteTripRequest request,
            Authentication authentication) {

        String callerRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";
        String callerName = authentication != null
                ? String.valueOf(authentication.getPrincipal())
                : "unknown";
        String reason = request != null ? request.getReason() : null;

        service.deleteProposedTrip(tripId, callerRole, callerName, reason);
        return ResponseEntity.ok(Map.of("message", "Trip " + tripId + " deleted successfully."));
    }

    @PreAuthorize("hasRole('OC')")
    @PostMapping("/generate/{serviceDate}")
    public ResponseEntity<Map<String, Object>> generateSchedule(
            @PathVariable String serviceDate,
            Authentication authentication) {

        String callerName = authentication != null
                ? String.valueOf(authentication.getPrincipal())
                : "unknown";

        List<ScheduleTrip> trips = service.generateFullDayScheduleForDate(serviceDate, callerName);
        return ResponseEntity.ok(Map.of(
            "message", trips.size() + " PROPOSED trips ready for " + serviceDate + ". Awaiting SADA approval.",
            "tripCount", trips.size(),
            "serviceDate", serviceDate
        ));
    }

    @PreAuthorize("hasAnyRole('SADA','SYSTEM')")
    @PostMapping("/approve-day/{serviceDate}")
    public ResponseEntity<Map<String, Object>> approveDaySchedule(@PathVariable String serviceDate) {
        int count = service.approveDaySchedule(serviceDate);
        return ResponseEntity.ok(Map.of(
            "message", "Approved " + count + " trips for " + serviceDate,
            "approvedCount", count
        ));
    }

    @PreAuthorize("hasAnyRole('SADA','SYSTEM')")
    @PostMapping("/reject-day/{serviceDate}")
    public ResponseEntity<Map<String, Object>> rejectDaySchedule(@PathVariable String serviceDate) {
        int count = service.rejectDaySchedule(serviceDate);
        return ResponseEntity.ok(Map.of(
            "message", "Rejected/cancelled " + count + " trips for " + serviceDate,
            "cancelledCount", count
        ));
    }

    @PreAuthorize("hasAnyRole('SADA','SYSTEM')")
    @PostMapping("/decide-trips")
    public ResponseEntity<Map<String, Object>> decideTrips(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String serviceDate = body != null && body.get("serviceDate") != null ? String.valueOf(body.get("serviceDate")) : null;
        if (serviceDate == null || serviceDate.isBlank()) {
            throw new IllegalArgumentException("serviceDate is required");
        }
        @SuppressWarnings("unchecked")
        List<String> approvedTripIds = body.get("approvedTripIds") instanceof List ? (List<String>) body.get("approvedTripIds") : List.of();
        @SuppressWarnings("unchecked")
        List<String> rejectedTripIds = body.get("rejectedTripIds") instanceof List ? (List<String>) body.get("rejectedTripIds") : List.of();
        String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : "Per-trip decision";
        String callerRole = authentication != null
                ? authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("")
                : "";

        Map<String, Integer> result = service.decideSelectedTrips(serviceDate, approvedTripIds, rejectedTripIds, callerRole, reason);
        return ResponseEntity.ok(Map.of(
            "message", "Granular trip decisions applied for " + serviceDate,
            "approvedCount", result.getOrDefault("approved", 0),
            "rejectedCount", result.getOrDefault("rejected", 0)
        ));
    }

    @PreAuthorize("hasRole('OC')")
    @PostMapping("/auto-resolve-collisions/{serviceDate}")
    public ResponseEntity<Map<String, Object>> autoResolveCollisions(@PathVariable String serviceDate) {
        List<ScheduleTrip> trips = service.getTripsForDate(serviceDate);
        int fixed = 0;
        if (trips != null && !trips.isEmpty()) {
            fixed = service.resolveScheduleCollisions(trips);
            if (fixed > 0) {
                service.saveTrips(trips);
            }
        }
        return ResponseEntity.ok(Map.of(
            "message", "Resolved " + fixed + " safety conflicts",
            "resolvedCount", fixed,
            "serviceDate", serviceDate
        ));
    }
}