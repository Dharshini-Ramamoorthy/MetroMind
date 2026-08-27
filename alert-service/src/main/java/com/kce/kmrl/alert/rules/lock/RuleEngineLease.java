package com.kce.kmrl.alert.rules.lock;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "alert_rule_leases")
public class RuleEngineLease {
    @Id
    private String name;
    private String owner;
    private Instant expiresAt;

    public RuleEngineLease() {}
    public RuleEngineLease(String name, String owner, Instant expiresAt) {
        this.name = name;
        this.owner = owner;
        this.expiresAt = expiresAt;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
