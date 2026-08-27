package com.kce.kmrl.alert.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CreateAlertRequest {

    @NotBlank(message = "Incident ID must not be blank (e.g. INC-2299)")
    private String id;

    @NotBlank(message = "Time must not be blank (e.g. 14:32)")
    private String time;

    @NotBlank(message = "Severity must not be blank (SEV3, SEV2, or SEV1)")
    private String severity;

    @NotBlank(message = "Title must not be blank")
    private String title;

    @NotBlank(message = "Subtitle must not be blank")
    private String subtitle;

    @NotBlank(message = "Asset must not be blank")
    private String asset;

    @NotEmpty(message = "At least one diagnostic field is required")
    @Valid
    private List<FieldRequest> fields;

    @NotBlank(message = "Predictive consequence text must not be blank")
    private String predictive;

    @NotEmpty(message = "Sparkline data must not be empty")
    private List<Integer> sparkline;

    private List<String> targetRoles;

    private String targetUserId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public List<FieldRequest> getFields() { return fields; }
    public void setFields(List<FieldRequest> fields) { this.fields = fields; }

    public String getPredictive() { return predictive; }
    public void setPredictive(String predictive) { this.predictive = predictive; }

    public List<Integer> getSparkline() { return sparkline; }
    public void setSparkline(List<Integer> sparkline) { this.sparkline = sparkline; }

    public List<String> getTargetRoles() { return targetRoles; }
    public void setTargetRoles(List<String> targetRoles) { this.targetRoles = targetRoles; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public static class FieldRequest {
        @NotBlank(message = "Field label must not be blank")
        private String label;

        @NotNull(message = "Field value must not be null")
        private String value;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
