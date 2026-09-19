package com.kce.kmrl.alert.rules;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.Severity;
import com.kce.kmrl.alert.repository.AlertRepository;
import com.kce.kmrl.alert.repository.AuditLedgerRepository;
import com.kce.kmrl.alert.rules.state.RuleStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/alert_test_db",
    "eureka.client.enabled=false",
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false",
    "alert.suppression.rearm-minutes=20"
})
class AlertSuppressionRearmTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AuditLedgerRepository ledgerRepository;

    @Autowired
    private RuleStateStore stateStore;

    @Autowired
    private RuleAlertPublisher publisher;

    private static final String ALERT_ID = "ALT-HEALTH-KMRL-101";

    @BeforeEach
    void setup() {
        alertRepository.deleteById(ALERT_ID);
        stateStore.forget("suppressed:" + ALERT_ID);
    }

    @Test
    void shouldRearmAndRepublishWhenTimeAdvancesPastSuppressionTtl() {
        Instant start = Instant.parse("2026-09-18T10:00:00Z");
        ZoneId zone = ZoneId.of("UTC");
        Clock testClock = Clock.fixed(start, zone);
        publisher.setClock(testClock);

        // 1. Initial breach: publish health alert
        publishHealthAlert(ALERT_ID, 35);
        Optional<Alert> initial = alertRepository.findById(ALERT_ID);
        assertTrue(initial.isPresent(), "Health alert should be published initially");

        // 2. Operator clears the alert (simulating AlertServiceImpl.clearAlert)
        publisher.suppressLevel(ALERT_ID);
        alertRepository.deleteById(ALERT_ID);
        assertFalse(alertRepository.findById(ALERT_ID).isPresent(), "Alert should be cleared");

        // 3. Evaluator runs at t + 5 minutes with condition still breaching
        publisher.setClock(Clock.offset(testClock, Duration.ofMinutes(5)));
        publishHealthAlert(ALERT_ID, 32);
        assertFalse(alertRepository.findById(ALERT_ID).isPresent(),
                "Alert should remain suppressed within the 20-minute rearm window");

        // 4. Time advances past the suppression TTL (t + 25 minutes)
        publisher.setClock(Clock.offset(testClock, Duration.ofMinutes(25)));

        // 5. Evaluator runs again with condition still breaching
        publishHealthAlert(ALERT_ID, 30);
        Optional<Alert> republished = alertRepository.findById(ALERT_ID);
        assertTrue(republished.isPresent(),
                "Alert should re-arm and republish once time advances past the suppression TTL");
    }

    private void publishHealthAlert(String alertId, int healthIndex) {
        publisher.publishLevel(
                alertId,
                Severity.SEV3,
                "Health index critical — KMRL-101",
                "Fleet & Induction health monitor",
                "KMRL-101",
                List.of(field("Health Index", String.valueOf(healthIndex))),
                "Critical health index",
                List.of(healthIndex, healthIndex),
                List.of("MDS"),
                null,
                "KMRL-101"
        );
    }

    private Alert.AlertField field(String label, String value) {
        Alert.AlertField f = new Alert.AlertField();
        f.setLabel(label);
        f.setValue(value);
        return f;
    }
}

