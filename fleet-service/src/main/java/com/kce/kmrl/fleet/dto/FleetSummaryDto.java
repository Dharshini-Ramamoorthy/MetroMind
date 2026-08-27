package com.kce.kmrl.fleet.dto;

public class FleetSummaryDto {
    private long active;
    private long standby;
    private long maintenance;
    private long total;

    public FleetSummaryDto() {
    }

    public FleetSummaryDto(long active, long standby, long maintenance, long total) {
        this.active = active;
        this.standby = standby;
        this.maintenance = maintenance;
        this.total = total;
    }

    public long getActive() {
        return active;
    }

    public void setActive(long active) {
        this.active = active;
    }

    public long getStandby() {
        return standby;
    }

    public void setStandby(long standby) {
        this.standby = standby;
    }

    public long getMaintenance() {
        return maintenance;
    }

    public void setMaintenance(long maintenance) {
        this.maintenance = maintenance;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
