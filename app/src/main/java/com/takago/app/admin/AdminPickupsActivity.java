package com.takago.app.admin;

import com.takago.app.data.model.PickupRow;
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
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.Locale;

public class AdminPickupsActivity extends AppCompatActivity {

    private static final String[] FILTERS = {"All", "Pending", "Assigned", "On the way", "Completed", "Cancelled", "Expired"};

    private DatabaseHelper dbHelper;
    private LinearLayout pickupListContainer;
    private LinearLayout filterChipRow;
    private String selectedFilter = "All";
    private String scopedWard;
    private int municipalityId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_pickups);

        dbHelper = new DatabaseHelper(this);
        pickupListContainer = findViewById(R.id.pickupListContainer);
        filterChipRow = findViewById(R.id.filterChipRow);
        scopedWard = getIntent().getStringExtra("ward");
        UserAccount admin = dbHelper.getUserById(new SessionManager(this).getUserId());
        municipalityId = admin != null ? admin.municipalityId : -1;
        if (scopedWard != null && !scopedWard.trim().isEmpty()) {
            ((TextView) findViewById(R.id.tvPickupListTitle)).setText(scopedWard + " requests");
            findViewById(R.id.bottomNav).setVisibility(View.GONE);
        }

        buildFilterChips();
        if (scopedWard == null || scopedWard.trim().isEmpty()) {
            setupBottomNav();
        }
        loadPickups();
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
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                selectedFilter = filter;
                refreshChipStyles();
                loadPickups();
            });
            chip.setTag(filter);
            filterChipRow.addView(chip);
        }
        refreshChipStyles();
    }

    private void refreshChipStyles() {
        for (int i = 0; i < filterChipRow.getChildCount(); i++) {
            TextView chip = (TextView) filterChipRow.getChildAt(i);
            boolean selected = chip.getTag().equals(selectedFilter);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF555555);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadPickups() {
        pickupListContainer.removeAllViews();
        List<PickupRow> pickups = scopedWard != null && !scopedWard.trim().isEmpty()
                ? dbHelper.getPickupsInWard(scopedWard)
                : municipalityId > 0 ? dbHelper.getPickupsInMunicipality(municipalityId) : dbHelper.getAllPickups();

        for (PickupRow pickup : pickups) {
            if (!selectedFilter.equals("All") && !selectedFilter.equalsIgnoreCase(pickup.status)) {
                continue;
            }

            View row = LayoutInflater.from(this).inflate(R.layout.row_admin_pickup, pickupListContainer, false);
            TextView tvCode = row.findViewById(R.id.tvPickupCode);
            TextView tvCategory = row.findViewById(R.id.tvPickupCategory);
            TextView tvStatus = row.findViewById(R.id.tvPickupStatus);

            tvCode.setText(pickup.code + " · " + pickup.ward);
            tvCategory.setText(pickup.category);
            tvStatus.setText(pickup.status);
            applyStatusStyle(tvStatus, pickup.status);

            row.setOnClickListener(v -> showPickupDetails(pickup));

            pickupListContainer.addView(row);
        }
    }

    private void showPickupDetails(PickupRow pickup) {
        String driverName = "Unassigned";
        if (pickup.driverId != 0) {
            UserAccount driver = dbHelper.getUserById(pickup.driverId);
            if (driver != null) {
                driverName = driver.name;
            }
        }

        String details = "Ward: " + pickup.ward +
                "\nAddress: " + (pickup.address != null && !pickup.address.isEmpty() ? pickup.address : "-") +
                "\nCategory: " + pickup.category +
                "\nWaste type: " + (pickup.wasteType != null ? pickup.wasteType : "-") +
                "\nResident: " + (pickup.residentDisplayName != null ? pickup.residentDisplayName : "-") +
                "\nDate: " + (pickup.pickupDate != null ? pickup.pickupDate : "-") +
                "\nWeight: " + String.format(Locale.US, "%.1f kg", pickup.weightKg) +
                "\nDriver: " + driverName +
                "\nStatus: " + pickup.status +
                "\nPricing: " + (pickup.pricingStatus != null ? pickup.pricingStatus : "-") +
                (pickup.finalPrice > 0 ? String.format(Locale.US, "\nFinal price: TZS %,.0f", pickup.finalPrice) : "");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(pickup.code)
                .setMessage(details)
                .setNegativeButton("Close", null);

        if ("PendingApproval".equals(pickup.pricingStatus)) {
            builder.setPositiveButton("Set final price", (d, w) -> promptManualPrice(pickup));
        }

        builder.show();
    }

    private void promptManualPrice(PickupRow pickup) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Final price (TZS)");

        new AlertDialog.Builder(this)
                .setTitle("Set final price - " + pickup.code)
                .setMessage("This waste type requires manual pricing approval. Enter the final price to charge.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this, "Please enter a price", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double price;
                    try {
                        price = Double.parseDouble(text);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (price < 0) {
                        Toast.makeText(this, "Price cannot be negative", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dbHelper.setManualFinalPrice(pickup.id, price);
                    Toast.makeText(this, "Final price set", Toast.LENGTH_SHORT).show();
                    loadPickups();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        switch (status) {
            case "Pending":
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "Assigned":
                tvStatus.setBackgroundResource(R.drawable.bg_status_assigned);
                tvStatus.setTextColor(0xFF1565C0);
                break;
            case "Completed":
                tvStatus.setBackgroundResource(R.drawable.bg_status_completed);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            case "Cancelled":
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFFC62828);
                break;
            case "Expired":
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFF888888);
                break;
            default: // On the way
                tvStatus.setBackgroundResource(R.drawable.bg_status_green);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_pickups);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_pickups) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, MunicipalAdminHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_users) {
                startActivity(new Intent(this, AdminUsersActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_reports) {
                startActivity(new Intent(this, AdminReportsActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
