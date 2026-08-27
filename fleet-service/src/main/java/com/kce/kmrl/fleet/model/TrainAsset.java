package com.kce.kmrl.fleet.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "train_assets")
public class TrainAsset {

    @Id
    private String id;

    private String trainNumber;
    private String model;

    private TrainStatus status;

    private String currentDepot;
    private String track;

    private String assignedTripCode;
    private String assignedRoute;

    private Integer healthIndex;
    private String lastServicedAt;
    private Integer brakePressureKpa;
    private Integer totalMileageKm;

    private Integer mileageAtLastServiceKm;

    public TrainAsset() {
    }

    public TrainAsset(String id, String trainNumber, String model, TrainStatus status,
                      String currentDepot, String track, String assignedTripCode,
                      String assignedRoute, Integer healthIndex, String lastServicedAt,
                      Integer brakePressureKpa, Integer totalMileageKm) {
        this.id = id;
        this.trainNumber = trainNumber;
        this.model = model;
        this.status = status;
        this.currentDepot = currentDepot;
        this.track = track;
        this.assignedTripCode = assignedTripCode;
        this.assignedRoute = assignedRoute;
        this.healthIndex = healthIndex;
        this.lastServicedAt = lastServicedAt;
        this.brakePressureKpa = brakePressureKpa;
        this.totalMileageKm = totalMileageKm;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public TrainStatus getStatus() {
        return status;
    }

    public void setStatus(TrainStatus status) {
        this.status = status;
    }

    public String getCurrentDepot() {
        return currentDepot;
    }

    public void setCurrentDepot(String currentDepot) {
        this.currentDepot = currentDepot;
    }

    public String getTrack() {
        return track;
    }

    public void setTrack(String track) {
        this.track = track;
    }

    public String getAssignedTripCode() {
        return assignedTripCode;
    }

    public void setAssignedTripCode(String assignedTripCode) {
        this.assignedTripCode = assignedTripCode;
    }

    public String getAssignedRoute() {
        return assignedRoute;
    }

    public void setAssignedRoute(String assignedRoute) {
        this.assignedRoute = assignedRoute;
    }

    public Integer getHealthIndex() {
        return healthIndex;
    }

    public void setHealthIndex(Integer healthIndex) {
        this.healthIndex = healthIndex;
    }

    public String getLastServicedAt() {
        return lastServicedAt;
    }

    public void setLastServicedAt(String lastServicedAt) {
        this.lastServicedAt = lastServicedAt;
    }

    public Integer getBrakePressureKpa() {
        return brakePressureKpa;
    }

    public void setBrakePressureKpa(Integer brakePressureKpa) {
        this.brakePressureKpa = brakePressureKpa;
    }

    public Integer getTotalMileageKm() {
        return totalMileageKm;
    }

    public void setTotalMileageKm(Integer totalMileageKm) {
        this.totalMileageKm = totalMileageKm;
    }

    public Integer getMileageAtLastServiceKm() {
        return mileageAtLastServiceKm;
    }

    public void setMileageAtLastServiceKm(Integer mileageAtLastServiceKm) {
        this.mileageAtLastServiceKm = mileageAtLastServiceKm;
    }
}
