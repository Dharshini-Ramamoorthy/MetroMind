package com.kce.kmrl.approver.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.kce.kmrl.approver.entity.ApprovalHistory;
import com.kce.kmrl.approver.entity.ApprovalStatus;

@Repository
public interface ApprovalHistoryRepository extends MongoRepository<ApprovalHistory, String> {
    long countByDecision(ApprovalStatus decision);
}