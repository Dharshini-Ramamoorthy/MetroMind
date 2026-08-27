package com.kce.kmrl.schedule.dto;

public class MaintenanceTicketDto {
    private String trainNumber;
    private String status;
    private String repairType;
    private String plannedMaintenanceDate;

    public MaintenanceTicketDto() {}

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRepairType() { return repairType; }
    public void setRepairType(String repairType) { this.repairType = repairType; }

    public String getPlannedMaintenanceDate() { return plannedMaintenanceDate; }
    public void setPlannedMaintenanceDate(String plannedMaintenanceDate) { this.plannedMaintenanceDate = plannedMaintenanceDate; }
}
