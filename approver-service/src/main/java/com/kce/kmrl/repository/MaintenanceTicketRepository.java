package com.kce.kmrl.repository;

import com.kce.kmrl.entity.MaintenanceTicket;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads from the shared "maintenance" collection (same data maintenance-service
 * writes to). Provides queries to find tickets by status for the approval queue.
 */
@Repository
public interface MaintenanceTicketRepository extends MongoRepository<MaintenanceTicket, String> {

    List<MaintenanceTicket> findByStatus(String status);

    List<MaintenanceTicket> findByStatusIn(List<String> statuses);

    long countByStatus(String status);
}
