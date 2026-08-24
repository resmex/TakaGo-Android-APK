package com.takago.app.common;

import java.util.Locale;

/** One UI interpretation of the strict pickup workflow defined by the Laravel backend. */
public final class PickupStatusUi {
    public enum DriverAction { NONE, ACCEPT, START_TRIP, MARK_ARRIVED, START_COLLECTION, RECORD_WEIGHT, FINISH }
    public enum ResidentAction { NONE, FIND_DRIVER, TRACK, REVIEW_COLLECTION, PAY, RECEIPT }

    private PickupStatusUi() { }

    public static String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.US).replace(' ', '_');
    }

    public static String display(String status) {
        String value = normalize(status);
        if (value.isEmpty()) return "Status Updating";
        StringBuilder label = new StringBuilder();
        for (String word : value.split("_")) {
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    public static DriverAction driverAction(String status) {
        switch (normalize(status)) {
            case "assigned": return DriverAction.ACCEPT;
            case "accepted": return DriverAction.START_TRIP;
            case "on_the_way": return DriverAction.MARK_ARRIVED;
            case "arrived": return DriverAction.START_COLLECTION;
            case "collecting": return DriverAction.RECORD_WEIGHT;
            default: return DriverAction.NONE;
        }
    }

    public static String driverLabel(String status) {
        switch (normalize(status)) {
            case "assigned": return "Accept Pickup";
            case "accepted": return "Start Trip";
            case "on_the_way": return "Mark as Arrived";
            case "arrived": return "Start Collection";
            case "collecting": return "Record Weight";
            case "weight_recorded": case "resident_confirmation": return "Waiting for Resident Confirmation";
            case "price_confirmed": case "payment_pending": return "Waiting for Payment";
            case "paid": return "Payment Completed";
            case "completed": return "Collection Completed";
            case "cancelled": return "Pickup Cancelled";
            case "rejected": return "Pickup Rejected";
            case "pending": return "Waiting for Assignment";
            default: return "Status Updating";
        }
    }

    public static ResidentAction residentAction(String status) {
        switch (normalize(status)) {
            case "pending": return ResidentAction.FIND_DRIVER;
            case "assigned": case "accepted": case "on_the_way": return ResidentAction.TRACK;
            case "weight_recorded": return ResidentAction.REVIEW_COLLECTION;
            case "price_confirmed": case "payment_pending": return ResidentAction.PAY;
            case "completed": return ResidentAction.RECEIPT;
            default: return ResidentAction.NONE;
        }
    }

    public static String residentLabel(String status) {
        switch (normalize(status)) {
            case "pending": return "Waiting for Driver";
            case "assigned": return "Driver Assigned";
            case "accepted": return "Driver Preparing";
            case "on_the_way": return "Track Driver";
            case "arrived": return "Driver Arrived";
            case "collecting": return "Collection in Progress";
            case "weight_recorded": return "Review Collection";
            case "resident_confirmation": return "Collection Confirmed";
            case "price_confirmed": return "Choose Payment Method";
            case "payment_pending": return "Complete Payment";
            case "paid": return "Payment Completed";
            case "completed": return "View Receipt";
            case "cancelled": return "Pickup Cancelled";
            case "rejected": return "Pickup Rejected";
            default: return "Status Updating";
        }
    }
}
