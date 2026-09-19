package com.kce.kmrl.alert.dto;

import com.kce.kmrl.alert.entity.Alert;

import java.time.Instant;
import java.util.List;

public class AlertResponse {

    private String id;
    private String time;
    private String severity;
    private String title;
    private String subtitle;
    private String asset;
    private List<FieldDto> fields;
    private String predictive;
    private List<Integer> sparkline;
    private Instant createdAt;
    private List<String> targetRoles;
    private String targetUserId;
    private String sourceEntityId;
    private String maintenanceWorkOrderId;

    public AlertResponse() {}

    public static AlertResponse from(Alert alert) {
        AlertResponse r = new AlertResponse();
        r.id           = alert.getId();
        r.time         = alert.getTime();
        r.severity     = alert.getSeverity() != null ? alert.getSeverity().name().toLowerCase() : null;
        r.title        = alert.getTitle();
        r.subtitle     = alert.getSubtitle();
        r.asset        = alert.getAsset();
        r.predictive   = alert.getPredictive();
        r.sparkline    = alert.getSparkline();
        r.createdAt    = alert.getCreatedAt();
        r.targetRoles  = alert.getTargetRoles();
        r.targetUserId = alert.getTargetUserId();
        r.sourceEntityId = alert.getSourceEntityId();
        r.maintenanceWorkOrderId = alert.getMaintenanceWorkOrderId();

        if (alert.getFields() != null) {
            r.fields = alert.getFields().stream()
                .map(f -> new FieldDto(f.getLabel(), f.getValue()))
                .toList();
        }
        return r;
    }

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

    public List<FieldDto> getFields() { return fields; }
    public void setFields(List<FieldDto> fields) { this.fields = fields; }

    public String getPredictive() { return predictive; }
    public void setPredictive(String predictive) { this.predictive = predictive; }

    public List<Integer> getSparkline() { return sparkline; }
    public void setSparkline(List<Integer> sparkline) { this.sparkline = sparkline; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<String> getTargetRoles() { return targetRoles; }
    public void setTargetRoles(List<String> targetRoles) { this.targetRoles = targetRoles; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public String getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(String sourceEntityId) { this.sourceEntityId = sourceEntityId; }

    public String getMaintenanceWorkOrderId() { return maintenanceWorkOrderId; }
    public void setMaintenanceWorkOrderId(String maintenanceWorkOrderId) { this.maintenanceWorkOrderId = maintenanceWorkOrderId; }

    public static class FieldDto {
        private String label;
        private String value;

        public FieldDto() {}
        public FieldDto(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
