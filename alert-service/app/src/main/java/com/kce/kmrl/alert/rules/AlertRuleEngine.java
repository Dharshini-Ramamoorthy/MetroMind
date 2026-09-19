package com.kce.kmrl.alert.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import com.kce.kmrl.alert.rules.lock.RuleEngineLeaseService;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleEngine.class);

    private final FleetRuleEvaluator fleetRuleEvaluator;
    private final ScheduleRuleEvaluator scheduleRuleEvaluator;
    private final MaintenanceRuleEvaluator maintenanceRuleEvaluator;
    private final ApproverRuleEvaluator approverRuleEvaluator;
    private final RuleEngineLeaseService leaseService;

    public AlertRuleEngine(FleetRuleEvaluator fleetRuleEvaluator,
                            ScheduleRuleEvaluator scheduleRuleEvaluator,
                            MaintenanceRuleEvaluator maintenanceRuleEvaluator,
                            ApproverRuleEvaluator approverRuleEvaluator,
                            RuleEngineLeaseService leaseService) {
        this.fleetRuleEvaluator = fleetRuleEvaluator;
        this.scheduleRuleEvaluator = scheduleRuleEvaluator;
        this.maintenanceRuleEvaluator = maintenanceRuleEvaluator;
        this.approverRuleEvaluator = approverRuleEvaluator;
        this.leaseService = leaseService;
    }

    @Scheduled(fixedDelayString = "${alert.poll.fixed-delay-ms:30000}",
               initialDelayString = "${alert.poll.initial-delay-ms:10000}")
    public void runAllRules() {
        if (!leaseService.tryAcquire("alert-rule-engine", Duration.ofMinutes(2))) {
            log.debug("Another Alert Service instance owns the rule-engine lease; skipping this cycle.");
            return;
        }
        runSafely("fleet", fleetRuleEvaluator::evaluate);
        runSafely("schedule", scheduleRuleEvaluator::evaluate);
        runSafely("maintenance", maintenanceRuleEvaluator::evaluate);
        runSafely("approver", approverRuleEvaluator::evaluate);
    }

    private void runSafely(String name, Runnable evaluator) {
        try {
            evaluator.run();
        } catch (Exception e) {

            log.error("Rule evaluation for '{}' failed unexpectedly this cycle: {}", name, e.getMessage(), e);
        }
    }
}
