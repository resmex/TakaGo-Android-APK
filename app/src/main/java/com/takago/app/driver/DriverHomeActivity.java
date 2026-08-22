package com.takago.app.driver;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.common.ImageUtils;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.location.ReadableLocationManager;
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
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ServerSyncManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.Locale;

public class DriverHomeActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    private TextView tvUserName, tvUserLocation, tvStatToday, tvStatDistance, tvStatRating;
    private TextView tvNextPickupAddress, tvNextPickupSubtitle, tvNextPickupStatus, tvNoRequestsToday;
    private ImageView ivUserAvatar;
    private LinearLayout requestsContainer;
    private int nextPickupId = -1;
    private String nextPickupStatus = "";
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        tvUserName = findViewById(R.id.tvUserName);
        tvUserLocation = findViewById(R.id.tvUserLocation);
        tvStatToday = findViewById(R.id.tvStatToday);
        tvStatDistance = findViewById(R.id.tvStatDistance);
        tvStatRating = findViewById(R.id.tvStatRating);
        tvNextPickupAddress = findViewById(R.id.tvNextPickupAddress);
        tvNextPickupSubtitle = findViewById(R.id.tvNextPickupSubtitle);
        tvNextPickupStatus = findViewById(R.id.tvNextPickupStatus);
        tvNoRequestsToday = findViewById(R.id.tvNoRequestsToday);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        requestsContainer = findViewById(R.id.requestsContainer);

        findViewById(R.id.btnStartTrip).setOnClickListener(v -> {
            if (nextPickupId == -1) {
                Toast.makeText(this, "No pickup assigned right now", Toast.LENGTH_SHORT).show();
                return;
            }
            if ("Assigned".equalsIgnoreCase(nextPickupStatus)) {
                dbHelper.acceptPickup(nextPickupId, session.getUserId());
                dbHelper.markPickupOnTheWayLocal(nextPickupId, session.getUserId());
                ServerSyncManager.acceptAndStart(this, nextPickupId,
                        error -> runOnUiThread(() -> { if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; } openNextPickup(); }));
                return;
            }
            if ("Accepted".equalsIgnoreCase(nextPickupStatus)) {
                dbHelper.markPickupOnTheWayLocal(nextPickupId, session.getUserId());
                ServerSyncManager.transition(this, nextPickupId, "on_the_way", null, null, null,
                        error -> runOnUiThread(() -> { if (error != null) { Toast.makeText(this,error,Toast.LENGTH_LONG).show(); return; } openNextPickup(); }));
                return;
            }
            openNextPickup();
        });

        findViewById(R.id.tvSeeAllRequests).setOnClickListener(v ->
                startActivity(new Intent(this, DriverTripsActivity.class)));

        findViewById(R.id.btnNotification).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));

        setupBottomNav();
    }

    private void openNextPickup() {
        String status = nextPickupStatus == null ? "" : nextPickupStatus.toLowerCase(Locale.US).replace(' ', '_');
        Class<?> destination = status.equals("arrived") || status.equals("collecting")
                || status.equals("weight_recorded") || status.equals("resident_confirmation")
                || status.equals("price_confirmed") || status.equals("payment_pending")
                || status.equals("paid") ? DriverStartTripActivity.class : DriverNavigationActivity.class;
        Intent intent = new Intent(this, destination);
        intent.putExtra("tripId", nextPickupId);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboard();
        ServerSyncManager.syncAll(this);
        refreshHandler.postDelayed(this::loadDashboard, 1400);
    }

    private void loadDashboard() {
        int driverId = session.getUserId();
        tvUserName.setText(session.getName());
        ReadableLocationManager.refresh(this, dbHelper, driverId,
                (primary, ward) -> tvUserLocation.setText(LocationTextStyle.twoLine(primary, ward)));

        UserAccount driver = dbHelper.getUserById(driverId);
        if (driver != null) {
            double completedRouteKm = dbHelper.getDriverCompletedRouteDistanceKm(driverId);
            tvStatDistance.setText(formatKm(Math.max(driver.totalDistanceKm, completedRouteKm)));
            tvStatRating.setText(String.format(Locale.US, "%.1f", driver.rating));
            ImageUtils.loadAvatar(ivUserAvatar, driver.profileImagePath);
        }

        tvStatToday.setText(String.valueOf(dbHelper.getDriverTodayCount(driverId)));

        PickupRow next = dbHelper.getNextPickupForDriver(driverId);
        if (next != null) {
            nextPickupId = next.id;
            nextPickupStatus = next.status == null ? "" : next.status;
            tvNextPickupAddress.setText(PickupAddressFormatter.primary(next));
            double routeKm = next.routeDistanceMeters > 0 ? next.routeDistanceMeters / 1000d : next.distanceKm;
            int routeMinutes = next.routeDurationSeconds > 0
                    ? Math.max(1, (int) Math.ceil(next.routeDurationSeconds / 60d)) : next.etaMin;
            tvNextPickupSubtitle.setText(String.format(Locale.US, "%.1f km away  ETA %d min", routeKm, routeMinutes));
            tvNextPickupStatus.setText(driverStatus(next.status));
        } else {
            nextPickupId = -1;
            nextPickupStatus = "";
            tvNextPickupAddress.setText("No pickup assigned");
            tvNextPickupSubtitle.setText("You're all caught up");
            tvNextPickupStatus.setText("");
        }

        loadTodaysRequests(driverId);
        loadNotificationBadge(driverId);
    }

    private void loadNotificationBadge(int driverId) {
        int unread = dbHelper.getUnreadNotificationCount(driverId);
        TextView tvBadge = findViewById(R.id.tvNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
    }

    private void loadTodaysRequests(int driverId) {
        requestsContainer.removeAllViews();
        List<PickupRow> todayTrips = dbHelper.getDriverTrips(driverId, "Today");

        if (todayTrips.isEmpty()) {
            tvNoRequestsToday.setVisibility(View.VISIBLE);
            return;
        }
        tvNoRequestsToday.setVisibility(View.GONE);

        for (PickupRow trip : todayTrips) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_driver_request, requestsContainer, false);
            TextView tvStatus = row.findViewById(R.id.tvRequestStatus);
            TextView tvAddress = row.findViewById(R.id.tvRequestAddress);
            TextView tvMeta = row.findViewById(R.id.tvRequestMeta);
            if (tvAddress != null) tvAddress.setText(PickupAddressFormatter.primary(trip));
            if (tvMeta != null) tvMeta.setText(PickupAddressFormatter.wardLine(trip));
            tvStatus.setText(driverStatus(trip.status));
            applyStatusStyle(tvStatus, trip.status);

            int tripId = trip.id;
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, DriverPickupDetailsActivity.class);
                intent.putExtra("tripId", tripId);
                startActivity(intent);
            });

            requestsContainer.addView(row);
        }
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        switch (status) {
            case "pending":
            case "Pending":
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "Assigned":
                tvStatus.setBackgroundResource(R.drawable.bg_status_assigned);
                tvStatus.setTextColor(0xFF1565C0);
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
            default: // Completed
                tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
        }
    }

    private static String driverStatus(String status){if(status==null)return "—";switch(status.toLowerCase()){case "assigned":return "New pickup request";case "accepted":return "Navigate / Start driving";case "on the way":return "Drive to resident";case "arrived":return "Start collection";case "collecting":return "Record actual weight";case "weight recorded":case "resident confirmation":return "Waiting for resident";case "price confirmed":case "payment pending":return "Waiting for payment";case "paid":return "Payment confirmed";case "completed":return "Job completed";default:return status;}}

    private String formatKm(double km) {
        if (km == Math.floor(km)) {
            return (int) km + " km";
        }
        return String.format(Locale.US, "%.1f km", km);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_trips) {
                startActivity(new Intent(this, DriverTripsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, DriverHistoryActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, DriverProfileActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
}
