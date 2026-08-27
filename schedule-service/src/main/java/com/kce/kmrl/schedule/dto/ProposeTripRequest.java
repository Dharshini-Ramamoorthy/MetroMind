package com.kce.kmrl.schedule.dto;

public class ProposeTripRequest {
    private String tripCode;
    private String routeName;
    private String startTime;
    private String endTime;
    private String notes;
    private String serviceDate;
    private String source;
    private String assignedTrainId;
    private String assignedTrainName;

    public ProposeTripRequest() {
    }

    public ProposeTripRequest(String tripCode, String routeName, String startTime, String endTime, String notes) {
        this.tripCode = tripCode;
        this.routeName = routeName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.notes = notes;
    }

    public String getTripCode() {
        return tripCode;
    }

    public void setTripCode(String tripCode) {
        this.tripCode = tripCode;
    }

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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAssignedTrainId() {
        return assignedTrainId;
    }

    public void setAssignedTrainId(String assignedTrainId) {
        this.assignedTrainId = assignedTrainId;
    }

    public String getAssignedTrainName() {
        return assignedTrainName;
    }

    public void setAssignedTrainName(String assignedTrainName) {
        this.assignedTrainName = assignedTrainName;
    }
}
