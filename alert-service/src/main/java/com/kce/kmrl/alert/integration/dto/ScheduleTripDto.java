package com.kce.kmrl.alert.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTripDto {

    private String id;
    private String tripCode;
    private String routeName;
    private String assignedTrainId;
    private String assignedTrainName;
    private String status;
    private String startTime;
    private String endTime;
    private Integer startMinutes;
    private Integer endMinutes;
    private String serviceDate;
    private String source;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTripCode() { return tripCode; }
    public void setTripCode(String tripCode) { this.tripCode = tripCode; }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }

    public String getAssignedTrainId() { return assignedTrainId; }
    public void setAssignedTrainId(String assignedTrainId) { this.assignedTrainId = assignedTrainId; }

    public String getAssignedTrainName() { return assignedTrainName; }
    public void setAssignedTrainName(String assignedTrainName) { this.assignedTrainName = assignedTrainName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Integer getStartMinutes() { return startMinutes; }
    public void setStartMinutes(Integer startMinutes) { this.startMinutes = startMinutes; }

    public Integer getEndMinutes() { return endMinutes; }
    public void setEndMinutes(Integer endMinutes) { this.endMinutes = endMinutes; }

    public String getServiceDate() { return serviceDate; }
    public void setServiceDate(String serviceDate) { this.serviceDate = serviceDate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
