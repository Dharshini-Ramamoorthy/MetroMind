package com.kce.kmrl.schedule.dto;

import java.util.List;

public class TrackGroupDto {
    private String label;
    private List<TrainAssetDto> trains;

    public TrackGroupDto() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<TrainAssetDto> getTrains() {
        return trains;
    }

    public void setTrains(List<TrainAssetDto> trains) {
        this.trains = trains;
    }
}
