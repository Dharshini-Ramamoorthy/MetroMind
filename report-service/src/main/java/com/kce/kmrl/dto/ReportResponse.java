package com.kce.kmrl.dto;

import java.time.Instant;

public class ReportResponse {

    private final String id;
    private final String title;
    private final String category;
    private final Instant generatedDate;
    private final String author;
    private final String fileSize;
    private final String status;
    private final String summary;
    private final String createdBy;
    private final Instant createdAt;

    public ReportResponse(String id, String title, String category, Instant generatedDate,
                          String author, String fileSize, String status, String summary,
                          String createdBy, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.generatedDate = generatedDate;
        this.author = author;
        this.fileSize = fileSize;
        this.status = status;
        this.summary = summary;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public Instant getGeneratedDate() { return generatedDate; }
    public String getAuthor() { return author; }
    public String getFileSize() { return fileSize; }
    public String getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
