package com.takago.app.operator;

import com.takago.app.data.model.VehicleRow;
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

import androidx.appcompat.app.AlertDialog;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class TruckOwnerFleetActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LinearLayout vehicleListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_truck_owner_fleet);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        vehicleListContainer = findViewById(R.id.vehicleListContainer);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnAddVehicle).setOnClickListener(v ->
                startActivity(new Intent(this, SubmitVehicleActivity.class)));

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVehicles();
    }

    private void loadVehicles() {
        vehicleListContainer.removeAllViews();
        // Only this Waste Operator's own fleet - never someone else's vehicles.
        List<VehicleRow> vehicles = dbHelper.getVehiclesForOperator(session.getUserId());

        for (VehicleRow vehicle : vehicles) {
            // Reuses the Admin's vehicle row layout, but its Approve/Reject actions stay hidden here
            // since the Waste Operator can only view fleet status, not approve vehicles.
            View row = LayoutInflater.from(this).inflate(R.layout.row_vehicle_approval, vehicleListContainer, false);

            TextView tvPlate = row.findViewById(R.id.tvVehiclePlate);
            TextView tvModel = row.findViewById(R.id.tvVehicleModel);
            TextView tvStatus = row.findViewById(R.id.tvVehicleStatus);

            tvPlate.setText(vehicle.plate);
            tvModel.setText(vehicle.model);
            tvStatus.setText(vehicle.status);
            applyStatusStyle(tvStatus, vehicle.status);

            if ("Rejected".equals(vehicle.status)) {
                row.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle(vehicle.plate + " rejected")
                        .setMessage(vehicle.rejectionReason != null ? vehicle.rejectionReason : "No reason given.")
                        .setPositiveButton("OK", null)
                        .show());
            }

            vehicleListContainer.addView(row);
        }
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        switch (status) {
            case "Pending":
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "Rejected":
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFFC62828);
                break;
            default: // Approved
                tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_fleet);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_fleet) {
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
