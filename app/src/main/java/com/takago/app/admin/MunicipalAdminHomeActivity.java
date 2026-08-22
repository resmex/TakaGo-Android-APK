package com.takago.app.admin;

import com.takago.app.ui.CircularProgressView;
import com.takago.app.data.model.UserAccount;
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

import java.util.Locale;

public class MunicipalAdminHomeActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    private TextView tvCouncilName, tvTotalUsers, tvTotalOperators, tvTotalDrivers, tvTotalTrucks;
    private TextView tvVehiclesActivePercent, tvRecycledTons, tvVehicleApprovals, tvComplaints;
    private CircularProgressView ringVehiclesActive;
    private View[] bars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_municipal_admin_home);

        dbHelper = new DatabaseHelper(this);

        tvCouncilName = findViewById(R.id.tvCouncilName);
        tvTotalUsers = findViewById(R.id.tvTotalUsers);
        tvTotalOperators = findViewById(R.id.tvTotalOperators);
        tvTotalDrivers = findViewById(R.id.tvTotalDrivers);
        tvTotalTrucks = findViewById(R.id.tvTotalTrucks);
        tvVehiclesActivePercent = findViewById(R.id.tvVehiclesActivePercent);
        tvRecycledTons = findViewById(R.id.tvRecycledTons);
        tvVehicleApprovals = findViewById(R.id.tvVehicleApprovals);
        tvComplaints = findViewById(R.id.tvComplaints);
        ringVehiclesActive = findViewById(R.id.ringVehiclesActive);

        bars = new View[]{
                findViewById(R.id.bar1), findViewById(R.id.bar2), findViewById(R.id.bar3),
                findViewById(R.id.bar4), findViewById(R.id.bar5), findViewById(R.id.bar6),
                findViewById(R.id.bar7)
        };

        findViewById(R.id.btnNotification).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));

        setupMenuRows();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void loadDashboardData() {
        SessionManager session = new SessionManager(this);
        UserAccount admin = dbHelper.getUserById(session.getUserId());
        int municipalityId = admin != null ? admin.municipalityId : -1;
        String councilName = session.getName();
        if (councilName != null && !councilName.isEmpty()) {
            tvCouncilName.setText(councilName);
        }

        loadNotificationBadge(session.getUserId());

        tvTotalUsers.setText(formatCount(municipalityId > 0 ? dbHelper.getTotalUsersInMunicipality(municipalityId) : dbHelper.getTotalUsers()));
        tvTotalOperators.setText(String.valueOf(municipalityId > 0 ? dbHelper.getTotalOperatorsInMunicipality(municipalityId) : dbHelper.getTotalOperators()));
        tvTotalDrivers.setText(String.valueOf(municipalityId > 0 ? dbHelper.getTotalDriversInMunicipality(municipalityId) : dbHelper.getTotalDrivers()));
        tvTotalTrucks.setText(String.valueOf(municipalityId > 0 ? dbHelper.getTotalTrucksInMunicipality(municipalityId) : dbHelper.getTotalTrucks()));

        int activePercent = dbHelper.getVehiclesActivePercent();
        ringVehiclesActive.setProgress(activePercent);
        tvVehiclesActivePercent.setText(activePercent + "%");

        double recycledTons = dbHelper.getRecycledTonsMonth();
        tvRecycledTons.setText(formatTons(recycledTons) + " TONS");

        int pendingApprovals = municipalityId > 0 ? dbHelper.getPendingVehicleApprovalsCountInMunicipality(municipalityId) : dbHelper.getPendingVehicleApprovalsCount();
        tvVehicleApprovals.setText("Vehicle approvals · " + pendingApprovals);

        int openComplaints = municipalityId > 0 ? dbHelper.getOpenComplaintsCountInMunicipality(municipalityId) : dbHelper.getOpenComplaintsCount();
        tvComplaints.setText("Complaints · " + openComplaints + " Open");

        drawPickupsChart();
    }

    private void loadNotificationBadge(int userId) {
        int unread = dbHelper.getUnreadNotificationCount(userId);
        TextView tvBadge = findViewById(R.id.tvNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
    }

    private void drawPickupsChart() {
        int[] counts = dbHelper.getPickupsLast7Days();
        int maxCount = 1;
        for (int count : counts) {
            if (count > maxCount) {
                maxCount = count;
            }
        }

        float maxBarHeightDp = 100f;
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < bars.length; i++) {
            float heightDp = Math.max(4f, (counts[i] / (float) maxCount) * maxBarHeightDp);
            android.view.ViewGroup.LayoutParams params = bars[i].getLayoutParams();
            params.height = Math.round(heightDp * density);
            bars[i].setLayoutParams(params);
        }
    }

    private String formatCount(int value) {
        if (value >= 1000) {
            double thousands = value / 1000.0;
            return String.format(Locale.US, "%.1fK", thousands);
        }
        return String.valueOf(value);
    }

    private String formatTons(double tons) {
        if (tons == Math.floor(tons)) {
            return String.valueOf((int) tons);
        }
        return String.format(Locale.US, "%.1f", tons);
    }

    private void setupMenuRows() {
        findViewById(R.id.rowManageUsers).setOnClickListener(v ->
                startActivity(new Intent(this, AdminUsersActivity.class)));

        findViewById(R.id.rowManageWards).setOnClickListener(v ->
                startActivity(new Intent(this, ManageWardsActivity.class)));

        findViewById(R.id.rowPickupRequests).setOnClickListener(v ->
                startActivity(new Intent(this, AdminPickupsActivity.class)));

        findViewById(R.id.rowVehicleApprovals).setOnClickListener(v ->
                startActivity(new Intent(this, VehicleApprovalsActivity.class)));

        findViewById(R.id.rowComplaints).setOnClickListener(v ->
                startActivity(new Intent(this, ComplaintsActivity.class)));

        findViewById(R.id.rowReports).setOnClickListener(v ->
                startActivity(new Intent(this, AdminReportsActivity.class)));

        findViewById(R.id.rowPricingSettings).setOnClickListener(v ->
                startActivity(new Intent(this, PricingSettingsActivity.class)));

        findViewById(R.id.rowSignOut).setOnClickListener(v -> {
            new SessionManager(this).clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_users) {
                startActivity(new Intent(this, AdminUsersActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_pickups) {
                startActivity(new Intent(this, AdminPickupsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_reports) {
                startActivity(new Intent(this, AdminReportsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
}
