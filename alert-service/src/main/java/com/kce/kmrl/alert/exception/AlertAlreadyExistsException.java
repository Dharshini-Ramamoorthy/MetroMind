package com.kce.kmrl.alert.exception;

public class AlertAlreadyExistsException extends RuntimeException {

    public AlertAlreadyExistsException(String alertId) {
        super("Alert already exists: " + alertId);
    }
}
