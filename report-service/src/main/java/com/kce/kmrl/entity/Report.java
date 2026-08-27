package com.kce.kmrl.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "reports")
public class Report {

    @Id
    private String id;

    private String title;
    private ReportCategory category;
    private Instant generatedDate;
    private String author;
    private String fileSize;
    private ReportStatus status;
    private String summary;
    private String createdBy;
    private Instant createdAt;

    private byte[] pdfContent;

    private boolean liveGenerated;

    public Report() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ReportCategory getCategory() { return category; }
    public void setCategory(ReportCategory category) { this.category = category; }

    public Instant getGeneratedDate() { return generatedDate; }
    public void setGeneratedDate(Instant generatedDate) { this.generatedDate = generatedDate; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }

    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public byte[] getPdfContent() { return pdfContent; }
    public void setPdfContent(byte[] pdfContent) { this.pdfContent = pdfContent; }

    public boolean isLiveGenerated() { return liveGenerated; }
    public void setLiveGenerated(boolean liveGenerated) { this.liveGenerated = liveGenerated; }
}
