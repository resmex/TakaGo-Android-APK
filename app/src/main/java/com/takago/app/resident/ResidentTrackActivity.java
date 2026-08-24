package com.takago.app.resident;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.takago.app.R;
import com.takago.app.common.InsetsUtils;
import com.takago.app.common.LocationTextStyle;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.common.PickupStatusUi;
import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.RouteStopRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.location.LatLngPoint;
import com.takago.app.location.ReadableLocationManager;
import com.takago.app.location.MapMarkerFactory;
import com.takago.app.location.RoutingService;
import com.takago.app.location.map.MapEngine;
import com.takago.app.location.map.MapManager;
import com.takago.app.network.ServerSyncManager;

import java.util.Arrays;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.IOException;

public class ResidentTrackActivity extends AppCompatActivity {
    private static final String TAG = "ResidentTracking";
    private static final long LOCATION_REFRESH_MS = 2_500L;
    private static final double MIN_REROUTE_DISTANCE_KM = 0.075;
    private static final int MAX_AUTOMATIC_ROUTE_FAILURES = 3;

    private enum TrackingState {
        LOADING, ROUTE_AVAILABLE, ROUTE_UNAVAILABLE, NO_DRIVER_LOCATION,
        NO_PICKUP_LOCATION, OFFLINE
    }

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private MapManager mapManager;
    private MapEngine mapEngine;
    private TextView tvMapNotice;
    private TextView tvArrivalTime;
    private TextView tvTrackDistance;
    private TextView btnRetryRoute;
    private TextView tvTrackPickupLocation;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PickupRow activePickup;
    private int activeDriverId = -1;
    private double pickupLat = Double.NaN;
    private double pickupLng = Double.NaN;
    private double lastKnownDriverLat = Double.NaN;
    private double lastKnownDriverLng = Double.NaN;
    private double residentLat = Double.NaN;
    private double residentLng = Double.NaN;
    private double lastRoutedDriverLat = Double.NaN;
    private double lastRoutedDriverLng = Double.NaN;
    private List<LatLngPoint> lastRoutePoints;
    private boolean routeRequestInFlight;
    private int automaticRouteFailures;
    private int renderedStopCount;
    private double lastAddressDriverLat = Double.NaN, lastAddressDriverLng = Double.NaN;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshTrackingData(false);
            ServerSyncManager.syncTracking(ResidentTrackActivity.this,
                    () -> refreshTrackingData(false));
            mainHandler.postDelayed(this, LOCATION_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_track);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        tvMapNotice = findViewById(R.id.tvMapNotice);
        configureCompactNotice();
        tvArrivalTime = findViewById(R.id.tvArrivalTime);
        tvTrackDistance = findViewById(R.id.tvTrackDistance);
        btnRetryRoute = findViewById(R.id.btnRetryRoute);
        tvTrackPickupLocation = findViewById(R.id.tvTrackPickupLocation);
        clearTrackingValues();
        FrameLayout mapContainer = findViewById(R.id.mapContainer);
        mapManager = new MapManager(this, mapContainer, this::onMapReady);
        mapManager.create();

        InsetsUtils.applyStatusBarTopMargin(findViewById(R.id.btnBack));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRecenter).setOnClickListener(v -> fitCamera());
        btnRetryRoute.setOnClickListener(v -> retryRoute());

        loadLatestPickup();
    }

    private void onMapReady(MapEngine engine) {
        mapEngine = engine;
        engine.setNoticeListener(this::showNotice);
        refreshTrackingData(false);
    }

    /** Reloads the authoritative pickup, assignment, status and coordinates from SQLite. */
    private void loadLatestPickup() {
        activePickup = dbHelper.getActivePickupForResident(session.getUserId());
        UserAccount resident = dbHelper.getUserById(session.getUserId());
        residentLat = resident != null ? resident.latitude : Double.NaN;
        residentLng = resident != null ? resident.longitude : Double.NaN;
        activeDriverId = activePickup != null ? activePickup.driverId : -1;
        pickupLat = activePickup != null ? activePickup.latitude : Double.NaN;
        pickupLng = activePickup != null ? activePickup.longitude : Double.NaN;
        if (activePickup == null) tvTrackPickupLocation.setText("Driver location is not available yet");
        showHomeCardEtaDistance();
    }

    private void refreshTrackingData(boolean manualRetry) {
        loadLatestPickup();
        if (mapEngine == null) return;
        renderResidentLocation();

        if (!RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            mapEngine.removeMarker("pickup");
            mapEngine.clearRoute();
            setState(TrackingState.NO_PICKUP_LOCATION);
            if (RoutingService.isValidCoordinate(residentLat, residentLng))
                mapEngine.setCenter(new LatLngPoint(residentLat, residentLng), 16f);
            return;
        }

        LatLngPoint pickup = new LatLngPoint(pickupLat, pickupLng);
        mapEngine.setCenter(pickup, 15f);
        if (activePickup != null && activePickup.groupId > 0) {
            mapEngine.removeMarker("pickup");
            renderGroupedStops();
        } else {
            clearGroupedStopMarkers();
            mapEngine.removeMarker("pickup");
        }

        if (activePickup != null && "arrived".equals(PickupStatusUi.normalize(activePickup.status))) {
            mapEngine.setCenter(pickup, 15f);
            mapEngine.clearRoute();
            tvArrivalTime.setText("Driver Arrived");
            tvTrackDistance.setText("At pickup location");
            setState(TrackingState.ROUTE_AVAILABLE);
            return;
        }
        if (activePickup == null || !isTrackableStatus(activePickup.status)) {
            mapEngine.setCenter(pickup, 15f);
            mapEngine.removeMarker("driver");
            mapEngine.clearRoute();
            setState(TrackingState.NO_DRIVER_LOCATION);
            return;
        }

        UserAccount driver = activeDriverId > 0 ? dbHelper.getUserById(activeDriverId) : null;
        if (driver == null || !RoutingService.isValidCoordinate(driver.latitude, driver.longitude)) {
            mapEngine.removeMarker("driver");
            mapEngine.clearRoute();
            setState(TrackingState.NO_DRIVER_LOCATION);
            return;
        }

        Log.i(TAG, "Latest DB driver coordinates: lat=" + driver.latitude + ", lng=" + driver.longitude);
        Log.i(TAG, "Latest DB pickup coordinates: lat=" + pickupLat + ", lng=" + pickupLng);
        lastKnownDriverLat = driver.latitude;
        lastKnownDriverLng = driver.longitude;
        updateDriverAddress(driver);
        mapEngine.updateMovingMarker("driver", new LatLngPoint(driver.latitude, driver.longitude),
                MapMarkerFactory.driverTruck(this), true);
        fitCamera();

        if (RoutingService.areSameLocation(driver.latitude, driver.longitude, pickupLat, pickupLng)) {
            mapEngine.clearRoute();
            tvArrivalTime.setText("Driver has arrived");
            tvTrackDistance.setText("At pickup location");
            setState(TrackingState.ROUTE_AVAILABLE);
            return;
        }
        if (!isOnline()) {
            setState(TrackingState.OFFLINE);
            return;
        }

        boolean firstRoute = Double.isNaN(lastRoutedDriverLat);
        double movedKm = firstRoute ? Double.MAX_VALUE : RoutingService.haversineKm(
                lastRoutedDriverLat, lastRoutedDriverLng, driver.latitude, driver.longitude);
        if (manualRetry || firstRoute || movedKm >= MIN_REROUTE_DISTANCE_KM) {
            requestRoute(driver.latitude, driver.longitude);
        }
    }

    private void requestRoute(double driverLat, double driverLng) {
        if (routeRequestInFlight) return;
        if (!RoutingService.isValidCoordinate(driverLat, driverLng)
                || !RoutingService.isValidCoordinate(pickupLat, pickupLng)
                || RoutingService.areSameLocation(driverLat, driverLng, pickupLat, pickupLng)) {
            setState(TrackingState.ROUTE_UNAVAILABLE);
            return;
        }
        double nearbyKm = RoutingService.haversineKm(driverLat, driverLng, pickupLat, pickupLng);
        if (nearbyKm <= 0.30) {
            lastRoutePoints = Arrays.asList(new LatLngPoint(driverLat, driverLng), new LatLngPoint(pickupLat, pickupLng));
            mapEngine.setRoute(lastRoutePoints, 0xFF123A63, 0xFFFFFFFF); fitCamera();
            tvArrivalTime.setText(nearbyKm <= 0.10 ? "Arriving now" : "1 min");
            tvTrackDistance.setText(Math.round(nearbyKm * 1000) + " m away");
            setState(TrackingState.ROUTE_AVAILABLE); return;
        }

        routeRequestInFlight = true;
        lastRoutedDriverLat = driverLat;
        lastRoutedDriverLng = driverLng;
        setState(TrackingState.LOADING);
        List<LatLngPoint> waypoints = routeWaypoints(driverLat, driverLng);
        RoutingService.fetchRoute(mainHandler, waypoints, false,
                new RoutingService.RouteCallback() {
                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes) {
                        routeRequestInFlight = false;
                        automaticRouteFailures = 0;
                        lastRoutePoints = points;
                        mapEngine.setRoute(points, 0xFF123A63, 0xFFFFFFFF);
                        fitCamera();
                        tvArrivalTime.setText(etaMinutes + " min");
                        tvTrackDistance.setText(String.format(Locale.US, "%.1f km away", distanceKm));
                        setState(TrackingState.ROUTE_AVAILABLE);
                    }

                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                                        String encodedPolyline, int distanceMeters, int durationSeconds) {
                        if (activePickup != null) dbHelper.savePickupRoute(activePickup.id,
                                encodedPolyline, distanceMeters, durationSeconds);
                        onRoute(points, distanceKm, etaMinutes);
                    }

                    @Override
                    public void onRouteFailed(String ignoredTechnicalMessage) {
                        routeRequestInFlight = false;
                        automaticRouteFailures++;
                        lastRoutedDriverLat = Double.NaN;
                        lastRoutedDriverLng = Double.NaN;
                        if (lastRoutePoints == null || lastRoutePoints.isEmpty()) {
                            lastRoutePoints = Arrays.asList(
                                    new LatLngPoint(lastKnownDriverLat, lastKnownDriverLng),
                                    new LatLngPoint(pickupLat, pickupLng));
                            mapEngine.setRoute(lastRoutePoints, 0xFF123A63, 0xFFFFFFFF);
                        }
                        if (!showHomeCardEtaDistance()) {
                            double directKm = RoutingService.haversineKm(lastKnownDriverLat,
                                    lastKnownDriverLng, pickupLat, pickupLng);
                            tvArrivalTime.setText(Math.max(1, (int) Math.ceil(directKm / 25d * 60d)) + " min est.");
                            tvTrackDistance.setText(directKm < 1d
                                    ? Math.round(directKm * 1000d) + " m away"
                                    : String.format(Locale.US, "%.1f km direct", directKm));
                        }
                        fitCamera();
                        showNotice(isOnline() ? "Direct route shown; retrying the road route automatically."
                                : "Offline route shown; reconnect for live road routing.");
                        showRetry();
                    }
                });
    }

    private static boolean isTrackableStatus(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.US).replace(' ', '_');
        return normalized.equals("assigned") || normalized.equals("accepted")
                || normalized.equals("on_the_way");
    }

    private void retryRoute() {
        if (routeRequestInFlight) return;
        if (!isOnline()) {
            setState(TrackingState.OFFLINE);
            return;
        }
        btnRetryRoute.setEnabled(false);
        refreshTrackingData(true);
    }

    private void setState(TrackingState state) {
        btnRetryRoute.setEnabled(!routeRequestInFlight);
        switch (state) {
            case LOADING:
                if (!showHomeCardEtaDistance()) tvArrivalTime.setText("Updating route...");
                showNotice("Updating road route...");
                btnRetryRoute.setVisibility(View.GONE);
                break;
            case ROUTE_AVAILABLE:
                hideNotice();
                btnRetryRoute.setVisibility(View.GONE);
                break;
            case ROUTE_UNAVAILABLE:
                showHomeCardEtaDistance();
                showNotice("Road route is temporarily unavailable.");
                hideNoticeAfterDelay("Road route is temporarily unavailable.");
                showRetry();
                break;
            case NO_DRIVER_LOCATION:
                showHomeCardEtaDistance();
                showNotice("Driver location will update automatically.");
                btnRetryRoute.setVisibility(View.GONE);
                break;
            case NO_PICKUP_LOCATION:
                clearTrackingValues();
                showNotice("Pickup location is unavailable.");
                btnRetryRoute.setVisibility(View.GONE);
                break;
            case OFFLINE:
                showHomeCardEtaDistance();
                showNotice("Offline map shown. Live routing requires internet.");
                showRetry();
                break;
        }
    }

    /** Uses exactly the same authoritative route cache and formatting source as Resident Home. */
    private boolean showHomeCardEtaDistance() {
        if (activePickup == null) return false;
        double distanceKm = activePickup.routeDistanceMeters > 0
                ? activePickup.routeDistanceMeters / 1000d : activePickup.distanceKm;
        int eta = activePickup.routeDurationSeconds > 0
                ? Math.max(1, activePickup.routeDurationSeconds / 60) : activePickup.etaMin;
        if (distanceKm <= 0 && eta <= 0) return false;
        tvTrackDistance.setText(distanceKm > 0
                ? String.format(Locale.US, "%.1f km away", distanceKm) : "Distance pending");
        tvArrivalTime.setText(eta > 0 ? eta + " min" : "ETA pending");
        return true;
    }

    private void showRetry() {
        btnRetryRoute.setText("Retry route");
        btnRetryRoute.setVisibility(View.VISIBLE);
        btnRetryRoute.setEnabled(!routeRequestInFlight);
    }

    private void clearTrackingValues() {
        if (tvArrivalTime != null) tvArrivalTime.setText("");
        if (tvTrackDistance != null) tvTrackDistance.setText("");
    }

    private void showNotice(String message) {
        tvMapNotice.setText(message);
        tvMapNotice.setVisibility(View.VISIBLE);
    }

    private void hideNotice() {
        tvMapNotice.setVisibility(View.GONE);
    }

    private void hideNoticeAfterDelay(String message) {
        mainHandler.postDelayed(() -> {
            if (message.contentEquals(tvMapNotice.getText())) hideNotice();
        }, 5_000L);
    }

    private void configureCompactNotice() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = 112;
        tvMapNotice.setLayoutParams(params);
        tvMapNotice.setTextColor(Color.DKGRAY);
        tvMapNotice.setPadding(24, 12, 24, 12);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEEFFFFFF);
        background.setCornerRadius(24f);
        tvMapNotice.setBackground(background);
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network != null ? manager.getNetworkCapabilities(network) : null;
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void fitCamera() {
        if (mapEngine == null) return;
        if (lastRoutePoints != null && !lastRoutePoints.isEmpty()) {
            mapEngine.zoomToBounds(lastRoutePoints, 140);
        } else if (RoutingService.isValidCoordinate(lastKnownDriverLat, lastKnownDriverLng)
                && RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            mapEngine.zoomToBounds(Arrays.asList(
                    new LatLngPoint(lastKnownDriverLat, lastKnownDriverLng),
                    new LatLngPoint(pickupLat, pickupLng)), 120);
        } else if (RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            mapEngine.setCenter(new LatLngPoint(pickupLat, pickupLng), 15f);
        } else if (RoutingService.isValidCoordinate(residentLat, residentLng)) {
            mapEngine.setCenter(new LatLngPoint(residentLat, residentLng), 16f);
        }
    }

    private void renderResidentLocation() {
        if (mapEngine == null) return;
        if (!RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            mapEngine.removeMarker("resident");
            return;
        }
        mapEngine.addOrUpdateMarker("resident", new LatLngPoint(pickupLat, pickupLng),
                MapMarkerFactory.residentWaste(this), 0.5f, 0.5f, false);
    }

    private void updateDriverAddress(UserAccount driver) {
        String cached = ReadableLocationManager.primary(driver);
        if (!cached.isEmpty() && !cached.equalsIgnoreCase(ReadableLocationManager.wardLine(driver))) {
            tvTrackPickupLocation.setText("Driver is at: " + cached);
        } else {
            tvTrackPickupLocation.setText(String.format(Locale.US,
                    "Driver is at: %.5f, %.5f", driver.latitude, driver.longitude));
        }
        if (!Double.isNaN(lastAddressDriverLat)
                && RoutingService.haversineKm(lastAddressDriverLat, lastAddressDriverLng,
                driver.latitude, driver.longitude) < MIN_REROUTE_DISTANCE_KM) return;
        lastAddressDriverLat = driver.latitude;
        lastAddressDriverLng = driver.longitude;
        final double lat = driver.latitude, lng = driver.longitude;
        new Thread(() -> {
            String label = null;
            try {
                List<Address> rows = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lng, 1);
                if (rows != null && !rows.isEmpty()) {
                    Address address = rows.get(0);
                    label = address.getAddressLine(0);
                    if (label == null || label.trim().isEmpty()) label = address.getFeatureName();
                }
            } catch (IOException | IllegalArgumentException ignored) { }
            final String resolved = label;
            runOnUiThread(() -> {
                if (Math.abs(lastKnownDriverLat - lat) > 0.00001
                        || Math.abs(lastKnownDriverLng - lng) > 0.00001) return;
                if (resolved != null && !resolved.trim().isEmpty())
                    tvTrackPickupLocation.setText("Driver is at: " + resolved.trim());
            });
        }).start();
    }

    private void renderGroupedStops() {
        clearGroupedStopMarkers();
        if (activePickup == null || activePickup.groupId <= 0) return;
        List<RouteStopRow> stops = dbHelper.getRouteStops(activePickup.groupId);
        int lastStopOrder = lastStopOrder(stops);
        for (RouteStopRow stop : stops) {
            if (!RoutingService.isValidCoordinate(stop.latitude, stop.longitude)) continue;
            if (stop.pickupId == activePickup.id) continue;
            boolean completed = "Completed".equalsIgnoreCase(stop.status);
            boolean isFinal = stop.stopOrder == lastStopOrder;
            String markerId = "route_stop_" + renderedStopCount++;
            mapEngine.addOrUpdateMarker(markerId, new LatLngPoint(stop.latitude, stop.longitude),
                    isFinal ? MapMarkerFactory.finalStop(this, stop.stopOrder, completed)
                            : MapMarkerFactory.intermediateStop(this, stop.stopOrder, completed),
                    0.5f, isFinal && !completed ? 1.0f : 0.5f, false);
        }
    }

    private void clearGroupedStopMarkers() {
        for (int i = 0; i < renderedStopCount; i++) mapEngine.removeMarker("route_stop_" + i);
        renderedStopCount = 0;
    }

    private List<LatLngPoint> routeWaypoints(double driverLat, double driverLng) {
        List<LatLngPoint> waypoints = new java.util.ArrayList<>();
        waypoints.add(new LatLngPoint(driverLat, driverLng));
        if (activePickup == null || activePickup.groupId <= 0) {
            waypoints.add(new LatLngPoint(pickupLat, pickupLng));
            return waypoints;
        }
        List<RouteStopRow> stops = dbHelper.getRouteStops(activePickup.groupId);
        for (RouteStopRow stop : stops) {
            if ("Completed".equalsIgnoreCase(stop.status)
                    || !RoutingService.isValidCoordinate(stop.latitude, stop.longitude)) continue;
            waypoints.add(new LatLngPoint(stop.latitude, stop.longitude));
        }
        if (waypoints.size() == 1) waypoints.add(new LatLngPoint(pickupLat, pickupLng));
        return waypoints;
    }

    private int lastStopOrder(List<RouteStopRow> stops) {
        int last = 0;
        for (RouteStopRow stop : stops) {
            if (RoutingService.isValidCoordinate(stop.latitude, stop.longitude)) {
                last = Math.max(last, stop.stopOrder);
            }
        }
        return last;
    }

    private static boolean isFreshDriverLocation(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty()) return false;
        try {
            Date updated = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(timestamp);
            return updated != null && System.currentTimeMillis() - updated.getTime() <= 120_000L;
        } catch (java.text.ParseException ignored) {
            return false;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        mapManager.onResume();
        ServerSyncManager.syncAll(this);
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.post(refreshRunnable);
    }

    @Override protected void onPause() {
        mainHandler.removeCallbacks(refreshRunnable);
        mapManager.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        RoutingService.cancelPendingRequests();
        mainHandler.removeCallbacksAndMessages(null);
        mapManager.onDestroy();
        super.onDestroy();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        mapManager.onLowMemory();
    }
}
