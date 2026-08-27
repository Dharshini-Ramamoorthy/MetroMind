package com.kce.kmrl.dto;

import com.kce.kmrl.entity.MaintenanceStatus;
import com.kce.kmrl.entity.RepairType;
import com.kce.kmrl.entity.TrainWithdrawalStatus;
import java.time.Instant;
import java.time.LocalDate;

public class MaintenanceResponse {

    private String id;
    private String trainNumber;
    private String description;
    private RepairType repairType;
    private LocalDate plannedMaintenanceDate;
    private MaintenanceStatus status;
    private String createdBy;
    private String approverComments;
    private Instant createdAt;
    private boolean trainPulled;
    private TrainWithdrawalStatus withdrawalStatus;
    private Instant trainPulledAt;
    private String cofDocumentUrl;
    private String cofDocumentOriginalFileName;
    private String cofSubmittedBy;
    private Instant cofSubmittedAt;
    private boolean approvalSubmissionFailed;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public RepairType getRepairType() { return repairType; }
    public void setRepairType(RepairType repairType) { this.repairType = repairType; }
    public LocalDate getPlannedMaintenanceDate() { return plannedMaintenanceDate; }
    public void setPlannedMaintenanceDate(LocalDate plannedMaintenanceDate) { this.plannedMaintenanceDate = plannedMaintenanceDate; }
    public MaintenanceStatus getStatus() { return status; }
    public void setStatus(MaintenanceStatus status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getApproverComments() { return approverComments; }
    public void setApproverComments(String approverComments) { this.approverComments = approverComments; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isTrainPulled() { return trainPulled; }
    public void setTrainPulled(boolean trainPulled) { this.trainPulled = trainPulled; }
    public TrainWithdrawalStatus getWithdrawalStatus() { return withdrawalStatus; }
    public void setWithdrawalStatus(TrainWithdrawalStatus withdrawalStatus) { this.withdrawalStatus = withdrawalStatus; }
    public Instant getTrainPulledAt() { return trainPulledAt; }
    public void setTrainPulledAt(Instant trainPulledAt) { this.trainPulledAt = trainPulledAt; }
    public String getCofDocumentUrl() { return cofDocumentUrl; }
    public void setCofDocumentUrl(String cofDocumentUrl) { this.cofDocumentUrl = cofDocumentUrl; }
    public String getCofDocumentOriginalFileName() { return cofDocumentOriginalFileName; }
    public void setCofDocumentOriginalFileName(String v) { this.cofDocumentOriginalFileName = v; }
    public String getCofSubmittedBy() { return cofSubmittedBy; }
    public void setCofSubmittedBy(String cofSubmittedBy) { this.cofSubmittedBy = cofSubmittedBy; }
    public Instant getCofSubmittedAt() { return cofSubmittedAt; }
    public void setCofSubmittedAt(Instant cofSubmittedAt) { this.cofSubmittedAt = cofSubmittedAt; }
    public boolean isApprovalSubmissionFailed() { return approvalSubmissionFailed; }
    public void setApprovalSubmissionFailed(boolean v) { this.approvalSubmissionFailed = v; }
}