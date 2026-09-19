package com.kce.kmrl.alert.integration.dto;

public class CreateMaintenanceTicketRequest {
    private String trainNumber;
    private String description;
    private String priority;
    private String createdBy;

    public CreateMaintenanceTicketRequest() {}

    public CreateMaintenanceTicketRequest(String trainNumber, String description, String priority, String createdBy) {
        this.trainNumber = trainNumber;
        this.description = description;
        this.priority = priority;
        this.createdBy = createdBy;
    }

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
