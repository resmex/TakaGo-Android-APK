package com.takago.app.data.model;

public class PriceResult {
    public boolean success;
    public String errorMessage;
    public boolean requiresManualApproval;
    public double measuredWeightKg;
    public double includedWeightKg;
    public double extraWeightKg;
    public double bookingFee;
    public double weightFee;
    public double distanceFee;
    public double finalPrice;
}
