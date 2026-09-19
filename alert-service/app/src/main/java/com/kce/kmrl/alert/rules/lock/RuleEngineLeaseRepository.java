package com.kce.kmrl.alert.rules.lock;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleEngineLeaseRepository extends MongoRepository<RuleEngineLease, String> {
}
