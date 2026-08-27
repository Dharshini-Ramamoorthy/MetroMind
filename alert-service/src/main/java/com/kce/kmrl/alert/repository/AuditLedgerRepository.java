package com.kce.kmrl.alert.repository;

import com.kce.kmrl.alert.entity.AuditLedgerEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLedgerRepository extends MongoRepository<AuditLedgerEntry, String> {

    List<AuditLedgerEntry> findAllByOrderByResolvedAtDesc();
}
