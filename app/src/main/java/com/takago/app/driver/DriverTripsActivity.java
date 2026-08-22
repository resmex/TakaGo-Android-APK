package com.takago.app.driver;

import com.takago.app.data.model.PickupRow;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.common.InsetsUtils;
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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.Locale;

public class DriverTripsActivity extends AppCompatActivity {

    private static final String[] FILTERS = {"All", "Assigned", "On the way", "Completed", "Cancelled"};

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LinearLayout filterChipRow, tripListContainer;
    private TextView tvTripsSummary;
    private String selectedFilter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_trips);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        filterChipRow = findViewById(R.id.filterChipRow);
        tripListContainer = findViewById(R.id.tripListContainer);
        tvTripsSummary = findViewById(R.id.tvTripsSummary);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        buildFilterChips();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrips();
        com.takago.app.network.ServerSyncManager.syncAll(this);
        tripListContainer.postDelayed(this::loadTrips, 1400L);
    }

    private void buildFilterChips() {
        for (String filter : FILTERS) {
            TextView chip = new TextView(this);
            chip.setText(filter);
            chip.setTextSize(13);
            chip.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            params.bottomMargin = dp(12);
            chip.setLayoutParams(params);
            chip.setTag(filter);
            chip.setOnClickListener(v -> {
                selectedFilter = filter;
                refreshChipStyles();
                loadTrips();
            });
            filterChipRow.addView(chip);
        }
        refreshChipStyles();
    }

    private void refreshChipStyles() {
        for (int i = 0; i < filterChipRow.getChildCount(); i++) {
            TextView chip = (TextView) filterChipRow.getChildAt(i);
            boolean selected = chip.getTag().equals(selectedFilter);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_outline);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF555555);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadTrips() {
        tripListContainer.removeAllViews();
        List<PickupRow> trips = dbHelper.getDriverTripsByStatus(session.getUserId(), selectedFilter);

        tvTripsSummary.setText(String.format(Locale.US, "%d pickups", trips.size()));

        for (PickupRow trip : trips) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_driver_trip, tripListContainer, false);
            TextView tvAddress = row.findViewById(R.id.tvTripAddress);
            TextView tvMeta = row.findViewById(R.id.tvTripMeta);
            TextView tvStatus = row.findViewById(R.id.tvTripStatus);

            tvAddress.setText(PickupAddressFormatter.primary(trip));
            tvMeta.setText(String.format(Locale.US, "%s   %d kg   %s",
                    PickupAddressFormatter.wardLine(trip), (int) trip.weightKg, trip.timeText));
            tvStatus.setText(trip.status);
            applyStatusStyle(tvStatus, trip.status);

            int tripId = trip.id;
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, DriverPickupDetailsActivity.class);
                intent.putExtra("tripId", tripId);
                startActivity(intent);
            });

            tripListContainer.addView(row);
        }
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        switch (status) {
            case "pending":
            case "Pending":
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "On the way":
            case "Collected":
                tvStatus.setBackgroundResource(R.drawable.bg_status_green);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            case "Cancelled":
            case "Rejected":
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFFC62828);
                break;
            case "Completed":
                tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            default: // Assigned
                tvStatus.setBackgroundResource(R.drawable.bg_status_assigned);
                tvStatus.setTextColor(0xFF1565C0);
                break;
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_trips);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_trips) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, DriverHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, DriverHistoryActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, DriverProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
