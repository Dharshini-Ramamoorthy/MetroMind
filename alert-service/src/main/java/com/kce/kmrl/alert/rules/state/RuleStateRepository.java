package com.kce.kmrl.alert.rules.state;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleStateRepository extends MongoRepository<RuleState, String> {
}
