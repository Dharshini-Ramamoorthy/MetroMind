package com.kce.kmrl.schedule.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "schedule_trips")
public class ScheduleTrip {

    @Id
    private String id;

    private String tripCode;
    private String routeName;

    private String assignedTrainId;
    private String assignedTrainName;

    private TripStatus status;

    private String startTime;
    private String endTime;
    private int startMinutes;
    private int endMinutes;

    private boolean seedGenerated;

    private String source = "MANUAL";

    @Indexed
    private String serviceDate;

    private String changedBy;
    private Instant changedAt;
    private String changeReason;

    private String peakStation;
    private double peakDemand;
    private double predictedSurgeMultiplier;
    private int estimatedCapacityPct;

    private String assignmentStatus = "ASSIGNED";
    private String assignmentReason;
    private String trackId;
    private String sectionIds;
    private String platformId;
    private Integer occupancyStart;
    private Integer occupancyEnd;

    public ScheduleTrip() {
    }

    public ScheduleTrip(String id, String tripCode, String routeName, String assignedTrainId,
                        String assignedTrainName, TripStatus status, String startTime,
                        String endTime, int startMinutes, int endMinutes, boolean seedGenerated,
                        String serviceDate) {
        this.id = id;
        this.tripCode = tripCode;
        this.routeName = routeName;
        this.assignedTrainId = assignedTrainId;
        this.assignedTrainName = assignedTrainName;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startMinutes = startMinutes;
        this.endMinutes = endMinutes;
        this.seedGenerated = seedGenerated;
        this.serviceDate = serviceDate;
    }

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

    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public int getStartMinutes() { return startMinutes; }
    public void setStartMinutes(int startMinutes) { this.startMinutes = startMinutes; }

    public int getEndMinutes() { return endMinutes; }
    public void setEndMinutes(int endMinutes) { this.endMinutes = endMinutes; }

    public boolean isSeedGenerated() { return seedGenerated; }
    public void setSeedGenerated(boolean seedGenerated) { this.seedGenerated = seedGenerated; }

    public String getServiceDate() { return serviceDate; }
    public void setServiceDate(String serviceDate) { this.serviceDate = serviceDate; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getPeakStation() { return peakStation; }
    public void setPeakStation(String peakStation) { this.peakStation = peakStation; }

    public double getPeakDemand() { return peakDemand; }
    public void setPeakDemand(double peakDemand) { this.peakDemand = peakDemand; }

    public double getPredictedSurgeMultiplier() { return predictedSurgeMultiplier; }
    public void setPredictedSurgeMultiplier(double predictedSurgeMultiplier) { this.predictedSurgeMultiplier = predictedSurgeMultiplier; }

    public int getEstimatedCapacityPct() { return estimatedCapacityPct; }
    public void setEstimatedCapacityPct(int estimatedCapacityPct) { this.estimatedCapacityPct = estimatedCapacityPct; }

    public String getAssignmentStatus() { return assignmentStatus; }
    public void setAssignmentStatus(String assignmentStatus) { this.assignmentStatus = assignmentStatus; }

    public String getAssignmentReason() { return assignmentReason; }
    public void setAssignmentReason(String assignmentReason) { this.assignmentReason = assignmentReason; }

    public String getTrackId() { return trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId; }

    public String getSectionIds() { return sectionIds; }
    public void setSectionIds(String sectionIds) { this.sectionIds = sectionIds; }

    public String getPlatformId() { return platformId; }
    public void setPlatformId(String platformId) { this.platformId = platformId; }

    public Integer getOccupancyStart() { return occupancyStart; }
    public void setOccupancyStart(Integer occupancyStart) { this.occupancyStart = occupancyStart; }

    public Integer getOccupancyEnd() { return occupancyEnd; }
    public void setOccupancyEnd(Integer occupancyEnd) { this.occupancyEnd = occupancyEnd; }
}