package com.kce.kmrl.repository;

import com.kce.kmrl.entity.ApprovalHistory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Stores approval decision history in the "approval_history" collection.
 */
@Repository
public interface ApprovalHistoryRepository extends MongoRepository<ApprovalHistory, String> {

    List<ApprovalHistory> findAll(Sort sort);

    long countByDecision(String decision);

    List<ApprovalHistory> findByTicketId(String ticketId);
}
