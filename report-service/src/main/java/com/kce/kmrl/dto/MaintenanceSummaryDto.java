package com.kce.kmrl.dto;

public class MaintenanceSummaryDto {
    private long open;
    private long inProgress;
    private long completed;
    private long critical;
    private long high;
    private long total;

    public MaintenanceSummaryDto() {}

    public MaintenanceSummaryDto(long open, long inProgress, long completed, long critical, long high, long total) {
        this.open = open;
        this.inProgress = inProgress;
        this.completed = completed;
        this.critical = critical;
        this.high = high;
        this.total = total;
    }

    public long getOpen() { return open; }
    public void setOpen(long open) { this.open = open; }

    public long getInProgress() { return inProgress; }
    public void setInProgress(long inProgress) { this.inProgress = inProgress; }

    public long getCompleted() { return completed; }
    public void setCompleted(long completed) { this.completed = completed; }

    public long getCritical() { return critical; }
    public void setCritical(long critical) { this.critical = critical; }

    public long getHigh() { return high; }
    public void setHigh(long high) { this.high = high; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
