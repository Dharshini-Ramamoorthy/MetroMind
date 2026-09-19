package com.kce.kmrl.alert.controller;

import com.kce.kmrl.alert.dto.AlertResponse;
import com.kce.kmrl.alert.dto.AlertSummaryResponse;
import com.kce.kmrl.alert.dto.LedgerEntryResponse;
import com.kce.kmrl.alert.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AlertSummaryResponse> getSummary(Authentication authentication) {
        return ResponseEntity.ok(alertService.getSummary(role(authentication), userId(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAllAlerts(Authentication authentication) {
        return ResponseEntity.ok(alertService.getAllAlerts(role(authentication), userId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlertById(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(alertService.getAlertById(id, role(authentication), userId(authentication)));
    }

    @PreAuthorize("hasAnyRole('OC','MDS','SADA')")
    @PostMapping("/{id}/clear")
    public ResponseEntity<LedgerEntryResponse> clearAlert(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(
                alertService.clearAlert(id, userId(authentication), role(authentication)));
    }

    @PreAuthorize("hasAnyRole('OC','MDS','SADA')")
    @PostMapping("/{id}/isolate")
    public ResponseEntity<Map<String, String>> isolateAlert(
            @PathVariable String id,
            Authentication authentication) {
        String message = alertService.isolateAlert(id, userId(authentication), role(authentication));
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PreAuthorize("hasAnyRole('OC','MDS','SADA')")
    @PostMapping("/{id}/dispatch")
    public ResponseEntity<AlertResponse> dispatchAlert(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(
                alertService.dispatchAlert(id, userId(authentication), role(authentication)));
    }

    @PreAuthorize("hasAnyRole('OC','MDS','SADA')")
    @GetMapping("/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> getLedger() {
        return ResponseEntity.ok(alertService.getLedger());
    }

    private String role(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse(null);
    }

    private String userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return String.valueOf(authentication.getPrincipal());
    }
}
