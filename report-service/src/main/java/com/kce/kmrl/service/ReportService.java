package com.kce.kmrl.service;

import com.kce.kmrl.dto.GenerateReportRequest;
import com.kce.kmrl.dto.LiveSummaryDto;
import com.kce.kmrl.dto.ReportResponse;
import com.kce.kmrl.dto.StatusUpdateRequest;

import java.util.List;

public interface ReportService {

    List<ReportResponse> getAllReports();

    List<ReportResponse> getReportsByCategory(String category);

    ReportResponse getReportById(String id);

    ReportResponse updateReportStatus(String id, StatusUpdateRequest request);

    ReportResponse generateLiveReport(GenerateReportRequest request);

    LiveSummaryDto getLiveSummary();

    byte[] getReportPdf(String id);
}
