package com.kce.kmrl.service;

import com.kce.kmrl.client.ResilientReportSourceClient;
import com.kce.kmrl.dto.FleetSummaryDto;
import com.kce.kmrl.dto.LiveSummaryDto;
import com.kce.kmrl.dto.MaintenanceSummaryDto;
import com.kce.kmrl.dto.MaintenanceTicketDto;
import com.kce.kmrl.dto.ScheduleSummaryDto;
import com.kce.kmrl.dto.ScheduleTripDto;
import com.kce.kmrl.entity.ReportCategory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportGenerationService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final ResilientReportSourceClient sourceClient;

    public ReportGenerationService(ResilientReportSourceClient sourceClient) {
        this.sourceClient = sourceClient;
    }

    public LiveSummaryDto fetchLiveSummary() {
        FleetSummaryDto fleet = sourceClient.getFleetSummary();

        List<MaintenanceTicketDto> tickets = sourceClient.getMaintenanceTickets();
        MaintenanceSummaryDto maintenance = tickets != null ? summarizeMaintenance(tickets) : null;

        List<ScheduleTripDto> trips = sourceClient.getScheduleTrips();
        ScheduleSummaryDto schedule = trips != null ? summarizeSchedule(trips) : null;

        return new LiveSummaryDto(fleet, maintenance, schedule);
    }

    public LiveSummaryDto fetchLiveSummary(ReportCategory category) {
        if (category == ReportCategory.OVERALL || category == ReportCategory.ALERTS || category == ReportCategory.SAFETY) {
            return fetchLiveSummary();
        }

        FleetSummaryDto fleet = null;
        MaintenanceSummaryDto maintenance = null;
        ScheduleSummaryDto schedule = null;

        switch (category) {
            case FLEET -> fleet = sourceClient.getFleetSummary();
            case MAINTENANCE -> {
                List<MaintenanceTicketDto> tickets = sourceClient.getMaintenanceTickets();
                maintenance = tickets != null ? summarizeMaintenance(tickets) : null;
            }
            case SCHEDULE -> {
                List<ScheduleTripDto> trips = sourceClient.getScheduleTrips();
                schedule = trips != null ? summarizeSchedule(trips) : null;
            }
            default -> {  }
        }

        return new LiveSummaryDto(fleet, maintenance, schedule);
    }

    public String buildSummaryText(ReportCategory category, LiveSummaryDto live) {
        StringBuilder sb = new StringBuilder();
        sb.append("Live ").append(category.name().toLowerCase())
                .append(" snapshot as of ").append(DATE_FMT.format(java.time.Instant.now())).append(". ");

        switch (category) {
            case FLEET -> sb.append(fleetSentence(live));
            case MAINTENANCE -> sb.append(maintenanceSentence(live));
            case SCHEDULE -> sb.append(scheduleSentence(live));
            case ALERTS, SAFETY, OVERALL -> sb
                    .append(fleetSentence(live)).append(" ")
                    .append(maintenanceSentence(live)).append(" ")
                    .append(scheduleSentence(live));
        }

        return sb.toString();
    }

    private String fleetSentence(LiveSummaryDto live) {
        if (live.getFleet() == null) {
            return "Fleet data was unavailable at generation time.";
        }
        FleetSummaryDto f = live.getFleet();
        return String.format(
            "Fleet: %d active, %d standby, %d in maintenance (of %d trains total).",
            f.getActive(), f.getStandby(), f.getMaintenance(), f.getTotal());
    }

    private String maintenanceSentence(LiveSummaryDto live) {
        if (live.getMaintenance() == null) {
            return "Maintenance data was unavailable at generation time.";
        }
        MaintenanceSummaryDto m = live.getMaintenance();
        return String.format(
            "Maintenance: %d open, %d in progress, %d completed tickets (%d critical, %d high priority).",
            m.getOpen(), m.getInProgress(), m.getCompleted(), m.getCritical(), m.getHigh());
    }

    private String scheduleSentence(LiveSummaryDto live) {
        if (live.getSchedule() == null) {
            return "Schedule data was unavailable at generation time.";
        }
        ScheduleSummaryDto s = live.getSchedule();
        return String.format(
            "Schedule (current window): %d active, %d completed, %d delayed, %d planned trips.",
            s.getActive(), s.getCompleted(), s.getDelayed(), s.getPlanned());
    }

    private MaintenanceSummaryDto summarizeMaintenance(List<MaintenanceTicketDto> tickets) {
        long open = 0, inProgress = 0, completed = 0, critical = 0, high = 0;
        for (MaintenanceTicketDto t : tickets) {
            String status = t.getStatus() != null ? t.getStatus().toUpperCase() : "";
            String priority = t.getPriority() != null ? t.getPriority().toUpperCase() : "";
            switch (status) {
                case "OPEN" -> open++;
                case "IN_PROGRESS" -> inProgress++;
                case "COMPLETED" -> completed++;
                default -> {  }
            }
            if (priority.equals("CRITICAL")) critical++;
            if (priority.equals("HIGH")) high++;
        }
        return new MaintenanceSummaryDto(open, inProgress, completed, critical, high, tickets.size());
    }

    private ScheduleSummaryDto summarizeSchedule(List<ScheduleTripDto> trips) {
        long active = 0, completed = 0, delayed = 0, planned = 0;
        for (ScheduleTripDto t : trips) {
            String status = t.getStatus() != null ? t.getStatus().toUpperCase() : "";
            switch (status) {
                case "ACTIVE" -> active++;
                case "COMPLETED" -> completed++;
                case "DELAYED" -> delayed++;
                case "PLANNED" -> planned++;
                default -> {  }
            }
        }
        return new ScheduleSummaryDto(active, completed, delayed, planned, trips.size());
    }
}
