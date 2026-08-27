package com.kce.kmrl.alert.exception;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(String alertId) {
        super("Alert not found: " + alertId);
    }
}
