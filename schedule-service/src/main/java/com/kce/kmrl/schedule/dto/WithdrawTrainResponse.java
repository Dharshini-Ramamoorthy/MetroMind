package com.kce.kmrl.schedule.dto;

public class WithdrawTrainResponse {

    private boolean hadActiveTrip;
    private int futureTripsAffected;
    private int futureTripsAwaitingReplacement;

    public WithdrawTrainResponse() {}

    public WithdrawTrainResponse(boolean hadActiveTrip) {
        this.hadActiveTrip = hadActiveTrip;
    }

    public WithdrawTrainResponse(boolean hadActiveTrip, int futureTripsAffected, int futureTripsAwaitingReplacement) {
        this.hadActiveTrip = hadActiveTrip;
        this.futureTripsAffected = futureTripsAffected;
        this.futureTripsAwaitingReplacement = futureTripsAwaitingReplacement;
    }

    public boolean isHadActiveTrip() { return hadActiveTrip; }
    public void setHadActiveTrip(boolean hadActiveTrip) { this.hadActiveTrip = hadActiveTrip; }

    public int getFutureTripsAffected() { return futureTripsAffected; }
    public void setFutureTripsAffected(int futureTripsAffected) { this.futureTripsAffected = futureTripsAffected; }

    public int getFutureTripsAwaitingReplacement() { return futureTripsAwaitingReplacement; }
    public void setFutureTripsAwaitingReplacement(int futureTripsAwaitingReplacement) { this.futureTripsAwaitingReplacement = futureTripsAwaitingReplacement; }
}
