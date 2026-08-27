package com.kce.kmrl.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/user-service")
    public ResponseEntity<?> userServiceGetFallback() {
        return unavailable("User service");
    }

    @PostMapping("/user-service")
    public ResponseEntity<?> userServicePostFallback() {
        return unavailable("User service");
    }

    @GetMapping("/fleet-service")
    public ResponseEntity<?> fleetServiceGetFallback() {
        return unavailable("Fleet service");
    }

    @PostMapping("/fleet-service")
    public ResponseEntity<?> fleetServicePostFallback() {
        return unavailable("Fleet service");
    }

    @PutMapping("/fleet-service")
    public ResponseEntity<?> fleetServicePutFallback() {
        return unavailable("Fleet service");
    }

    @GetMapping("/schedule-service")
    public ResponseEntity<?> scheduleServiceGetFallback() {
        return unavailable("Schedule service");
    }

    @PostMapping("/schedule-service")
    public ResponseEntity<?> scheduleServicePostFallback() {
        return unavailable("Schedule service");
    }

    @GetMapping("/maintenance-service")
    public ResponseEntity<?> maintenanceServiceGetFallback() {
        return unavailable("Maintenance service");
    }

    @PostMapping("/maintenance-service")
    public ResponseEntity<?> maintenanceServicePostFallback() {
        return unavailable("Maintenance service");
    }

    @PutMapping("/maintenance-service")
    public ResponseEntity<?> maintenanceServicePutFallback() {
        return unavailable("Maintenance service");
    }

    @GetMapping("/alert-service")
    public ResponseEntity<?> alertServiceGetFallback() {
        return unavailable("Alert service");
    }

    @PostMapping("/alert-service")
    public ResponseEntity<?> alertServicePostFallback() {
        return unavailable("Alert service");
    }

    @GetMapping("/report-service")
    public ResponseEntity<?> reportServiceGetFallback() {
        return unavailable("Report service");
    }

    @PostMapping("/report-service")
    public ResponseEntity<?> reportServicePostFallback() {
        return unavailable("Report service");
    }

    @PatchMapping("/report-service")
    public ResponseEntity<?> reportServicePatchFallback() {
        return unavailable("Report service");
    }

    @GetMapping("/approver-service")
    public ResponseEntity<?> approverServiceGetFallback() {
        return unavailable("Approver service");
    }

    @PostMapping("/approver-service")
    public ResponseEntity<?> approverServicePostFallback() {
        return unavailable("Approver service");
    }

    private ResponseEntity<?> unavailable(String serviceName) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "message", serviceName + " is temporarily unavailable. Please try again in a moment.",
                "status", 503
        ));
    }
}
