package com.takago.app.operator;

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

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class TruckOwnerProfileActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_truck_owner_profile);

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
        UserAccount owner = dbHelper.getUserById(session.getUserId());
        if (owner == null) {
            return;
        }

        ((TextView) findViewById(R.id.tvProfileName)).setText(owner.name);
        ((TextView) findViewById(R.id.tvProfileWard)).setText(
                (owner.ward != null ? owner.ward : "Upanga") + " Ward");
        ImageUtils.loadAvatar((ImageView) findViewById(R.id.ivProfileAvatar), owner.profileImagePath);

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
        findViewById(R.id.rowNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));

        findViewById(R.id.rowPrivacy).setOnClickListener(v -> {
            Intent intent = new Intent(this, InfoActivity.class);
            intent.putExtra(InfoActivity.EXTRA_TITLE, "Privacy & Security");
            intent.putExtra(InfoActivity.EXTRA_BODY,
                    "takaGo stores your fleet, driver and vehicle records locally on this device to run " +
                            "dispatch and approval features. Driver locations are only shared with residents " +
                            "while a pickup is active. Contact the Municipal Admin for account-level changes.");
            startActivity(intent);
        });
        findViewById(R.id.rowHelp).setOnClickListener(v -> {
            Intent intent = new Intent(this, InfoActivity.class);
            intent.putExtra(InfoActivity.EXTRA_TITLE, "Help Centre");
            intent.putExtra(InfoActivity.EXTRA_BODY,
                    "Register drivers and vehicles from the Drivers/Fleet tabs. New vehicles need Municipal " +
                            "Admin approval before a driver can be assigned to them. If a request stays " +
                            "unassigned too long, use Assign driver on Home to hand it to one of your own " +
                            "drivers manually.");
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

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, TruckOwnerHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_drivers) {
                startActivity(new Intent(this, TruckOwnerDriversActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_fleet) {
                startActivity(new Intent(this, TruckOwnerFleetActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
