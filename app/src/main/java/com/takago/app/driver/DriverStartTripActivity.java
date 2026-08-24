package com.takago.app.driver;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.PriceResult;
import com.takago.app.data.model.RouteStopRow;
import com.takago.app.common.ImageUtils;
import com.takago.app.common.InsetsUtils;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.location.LatLngPoint;
import com.takago.app.location.MapMarkerFactory;
import com.takago.app.location.RoutingService;
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
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.location.LocationManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import org.json.JSONObject;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DriverStartTripActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private int tripId;
    private MapManager mapManager;
    private MapEngine mapEngine;
    private FusedLocationProviderClient locationClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private double pickupLat = Double.NaN;
    private double pickupLng = Double.NaN;
    private boolean routePreviewRequested;
    private boolean pendingTripAccept;
    private PickupRow activeTrip;
    private int renderedStopCount;

    private String pendingScalePhotoPath;
    private Uri pendingCameraUri;
    private TextView tvScalePhotoLabelRef;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String path = ImageUtils.copyUriToAppFile(this, uri, "scale");
                    if (path != null) {
                        onScalePhotoSelected(path);
                    }
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    onScalePhotoSelected(pendingCameraUri.getPath());
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchScaleCamera();
                } else {
                    Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    if (pendingTripAccept) {
                        pendingTripAccept = false;
                        acceptTrip();
                    } else {
                        requestRoutePreview();
                    }
                } else {
                    Toast.makeText(this,
                            "Location permission is needed to show the street route to pickup.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_start_trip);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        tripId = getIntent().getIntExtra("tripId", -1);

        FrameLayout mapContainer = prepareMapContainer();
        mapManager = new MapManager(this, mapContainer, engine -> {
            mapEngine = engine;
            engine.setNoticeListener(message ->
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            loadTrip();
        });
        mapManager.create();

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCallResident).setOnClickListener(v ->
                Toast.makeText(this, "Calling resident...", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnOpenNavigation).setOnClickListener(v -> openNavigation());
        findViewById(R.id.btnAddProofPhoto).setOnClickListener(v -> showScalePhotoChooser());

        findViewById(R.id.btnAcceptTrip).setOnClickListener(v -> attemptAcceptTrip());
        findViewById(R.id.btnRejectTrip).setOnClickListener(v -> rejectTrip());
        findViewById(R.id.btnMarkCollected).setOnClickListener(v -> markCollected());
        findViewById(R.id.btnMarkCompleted).setOnClickListener(v -> markCompleted());
        findViewById(R.id.btnCancelTrip).setOnClickListener(v -> promptCancelReason());
    }

    /** Replaces the legacy XML map fragment before the shared manager creates either provider. */
    private FrameLayout prepareMapContainer() {
        View legacyView = findViewById(R.id.mapFragment);
        android.view.ViewGroup parent = (android.view.ViewGroup) legacyView.getParent();
        int index = parent.indexOfChild(legacyView);
        android.view.ViewGroup.LayoutParams params = legacyView.getLayoutParams();
        Fragment legacyFragment = getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (legacyFragment != null) {
            getSupportFragmentManager().beginTransaction().remove(legacyFragment).commitNow();
        } else {
            parent.removeView(legacyView);
        }
        FrameLayout container = new FrameLayout(this);
        container.setId(View.generateViewId());
        parent.addView(container, index, params);
        return container;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapManager != null) mapManager.onResume();
        com.takago.app.network.ServerSyncManager.syncTracking(this, this::loadTrip);
    }

    @Override
    protected void onPause() {
        if (mapManager != null) mapManager.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        RoutingService.cancelPendingRequests();
        if (mapManager != null) mapManager.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapManager != null) mapManager.onLowMemory();
    }

    private void loadTrip() {
        PickupRow trip = dbHelper.getTripById(tripId);
        if (trip == null) {
            return;
        }

        TextView tvTripHeaderTitle = findViewById(R.id.tvTripHeaderTitle);
        TextView tvTripHeaderSubtitle = findViewById(R.id.tvTripHeaderSubtitle);
        TextView tvResidentName = findViewById(R.id.tvResidentName);
        TextView tvResidentAddress = findViewById(R.id.tvResidentAddress);
        TextView tvPickupStatus = findViewById(R.id.tvPickupStatus);
        activeTrip = trip;

        tvTripHeaderTitle.setText("Pickup " + trip.code);
        tvTripHeaderSubtitle.setText(PickupAddressFormatter.wardLine(trip) + (trip.distanceKm > 0
                ? String.format(Locale.US, " %.1f km", trip.distanceKm) : ""));

        tvResidentName.setText(trip.residentDisplayName != null ? trip.residentDisplayName : "Resident");
        tvResidentAddress.setText(PickupAddressFormatter.styledTwoLine(trip));
        tvPickupStatus.setText(trip.status);
        if (trip.photoPath != null && !trip.photoPath.trim().isEmpty())
            ImageUtils.loadAvatar(findViewById(R.id.ivHeaderIcon), trip.photoPath);

        updateButtonsForStatus(trip.status);
        if (mapEngine != null && RoutingService.isValidCoordinate(trip.latitude, trip.longitude)) {
            pickupLat = trip.latitude;
            pickupLng = trip.longitude;
            LatLngPoint pickup = new LatLngPoint(trip.latitude, trip.longitude);
            mapEngine.setCenter(pickup, 15f);
            if (trip.groupId > 0) {
                mapEngine.removeMarker("pickup");
                renderGroupedStopMarkers(trip);
            } else {
                clearGroupedStopMarkers();
                mapEngine.addOrUpdateMarker("pickup", pickup, MapMarkerFactory.pickupPin(this),
                        0.5f, 1.0f, false);
            }
            requestRoutePreview();
        }
    }

    /** Draws a real street route from the driver's current position to this pickup. */
    private void requestRoutePreview() {
        if (mapEngine == null || routePreviewRequested
                || !RoutingService.isValidCoordinate(pickupLat, pickupLng)) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        routePreviewRequested = true;
        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            routePreviewRequested = false;
                            Toast.makeText(this,
                                    "GPS position is unavailable. Turn on location and try again.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        drawStreetRoute(location.getLatitude(), location.getLongitude());
                    })
                    .addOnFailureListener(error -> {
                        routePreviewRequested = false;
                        Toast.makeText(this,
                                "Could not get your current location. The pickup marker is still available.",
                                Toast.LENGTH_LONG).show();
                    });
        } catch (SecurityException error) {
            routePreviewRequested = false;
            Toast.makeText(this, "Location permission is required for routing.", Toast.LENGTH_LONG).show();
        }
    }

    private void drawStreetRoute(double driverLat, double driverLng) {
        if (!RoutingService.isValidCoordinate(driverLat, driverLng)) {
            routePreviewRequested = false;
            return;
        }
        LatLngPoint driver = new LatLngPoint(driverLat, driverLng);
        mapEngine.addOrUpdateMarker("driver", driver,
                ContextCompat.getDrawable(this, R.drawable.ic_truck_outline), 0.5f, 0.5f, true);
        List<LatLngPoint> waypoints = routeWaypoints(driverLat, driverLng);
        RoutingService.fetchRoute(mainHandler, waypoints, false,
                new RoutingService.RouteCallback() {
                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes) {
                        mapEngine.setRoute(points, 0xFF2962FF, 0xFFFFFFFF);
                        mapEngine.zoomToBounds(points, 120);
                        ((TextView) findViewById(R.id.tvTripHeaderSubtitle)).setText(
                                String.format(Locale.US, "%.1f km · about %d min", distanceKm, etaMinutes));
                    }

                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                                        String encodedPolyline, int distanceMeters, int durationSeconds) {
                        dbHelper.savePickupRoute(tripId, encodedPolyline, distanceMeters, durationSeconds);
                        onRoute(points, distanceKm, etaMinutes);
                    }

                    @Override
                    public void onRouteFailed(String message) {
                        routePreviewRequested = false;
                        mapEngine.zoomToBounds(waypoints, 120);
                        Toast.makeText(DriverStartTripActivity.this,
                                message + " Tap the navigation button to retry.", Toast.LENGTH_LONG).show();
                    }
                });
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

    /** Only the buttons that make sense for the trip's current status are shown. */
    private void updateButtonsForStatus(String status) {
        String normalized = normalizeStatus(status);
        boolean isAssigned = "assigned".equals(normalized);
        boolean needsNavigation = "accepted".equals(normalized) || "on_the_way".equals(normalized);
        boolean isArrived = "arrived".equals(normalized);
        boolean isCollecting = "collecting".equals(normalized);
        boolean isFinished = "weight_recorded".equals(normalized)
                || "resident_confirmation".equals(normalized) || "price_confirmed".equals(normalized)
                || "payment_pending".equals(normalized) || "paid".equals(normalized)
                || "completed".equals(normalized) || "cancelled".equals(normalized);

        findViewById(R.id.rowAcceptReject).setVisibility(isAssigned ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnOpenNavigation).setVisibility(isFinished || isAssigned ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnAddProofPhoto).setVisibility(isFinished || isAssigned ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnOpenNavigation).setVisibility(needsNavigation ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnMarkCollected).setVisibility(isArrived ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnMarkCompleted).setVisibility(isCollecting ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCancelTrip).setVisibility(isFinished ? View.GONE : View.VISIBLE);
    }

    private void attemptAcceptTrip() {
        PickupRow trip = dbHelper.getTripById(tripId);
        int driverId = session.getUserId();
        if (trip == null || trip.driverId != driverId) {
            Toast.makeText(this, "This request is no longer assigned to you.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!RoutingService.isValidCoordinate(trip.latitude, trip.longitude)) {
            Toast.makeText(this, "Pickup location is unavailable.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!dbHelper.isDriverVehicleApproved(driverId)) {
            Toast.makeText(this, "An approved vehicle is required to start this trip.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingTripAccept = true;
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null || (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            Toast.makeText(this, "Turn on GPS before starting the trip.", Toast.LENGTH_LONG).show();
            return;
        }
        acceptTrip();
    }

    private void acceptTrip() {
        com.takago.app.network.ServerSyncManager.acceptAndStart(this, tripId, error -> runOnUiThread(() -> {
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
            Toast.makeText(this, "Trip started", Toast.LENGTH_SHORT).show(); loadTrip(); openNavigation();
        }));
    }

    private void openNavigation() {
        PickupRow current = dbHelper.getTripById(tripId);
        if (current != null && "Accepted".equalsIgnoreCase(current.status)) {
            com.takago.app.network.ServerSyncManager.transition(this, tripId, "on_the_way", null, null, null,
                    error -> runOnUiThread(() -> { if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; } launchNavigationMap(); }));
            return;
        }
        launchNavigationMap();
    }

    private void launchNavigationMap() {
        Intent intent = new Intent(this, DriverNavigationActivity.class);
        intent.putExtra("tripId", tripId);
        startActivity(intent);
    }

    private void rejectTrip() {
        com.takago.app.network.ServerSyncManager.transition(this, tripId, "rejected", null, null,
                "Rejected by assigned driver", error -> runOnUiThread(() -> {
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, "Trip rejected and returned for reassignment", Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    private void markCollected() {
        com.takago.app.network.ServerSyncManager.transition(this, tripId, "collecting", null, null, null,
                error -> runOnUiThread(() -> {
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
                    Toast.makeText(this, "Collection started", Toast.LENGTH_SHORT).show();
                    com.takago.app.network.ServerSyncManager.syncTracking(this, this::loadTrip);
                }));
    }

    private void markCompleted() {
        promptMeasuredWeight();
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.US).replace(' ', '_');
    }

    private void promptMeasuredWeight() {
        pendingScalePhotoPath = null;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_measured_weight, null);
        EditText etWeight = view.findViewById(R.id.etMeasuredWeight);
        tvScalePhotoLabelRef = view.findViewById(R.id.tvScalePhotoLabel);
        view.findViewById(R.id.btnAttachScalePhoto).setOnClickListener(v -> showScalePhotoChooser());

        new AlertDialog.Builder(this)
                .setTitle("Enter measured weight")
                .setMessage("Enter the waste weight from the truck scale to calculate the final price.")
                .setView(view)
                .setPositiveButton("Calculate price", (dialog, which) -> {
                    String weightText = etWeight.getText().toString().trim();
                    if (weightText.isEmpty()) {
                        Toast.makeText(this, "Please enter the measured weight", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double weight;
                    try {
                        weight = Double.parseDouble(weightText);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (weight <= 0) {
                        Toast.makeText(this, "Weight must be greater than 0 kg", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    finalizePricing(weight);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void finalizePricing(double measuredWeightKg) {
        com.takago.app.network.ServerSyncManager.finalizePickup(this, tripId, measuredWeightKg, error -> runOnUiThread(() -> {
            if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
            new AlertDialog.Builder(this).setTitle("Weight recorded")
                    .setMessage(String.format(Locale.US, "Actual weight: %.1f kg%nWaiting for the resident to confirm the collection.", measuredWeightKg))
                    .setPositiveButton("Done", (d, w) -> finish()).setCancelable(false).show();
        }));
        if (true) return;
        PriceResult result = dbHelper.computeFinalPrice(tripId, measuredWeightKg, pendingScalePhotoPath);
        if (!result.success) {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }

        dbHelper.markTripCollected(tripId);
        com.takago.app.network.ServerSyncManager.finalizePickup(this, tripId, measuredWeightKg, error -> runOnUiThread(() -> {
            if (error != null) Toast.makeText(this, "Price sync failed: " + error, Toast.LENGTH_LONG).show();
        }));

        if (result.requiresManualApproval) {
            new AlertDialog.Builder(this)
                    .setTitle("Pricing pending approval")
                    .setMessage("This waste type needs manual pricing approval from the Municipal Admin. " +
                            "The trip is now marked completed; the resident will be notified once a price is set.")
                    .setPositiveButton("OK", (d, w) -> finish())
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Trip completed")
                .setMessage(String.format(Locale.US,
                        "Final price: TZS %,.0f%nBooking fee: TZS %,.0f%nWeight charge: TZS %,.0f%nDistance fee: TZS %,.0f",
                        result.finalPrice, result.bookingFee, result.weightFee, result.distanceFee))
                .setPositiveButton("Done", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void promptCashCode() {
        EditText input = new EditText(this);
        input.setHint("4-digit code from resident");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("Confirm cash received")
                .setMessage("Enter the code only after the resident hands you the cash.")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject().put("confirmation_code", input.getText().toString().trim());
                        ApiClient.post("/pickups/" + tripId + "/payments/cash/confirm", session.getApiToken(), body, new ApiClient.JsonCallback() {
                            public void onSuccess(JSONObject json) { runOnUiThread(() -> { Toast.makeText(DriverStartTripActivity.this, json.optString("message", "Cash confirmed"), Toast.LENGTH_LONG).show(); finish(); }); }
                            public void onError(String message) { runOnUiThread(() -> Toast.makeText(DriverStartTripActivity.this, message, Toast.LENGTH_LONG).show()); }
                        });
                    } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void showScalePhotoChooser() {
        String[] options = {"Take photo", "Choose from gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Attach scale photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraAndLaunch();
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void requestCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            launchScaleCamera();
        }
    }

    private void launchScaleCamera() {
        File photoFile = new File(getExternalFilesDir("Pictures"), "scale_" + System.currentTimeMillis() + ".jpg");
        pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        cameraLauncher.launch(pendingCameraUri);
    }

    private void onScalePhotoSelected(String path) {
        String prepared=ImageUtils.prepareImageForUpload(this,path,"proof",ImageUtils.MAX_PICKUP_IMAGE_BYTES);
        if(prepared==null){pendingScalePhotoPath=null;Toast.makeText(this,"Choose a valid JPG, PNG or WebP image up to 4 MB.",Toast.LENGTH_LONG).show();return;}
        pendingScalePhotoPath = prepared;
        if (tvScalePhotoLabelRef != null) {
            tvScalePhotoLabelRef.setText("Scale photo added");
            tvScalePhotoLabelRef.setTextColor(0xFF2E7D32);
        }
        if (tripId > 0) {
            ApiClient.uploadPickupImage(session.getApiToken(), tripId, "proof", prepared,
                    new ApiClient.JsonCallback() {
                        public void onSuccess(JSONObject json) { runOnUiThread(() -> Toast.makeText(DriverStartTripActivity.this, "Proof photo saved", Toast.LENGTH_SHORT).show()); }
                        public void onError(String message) { runOnUiThread(() -> Toast.makeText(DriverStartTripActivity.this, "Proof photo upload failed: " + message, Toast.LENGTH_LONG).show()); }
                    });
        }
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
}
