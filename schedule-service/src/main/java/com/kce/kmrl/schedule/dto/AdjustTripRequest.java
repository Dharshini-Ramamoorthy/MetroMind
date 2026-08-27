package com.kce.kmrl.schedule.dto;

public class AdjustTripRequest {
    private String routeName;
    private String startTime;
    private String endTime;
    private String assignedTrainId;

    private String reason;

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getAssignedTrainId() {
        return assignedTrainId;
    }

    public void setAssignedTrainId(String assignedTrainId) {
        this.assignedTrainId = assignedTrainId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
