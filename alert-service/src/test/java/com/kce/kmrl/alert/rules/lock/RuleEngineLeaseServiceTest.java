package com.kce.kmrl.alert.rules.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/alert_test_db",
    "eureka.client.enabled=false",
    "eureka.client.register-with-eureka=false",
    "eureka.client.fetch-registry=false"
})
class RuleEngineLeaseServiceTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RuleEngineLeaseService leaseService;

    @BeforeEach
    void cleanUp() {
        mongoTemplate.dropCollection(RuleEngineLease.class);
    }

    @Test
    void shouldAllowSameInstanceToReacquireLeaseAcrossConsecutiveCallsWithinLeaseWindow() {
        String leaseName = "alert-rule-engine-lock";
        Duration duration = Duration.ofMinutes(2);

        // First call acquires the lease
        boolean firstAcquire = leaseService.tryAcquire(leaseName, duration);
        assertTrue(firstAcquire, "First attempt should acquire the lease");

        // Second call by the same instance extends/renews the lease
        boolean secondAcquire = leaseService.tryAcquire(leaseName, duration);
        assertTrue(secondAcquire, "Same instance should successfully re-acquire/extend the lease within window");

        // A different instance should fail to acquire because the lease is active and owned by leaseService
        RuleEngineLeaseService anotherInstance = new RuleEngineLeaseService(mongoTemplate);
        boolean anotherAcquire = anotherInstance.tryAcquire(leaseName, duration);
        assertFalse(anotherAcquire, "Another instance should not acquire active lease owned by different instance");
    }
}
