package com.kce.kmrl.fleet.dto;

import com.kce.kmrl.fleet.model.TrainAsset;
import java.util.List;

public class TrackGroupDto {
    private String label;
    private List<TrainAsset> trains;

    public TrackGroupDto() {
    }

    public TrackGroupDto(String label, List<TrainAsset> trains) {
        this.label = label;
        this.trains = trains;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<TrainAsset> getTrains() {
        return trains;
    }

    public void setTrains(List<TrainAsset> trains) {
        this.trains = trains;
    }
}
