package com.kce.kmrl.alert.config;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.AuditLedgerEntry;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.repository.AlertRepository;
import com.kce.kmrl.alert.repository.AuditLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AlertRepository alertRepository;
    private final AuditLedgerRepository ledgerRepository;
    private final boolean demoDataEnabled;

    public DataSeeder(AlertRepository alertRepository,
                      AuditLedgerRepository ledgerRepository,
                      @Value("${alert.seed.demo-data:false}") boolean demoDataEnabled) {
        this.alertRepository = alertRepository;
        this.ledgerRepository = ledgerRepository;
        this.demoDataEnabled = demoDataEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demoDataEnabled) {
            log.info("DataSeeder: alert.seed.demo-data=false — skipping static demo alerts/ledger " +
                    "(rules.AlertRuleEngine will populate real ones from live service data instead).");
            return;
        }
        seedAlerts();
        seedLedger();
    }

    private void seedAlerts() {
        if (alertRepository.count() > 0) {
            log.info("DataSeeder: alerts collection already has data — skipping alert seed.");
            return;
        }

        Alert a1 = alert("INC-2291", "14:32", Severity.SEV3,
            "Traction power breaker trip — Sub-station 04",
            "Aluva depot feeder yard",
            "Sub-station 04 Breaker (Feeder Bay 2)",
            List.of(
                field("Location", "Aluva Depot"),
                field("Detected", "14:32:08"),
                field("Signal Loss", "412 ms"),
                field("Affected Sets", "Set Alpha, Set Bravo")
            ),
            "AI pipeline predicts a 14-minute cumulative network delay within the next " +
            "two headway cycles if the Aluva bottleneck is not isolated within 6 minutes.",
            List.of(22, 24, 23, 41, 78, 95, 88, 91),
            Instant.parse("2026-08-07T09:02:00Z"),
            List.of("OC", "MDS")
        );

        Alert a2 = alert("INC-2290", "14:11", Severity.SEV2,
            "Platform screen door wireless link variance",
            "Edapally station, Platform 2",
            "Edapally Platform Screen Door Wireless Link",
            List.of(
                field("Location", "Edapally Station"),
                field("Detected", "14:11:52"),
                field("Packet Loss", "6.3%"),
                field("Affected Sets", "None (station-side)")
            ),
            "If unresolved, dwell time at Edapally may extend by 20–35 seconds per stop " +
            "during peak headway, with low risk of cascading delay.",
            List.of(10, 12, 14, 19, 26, 24, 29, 31),
            Instant.parse("2026-08-07T08:41:00Z"),
            List.of("OC")
        );

        Alert a3 = alert("INC-2289", "13:58", Severity.SEV2,
            "Set Bravo headway overlap — Kaloor corridor",
            "Kaloor junction approach",
            "CBTC Headway Controller — Kaloor Segment",
            List.of(
                field("Location", "Kaloor Corridor"),
                field("Detected", "13:58:04"),
                field("Headway Delta", "-18 sec"),
                field("Affected Sets", "Set Bravo, Set Charlie")
            ),
            "Continued overlap risks an automatic speed restriction on the Kaloor approach, " +
            "adding an estimated 3–5 minutes to the affected run.",
            List.of(30, 28, 33, 40, 44, 42, 47, 50),
            Instant.parse("2026-08-07T08:28:00Z"),
            List.of("OC")
        );

        Alert a4 = alert("INC-2288", "13:40", Severity.SEV1,
            "Telemetry lag on OCC dashboard refresh",
            "Operations Control Centre",
            "OCC Telemetry Aggregator — Node 3",
            List.of(
                field("Location", "OCC Core"),
                field("Detected", "13:40:15"),
                field("Refresh Lag", "2.1 sec"),
                field("Affected Sets", "None")
            ),
            "No operational impact expected. Dashboard refresh lag is within tolerance; " +
            "monitoring for recurrence.",
            List.of(4, 5, 4, 6, 7, 5, 6, 6),
            Instant.parse("2026-08-07T08:10:00Z"),
            List.of("SADA")
        );

        Alert a5 = alert("INC-2287", "13:22", Severity.SEV1,
            "Software version skew — onboard diagnostics",
            "Set Delta, Car 2",
            "Onboard Diagnostics Unit — Set Delta / Car 2",
            List.of(
                field("Location", "Set Delta (in service)"),
                field("Detected", "13:22:47"),
                field("Version Skew", "1 minor rev"),
                field("Affected Sets", "Set Delta")
            ),
            "Minor version skew logged for audit only. No functional degradation expected " +
            "before next scheduled induction.",
            List.of(3, 3, 4, 3, 4, 4, 3, 4),
            Instant.parse("2026-08-07T07:52:00Z"),
            List.of("MDS")
        );

        Alert a6 = alert("INC-2286", "12:57", Severity.SEV2,
            "Wet-rail adhesion advisory — yard bay tracks",
            "Muttom yard, Bay 5–7",
            "Yard Bay Track Sensors 5–7",
            List.of(
                field("Location", "Muttom Yard"),
                field("Detected", "12:57:31"),
                field("Surface Moisture", "High"),
                field("Affected Sets", "Yard shunting only")
            ),
            "Elevated adhesion risk during shunting moves. Recommend reduced yard speed " +
            "until surface moisture drops below advisory threshold.",
            List.of(15, 20, 34, 38, 36, 40, 37, 39),
            Instant.parse("2026-08-07T07:27:00Z"),
            List.of("OC", "MDS")
        );

        alertRepository.saveAll(List.of(a1, a2, a3, a4, a5, a6));
        log.info("DataSeeder: seeded {} active alerts.", 6);
    }

    private void seedLedger() {
        if (ledgerRepository.count() > 0) {
            log.info("DataSeeder: ledger collection already has data — skipping ledger seed.");
            return;
        }

        AuditLedgerEntry l1 = ledger("INC-2251",
            LocalDate.of(2026, 7, 5).atTime(22, 14, 44).atZone(IST).toInstant(),
            Severity.SEV3, "Arun K.", "OP-0987", "9f3a2c…e701b4");

        AuditLedgerEntry l2 = ledger("INC-2247",
            LocalDate.of(2026, 7, 5).atTime(18, 2, 21).atZone(IST).toInstant(),
            Severity.SEV2, "Ravi", "OP-1042", "44d1b7…aa9f02");

        AuditLedgerEntry l3 = ledger("INC-2239",
            LocalDate.of(2026, 7, 5).atTime(9, 41, 58).atZone(IST).toInstant(),
            Severity.SEV1, "Sana R.", "OP-1108", "c02e91…5b31de");

        AuditLedgerEntry l4 = ledger("INC-2231",
            LocalDate.of(2026, 7, 4).atTime(20, 37, 19).atZone(IST).toInstant(),
            Severity.SEV2, "Anjali", "AD-0031", "7ba045…12c9f0");

        ledgerRepository.saveAll(List.of(l1, l2, l3, l4));
        log.info("DataSeeder: seeded {} ledger entries.", 4);
    }

    private Alert alert(String id, String time, Severity sev, String title, String subtitle,
                        String asset, List<Alert.AlertField> fields, String predictive,
                        List<Integer> sparkline, Instant createdAt, List<String> targetRoles) {
        Alert a = new Alert();
        a.setId(id);
        a.setTime(time);
        a.setSeverity(sev);
        a.setTitle(title);
        a.setSubtitle(subtitle);
        a.setAsset(asset);
        a.setFields(fields);
        a.setPredictive(predictive);
        a.setSparkline(sparkline);
        a.setCreatedAt(createdAt);
        a.setTargetRoles(targetRoles);
        return a;
    }

    private Alert.AlertField field(String label, String value) {
        return new Alert.AlertField(label, value);
    }

    private AuditLedgerEntry ledger(String incidentId, Instant resolvedAt, Severity sev,
                                    String operatorName, String operatorId, String hash) {
        AuditLedgerEntry e = new AuditLedgerEntry();
        e.setIncidentId(incidentId);
        e.setResolvedAt(resolvedAt);
        e.setSeverity(sev);
        e.setOperatorName(operatorName);
        e.setOperatorId(operatorId);
        e.setValidationHash(hash);
        return e;
    }
}
