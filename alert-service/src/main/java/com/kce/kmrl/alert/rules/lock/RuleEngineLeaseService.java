package com.kce.kmrl.alert.rules.lock;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class RuleEngineLeaseService {

    private final MongoTemplate mongoTemplate;
    private final String owner = UUID.randomUUID().toString();

    public RuleEngineLeaseService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean tryAcquire(String name, Duration leaseDuration) {
        Instant now = Instant.now();
        Instant expires = now.plus(leaseDuration);

        Query expired = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(name),
                new Criteria().orOperator(
                        Criteria.where("expiresAt").lte(now),
                        Criteria.where("expiresAt").exists(false)
                )
        ));
        Update update = new Update().set("owner", owner).set("expiresAt", expires).set("name", name);

        if (mongoTemplate.updateFirst(expired, update, RuleEngineLease.class).getModifiedCount() > 0) {
            return true;
        }

        try {
            mongoTemplate.insert(new RuleEngineLease(name, owner, expires));
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
