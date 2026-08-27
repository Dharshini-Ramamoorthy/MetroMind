package com.kce.kmrl.exception;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String id) {
        super("No maintenance ticket found with id: " + id);
    }
}
