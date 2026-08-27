package com.kce.kmrl.schedule.repository;

import com.kce.kmrl.schedule.model.ScheduleTrip;
import com.kce.kmrl.schedule.model.TripStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleTripRepository extends MongoRepository<ScheduleTrip, String> {

    List<ScheduleTrip> findByServiceDate(String serviceDate);

    boolean existsByServiceDate(String serviceDate);

    List<ScheduleTrip> findByServiceDateAndStatus(String serviceDate, TripStatus status);

    List<ScheduleTrip> findByServiceDateAndSeedGeneratedTrue(String serviceDate);
    boolean existsByServiceDateAndSeedGeneratedTrue(String serviceDate);

    Optional<ScheduleTrip> findByTripCode(String tripCode);

    List<ScheduleTrip> findByAssignedTrainIdAndStatus(String assignedTrainId, TripStatus status);

    List<ScheduleTrip> findByAssignedTrainIdAndStatusIn(String assignedTrainId,
                                                         List<TripStatus> statuses);
}
