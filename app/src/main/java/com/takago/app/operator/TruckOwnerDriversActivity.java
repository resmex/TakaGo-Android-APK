package com.takago.app.operator;

import com.takago.app.data.model.UserAccount;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class TruckOwnerDriversActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LinearLayout driverListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_truck_owner_drivers);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        driverListContainer = findViewById(R.id.driverListContainer);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnAddDriver).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterDriverActivity.class)));

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDrivers();
    }

    private void loadDrivers() {
        driverListContainer.removeAllViews();
        // Only this Waste Operator's own registered drivers - never someone else's fleet.
        List<UserAccount> drivers = dbHelper.getDriversForOperator(session.getUserId());

        for (UserAccount driver : drivers) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_truck_owner_driver, driverListContainer, false);

            TextView tvName = row.findViewById(R.id.tvDriverName);
            TextView tvMeta = row.findViewById(R.id.tvDriverMeta);
            TextView tvRating = row.findViewById(R.id.tvDriverRating);
            TextView tvAvailability = row.findViewById(R.id.tvDriverAvailability);
            View ivMenu = row.findViewById(R.id.ivDriverMenu);

            tvName.setText(driver.name);
            tvMeta.setText(driver.driverPlate + "   " + driver.tripsCount + " trips");
            tvRating.setText(String.valueOf(driver.rating));
            tvAvailability.setText(driver.availabilityStatus);

            if ("Off".equals(driver.availabilityStatus)) {
                tvAvailability.setBackgroundResource(R.drawable.bg_chip_unselected);
                tvAvailability.setTextColor(0xFF888888);
            } else {
                tvAvailability.setBackgroundResource(R.drawable.bg_status_active);
                tvAvailability.setTextColor(0xFF2E7D32);
            }

            ivMenu.setOnClickListener(v ->
                    Toast.makeText(this, "Manage " + driver.name, Toast.LENGTH_SHORT).show());

            driverListContainer.addView(row);
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_drivers);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_drivers) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, TruckOwnerHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_fleet) {
                startActivity(new Intent(this, TruckOwnerFleetActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, TruckOwnerProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
