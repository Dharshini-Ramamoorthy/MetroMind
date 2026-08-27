package com.kce.kmrl.dto;

public class LiveSummaryDto {
    private FleetSummaryDto fleet;
    private MaintenanceSummaryDto maintenance;
    private ScheduleSummaryDto schedule;

    public LiveSummaryDto() {}

    public LiveSummaryDto(FleetSummaryDto fleet, MaintenanceSummaryDto maintenance, ScheduleSummaryDto schedule) {
        this.fleet = fleet;
        this.maintenance = maintenance;
        this.schedule = schedule;
    }

    public FleetSummaryDto getFleet() { return fleet; }
    public void setFleet(FleetSummaryDto fleet) { this.fleet = fleet; }

    public MaintenanceSummaryDto getMaintenance() { return maintenance; }
    public void setMaintenance(MaintenanceSummaryDto maintenance) { this.maintenance = maintenance; }

    public ScheduleSummaryDto getSchedule() { return schedule; }
    public void setSchedule(ScheduleSummaryDto schedule) { this.schedule = schedule; }
}
