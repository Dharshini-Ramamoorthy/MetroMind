package com.kce.kmrl.alert.dto;

public class AlertSummaryResponse {

    private int totalActive;
    private int criticalCount;
    private int warningCount;
    private int infoCount;
    private int systemIntegrity;

    public AlertSummaryResponse() {}

    public AlertSummaryResponse(int totalActive, int criticalCount,
                                int warningCount, int infoCount) {
        this.totalActive    = totalActive;
        this.criticalCount  = criticalCount;
        this.warningCount   = warningCount;
        this.infoCount      = infoCount;

        int deduction = (criticalCount * 8) + (warningCount * 3) + infoCount;
        this.systemIntegrity = Math.max(0, 100 - deduction);
    }

    public int getTotalActive() { return totalActive; }
    public void setTotalActive(int totalActive) { this.totalActive = totalActive; }

    public int getCriticalCount() { return criticalCount; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }

    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }

    public int getInfoCount() { return infoCount; }
    public void setInfoCount(int infoCount) { this.infoCount = infoCount; }

    public int getSystemIntegrity() { return systemIntegrity; }
    public void setSystemIntegrity(int systemIntegrity) { this.systemIntegrity = systemIntegrity; }
}
