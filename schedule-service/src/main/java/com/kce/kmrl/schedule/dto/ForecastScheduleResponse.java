package com.kce.kmrl.schedule.dto;

import java.util.Map;

public class ForecastScheduleResponse {
    @com.fasterxml.jackson.annotation.JsonProperty("timestamp")
    private String timestamp;

    @com.fasterxml.jackson.annotation.JsonProperty("weather")
    private String weather;

    @com.fasterxml.jackson.annotation.JsonProperty("is_holiday")
    @com.fasterxml.jackson.annotation.JsonAlias("isHoliday")
    private boolean isHoliday;

    @com.fasterxml.jackson.annotation.JsonProperty("incident")
    private boolean incident;

    @com.fasterxml.jackson.annotation.JsonProperty("corridor_demand")
    @com.fasterxml.jackson.annotation.JsonAlias("corridorDemand")
    private Map<String, Double> corridorDemand;

    @com.fasterxml.jackson.annotation.JsonProperty("peak_station")
    @com.fasterxml.jackson.annotation.JsonAlias("peakStation")
    private String peakStation;

    @com.fasterxml.jackson.annotation.JsonProperty("peak_demand")
    @com.fasterxml.jackson.annotation.JsonAlias("peakDemand")
    private double peakDemand;

    @com.fasterxml.jackson.annotation.JsonProperty("predicted_surge_multiplier")
    @com.fasterxml.jackson.annotation.JsonAlias("predictedSurgeMultiplier")
    private double predictedSurgeMultiplier;

    @com.fasterxml.jackson.annotation.JsonProperty("recommended_headway_seconds")
    @com.fasterxml.jackson.annotation.JsonAlias("recommendedHeadwaySeconds")
    private int recommendedHeadwaySeconds;

    @com.fasterxml.jackson.annotation.JsonProperty("recommended_fleet_size")
    @com.fasterxml.jackson.annotation.JsonAlias("recommendedFleetSize")
    private int recommendedFleetSize;

    @com.fasterxml.jackson.annotation.JsonProperty("estimated_capacity_pct")
    @com.fasterxml.jackson.annotation.JsonAlias("estimatedCapacityPct")
    private int estimatedCapacityPct;

    @com.fasterxml.jackson.annotation.JsonProperty("data_source")
    @com.fasterxml.jackson.annotation.JsonAlias("dataSource")
    private String dataSource;

    public ForecastScheduleResponse() {}

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }
    public boolean isHoliday() { return isHoliday; }
    public void setHoliday(boolean holiday) { isHoliday = holiday; }
    public boolean isIncident() { return incident; }
    public void setIncident(boolean incident) { this.incident = incident; }
    public Map<String, Double> getCorridorDemand() { return corridorDemand; }
    public void setCorridorDemand(Map<String, Double> corridorDemand) { this.corridorDemand = corridorDemand; }
    public String getPeakStation() { return peakStation; }
    public void setPeakStation(String peakStation) { this.peakStation = peakStation; }
    public double getPeakDemand() { return peakDemand; }
    public void setPeakDemand(double peakDemand) { this.peakDemand = peakDemand; }
    public double getPredictedSurgeMultiplier() { return predictedSurgeMultiplier; }
    public void setPredictedSurgeMultiplier(double predictedSurgeMultiplier) { this.predictedSurgeMultiplier = predictedSurgeMultiplier; }
    public int getRecommendedHeadwaySeconds() { return recommendedHeadwaySeconds; }
    public void setRecommendedHeadwaySeconds(int recommendedHeadwaySeconds) { this.recommendedHeadwaySeconds = recommendedHeadwaySeconds; }
    public int getRecommendedFleetSize() { return recommendedFleetSize; }
    public void setRecommendedFleetSize(int recommendedFleetSize) { this.recommendedFleetSize = recommendedFleetSize; }
    public int getEstimatedCapacityPct() { return estimatedCapacityPct; }
    public void setEstimatedCapacityPct(int estimatedCapacityPct) { this.estimatedCapacityPct = estimatedCapacityPct; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
}
