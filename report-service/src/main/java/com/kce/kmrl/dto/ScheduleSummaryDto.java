package com.kce.kmrl.dto;

public class ScheduleSummaryDto {
    private long active;
    private long completed;
    private long delayed;
    private long planned;
    private long total;

    public ScheduleSummaryDto() {}

    public ScheduleSummaryDto(long active, long completed, long delayed, long planned, long total) {
        this.active = active;
        this.completed = completed;
        this.delayed = delayed;
        this.planned = planned;
        this.total = total;
    }

    public long getActive() { return active; }
    public void setActive(long active) { this.active = active; }

    public long getCompleted() { return completed; }
    public void setCompleted(long completed) { this.completed = completed; }

    public long getDelayed() { return delayed; }
    public void setDelayed(long delayed) { this.delayed = delayed; }

    public long getPlanned() { return planned; }
    public void setPlanned(long planned) { this.planned = planned; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
