package com.kce.kmrl.fleet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MaintenanceTicketDto {

    private String id;
    private String trainNumber;
    private String status;
    private String repairType;
    private String plannedMaintenanceDate;

    public MaintenanceTicketDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRepairType() { return repairType; }
    public void setRepairType(String repairType) { this.repairType = repairType; }

    public String getPlannedMaintenanceDate() { return plannedMaintenanceDate; }
    public void setPlannedMaintenanceDate(String plannedMaintenanceDate) { this.plannedMaintenanceDate = plannedMaintenanceDate; }
}
