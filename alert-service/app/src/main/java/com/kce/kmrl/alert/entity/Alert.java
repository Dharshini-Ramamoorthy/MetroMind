package com.kce.kmrl.alert.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "alerts")
public class Alert {

    @Id
    private String id;

    private String time;
    private Severity severity;
    private String title;
    private String subtitle;
    private String asset;

    private List<AlertField> fields;

    private String predictive;

    private List<Integer> sparkline;

    private Instant createdAt;

    private List<String> targetRoles;

    private String targetUserId;

    private String sourceEntityId;

    private String maintenanceWorkOrderId;

    public Alert() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public List<AlertField> getFields() { return fields; }
    public void setFields(List<AlertField> fields) { this.fields = fields; }

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

    public static class AlertField {
        private String label;
        private String value;

        public AlertField() {}
        public AlertField(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
