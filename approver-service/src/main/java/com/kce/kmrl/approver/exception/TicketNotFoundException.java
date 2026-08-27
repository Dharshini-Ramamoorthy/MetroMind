package com.kce.kmrl.approver.exception;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String id) {
        super("No maintenance ticket found with id: " + id);
    }
}
