package com.takago.app.resident;

import com.takago.app.common.InsetsUtils;
import com.takago.app.R;
import com.takago.app.*;
import com.takago.app.admin.*;
import com.takago.app.app.*;
import com.takago.app.auth.*;
import com.takago.app.common.*;
import com.takago.app.driver.*;
import com.takago.app.location.*;
import com.takago.app.location.map.MapEngine;
import com.takago.app.location.map.MapManager;
import com.takago.app.data.model.WardRow;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.notifications.*;
import com.takago.app.operator.*;
import com.takago.app.resident.*;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.PlaceAutocomplete;
import com.google.android.libraries.places.widget.PlaceAutocompleteActivity;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

/**
 * Full-screen map picker for the resident's pickup location - the only place in the pickup
 * request flow where the map is shown. Launched from the compact Pickup Location card via
 * "Change"; returns the picked lat/lng/address/ward to the caller on confirm.
 */
public class ResidentPickupLocationActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_LAT = "initialLat";
    public static final String EXTRA_INITIAL_LNG = "initialLng";
    public static final String EXTRA_RESULT_LAT = "resultLat";
    public static final String EXTRA_RESULT_LNG = "resultLng";
    public static final String EXTRA_RESULT_ADDRESS = "resultAddress";
    public static final String EXTRA_RESULT_HOUSE_NUMBER = "resultHouseNumber";
    public static final String EXTRA_RESULT_STREET_NAME = "resultStreetName";
    public static final String EXTRA_RESULT_FORMATTED_ADDRESS = "resultFormattedAddress";
    public static final String EXTRA_RESULT_WARD = "resultWard";
    public static final String EXTRA_RESULT_WARD_ID = "resultWardId";
    public static final String EXTRA_RESULT_PLACE_ID = "resultPlaceId";
    public static final String EXTRA_RESULT_PLACE_NAME = "resultPlaceName";
    public static final String EXTRA_RESULT_PLUS_CODE = "resultPlusCode";

    // Upanga, Dar es Salaam - default pin location shown before the user pans the map.
    private static final double DEFAULT_LAT = -6.8062;
    private static final double DEFAULT_LNG = 39.2830;

    private MapManager mapController;
    private MapEngine mapEngine;
    private TextView tvMapNotice;
    private ProgressBar geocodingProgress;
    private TextView tvLocationTitle, tvLocationSubtitle;

    private double selectedLat = DEFAULT_LAT;
    private double selectedLng = DEFAULT_LNG;
    private String selectedWard;
    private int selectedWardId = -1;
    private String selectedPlaceId;
    private String selectedHouseNumber;
    private String selectedStreetName;
    private String selectedFormattedAddress;
    private String selectedPlaceName;
    private String selectedPlusCode;
    private int geocodeSequence;
    private final Handler locationResolveHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingLocationResolve;
    private DatabaseHelper dbHelper;
    private FusedLocationProviderClient locationClient;
    private PlacesClient placesClient;
    private AutocompleteSessionToken autocompleteSessionToken;

    private final ActivityResultLauncher<Intent> autocompleteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (data == null || result.getResultCode() != PlaceAutocompleteActivity.RESULT_OK) return;
                AutocompletePrediction prediction = PlaceAutocomplete.getPredictionFromIntent(data);
                AutocompleteSessionToken token = PlaceAutocomplete.getSessionTokenFromIntent(data);
                if (prediction == null) return;
                FetchPlaceRequest request = FetchPlaceRequest.builder(prediction.getPlaceId(), Arrays.asList(
                                Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS,
                                Place.Field.LOCATION, Place.Field.ADDRESS_COMPONENTS))
                        .setSessionToken(token != null ? token : autocompleteSessionToken).build();
                placesClient.fetchPlace(request).addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    LatLng location = place.getLocation();
                    if (location == null) return;
                    selectedPlaceId = place.getId();
                    selectedLat = location.latitude;
                    selectedLng = location.longitude;
                    mapEngine.animateCenter(new LatLngPoint(selectedLat, selectedLng));
                    selectedPlaceName = place.getDisplayName();
                    reverseGeocode(selectedLat, selectedLng, selectedPlaceName);
                    autocompleteSessionToken = AutocompleteSessionToken.newInstance();
                }).addOnFailureListener(error ->
                        Toast.makeText(this, "Place details are temporarily unavailable.", Toast.LENGTH_LONG).show());
            });

    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    centerOnCurrentLocation();
                } else {
                    Toast.makeText(this, "Location permission is needed to find your position", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_pickup_location);

        dbHelper = new DatabaseHelper(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        if (Places.isInitialized()) placesClient = Places.createClient(this);
        autocompleteSessionToken = AutocompleteSessionToken.newInstance();

        selectedLat = getIntent().getDoubleExtra(EXTRA_INITIAL_LAT, DEFAULT_LAT);
        selectedLng = getIntent().getDoubleExtra(EXTRA_INITIAL_LNG, DEFAULT_LNG);

        geocodingProgress = findViewById(R.id.geocodingProgress);
        tvLocationTitle = findViewById(R.id.tvLocationTitle);
        tvLocationSubtitle = findViewById(R.id.tvLocationSubtitle);
        tvMapNotice = findViewById(R.id.tvMapNotice);

        InsetsUtils.applyStatusBarTopMargin(findViewById(R.id.cardLocation));

        FrameLayout mapContainer = findViewById(R.id.mapContainer);
        mapController = new MapManager(this, mapContainer, this::onMapReady);
        mapController.create();
        addSearchControl((FrameLayout) findViewById(android.R.id.content));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvChangeLocation).setOnClickListener(v -> centerOnCurrentLocation());
        findViewById(R.id.btnConfirmLocation).setOnClickListener(v -> confirmLocation());
    }

    private void addSearchControl(FrameLayout root) {
        TextView search = new TextView(this);
        search.setText("Search address, ward, place or Plus Code");
        search.setTextColor(0xFF666666);
        search.setTextSize(14f);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(24));
        search.setBackground(background);
        search.setElevation(dp(5));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP);
        params.setMargins(dp(16), dp(132), dp(16), 0);
        root.addView(search, params);
        search.setOnClickListener(v -> launchAutocomplete());
    }

    private void launchAutocomplete() {
        if (placesClient == null) {
            Toast.makeText(this, "Google Places is not configured.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new PlaceAutocomplete.IntentBuilder()
                .setAutocompleteSessionToken(autocompleteSessionToken)
                .setCountries(Arrays.asList("TZ"))
                .setOrigin(new LatLng(selectedLat, selectedLng))
                .build(this);
        autocompleteLauncher.launch(intent);
    }

    private void onMapReady(MapEngine engine) {
        this.mapEngine = engine;
        engine.setCenter(new LatLngPoint(selectedLat, selectedLng), 16f);
        // The pin overlay is fixed at the screen's center; the map pans underneath it, so
        // whatever the map is centered on when the user stops moving it is the selected point.
        engine.addOnCameraMoveListener(this::onLocationChanged);
        engine.setNoticeListener(this::showMapNotice);
        if (getIntent().hasExtra(EXTRA_INITIAL_LAT) && getIntent().hasExtra(EXTRA_INITIAL_LNG)) {
            reverseGeocode(selectedLat, selectedLng);
        } else {
            tvLocationTitle.setText("Finding your current location...");
            tvLocationSubtitle.setText("Ward unavailable");
            centerOnCurrentLocation();
        }
    }

    private void showMapNotice(String message) {
        tvMapNotice.setText(message);
        tvMapNotice.setVisibility(View.VISIBLE);
    }

    private void onLocationChanged(LatLngPoint center) {
        selectedLat = center.lat;
        selectedLng = center.lng;
        if (pendingLocationResolve != null) locationResolveHandler.removeCallbacks(pendingLocationResolve);
        double lat = selectedLat, lng = selectedLng;
        pendingLocationResolve = () -> reverseGeocode(lat, lng);
        locationResolveHandler.postDelayed(pendingLocationResolve, 350L);
    }

    /** Looks up a human-readable address for the pin. Falls back gracefully when offline. */
    private void reverseGeocode(double lat, double lng) {
        reverseGeocode(lat, lng, null);
    }

    private void reverseGeocode(double lat, double lng, String preferredPlace) {
        final int requestSequence = ++geocodeSequence;
        geocodingProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            WardRow polygonWardRow = dbHelper.findMappedWardContaining(lat, lng);
            int polygonWardId = polygonWardRow != null ? polygonWardRow.id : -1;
            String polygonWard = polygonWardRow != null ? polygonWardRow.name : null;
            Address address = null;
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results != null && !results.isEmpty()) {
                    address = results.get(0);
                }
            } catch (IOException | IllegalArgumentException e) {
                // No internet or geocoder service unavailable - handled below.
            }

            Address finalAddress = address;
            int finalPolygonWardId = polygonWardId;
            String finalPolygonWard = polygonWard;
            runOnUiThread(() -> {
                if (requestSequence != geocodeSequence
                        || Math.abs(lat - selectedLat) > 0.000001
                        || Math.abs(lng - selectedLng) > 0.000001) return;
                if (preferredPlace != null && !preferredPlace.trim().isEmpty()) {
                    applyResolvedAddress(requestSequence, lat, lng, finalAddress, preferredPlace,
                            finalPolygonWardId, finalPolygonWard);
                } else {
                    NearbyPoiResolver.resolve(this, lat, lng, poiName -> applyResolvedAddress(
                            requestSequence, lat, lng, finalAddress, poiName,
                            finalPolygonWardId, finalPolygonWard));
                }
            });
        }).start();
    }

    private void applyResolvedAddress(int requestSequence, double lat, double lng, Address address,
                                      String poiName, int wardId, String ward) {
        if (requestSequence != geocodeSequence
                || Math.abs(lat - selectedLat) > 0.000001
                || Math.abs(lng - selectedLng) > 0.000001) return;
        ReadableAddress readable = ReadableAddress.from(address, ward, poiName);
        geocodingProgress.setVisibility(View.GONE);
        tvLocationTitle.setText(!readable.label.isEmpty() ? readable.label : (ward == null ? "" : ward));
        selectedWard = ward;
        selectedWardId = wardId;
        selectedHouseNumber = readable.houseNumber;
        selectedStreetName = readable.streetName;
        selectedPlaceName = !readable.placeName.isEmpty() ? readable.placeName : readable.neighbourhood;
        selectedFormattedAddress = readable.formattedAddress;
        selectedPlusCode = readable.plusCode;
        tvLocationSubtitle.setText(ward != null ? ward + " Ward" : "Outside supported service area");
    }

    private void centerOnCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            Toast.makeText(this, "Could not find your current location", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        LatLngPoint point = new LatLngPoint(location.getLatitude(), location.getLongitude());
                        mapEngine.animateCenter(point);
                        onLocationChanged(point);
                    }).addOnFailureListener(error -> Toast.makeText(this,
                            "Could not find your current location", Toast.LENGTH_SHORT).show());
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmLocation() {
        if (selectedWardId <= 0) {
            Toast.makeText(this,
                    "This pickup location is outside the currently supported service area.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_LAT, selectedLat);
        result.putExtra(EXTRA_RESULT_LNG, selectedLng);
        result.putExtra(EXTRA_RESULT_ADDRESS, tvLocationTitle.getText().toString());
        result.putExtra(EXTRA_RESULT_HOUSE_NUMBER, selectedHouseNumber);
        result.putExtra(EXTRA_RESULT_STREET_NAME, selectedStreetName);
        result.putExtra(EXTRA_RESULT_FORMATTED_ADDRESS, selectedFormattedAddress);
        result.putExtra(EXTRA_RESULT_WARD, selectedWard);
        result.putExtra(EXTRA_RESULT_WARD_ID, selectedWardId);
        result.putExtra(EXTRA_RESULT_PLACE_ID, selectedPlaceId);
        result.putExtra(EXTRA_RESULT_PLACE_NAME, selectedPlaceName);
        result.putExtra(EXTRA_RESULT_PLUS_CODE, selectedPlusCode);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapController.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapController.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingLocationResolve != null) locationResolveHandler.removeCallbacks(pendingLocationResolve);
        mapController.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapController.onLowMemory();
    }
}
