package com.kce.kmrl.alert.rules;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.AuditLedgerEntry;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.repository.AlertRepository;
import com.kce.kmrl.alert.repository.AuditLedgerRepository;
import com.kce.kmrl.alert.rules.state.RuleStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Component
public class RuleAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(RuleAlertPublisher.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AlertRepository alertRepository;
    private final AuditLedgerRepository ledgerRepository;
    private final RuleStateStore stateStore;

    public RuleAlertPublisher(AlertRepository alertRepository,
                              AuditLedgerRepository ledgerRepository,
                              RuleStateStore stateStore) {
        this.alertRepository = alertRepository;
        this.ledgerRepository = ledgerRepository;
        this.stateStore = stateStore;
    }

    public void publishLevel(String alertId, Severity severity, String title, String subtitle,
                             String asset, List<Alert.AlertField> fields, String predictive,
                             List<Integer> sparkline, List<String> targetRoles, String targetUserId) {
        publishLevel(alertId, severity, title, subtitle, asset, fields, predictive,
                sparkline, targetRoles, targetUserId, null);
    }

    public void publishLevel(String alertId, Severity severity, String title, String subtitle,
                             String asset, List<Alert.AlertField> fields, String predictive,
                             List<Integer> sparkline, List<String> targetRoles, String targetUserId,
                             String sourceEntityId) {
        if (stateStore.seenBefore("suppressed:" + alertId)) {
            return;
        }
        Optional<Alert> existing = alertRepository.findById(alertId);
        Alert alert = existing.orElseGet(Alert::new);
        boolean isNew = existing.isEmpty();

        alert.setId(alertId);
        alert.setTime(LocalTime.now(IST).format(TIME_FMT));
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setSubtitle(subtitle);
        alert.setAsset(asset);
        alert.setFields(fields);
        alert.setPredictive(predictive);
        alert.setSparkline(sparkline);
        alert.setTargetRoles(targetRoles);
        alert.setTargetUserId(targetUserId);
        alert.setSourceEntityId(sourceEntityId);
        if (isNew) {
            alert.setCreatedAt(Instant.now());
        }

        alertRepository.save(alert);
    }

    public void resolveLevel(String alertId) {
        stateStore.forget("suppressed:" + alertId);
        Optional<Alert> existing = alertRepository.findById(alertId);
        if (existing.isEmpty()) {
            return;
        }

        Alert alert = existing.get();
        Instant now = Instant.now();
        AuditLedgerEntry entry = new AuditLedgerEntry();
        entry.setIncidentId(alert.getId());
        entry.setAction("AUTO_RESOLVE");
        entry.setResolvedAt(now);
        entry.setSeverity(alert.getSeverity());
        entry.setOperatorName("SYSTEM");
        entry.setOperatorId("SYSTEM");
        entry.setOperatorRole("SYSTEM");
        entry.setDetails("Underlying rule condition cleared.");
        entry.setValidationHash(hash(entry));

        ledgerRepository.save(entry);
        alertRepository.deleteById(alertId);
        log.info("Auto-resolved {}", alertId);
    }

    public void suppressLevel(String alertId) {
        if (alertId != null && isLevelAlertId(alertId)) {
            stateStore.put("suppressed:" + alertId, "true");
        }
    }

    private boolean isLevelAlertId(String id) {
        return id.startsWith("ALT-HEALTH-") || id.startsWith("ALT-BRAKE-")
                || id.startsWith("ALT-SERVICEDUE-") || id.startsWith("ALT-DELAY-")
                || id.startsWith("ALT-UNASSIGNED-") || id.startsWith("ALT-CONFLICT-")
                || id.startsWith("ALT-SLA-") || id.startsWith("ALT-QUEUESLA-");
    }

    public void publishEventOnce(String dedupeKey, String alertIdPrefix, Severity severity, String title,
                                 String subtitle, String asset, List<Alert.AlertField> fields,
                                 String predictive, List<Integer> sparkline,
                                 List<String> targetRoles, String targetUserId) {
        if (!stateStore.claimIfAbsent(dedupeKey)) {
            return;
        }

        Alert alert = new Alert();
        alert.setId(eventAlertId(dedupeKey, alertIdPrefix));
        alert.setTime(LocalTime.now(IST).format(TIME_FMT));
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setSubtitle(subtitle);
        alert.setAsset(asset);
        alert.setFields(fields);
        alert.setPredictive(predictive);
        alert.setSparkline(sparkline);
        alert.setCreatedAt(Instant.now());
        alert.setTargetRoles(targetRoles);
        alert.setTargetUserId(targetUserId);

        try {
            alertRepository.save(alert);
        } catch (RuntimeException ex) {

            stateStore.forget(dedupeKey);
            throw ex;
        }
    }

    private String eventAlertId(String dedupeKey, String prefix) {
        String hash = sha256(dedupeKey);
        return prefix + "-" + hash.substring(0, 12).toUpperCase();
    }

    private String hash(AuditLedgerEntry entry) {
        String payload = String.join("|",
                safe(entry.getIncidentId()), safe(entry.getAction()), safe(entry.getOperatorId()),
                safe(entry.getOperatorRole()), String.valueOf(entry.getResolvedAt().toEpochMilli()),
                String.valueOf(entry.getSeverity()), safe(entry.getDetails()));
        return sha256(payload);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
