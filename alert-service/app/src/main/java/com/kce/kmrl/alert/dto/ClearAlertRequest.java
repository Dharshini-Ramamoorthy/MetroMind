package com.kce.kmrl.alert.dto;

import jakarta.validation.constraints.NotBlank;

public class ClearAlertRequest {

    @NotBlank(message = "Operator name must not be blank")
    private String operatorName;

    private String operatorRole;

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }
}
