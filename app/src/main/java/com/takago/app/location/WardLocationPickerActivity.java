package com.takago.app.location;

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
import com.takago.app.notifications.*;
import com.takago.app.operator.*;
import com.takago.app.resident.*;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Map-based location picker used wherever the app needs "an exact operating area" instead of a
 * free-text ward name: registering a Ward Admin/Waste Operator, or registering a driver into a
 * specific street/ward. Shows type-ahead suggestions (via Android's built-in Geocoder - no paid
 * Places API), a pannable map with a fixed center pin, and an adjustable radius circle around the
 * chosen point. Returns the picked ward/place name, its lat/lng, and the radius in km.
 */
public class WardLocationPickerActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_LAT = "initialLat";
    public static final String EXTRA_INITIAL_LNG = "initialLng";
    public static final String EXTRA_RESULT_WARD = "resultWard";
    public static final String EXTRA_RESULT_LAT = "resultLat";
    public static final String EXTRA_RESULT_LNG = "resultLng";
    public static final String EXTRA_RESULT_RADIUS_KM = "resultRadiusKm";

    private static final double DEFAULT_LAT = -6.8062;
    private static final double DEFAULT_LNG = 39.2830;
    private static final double[] RADIUS_PRESETS_KM = {1, 2, 3, 5, 10, 15, 20};
    private static final long SEARCH_DEBOUNCE_MS = 400;

    private MapManager mapController;
    private MapEngine mapEngine;
    private TextView tvMapNotice;
    private EditText etSearch;
    private ProgressBar searchProgress;
    private LinearLayout suggestionsContainer;
    private TextView tvSelectedPlace;
    private TextView tvRadiusValue;

    private double selectedLat = DEFAULT_LAT;
    private double selectedLng = DEFAULT_LNG;
    private String selectedPlaceName;
    private int radiusIndex = 2; // 3 km default

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ward_location_picker);

        tvMapNotice = findViewById(R.id.tvMapNotice);
        etSearch = findViewById(R.id.etSearch);
        searchProgress = findViewById(R.id.searchProgress);
        suggestionsContainer = findViewById(R.id.suggestionsContainer);
        tvSelectedPlace = findViewById(R.id.tvSelectedPlace);
        tvRadiusValue = findViewById(R.id.tvRadiusValue);

        selectedLat = getIntent().getDoubleExtra(EXTRA_INITIAL_LAT, DEFAULT_LAT);
        selectedLng = getIntent().getDoubleExtra(EXTRA_INITIAL_LNG, DEFAULT_LNG);

        FrameLayout mapContainer = findViewById(R.id.mapContainer);
        mapController = new MapManager(this, mapContainer, this::onMapReady);
        mapController.create();

        setupSearch();
        updateRadiusLabel();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRadiusMinus).setOnClickListener(v -> changeRadius(-1));
        findViewById(R.id.btnRadiusPlus).setOnClickListener(v -> changeRadius(1));
        findViewById(R.id.btnConfirmLocation).setOnClickListener(v -> confirmLocation());
    }

    private void onMapReady(MapEngine engine) {
        this.mapEngine = engine;
        engine.setCenter(new LatLngPoint(selectedLat, selectedLng), 14f);
        engine.addOnCameraMoveListener(this::onMapMoved);
        engine.setNoticeListener(this::showMapNotice);
        drawRadiusCircle();
    }

    private void showMapNotice(String message) {
        tvMapNotice.setText(message);
        tvMapNotice.setVisibility(View.VISIBLE);
    }

    private void onMapMoved(LatLngPoint center) {
        selectedLat = center.lat;
        selectedLng = center.lng;
        selectedPlaceName = null;
        drawRadiusCircle();
        reverseGeocode(selectedLat, selectedLng);
    }

    private void reverseGeocode(double lat, double lng) {
        new Thread(() -> {
            String placeName = null;
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results != null && !results.isEmpty()) {
                    placeName = ReadableAddress.from(results.get(0), null, null).label;
                }
            } catch (IOException e) {
                // Offline or geocoder unavailable; keep the last readable label.
            }

            String finalPlaceName = placeName;
            mainHandler.post(() -> {
                if (finalPlaceName != null) {
                    selectedPlaceName = finalPlaceName;
                    tvSelectedPlace.setText(finalPlaceName);
                }
            });
        }).start();
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) {
                    mainHandler.removeCallbacks(pendingSearch);
                }
                String query = s.toString().trim();
                if (query.length() < 3) {
                    suggestionsContainer.setVisibility(View.GONE);
                    suggestionsContainer.removeAllViews();
                    return;
                }
                pendingSearch = () -> searchPlaces(query);
                mainHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void searchPlaces(String query) {
        searchProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Address> matches = new ArrayList<>();
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                // Bias toward Tanzania since that's where every driver/ward/operator in this app operates.
                List<Address> results = geocoder.getFromLocationName(query + ", Tanzania", 5);
                if (results != null) {
                    matches.addAll(results);
                }
            } catch (IOException e) {
                // No internet or geocoder unavailable - matches stays empty, handled below.
            }

            mainHandler.post(() -> {
                searchProgress.setVisibility(View.GONE);
                showSuggestions(matches);
            });
        }).start();
    }

    private void showSuggestions(List<Address> matches) {
        suggestionsContainer.removeAllViews();

        if (matches.isEmpty()) {
            suggestionsContainer.setVisibility(View.GONE);
            return;
        }
        suggestionsContainer.setVisibility(View.VISIBLE);

        for (Address match : matches) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_place_suggestion, suggestionsContainer, false);
            TextView tvTitle = row.findViewById(R.id.tvSuggestionTitle);
            TextView tvSubtitle = row.findViewById(R.id.tvSuggestionSubtitle);

            String title = match.getFeatureName() != null ? match.getFeatureName() : match.getAddressLine(0);
            String subtitle = match.getAddressLine(0);

            tvTitle.setText(title != null ? title : "Unnamed place");
            tvSubtitle.setText(subtitle != null ? subtitle : "");

            row.setOnClickListener(v -> {
                selectedLat = match.getLatitude();
                selectedLng = match.getLongitude();
                selectedPlaceName = title;
                tvSelectedPlace.setText(title != null ? title : subtitle);
                mapEngine.animateCenter(new LatLngPoint(selectedLat, selectedLng));
                drawRadiusCircle();
                suggestionsContainer.setVisibility(View.GONE);
                suggestionsContainer.removeAllViews();
                etSearch.clearFocus();
            });

            suggestionsContainer.addView(row);
        }
    }

    private void changeRadius(int direction) {
        radiusIndex = Math.max(0, Math.min(RADIUS_PRESETS_KM.length - 1, radiusIndex + direction));
        updateRadiusLabel();
        drawRadiusCircle();
    }

    private void updateRadiusLabel() {
        double km = RADIUS_PRESETS_KM[radiusIndex];
        tvRadiusValue.setText(String.format(Locale.US, "%.0f km", km));
    }

    private void drawRadiusCircle() {
        if (mapEngine == null) {
            return;
        }
        mapEngine.setRadiusCircle(new LatLngPoint(selectedLat, selectedLng),
                RADIUS_PRESETS_KM[radiusIndex], 0x334CAF50, 0xFF4CAF50);
    }

    private void confirmLocation() {
        if (selectedPlaceName == null) {
            Toast.makeText(this, "Still locating this spot, please wait a moment", Toast.LENGTH_SHORT).show();
        }

        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_WARD, selectedPlaceName != null ? selectedPlaceName : tvSelectedPlace.getText().toString());
        result.putExtra(EXTRA_RESULT_LAT, selectedLat);
        result.putExtra(EXTRA_RESULT_LNG, selectedLng);
        result.putExtra(EXTRA_RESULT_RADIUS_KM, RADIUS_PRESETS_KM[radiusIndex]);
        setResult(RESULT_OK, result);
        finish();
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
        mapController.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapController.onLowMemory();
    }
}
