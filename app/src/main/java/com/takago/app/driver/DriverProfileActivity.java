package com.takago.app.driver;

import com.takago.app.data.model.UserAccount;
import com.takago.app.common.ImageUtils;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DriverProfileActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_profile);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        setupClicks();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        UserAccount driver = dbHelper.getUserById(session.getUserId());
        if (driver == null) {
            return;
        }

        TextView tvProfileName = findViewById(R.id.tvProfileName);
        TextView tvProfileLicense = findViewById(R.id.tvProfileLicense);
        TextView tvProfileVehicle = findViewById(R.id.tvProfileVehicle);
        ImageView ivProfileAvatar = findViewById(R.id.ivProfileAvatar);

        tvProfileName.setText(driver.name);
        if (driver.licenseInfo != null) {
            tvProfileLicense.setText(driver.licenseInfo);
        }
        if (driver.vehicleInfo != null) {
            tvProfileVehicle.setText(driver.vehicleInfo);
        }
        ImageUtils.loadAvatar(ivProfileAvatar, driver.profileImagePath);

        loadNotificationBadge();
    }

    private void loadNotificationBadge() {
        int unread = dbHelper.getUnreadNotificationCount(session.getUserId());
        TextView tvBadge = findViewById(R.id.tvProfileNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
    }

    private void setupClicks() {
        addTransactionsShortcut();
        findViewById(R.id.btnEdit).setOnClickListener(v ->
                startActivity(new Intent(this, DriverEditProfileActivity.class)));

        findViewById(R.id.rowVehicleDetails).setOnClickListener(v ->
                startActivity(new Intent(this, DriverCashActivity.class)));
        findViewById(R.id.rowLicenseDocuments).setOnClickListener(v ->
                Toast.makeText(this, "License & documents coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.rowNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));
        findViewById(R.id.rowLanguage).setOnClickListener(v ->
                Toast.makeText(this, "Language screen coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.rowPrivacy).setOnClickListener(v -> {
            Intent intent = new Intent(this, InfoActivity.class);
            intent.putExtra(InfoActivity.EXTRA_TITLE, "Privacy & Security");
            intent.putExtra(InfoActivity.EXTRA_BODY,
                    "takaGo stores your name, phone, ward and pickup history locally on this device " +
                            "to run the app's collection and dispatch features. Your live location is only " +
                            "shared with residents while you have an active pickup open. You can update or " +
                            "remove your profile photo at any time from Edit Profile.");
            startActivity(intent);
        });
        findViewById(R.id.rowHelp).setOnClickListener(v -> {
            Intent intent = new Intent(this, InfoActivity.class);
            intent.putExtra(InfoActivity.EXTRA_TITLE, "Help Centre");
            intent.putExtra(InfoActivity.EXTRA_BODY,
                    "Need help with a pickup? Accept a request from Trips, then use the in-trip screen to " +
                            "call the resident, navigate, and update status as you go. If a trip can't be " +
                            "completed, use Cancel and give a reason so the resident and Waste Operator are " +
                            "notified. For account issues, contact your Waste Operator or the Municipal Admin.");
            startActivity(intent);
        });

        findViewById(R.id.btnSignOut).setOnClickListener(v -> {
            session.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void addTransactionsShortcut() {
        View notifications = findViewById(R.id.rowNotifications);
        android.view.ViewGroup parent = (android.view.ViewGroup) notifications.getParent();
        TextView row = new TextView(this); row.setText("Transactions                                      ›");
        row.setTextSize(14); row.setTextColor(0xFF1A1A1A); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(16,0,16,0); row.setOnClickListener(v -> startActivity(new Intent(this, TransactionHistoryActivity.class)));
        parent.addView(row, parent.indexOfChild(notifications), new android.view.ViewGroup.LayoutParams(-1, (int)(56*getResources().getDisplayMetrics().density)));
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
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
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, DriverHistoryActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
