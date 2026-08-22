package com.takago.app.driver;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.RouteStopRow;
import com.takago.app.common.InsetsUtils;
import com.takago.app.location.RoutingService;
import com.takago.app.location.LatLngPoint;
import com.takago.app.location.MapMarkerFactory;
import com.takago.app.location.map.MapEngine;
import com.takago.app.location.map.MapManager;
import com.takago.app.R;
import com.takago.app.*;
import com.takago.app.admin.*;
import com.takago.app.app.*;
import com.takago.app.auth.*;
import com.takago.app.common.*;
import com.takago.app.driver.*;
import com.takago.app.location.*;
import com.takago.app.notifications.*;
import com.takago.app.operator.*;
import com.takago.app.resident.*;
import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

/**
 * Shown once a driver accepts a pickup request: a live Google map
 * fallback offline) with the driver's own position (tracked via GPS and saved to SQLite so the
 * resident's Track screen can see it), the pickup location, a route line, and controls to
 * progress the trip's status.
 */
public class DriverNavigationActivity extends AppCompatActivity {

    private static final double MIN_REROUTE_DISTANCE_KM = 0.05;
    private static final long PERIODIC_REROUTE_MS = 30000;
    private static final long PERIODIC_CHECK_INTERVAL_MS = 5000;

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LocationManager locationManager;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean useFusedLocation;
    private MapManager mapController;
    private MapEngine mapEngine;
    private TextView tvMapNotice;

    private int tripId;
    private int tripResidentId;
    private String tripStatus;
    private double pickupLat;
    private double pickupLng;

    private List<LatLngPoint> lastRoutePoints;
    private double lastRoutedLat = Double.NaN;
    private double lastRoutedLng = Double.NaN;
    private long lastRouteFetchAtMs = 0;
    private double lastKnownLat = Double.NaN;
    private double lastKnownLng = Double.NaN;
    private boolean locationActive = false;
    private boolean hasFitCameraToRoute = false;
    private boolean routeRequestInFlight;
    private int automaticRouteFailures;
    private double lastSavedLat = Double.NaN;
    private double lastSavedLng = Double.NaN;
    private long lastLocationSavedAtMs;
    private PickupRow activeTrip;
    private boolean arrivalConfirmedInUi;
    private int renderedStopCount;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Guarantees a route refresh at least every ~25s even if the GPS provider stops delivering
    // fresh fixes while the driver is stationary (movement-triggered rerouting alone can't cover that).
    private final Runnable periodicRerouteCheck = new Runnable() {
        @Override
        public void run() {
            if (!Double.isNaN(lastKnownLat)) {
                handleDriverLocation(lastKnownLat, lastKnownLng);
            }
            mainHandler.postDelayed(this, PERIODIC_CHECK_INTERVAL_MS);
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            handleDriverLocation(location);
        }
    };

    private final LocationCallback fusedLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (location != null) {
                handleDriverLocation(location);
            }
        }
    };

    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startLocationUpdates();
                } else {
                    Toast.makeText(this,
                            "Location permission denied - your position won't be shared with the resident",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_navigation);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        tvMapNotice = findViewById(R.id.tvMapNotice);
        configureCompactNotice();

        useFusedLocation = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;
        if (useFusedLocation) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }

        tripId = getIntent().getIntExtra("tripId", -1);

        FrameLayout mapContainer = findViewById(R.id.mapContainer);
        mapController = new MapManager(this, mapContainer, this::onMapReady);
        mapController.create();

        InsetsUtils.applyStatusBarTopMargin(findViewById(R.id.btnBack));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCallResident).setOnClickListener(v -> callResident());
        findViewById(R.id.btnNavMarkCollected).setOnClickListener(v -> handleCompactPrimaryAction());
        findViewById(R.id.btnNavMarkCompleted).setOnClickListener(v -> markCompleted());
        findViewById(R.id.btnNavCancel).setOnClickListener(v -> promptCancelReason());
        findViewById(R.id.btnRecenter).setOnClickListener(v -> fitCameraToRoute());
        findViewById(R.id.btnRetryRoute).setOnClickListener(v -> retryRoute());
        findViewById(R.id.btnViewStops).setOnClickListener(v -> showRouteStops());
    }

    private void onMapReady(MapEngine engine) {
        this.mapEngine = engine;
        engine.setNoticeListener(this::showMapNotice);
        loadTrip();
        if (isTravelActive(tripStatus)) {
            ensureLocationTracking();
        }
    }

    private void showMapNotice(String message) {
        tvMapNotice.setText(message);
        tvMapNotice.setVisibility(android.view.View.VISIBLE);
    }

    private void configureCompactNotice() {
        tvMapNotice.setTextColor(Color.DKGRAY);
        tvMapNotice.setPadding(24, 12, 24, 12);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEEFFFFFF);
        background.setCornerRadius(24f);
        tvMapNotice.setBackground(background);
    }

    private void loadTrip() {
        PickupRow trip = dbHelper.getTripById(tripId);
        if (trip == null) {
            finish();
            return;
        }

        pickupLat = trip.latitude;
        activeTrip = trip;
        pickupLng = trip.longitude;
        tripStatus = trip.status;
        tripResidentId = trip.residentId;

        ((TextView) findViewById(R.id.tvNavPickupCode)).setText("Pickup " + trip.code);
        ((TextView) findViewById(R.id.tvNavPickupWard)).setText(PickupAddressFormatter.styledTwoLine(trip));
        ((TextView) findViewById(R.id.tvNavStatus)).setText(trip.status);
        bindGroupedStopSummary(trip);
        updateCompactActionForStoredStatus(trip);

        if (!RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            ((TextView) findViewById(R.id.tvNavEta)).setText("");
            ((TextView) findViewById(R.id.tvNavDistance)).setText("");
            Toast.makeText(this, "Pickup location not available.", Toast.LENGTH_LONG).show();
            return;
        }

        LatLngPoint pickupPoint = new LatLngPoint(pickupLat, pickupLng);
        mapEngine.setCenter(pickupPoint, 15f);
        if (trip.groupId > 0) {
            mapEngine.removeMarker("pickup");
            renderGroupedStopMarkers(trip);
        } else {
            clearGroupedStopMarkers();
            mapEngine.addOrUpdateMarker("pickup", pickupPoint, MapMarkerFactory.pickupPin(this), 0.5f, 1.0f, false);
        }
    }

    private void ensureLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        if (!isLocationEnabled()) {
            promptEnableLocation();
            return;
        }
        startLocationUpdates();
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void promptEnableLocation() {
        new AlertDialog.Builder(this)
                .setTitle("Turn on location")
                .setMessage("Your device's location is off. Turn it on so the resident can see your live position.")
                .setPositiveButton("Open settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void startLocationUpdates() {
        if (locationActive) {
            return;
        }
        try {
            if (useFusedLocation) {
                startFusedLocationUpdates();
            } else {
                startManagerLocationUpdates();
            }
            locationActive = true;
            mainHandler.postDelayed(periodicRerouteCheck, PERIODIC_CHECK_INTERVAL_MS);
        } catch (SecurityException e) {
            // Permission was revoked between the check and this call - nothing more we can do.
        }
    }

    private void startFusedLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateDistanceMeters(10)
                .build();
        fusedLocationClient.requestLocationUpdates(request, fusedLocationCallback, Looper.getMainLooper());
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                handleDriverLocation(location);
            }
        });
    }

    private void startManagerLocationUpdates() {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 10, locationListener);
        Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last != null) {
            handleDriverLocation(last);
        }
    }

    private void stopLocationUpdates() {
        if (!locationActive) {
            return;
        }
        try {
            if (useFusedLocation) {
                fusedLocationClient.removeLocationUpdates(fusedLocationCallback);
            } else {
                locationManager.removeUpdates(locationListener);
            }
        } catch (SecurityException e) {
            // Ignore - we're tearing down anyway.
        }
        mainHandler.removeCallbacks(periodicRerouteCheck);
        locationActive = false;
    }

    private void handleDriverLocation(double lat, double lng) {
        handleDriverLocationValues(lat, lng, 0f, 0f, 0f);
    }

    private void handleDriverLocation(Location location) {
        handleDriverLocationValues(location.getLatitude(), location.getLongitude(),
                location.hasBearing() ? location.getBearing() : 0f,
                location.hasSpeed() ? location.getSpeed() : 0f,
                location.hasAccuracy() ? location.getAccuracy() : 0f);
    }

    private void handleDriverLocationValues(double lat, double lng,
                                            float bearing, float speed, float accuracy) {
        if (!RoutingService.isValidCoordinate(lat, lng)) {
            return;
        }
        lastKnownLat = lat;
        lastKnownLng = lng;
        updateArrivalActionVisibility(lat, lng);

        int driverId = session.getUserId();
        double movedSinceSave = Double.isNaN(lastSavedLat) ? Double.MAX_VALUE
                : RoutingService.haversineKm(lastSavedLat, lastSavedLng, lat, lng);
        if (movedSinceSave >= 0.01 || System.currentTimeMillis() - lastLocationSavedAtMs >= 3_000L) {
            dbHelper.updateDriverLocation(driverId, lat, lng, bearing, speed, accuracy);
            com.takago.app.network.ServerSyncManager.pushDriverLocation(this, driverId, lat, lng);
            lastSavedLat = lat;
            lastSavedLng = lng;
            lastLocationSavedAtMs = System.currentTimeMillis();
        }

        if (mapEngine != null) {
            mapEngine.updateMovingMarker("driver", new LatLngPoint(lat, lng),
                    ContextCompat.getDrawable(this, R.drawable.ic_truck_outline), true);
        }

        boolean pickupAvailable = RoutingService.isValidCoordinate(pickupLat, pickupLng)
                && !RoutingService.areSameLocation(lat, lng, pickupLat, pickupLng);
        boolean firstFix = Double.isNaN(lastRoutedLat);
        double movedKm = firstFix ? Double.MAX_VALUE
                : RoutingService.haversineKm(lastRoutedLat, lastRoutedLng, lat, lng);
        boolean periodicDue = System.currentTimeMillis() - lastRouteFetchAtMs >= PERIODIC_REROUTE_MS;

        if (pickupAvailable && !routeRequestInFlight && automaticRouteFailures < 3
                && (firstFix || movedKm >= MIN_REROUTE_DISTANCE_KM || periodicDue)) {
            lastRoutedLat = lat;
            lastRoutedLng = lng;
            requestRoute(lat, lng);
        }
    }

    /** Re-attempts the last route request - wired to the "Retry route" button shown on failure. */
    private void retryRoute() {
        if (routeRequestInFlight) return;
        if (!isOnline()) {
            showMapNotice("Live routing requires an internet connection.");
            return;
        }
        loadTrip();
        if (RoutingService.isValidCoordinate(lastKnownLat, lastKnownLng)
                && RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            requestRoute(lastKnownLat, lastKnownLng);
        }
    }

    private void requestRoute(double fromLat, double fromLng) {
        if (routeRequestInFlight || !isOnline()
                || !RoutingService.isValidCoordinate(fromLat, fromLng)
                || !RoutingService.isValidCoordinate(pickupLat, pickupLng)
                || RoutingService.areSameLocation(fromLat, fromLng, pickupLat, pickupLng)) {
            return;
        }
        double nearbyKm = RoutingService.haversineKm(fromLat, fromLng, pickupLat, pickupLng);
        if (nearbyKm <= 0.30) {
            lastRoutePoints = java.util.Arrays.asList(new LatLngPoint(fromLat, fromLng), new LatLngPoint(pickupLat, pickupLng));
            drawRoute(lastRoutePoints);
            ((TextView) findViewById(R.id.tvNavEta)).setText(nearbyKm <= 0.10 ? "Arriving now" : "1 min");
            ((TextView) findViewById(R.id.tvNavDistance)).setText(nearbyKm < 1 ? Math.round(nearbyKm * 1000) + " m away" : String.format(Locale.US, "%.1f km away", nearbyKm));
            return;
        }
        routeRequestInFlight = true;
        lastRouteFetchAtMs = System.currentTimeMillis();
        ((TextView) findViewById(R.id.tvNavEta)).setText("Finding pickup route...");
        ((TextView) findViewById(R.id.tvNavDistance)).setText("");
        findViewById(R.id.btnRetryRoute).setEnabled(false);
        findViewById(R.id.btnRetryRoute).setVisibility(android.view.View.GONE);
        List<LatLngPoint> waypoints = routeWaypoints(fromLat, fromLng);
        RoutingService.fetchRoute(mainHandler, waypoints, false, new RoutingService.RouteCallback() {
            @Override
            public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes) {
                routeRequestInFlight = false;
                automaticRouteFailures = 0;
                findViewById(R.id.btnRetryRoute).setVisibility(android.view.View.GONE);
                tvMapNotice.setVisibility(android.view.View.GONE);
                drawRoute(points);
                ((TextView) findViewById(R.id.tvNavEta)).setText(etaMinutes + " min");
                ((TextView) findViewById(R.id.tvNavDistance)).setText(
                        String.format(Locale.US, "%.1f km away", distanceKm));
            }

            @Override
            public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                                String encodedPolyline, int distanceMeters, int durationSeconds) {
                dbHelper.savePickupRoute(tripId, encodedPolyline, distanceMeters, durationSeconds);
                onRoute(points, distanceKm, etaMinutes);
            }

            @Override
            public void onRouteFailed(String message) {
                routeRequestInFlight = false;
                automaticRouteFailures++;
                double directKm = RoutingService.haversineKm(fromLat, fromLng, pickupLat, pickupLng);
                lastRoutePoints = java.util.Arrays.asList(new LatLngPoint(fromLat, fromLng), new LatLngPoint(pickupLat, pickupLng));
                drawRoute(lastRoutePoints);
                ((TextView) findViewById(R.id.tvNavEta)).setText(Math.max(1, (int)Math.ceil(directKm / 25d * 60d)) + " min est.");
                ((TextView) findViewById(R.id.tvNavDistance)).setText(String.format(Locale.US, "%.1f km direct", directKm));
                findViewById(R.id.btnRetryRoute).setVisibility(android.view.View.VISIBLE);
                findViewById(R.id.btnRetryRoute).setEnabled(true);
                ((TextView) findViewById(R.id.btnRetryRoute)).setText("Retry route");
                showMapNotice("Street service unavailable; direct route shown. Tap retry for roads.");
            }
        });
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network != null ? manager.getNetworkCapabilities(network) : null;
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void callResident() {
        UserAccount resident = tripResidentId > 0 ? dbHelper.getUserById(tripResidentId) : null;
        if (resident == null || resident.phone == null || resident.phone.trim().isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + resident.phone)));
    }

    private void updateArrivalActionVisibility(double driverLat, double driverLng) {
        if (activeTrip == null || arrivalConfirmedInUi
                || !isTravelActive(activeTrip.status)
                || !RoutingService.isValidCoordinate(pickupLat, pickupLng)) return;
        double distanceKm = RoutingService.haversineKm(driverLat, driverLng, pickupLat, pickupLng);
        ViewGroup action = findViewById(R.id.btnNavMarkCollected);
        action.setVisibility(distanceKm <= 0.125 ? android.view.View.VISIBLE : android.view.View.GONE);
        ((TextView) findViewById(R.id.tvNavPrimaryAction)).setText("Mark as arrived");
    }

    private void handleCompactPrimaryAction() {
        TextView label = findViewById(R.id.tvNavPrimaryAction);
        if (!arrivalConfirmedInUi && activeTrip != null
                && isTravelActive(activeTrip.status)) {
            if (Double.isNaN(lastKnownLat) || Double.isNaN(lastKnownLng)) {
                Toast.makeText(this, "Waiting for your GPS location", Toast.LENGTH_LONG).show(); return;
            }
            com.takago.app.network.ServerSyncManager.transition(this, tripId, "arrived", lastKnownLat, lastKnownLng, null, error -> runOnUiThread(() -> {
                if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
                arrivalConfirmedInUi = true; label.setText("Start collection"); Toast.makeText(this, "Arrival confirmed", Toast.LENGTH_SHORT).show();
            }));
            return;
        }
        com.takago.app.network.ServerSyncManager.transition(this, tripId, "collecting", null, null, null, error -> runOnUiThread(() -> {
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
            startActivity(new Intent(this, DriverStartTripActivity.class).putExtra("tripId", tripId)); finish();
        }));
    }

    private void updateCompactActionForStoredStatus(PickupRow trip) {
        View collectedAction = findViewById(R.id.btnNavMarkCollected);
        View completedAction = findViewById(R.id.btnNavMarkCompleted);
        collectedAction.setVisibility(View.GONE);
        completedAction.setVisibility(View.GONE);
        if ("Arrived".equalsIgnoreCase(trip.status)) {
            arrivalConfirmedInUi = true;
            ((TextView) findViewById(R.id.tvNavPrimaryAction)).setText("Start collection");
            collectedAction.setVisibility(View.VISIBLE);
        } else if ("Collected".equalsIgnoreCase(trip.status)
                && trip.measuredWeightKg > 0
                && trip.scalePhotoPath != null && !trip.scalePhotoPath.trim().isEmpty()) {
            completedAction.setVisibility(View.VISIBLE);
        }
    }

    private void bindGroupedStopSummary(PickupRow trip) {
        View row = findViewById(R.id.rowNavStops);
        if (trip.groupId <= 0) { row.setVisibility(View.GONE); return; }
        int[] summary = dbHelper.getRouteStopSummary(trip.groupId, trip.id);
        if (summary[1] <= 1 || summary[2] <= 0) { row.setVisibility(View.GONE); return; }
        ((TextView) findViewById(R.id.tvNavStops)).setText(
                "Stop " + summary[2] + " of " + summary[1]);
        row.setVisibility(View.VISIBLE);
    }

    private void renderGroupedStopMarkers(PickupRow trip) {
        clearGroupedStopMarkers();
        if (trip == null || trip.groupId <= 0 || mapEngine == null) return;
        List<RouteStopRow> stops = dbHelper.getRouteStops(trip.groupId);
        int lastStopOrder = lastStopOrder(stops);
        for (RouteStopRow stop : stops) {
            if (!RoutingService.isValidCoordinate(stop.latitude, stop.longitude)) continue;
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
        if (mapEngine == null) return;
        for (int i = 0; i < renderedStopCount; i++) mapEngine.removeMarker("route_stop_" + i);
        renderedStopCount = 0;
    }

    private List<LatLngPoint> routeWaypoints(double driverLat, double driverLng) {
        List<LatLngPoint> waypoints = new java.util.ArrayList<>();
        waypoints.add(new LatLngPoint(driverLat, driverLng));
        if (activeTrip == null || activeTrip.groupId <= 0) {
            waypoints.add(new LatLngPoint(pickupLat, pickupLng));
            return waypoints;
        }
        List<RouteStopRow> stops = dbHelper.getRouteStops(activeTrip.groupId);
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

    private void showRouteStops() {
        if (activeTrip == null || activeTrip.groupId <= 0) return;
        List<RouteStopRow> stops = dbHelper.getRouteStops(activeTrip.groupId);
        StringBuilder text = new StringBuilder();
        for (RouteStopRow stop : stops) {
            if (text.length() > 0) text.append('\n');
            text.append(stop.stopOrder).append(". Stop ").append(stop.stopOrder)
                    .append(" (").append(stop.status == null ? "Pending" : stop.status).append(')');
        }
        new AlertDialog.Builder(this).setTitle("Today's Route")
                .setMessage(text.length() == 0 ? "No grouped stops are available." : text.toString())
                .setPositiveButton("Close", null).show();
    }

    private static String compactLocation(PickupRow trip) {
        String ward = trip.ward == null ? "" : trip.ward.trim();
        String street = "";
        if (trip.address != null && !trip.address.trim().isEmpty())
            street = trip.address.split(",", 2)[0].trim();
        if (street.isEmpty() || street.equalsIgnoreCase(ward)) return ward;
        return ward.isEmpty() ? street : ward + " • " + street;
    }

    /** Draws the route as a colored line over a slightly wider white "casing", rounded like Uber/Google Maps. */
    private void drawRoute(List<LatLngPoint> points) {
        this.lastRoutePoints = points;
        mapEngine.setRoute(points, 0xFF123A63, 0xFFFFFFFF);

        if (!hasFitCameraToRoute && points.size() >= 2) {
            hasFitCameraToRoute = true;
            fitCameraToRoute();
        }
    }

    /** Frames both the driver and the pickup point in view, like a modern tracking app's initial trip view. */
    private void fitCameraToRoute() {
        if (mapEngine == null || lastRoutePoints == null || lastRoutePoints.isEmpty()) {
            return;
        }
        mapEngine.zoomToBounds(lastRoutePoints, 120);
    }

    private void markCollected() {
        dbHelper.markPickupCollected(tripId);
        ((TextView) findViewById(R.id.tvNavStatus)).setText("Collected");
        findViewById(R.id.btnNavMarkCollected).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnNavMarkCompleted).setVisibility(android.view.View.VISIBLE);
        Toast.makeText(this, "Marked as collected", Toast.LENGTH_SHORT).show();
    }

    private void markCompleted() {
        dbHelper.markTripCollected(tripId);
        Toast.makeText(this, "Trip completed", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void promptCancelReason() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Reason for cancelling");

        new AlertDialog.Builder(this)
                .setTitle("Cancel this trip?")
                .setMessage("A reason is required so the resident and Waste Operator know what happened.")
                .setView(input)
                .setPositiveButton("Cancel trip", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dbHelper.cancelPickup(tripId, reason);
                    Toast.makeText(this, "Trip cancelled", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Keep trip", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapController.onResume();
        if (isTravelActive(tripStatus)) {
            ensureLocationTracking();
        }
    }

    private static boolean isTravelActive(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(Locale.US).replace(' ', '_');
        return normalized.equals("accepted") || normalized.equals("on_the_way");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapController.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        RoutingService.cancelPendingRequests();
        super.onDestroy();
        mapController.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapController.onLowMemory();
    }
}
