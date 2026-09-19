package com.kce.kmrl.repository;

import com.kce.kmrl.entity.Report;
import com.kce.kmrl.entity.ReportCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {

    List<Report> findByCategory(ReportCategory category);

    @Query(value = "{}", fields = "{ 'pdfContent' : 0 }")
    List<Report> findAllWithoutPdf();

    @Query(value = "{ 'category' : ?0 }", fields = "{ 'pdfContent' : 0 }")
    List<Report> findByCategoryWithoutPdf(ReportCategory category);
}
