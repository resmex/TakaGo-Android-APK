package com.takago.app.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.R;
import com.takago.app.auth.LoginActivity;
import com.takago.app.data.model.UserAccount;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.notifications.NotificationActivity;
import com.takago.app.operator.ManualAssignActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class WardAdminHomeActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private String ward;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ward_admin_home);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        ward = currentWard();

        findViewById(R.id.btnNotification).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));
        findViewById(R.id.rowWardRequests).setOnClickListener(v ->
                startActivity(new Intent(this, AdminPickupsActivity.class).putExtra("ward", ward)));
        findViewById(R.id.rowOverdueRequests).setOnClickListener(v ->
                startActivity(new Intent(this, ManualAssignActivity.class).putExtra("ward", ward)));
        findViewById(R.id.rowWardComplaints).setOnClickListener(v ->
                startActivity(new Intent(this, ComplaintsActivity.class).putExtra("ward", ward)));
        findViewById(R.id.rowWardDrivers).setOnClickListener(v -> showWardDrivers());

        findViewById(R.id.rowSignOut).setOnClickListener(v -> {
            session.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ward = currentWard();
        loadWardSummary();
    }

    private String currentWard() {
        UserAccount admin = dbHelper.getUserById(session.getUserId());
        if (admin != null && admin.ward != null && !admin.ward.trim().isEmpty()) {
            return admin.ward;
        }
        return "Magomeni";
    }

    private void loadWardSummary() {
        int pending = dbHelper.getPickupCountInWard(ward, "Pending");
        int assigned = dbHelper.getPickupCountInWard(ward, "Assigned");
        int onTheWay = dbHelper.getPickupCountInWard(ward, "On the way");
        int completed = dbHelper.getPickupCountInWard(ward, "Completed");
        int drivers = dbHelper.getDriversInWard(ward).size();
        int residents = dbHelper.getUserCountInWard(ward, DatabaseHelper.ROLE_RESIDENT);
        int complaints = dbHelper.getOpenComplaintsCountInWard(ward);

        loadNotificationBadge();
        ((TextView) findViewById(R.id.tvWardName)).setText(ward + " Ward");
        ((TextView) findViewById(R.id.tvWardPending)).setText(String.valueOf(pending));
        ((TextView) findViewById(R.id.tvWardCompleted)).setText(String.valueOf(completed));
        ((TextView) findViewById(R.id.tvWardComplaints)).setText(String.valueOf(complaints));
        ((TextView) findViewById(R.id.tvWardDrivers)).setText(String.valueOf(drivers));
        ((TextView) findViewById(R.id.tvWardResidents)).setText(String.valueOf(residents));
        ((TextView) findViewById(R.id.tvWardActive)).setText(String.valueOf(pending + assigned + onTheWay));
        ((TextView) findViewById(R.id.tvAssignRequests)).setText("Assign pending requests - " + pending);
        ((TextView) findViewById(R.id.tvComplaintsRow)).setText("Complaints - " + complaints + " Open");
    }

    private void loadNotificationBadge() {
        int unread = dbHelper.getUnreadNotificationCount(session.getUserId());
        TextView tvBadge = findViewById(R.id.tvNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(android.view.View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(android.view.View.GONE);
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_users) {
                showWardDrivers();
                return true;
            } else if (id == R.id.nav_pickups) {
                startActivity(new Intent(this, AdminPickupsActivity.class).putExtra("ward", ward));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_reports) {
                startActivity(new Intent(this, ComplaintsActivity.class).putExtra("ward", ward));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void showWardDrivers() {
        List<UserAccount> drivers = dbHelper.getDriversInWard(ward);
        if (drivers.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(ward + " drivers")
                    .setMessage("No drivers are assigned to this ward.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }

        String[] rows = new String[drivers.size()];
        for (int i = 0; i < drivers.size(); i++) {
            UserAccount driver = drivers.get(i);
            String status = driver.availabilityStatus == null ? "Unknown" : driver.availabilityStatus;
            String plate = driver.driverPlate == null || driver.driverPlate.trim().isEmpty()
                    ? "No vehicle" : driver.driverPlate;
            rows[i] = driver.name + "\n" + status + " - " + plate;
        }

        new AlertDialog.Builder(this)
                .setTitle(ward + " drivers")
                .setItems(rows, null)
                .setPositiveButton("Close", null)
                .show();
    }
}
