package com.kce.kmrl.dto;

import jakarta.validation.constraints.NotBlank;

public class GenerateReportRequest {

    @NotBlank(message = "category is required")
    private String category;

    private String title;

    @NotBlank(message = "author is required")
    private String author;

    public GenerateReportRequest() {}

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
