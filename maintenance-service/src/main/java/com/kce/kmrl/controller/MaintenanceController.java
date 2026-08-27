package com.kce.kmrl.controller;

import com.kce.kmrl.dto.CertificateOfFitnessRequest;
import com.kce.kmrl.dto.CreateTicketRequest;
import com.kce.kmrl.dto.MaintenanceResponse;
import com.kce.kmrl.service.MaintenanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @Autowired
    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<MaintenanceResponse>> getAllTickets() {
        return ResponseEntity.ok(maintenanceService.getAllTickets());
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<MaintenanceResponse> getTicketById(@PathVariable String id) {
        return ResponseEntity.ok(maintenanceService.getTicketById(id));
    }

    @PreAuthorize("hasRole('MDS')")
    @PostMapping("/tickets")
    public ResponseEntity<MaintenanceResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return new ResponseEntity<>(maintenanceService.createTicket(request), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('MDS')")
    @PostMapping("/tickets/{id}/begin-routine-maintenance")
    public ResponseEntity<MaintenanceResponse> beginRoutineMaintenance(@PathVariable String id) {
        return ResponseEntity.ok(maintenanceService.beginRoutineMaintenance(id));
    }

    @PreAuthorize("hasRole('SYSTEM')")
    @PostMapping("/tickets/{id}/mark-pulled")
    public ResponseEntity<Void> markTrainPulled(@PathVariable String id, @RequestParam String trainNumber) {
        maintenanceService.markTrainPulled(id, trainNumber);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('SYSTEM')")
    @RequestMapping(value = "/tickets/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<MaintenanceResponse> applyApproverDecision(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(maintenanceService.applyApproverDecision(id, body.get("status"), body.get("comments")));
    }

    @PreAuthorize("hasRole('MDS')")
    @PostMapping(value = "/tickets/{id}/certificate-of-fitness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaintenanceResponse> submitCertificateOfFitness(
            @PathVariable String id,
            @RequestPart(value = "data", required = false) CertificateOfFitnessRequest request,
            @RequestParam(value = "engineerName", required = false) String engineerName,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestPart(value = "document") MultipartFile document) {
        CertificateOfFitnessRequest req = request;
        if (req == null) {
            req = new CertificateOfFitnessRequest();
            req.setEngineerName(engineerName != null && !engineerName.isBlank() ? engineerName : "Maintenance Engineer");
            req.setRemarks(remarks);
        }
        return ResponseEntity.ok(maintenanceService.submitCertificateOfFitness(id, req, document));
    }

    @GetMapping("/tickets/{id}/certificate-of-fitness/document")
    public ResponseEntity<Resource> getCofDocument(@PathVariable String id) {
        Resource resource = maintenanceService.getCofDocument(id);
        String filename = resource.getFilename() != null ? resource.getFilename() : "cof-document";
        MediaType mediaType = org.springframework.http.MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PreAuthorize("hasRole('MDS')")
    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable String id) {
        maintenanceService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('MDS')")
    @PostMapping("/tickets/{id}/retry-approval")
    public ResponseEntity<MaintenanceResponse> retryApproval(@PathVariable String id) {
        return ResponseEntity.ok(maintenanceService.retryApprovalSubmission(id));
    }
}