package com.kce.kmrl.dto;

public class ScheduleTripDto {
    private String id;
    private String tripCode;
    private String routeName;
    private String assignedTrainName;
    private String status;
    private String startTime;
    private String endTime;

    public ScheduleTripDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTripCode() { return tripCode; }
    public void setTripCode(String tripCode) { this.tripCode = tripCode; }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }

    public String getAssignedTrainName() { return assignedTrainName; }
    public void setAssignedTrainName(String assignedTrainName) { this.assignedTrainName = assignedTrainName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
