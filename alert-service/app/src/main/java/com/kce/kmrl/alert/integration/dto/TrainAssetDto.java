package com.kce.kmrl.alert.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrainAssetDto {

    private String id;
    private String trainNumber;
    private String status;
    private String currentDepot;
    private String track;
    private String assignedTripCode;
    private Integer healthIndex;
    private String lastServicedAt;
    private Integer brakePressureKpa;
    private Integer totalMileageKm;
    private Integer mileageAtLastServiceKm;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTrainNumber() { return trainNumber; }
    public void setTrainNumber(String trainNumber) { this.trainNumber = trainNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentDepot() { return currentDepot; }
    public void setCurrentDepot(String currentDepot) { this.currentDepot = currentDepot; }

    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }

    public String getAssignedTripCode() { return assignedTripCode; }
    public void setAssignedTripCode(String assignedTripCode) { this.assignedTripCode = assignedTripCode; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public String getLastServicedAt() { return lastServicedAt; }
    public void setLastServicedAt(String lastServicedAt) { this.lastServicedAt = lastServicedAt; }

    public Integer getBrakePressureKpa() { return brakePressureKpa; }
    public void setBrakePressureKpa(Integer brakePressureKpa) { this.brakePressureKpa = brakePressureKpa; }

    public Integer getTotalMileageKm() { return totalMileageKm; }
    public void setTotalMileageKm(Integer totalMileageKm) { this.totalMileageKm = totalMileageKm; }

    public Integer getMileageAtLastServiceKm() { return mileageAtLastServiceKm; }
    public void setMileageAtLastServiceKm(Integer mileageAtLastServiceKm) { this.mileageAtLastServiceKm = mileageAtLastServiceKm; }
}
