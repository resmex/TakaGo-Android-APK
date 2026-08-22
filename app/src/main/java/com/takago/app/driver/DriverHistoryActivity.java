package com.takago.app.driver;

import com.takago.app.data.model.PickupRow;
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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Completed/cancelled trips for the driver, modeled on ResidentHistoryActivity. */
public class DriverHistoryActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LinearLayout historyListContainer;
    private TextView tvNoHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_history);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        historyListContainer = findViewById(R.id.historyListContainer);
        tvNoHistory = findViewById(R.id.tvNoHistory);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        historyListContainer.removeAllViews();
        List<PickupRow> history = dbHelper.getDriverHistory(session.getUserId());

        if (history.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            return;
        }
        tvNoHistory.setVisibility(View.GONE);

        for (PickupRow trip : history) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_driver_trip, historyListContainer, false);
            TextView tvAddress = row.findViewById(R.id.tvTripAddress);
            TextView tvMeta = row.findViewById(R.id.tvTripMeta);
            TextView tvStatus = row.findViewById(R.id.tvTripStatus);

            tvAddress.setText(com.takago.app.common.PickupAddressFormatter.twoLine(trip));
            tvMeta.setText((trip.category != null ? trip.category : "Mixed") + "   " + formatDate(trip.pickupDate));
            tvStatus.setText(trip.status);
            applyStatusStyle(tvStatus, trip.status);

            int tripId = trip.id;
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, DriverPickupDetailsActivity.class);
                intent.putExtra("tripId", tripId);
                startActivity(intent);
            });

            historyListContainer.addView(row);
        }
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        if ("Cancelled".equals(status)) {
            tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
            tvStatus.setTextColor(0xFFC62828);
        } else {
            tvStatus.setBackgroundResource(R.drawable.bg_status_active);
            tvStatus.setTextColor(0xFF2E7D32);
        }
    }

    private String formatDate(String isoDate) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat output = new SimpleDateFormat("d MMMM", Locale.US);
            return output.format(input.parse(isoDate));
        } catch (ParseException | NullPointerException e) {
            return isoDate != null ? isoDate : "";
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_history);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, DriverHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_trips) {
                startActivity(new Intent(this, DriverTripsActivity.class));
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
