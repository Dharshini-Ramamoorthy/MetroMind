package com.kce.kmrl.fleet.repository;

import com.kce.kmrl.fleet.model.TrainAsset;
import com.kce.kmrl.fleet.model.TrainStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainAssetRepository extends MongoRepository<TrainAsset, String> {
    long countByStatus(TrainStatus status);
    List<TrainAsset> findByStatus(TrainStatus status);

    Optional<TrainAsset> findByTrainNumber(String trainNumber);
}
