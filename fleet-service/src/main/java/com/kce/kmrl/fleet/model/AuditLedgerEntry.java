package com.kce.kmrl.fleet.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "audit_ledger")
public class AuditLedgerEntry {

    @Id
    private String id;

    private String timestamp;
    private String trainNumber;
    private String vector;
    private String opCode;
    private String hash;

    public AuditLedgerEntry() {
    }

    public AuditLedgerEntry(String id, String timestamp, String trainNumber, String vector, String opCode, String hash) {
        this.id = id;
        this.timestamp = timestamp;
        this.trainNumber = trainNumber;
        this.vector = vector;
        this.opCode = opCode;
        this.hash = hash;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getVector() {
        return vector;
    }

    public void setVector(String vector) {
        this.vector = vector;
    }

    public String getOpCode() {
        return opCode;
    }

    public void setOpCode(String opCode) {
        this.opCode = opCode;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
