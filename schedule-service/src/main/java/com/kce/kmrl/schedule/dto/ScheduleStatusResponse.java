package com.kce.kmrl.schedule.dto;

public class ScheduleStatusResponse {
    private String serviceDate;
    private boolean generated;
    private long tripCount;
    private long proposedCount;
    private long plannedCount;
    private long activeCount;
    private String dayType;
    private double headwayMultiplier;

    public ScheduleStatusResponse() {}

    public ScheduleStatusResponse(String serviceDate, boolean generated, long tripCount,
                                   long proposedCount, long plannedCount, long activeCount,
                                   String dayType, double headwayMultiplier) {
        this.serviceDate = serviceDate;
        this.generated = generated;
        this.tripCount = tripCount;
        this.proposedCount = proposedCount;
        this.plannedCount = plannedCount;
        this.activeCount = activeCount;
        this.dayType = dayType;
        this.headwayMultiplier = headwayMultiplier;
    }

    public String getServiceDate() { return serviceDate; }
    public void setServiceDate(String serviceDate) { this.serviceDate = serviceDate; }

    public boolean isGenerated() { return generated; }
    public void setGenerated(boolean generated) { this.generated = generated; }

    public long getTripCount() { return tripCount; }
    public void setTripCount(long tripCount) { this.tripCount = tripCount; }

    public long getProposedCount() { return proposedCount; }
    public void setProposedCount(long proposedCount) { this.proposedCount = proposedCount; }

    public long getPlannedCount() { return plannedCount; }
    public void setPlannedCount(long plannedCount) { this.plannedCount = plannedCount; }

    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }

    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }

    public double getHeadwayMultiplier() { return headwayMultiplier; }
    public void setHeadwayMultiplier(double headwayMultiplier) { this.headwayMultiplier = headwayMultiplier; }
}
