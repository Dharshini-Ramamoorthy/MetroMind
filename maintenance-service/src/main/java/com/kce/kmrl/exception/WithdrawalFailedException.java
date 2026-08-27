package com.kce.kmrl.exception;

public class WithdrawalFailedException extends RuntimeException {

    private final String ticketId;
    private final String trainNumber;

    public WithdrawalFailedException(String ticketId, String trainNumber, String cause) {
        super("Maintenance ticket " + ticketId + " was created for train " + trainNumber
                + " but schedule-service withdrawal failed (withdrawalStatus=WITHDRAWAL_PENDING). "
                + "The train has NOT been marked IN_MAINTENANCE. Cause: " + cause);
        this.ticketId = ticketId;
        this.trainNumber = trainNumber;
    }

    public String getTicketId() { return ticketId; }
    public String getTrainNumber() { return trainNumber; }
}
