package com.kce.kmrl.approver.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.kce.kmrl.approver.entity.ApprovalStatus;
import com.kce.kmrl.approver.entity.ApprovalTask;

import java.util.List;

@Repository
public interface ApprovalTaskRepository extends MongoRepository<ApprovalTask, String> {
    List<ApprovalTask> findByStatus(ApprovalStatus status);
    long countByStatus(ApprovalStatus status);
}