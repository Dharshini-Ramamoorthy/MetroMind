package com.kce.kmrl.fleet.controller;

import com.kce.kmrl.fleet.model.AuditLedgerEntry;
import com.kce.kmrl.fleet.service.FleetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final FleetService service;

    public LedgerController(FleetService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AuditLedgerEntry>> getLedger() {
        return ResponseEntity.ok(service.getAuditLedger());
    }
}
