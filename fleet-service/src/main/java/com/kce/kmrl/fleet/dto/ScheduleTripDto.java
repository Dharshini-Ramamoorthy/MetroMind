package com.kce.kmrl.fleet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTripDto {

    private String id;
    private String tripCode;
    private String status;
    private String assignedTrainId;

    public ScheduleTripDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTripCode() {
        return tripCode;
    }

    public void setTripCode(String tripCode) {
        this.tripCode = tripCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedTrainId() {
        return assignedTrainId;
    }

    public void setAssignedTrainId(String assignedTrainId) {
        this.assignedTrainId = assignedTrainId;
    }
}
