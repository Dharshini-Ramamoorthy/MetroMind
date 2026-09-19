package com.kce.kmrl.controller;

import com.kce.kmrl.dto.GenerateReportRequest;
import com.kce.kmrl.dto.LiveSummaryDto;
import com.kce.kmrl.dto.ReportResponse;
import com.kce.kmrl.dto.StatusUpdateRequest;
import com.kce.kmrl.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<List<ReportResponse>> getAllReports(
            @RequestParam(value = "category", required = false) String category) {
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("ALL")) {
            return ResponseEntity.ok(reportService.getReportsByCategory(category));
        }
        return ResponseEntity.ok(reportService.getAllReports());
    }

    @GetMapping("/live-summary")
    public ResponseEntity<LiveSummaryDto> getLiveSummary() {
        return ResponseEntity.ok(reportService.getLiveSummary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable String id) {
        return ResponseEntity.ok(reportService.getReportById(id));
    }

    @PreAuthorize("hasAnyRole('OC','SADA')")
    @PostMapping("/generate")
    public ResponseEntity<ReportResponse> generateLiveReport(@Valid @RequestBody GenerateReportRequest request) {
        ReportResponse created = reportService.generateLiveReport(request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PreAuthorize("hasAnyRole('OC','SADA')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ReportResponse> updateReportStatus(
            @PathVariable String id,
            @RequestBody StatusUpdateRequest request) {
        ReportResponse updated = reportService.updateReportStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String id) {
        ReportService.ReportDownload download = reportService.downloadReport(id);

        String filename = (download.title() != null ? download.title() : "report")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "") + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(download.pdfContent());
    }
}
