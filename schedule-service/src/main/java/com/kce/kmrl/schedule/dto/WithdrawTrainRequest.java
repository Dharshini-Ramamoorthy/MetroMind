package com.kce.kmrl.schedule.dto;

public class WithdrawTrainRequest {

    private String trainId;
    private boolean emergency;

    private String maintenanceTicketId;

    public WithdrawTrainRequest() {}

    public String getTrainId() { return trainId; }
    public void setTrainId(String trainId) { this.trainId = trainId; }

    public boolean isEmergency() { return emergency; }
    public void setEmergency(boolean emergency) { this.emergency = emergency; }

    public String getMaintenanceTicketId() { return maintenanceTicketId; }
    public void setMaintenanceTicketId(String maintenanceTicketId) { this.maintenanceTicketId = maintenanceTicketId; }
}
