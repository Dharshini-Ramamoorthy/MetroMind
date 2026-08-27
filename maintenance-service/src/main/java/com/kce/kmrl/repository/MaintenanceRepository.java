package com.kce.kmrl.repository;

import com.kce.kmrl.entity.Maintenance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRepository extends MongoRepository<Maintenance, String> {
    List<Maintenance> findByTrainNumber(String trainNumber);
}
