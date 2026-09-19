package com.kce.kmrl.service;

import com.kce.kmrl.dto.GenerateReportRequest;
import com.kce.kmrl.dto.LiveSummaryDto;
import com.kce.kmrl.dto.ReportResponse;
import com.kce.kmrl.dto.StatusUpdateRequest;
import com.kce.kmrl.entity.Report;
import com.kce.kmrl.entity.ReportCategory;
import com.kce.kmrl.entity.ReportStatus;
import com.kce.kmrl.exception.ReportNotFoundException;
import com.kce.kmrl.pdf.ReportPdfGenerator;
import com.kce.kmrl.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportRepository repository;
    private final ReportGenerationService generationService;
    private final ReportPdfGenerator pdfGenerator;

    @Autowired
    public ReportServiceImpl(ReportRepository repository,
                              ReportGenerationService generationService,
                              ReportPdfGenerator pdfGenerator) {
        this.repository = repository;
        this.generationService = generationService;
        this.pdfGenerator = pdfGenerator;
    }

    @Override
    public List<ReportResponse> getAllReports() {
        return repository.findAllWithoutPdf()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public List<ReportResponse> getReportsByCategory(String category) {
        ReportCategory cat = ReportCategory.fromString(category);
        return repository.findByCategoryWithoutPdf(cat)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public ReportResponse getReportById(String id) {
        Report report = repository.findById(id)
            .orElseThrow(() -> new ReportNotFoundException(id));
        return toResponse(report);
    }

    @Override
    public ReportResponse generateLiveReport(GenerateReportRequest request) {
        ReportCategory category = ReportCategory.fromString(request.getCategory());

        LiveSummaryDto live = generationService.fetchLiveSummary(category);
        String summaryText = generationService.buildSummaryText(category, live);

        Report report = new Report();
        report.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle()
                : defaultTitle(category));
        report.setCategory(category);
        report.setAuthor(request.getAuthor());
        report.setSummary(summaryText);
        report.setCreatedBy(request.getAuthor());
        report.setLiveGenerated(true);
        report.setStatus(ReportStatus.PROCESSING);
        report.setGeneratedDate(Instant.now());
        report.setCreatedAt(Instant.now());

        Report saved = repository.save(report);

        byte[] pdf = pdfGenerator.generate(saved, live);
        saved.setPdfContent(pdf);
        saved.setFileSize(formatFileSize(pdf.length));
        saved.setStatus(ReportStatus.FINAL);

        Report finalSaved = repository.save(saved);
        return toResponse(finalSaved);
    }

    @Override
    public LiveSummaryDto getLiveSummary() {
        return generationService.fetchLiveSummary();
    }

    @Override
    public byte[] getReportPdf(String id) {
        Report report = repository.findById(id)
            .orElseThrow(() -> new ReportNotFoundException(id));
        if (report.getPdfContent() == null) {

            byte[] pdf = pdfGenerator.generate(report, null);
            report.setPdfContent(pdf);
            report.setFileSize(formatFileSize(pdf.length));
            repository.save(report);
            return pdf;
        }
        return report.getPdfContent();
    }

    @Override
    public ReportDownload downloadReport(String id) {
        Report report = repository.findById(id)
            .orElseThrow(() -> new ReportNotFoundException(id));
        byte[] pdf = report.getPdfContent();
        if (pdf == null) {
            pdf = pdfGenerator.generate(report, null);
            report.setPdfContent(pdf);
            report.setFileSize(formatFileSize(pdf.length));
            repository.save(report);
        }
        return new ReportDownload(report.getTitle(), pdf);
    }

    @Override
    public ReportResponse updateReportStatus(String id, StatusUpdateRequest request) {
        Report report = repository.findById(id)
            .orElseThrow(() -> new ReportNotFoundException(id));

        if (request.getStatus() != null) {

            report.setStatus(ReportStatus.fromString(request.getStatus()));
        }

        Report updated = repository.save(report);
        return toResponse(updated);
    }

    private String defaultTitle(ReportCategory category) {
        return switch (category) {
            case FLEET -> "Fleet Summary Report";
            case SCHEDULE -> "Schedule Summary Report";
            case MAINTENANCE -> "Maintenance Summary Report";
            case ALERTS -> "Critical Alarm & Deviation Incident Analysis";
            case SAFETY -> "System Safety & Interlocking Audit";
            case OVERALL -> "Overall Network Summary Report";
        };
    }

    private String formatFileSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        return String.format("%.1f MB", kb / 1024.0);
    }

    private ReportResponse toResponse(Report report) {
        return new ReportResponse(
            report.getId(),
            report.getTitle(),
            report.getCategory() != null ? report.getCategory().name() : null,
            report.getGeneratedDate(),
            report.getAuthor(),
            report.getFileSize(),
            report.getStatus() != null ? report.getStatus().name() : null,
            report.getSummary(),
            report.getCreatedBy(),
            report.getCreatedAt()
        );
    }
}
