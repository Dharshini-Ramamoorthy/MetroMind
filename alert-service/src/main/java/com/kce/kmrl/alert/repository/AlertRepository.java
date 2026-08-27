package com.kce.kmrl.alert.repository;

import com.kce.kmrl.alert.entity.Alert;
import com.kce.kmrl.alert.entity.Severity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends MongoRepository<Alert, String> {

    List<Alert> findBySeverity(Severity severity);

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByIdStartingWith(String prefix);
}
