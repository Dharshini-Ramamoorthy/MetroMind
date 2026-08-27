package com.kce.kmrl.repository;

import com.kce.kmrl.entity.Report;
import com.kce.kmrl.entity.ReportCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {

    List<Report> findByCategory(ReportCategory category);
}
