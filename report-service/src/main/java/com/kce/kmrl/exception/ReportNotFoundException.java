package com.kce.kmrl.exception;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String id) {
        super("No report found with id: " + id);
    }
}
