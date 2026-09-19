package com.kce.kmrl.alert.dto;

import com.kce.kmrl.alert.entity.AuditLedgerEntry;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LedgerEntryResponse {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));

    private String id;
    private String action;
    private String ts;
    private String sev;
    private String operator;
    private String opId;
    private String hash;

    public LedgerEntryResponse() {}

    public static LedgerEntryResponse from(AuditLedgerEntry entry) {
        LedgerEntryResponse r = new LedgerEntryResponse();
        r.id       = entry.getIncidentId();
        r.action   = entry.getAction();
        r.ts       = entry.getResolvedAt() != null ? TS_FMT.format(entry.getResolvedAt()) : "";
        r.sev      = entry.getSeverity() != null ? entry.getSeverity().name().toLowerCase() : "";
        r.operator = entry.getOperatorName();
        r.opId     = entry.getOperatorId();
        r.hash     = entry.getValidationHash();
        return r;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTs() { return ts; }
    public void setTs(String ts) { this.ts = ts; }

    public String getSev() { return sev; }
    public void setSev(String sev) { this.sev = sev; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getOpId() { return opId; }
    public void setOpId(String opId) { this.opId = opId; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
