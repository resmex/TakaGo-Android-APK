package com.takago.app.operator;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.ui.CircularProgressView;
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
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class TruckOwnerHomeActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private View[] weekBars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_truck_owner_home);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        weekBars = new View[]{
                findViewById(R.id.weekBar1), findViewById(R.id.weekBar2), findViewById(R.id.weekBar3),
                findViewById(R.id.weekBar4), findViewById(R.id.weekBar5), findViewById(R.id.weekBar6),
                findViewById(R.id.weekBar7)
        };

        findViewById(R.id.btnAssignDriver).setOnClickListener(v -> {
            Intent intent = new Intent(this, ManualAssignActivity.class);
            intent.putExtra("operatorId", session.getUserId());
            startActivity(intent);
        });
        findViewById(R.id.btnStatDrivers).setOnClickListener(v ->
                startActivity(new Intent(this, TruckOwnerDriversActivity.class)));
        findViewById(R.id.btnStatTrucks).setOnClickListener(v ->
                startActivity(new Intent(this, TruckOwnerFleetActivity.class)));
        findViewById(R.id.btnGoEarnings).setOnClickListener(v ->
                startActivity(new Intent(this, EarningsActivity.class)));

        findViewById(R.id.btnNotification).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboard();
    }

    private void loadDashboard() {
        int operatorId = session.getUserId();
        UserAccount owner = dbHelper.getUserById(operatorId);
        if (owner != null) {
            ((TextView) findViewById(R.id.tvCompanyName)).setText(owner.name);
            ((TextView) findViewById(R.id.tvStatTrucks)).setText(String.valueOf(owner.fleetTrucks));
            ((TextView) findViewById(R.id.tvStatDrivers)).setText(String.valueOf(owner.fleetDrivers));
            ((TextView) findViewById(R.id.tvStatRating)).setText(String.valueOf(owner.rating));
            ((TextView) findViewById(R.id.tvEarningsWeek)).setText(owner.fleetEarningsWeek);
            ((TextView) findViewById(R.id.tvEarningsChange)).setText(owner.fleetEarningsChange);
        }

        int vehiclesActivePercent = dbHelper.getVehiclesActivePercent();
        CircularProgressView ring = findViewById(R.id.ringVehiclesActive);
        ring.setColors(0xFFEEEEEE, 0xFF43A047);
        ring.setProgress(vehiclesActivePercent);
        ((TextView) findViewById(R.id.tvVehiclesActivePercent)).setText(vehiclesActivePercent + "%");

        PickupRow latest = dbHelper.getLatestAssignedPickup();
        if (latest != null) {
            ((TextView) findViewById(R.id.tvLatestRequestAddress)).setText(latest.ward);
            ((TextView) findViewById(R.id.tvLatestRequestSubtitle)).setText(
                    String.format("%.1f km away  ETA %d min", latest.distanceKm, latest.etaMin));
            ((TextView) findViewById(R.id.tvLatestRequestStatus)).setText(latest.status);
        }

        loadNotificationBadge(operatorId);
        drawWeekChart();
    }

    private void loadNotificationBadge(int operatorId) {
        int unread = dbHelper.getUnreadNotificationCount(operatorId);
        TextView tvBadge = findViewById(R.id.tvNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
    }

    private void drawWeekChart() {
        int[] counts = dbHelper.getPickupsLast7Days();
        int maxCount = 1;
        for (int count : counts) {
            if (count > maxCount) {
                maxCount = count;
            }
        }

        float density = getResources().getDisplayMetrics().density;
        float maxBarHeightDp = 90f;

        for (int i = 0; i < weekBars.length; i++) {
            float heightDp = Math.max(4f, (counts[i] / (float) maxCount) * maxBarHeightDp);
            android.view.ViewGroup.LayoutParams params = weekBars[i].getLayoutParams();
            params.height = Math.round(heightDp * density);
            weekBars[i].setLayoutParams(params);
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_drivers) {
                startActivity(new Intent(this, TruckOwnerDriversActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_fleet) {
                startActivity(new Intent(this, TruckOwnerFleetActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, TruckOwnerProfileActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
}
