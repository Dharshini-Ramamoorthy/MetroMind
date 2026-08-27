package com.kce.kmrl.fleet.dto;

import com.kce.kmrl.fleet.model.TrainStatus;

public class StatusUpdateRequest {
    private TrainStatus status;

    public StatusUpdateRequest() {
    }

    public StatusUpdateRequest(TrainStatus status) {
        this.status = status;
    }

    public TrainStatus getStatus() {
        return status;
    }

    public void setStatus(TrainStatus status) {
        this.status = status;
    }
}
