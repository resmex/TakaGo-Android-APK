package com.takago.app.data.model;

public class PickupRow {
    public int id;
    public String code, ward, category, status, pickupDate;
    public double weightKg, distanceKm;
    public String timeText, residentDisplayName;
    public int etaMin;
    public double latitude, longitude;
    public String address, houseNumber, streetName, placeName, formattedAddress, plusCode, photoPath, createdAt, completedAt;
    public int driverId;
    public String timeoutAt, driverResponseStatus, acceptedAt;
    public int assignedVehicleId;
    public int residentId;
    public int wardId, groupId, stopOrder;
    public String placeId, encodedPolyline, routeCalculatedAt;
    public int routeDistanceMeters, routeDurationSeconds;
    public String wasteType;
    public double estimatedPriceMin, estimatedPriceMax;
    public double measuredWeightKg, includedWeightKg, ratePerKg, distanceFee, wasteTypeMultiplier, finalPrice, bookingFee;
    public String scalePhotoPath, proofPhotoPath, pricingStatus, paymentStatus;
}
