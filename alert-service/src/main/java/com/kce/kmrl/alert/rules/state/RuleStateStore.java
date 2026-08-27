package com.kce.kmrl.alert.rules.state;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RuleStateStore {

    private final RuleStateRepository repository;

    public RuleStateStore(RuleStateRepository repository) {
        this.repository = repository;
    }

    public Optional<String> get(String key) {
        return repository.findById(key).map(RuleState::getValue);
    }

    public void put(String key, String value) {
        repository.save(new RuleState(key, value));
    }

    public boolean seenBefore(String key) {
        return repository.existsById(key);
    }

    public boolean claimIfAbsent(String key) {
        try {
            repository.insert(new RuleState(key, "true"));
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public void markSeen(String key) {
        claimIfAbsent(key);
    }

    public void forget(String key) {
        repository.deleteById(key);
    }
}
