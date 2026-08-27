package com.kce.kmrl.dto;

import jakarta.validation.constraints.NotBlank;

public class CertificateOfFitnessRequest {

    @NotBlank(message = "Engineer name is required.")
    private String engineerName;

    private String remarks;

    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
