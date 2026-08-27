package com.kce.kmrl.schedule.dto;

public class TrainAssetDto {
    private String id;
    private String trainNumber;
    private String model;
    private String status;
    private String currentDepot;
    private String track;
    private String assignedTripCode;
    private String assignedRoute;
    private Integer healthIndex;
    private Integer totalMileageKm;

    public TrainAssetDto() {
    }

    public TrainAssetDto(String id, String trainNumber, String status, long totalMileageKm, int healthIndex) {
        this.id = id;
        this.trainNumber = trainNumber;
        this.status = status;
        this.totalMileageKm = (int) totalMileageKm;
        this.healthIndex = healthIndex;
    }

    public TrainAssetDto(String id, String trainNumber, String model, String status,
                         String currentDepot, String track, String assignedTripCode,
                         String assignedRoute, Integer healthIndex, Integer totalMileageKm) {
        this.id = id;
        this.trainNumber = trainNumber;
        this.model = model;
        this.status = status;
        this.currentDepot = currentDepot;
        this.track = track;
        this.assignedTripCode = assignedTripCode;
        this.assignedRoute = assignedRoute;
        this.healthIndex = healthIndex;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public Integer getTotalMileageKm() {
        return totalMileageKm;
    }

    public void setTotalMileageKm(Integer totalMileageKm) {
        this.totalMileageKm = totalMileageKm;
    }
}
