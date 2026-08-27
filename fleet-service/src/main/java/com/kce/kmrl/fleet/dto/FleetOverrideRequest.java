package com.kce.kmrl.fleet.dto;

import jakarta.validation.constraints.NotBlank;

public class FleetOverrideRequest {

    @NotBlank(message = "A reason/justification is required to request a fleet override")
    private String reason;

    public FleetOverrideRequest() {}

    public FleetOverrideRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
