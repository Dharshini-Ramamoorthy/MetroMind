package com.kce.kmrl.fleet.repository;

import com.kce.kmrl.fleet.model.AuditLedgerEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLedgerRepository extends MongoRepository<AuditLedgerEntry, String> {
    List<AuditLedgerEntry> findAllByOrderByIdDesc();
}
