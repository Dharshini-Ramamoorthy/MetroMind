package com.kce.kmrl.dto;

import com.kce.kmrl.entity.RepairType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class CreateTicketRequest {

    @NotBlank(message = "Train number is required.")
    private String trainNumber;

    @NotBlank(message = "Description is required.")
    private String description;

    @NotNull(message = "Repair type is required.")
    private RepairType repairType;

    private LocalDate plannedMaintenanceDate;

    @NotBlank(message = "Created-by is required.")
    private String createdBy;

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public RepairType getRepairType() { return repairType; }
    public void setRepairType(RepairType repairType) { this.repairType = repairType; }
    public LocalDate getPlannedMaintenanceDate() { return plannedMaintenanceDate; }
    public void setPlannedMaintenanceDate(LocalDate plannedMaintenanceDate) { this.plannedMaintenanceDate = plannedMaintenanceDate; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}