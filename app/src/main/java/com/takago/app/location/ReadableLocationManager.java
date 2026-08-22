package com.takago.app.location;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.WardRow;
import com.takago.app.db.DatabaseHelper;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Resolves and caches the two-line location used in normal UI. */
public final class ReadableLocationManager {
    private static final double REGEOCODE_DISTANCE_KM = 0.10;
    private static final long CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1000L;

    public interface Callback { void onLocation(String primary, String wardLine); }

    private ReadableLocationManager() { }

    public static void refresh(Activity activity, DatabaseHelper db, int userId, Callback callback) {
        UserAccount saved = db.getUserById(userId);
        callback.onLocation(primary(saved), wardLine(saved));
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            LocationServices.getFusedLocationProviderClient(activity)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) return;
                        double lat = location.getLatitude(), lng = location.getLongitude();
                        if (hasReadableCache(saved)
                                && RoutingService.haversineKm(saved.latitude, saved.longitude, lat, lng)
                                < REGEOCODE_DISTANCE_KM && isFresh(saved.lastLocationUpdatedAt)) {
                            return;
                        }
                        new Thread(() -> geocode(activity, db, userId, lat, lng, saved, callback)).start();
                    });
        } catch (SecurityException ignored) { }
    }

    private static void geocode(Activity activity, DatabaseHelper db, int userId, double lat,
                                double lng, UserAccount saved, Callback callback) {
        Address address = null;
        try {
            List<Address> results = new Geocoder(activity, Locale.getDefault())
                    .getFromLocation(lat, lng, 1);
            if (results != null && !results.isEmpty()) {
                address = results.get(0);
            }
        } catch (IOException | IllegalArgumentException ignored) { }

        WardRow detected = db.findMappedWardContaining(lat, lng);
        String locationWard = detected != null ? detected.name
                : first(saved.locationWardName, saved.ward);
        Address finalAddress = address;
        activity.runOnUiThread(() -> NearbyPoiResolver.resolve(activity, lat, lng, poiName ->
                new Thread(() -> {
                    ReadableAddress readable = ReadableAddress.from(finalAddress, locationWard, poiName);
                    db.saveReadableUserLocation(userId, lat, lng, readable.houseNumber, readable.streetName,
                            first(readable.placeName, readable.neighbourhood), readable.formattedAddress,
                            readable.plusCode, locationWard);
                    UserAccount updated = db.getUserById(userId);
                    activity.runOnUiThread(() -> callback.onLocation(primary(updated), wardLine(updated)));
                }).start()));
    }

    public static String primary(UserAccount user) {
        if (user == null) return "";
        return ReadableAddress.cachedLabel(user.houseNumber, user.streetName, user.placeName,
                user.formattedAddress, user.plusCode, first(user.locationWardName, user.ward));
    }

    public static String wardLine(UserAccount user) {
        String ward = user == null ? "" : first(user.locationWardName, user.ward);
        return ward.isEmpty() ? "Ward unavailable" : ward.replaceAll("(?i)\\s+Ward$", "") + " Ward";
    }

    private static boolean hasReadableCache(UserAccount user) {
        return user != null && (!clean(user.houseNumber).isEmpty() || !clean(user.streetName).isEmpty()
                || !clean(user.placeName).isEmpty() || !clean(user.formattedAddress).isEmpty());
    }

    private static boolean isFresh(String value) {
        if (value == null) return false;
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value);
            return date != null && System.currentTimeMillis() - date.getTime() < CACHE_MAX_AGE_MS;
        } catch (ParseException ignored) { return false; }
    }

    private static String firstUsefulFormatted(String value, String ward, String detectedWard) {
        String text = clean(value);
        if (text.isEmpty() || isPlusCode(text)) return "";
        for (String part : text.split(",")) {
            String candidate = clean(part);
            if (!candidate.isEmpty() && !candidate.equalsIgnoreCase(clean(ward))
                    && !candidate.equalsIgnoreCase(clean(detectedWard))
                    && !candidate.equalsIgnoreCase("Dar es Salaam")
                    && !candidate.equalsIgnoreCase("Tanzania")) return candidate;
        }
        return "";
    }

    private static String first(String one, String two) {
        String value = clean(one); return value.isEmpty() ? clean(two) : value;
    }
    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
    private static boolean isPlusCode(String value) {
        return value != null && value.matches("(?i).*\\b[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}\\b.*");
    }
}
