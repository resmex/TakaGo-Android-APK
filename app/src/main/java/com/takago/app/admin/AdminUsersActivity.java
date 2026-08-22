package com.takago.app.admin;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class AdminUsersActivity extends AppCompatActivity {

    private static final String[] FILTER_LABELS = {"All", "Residents", "Drivers", "Owners", "Councillors", "Admins"};
    private static final String[] FILTER_ROLES = {
            null, DatabaseHelper.ROLE_RESIDENT, DatabaseHelper.ROLE_DRIVER,
            DatabaseHelper.ROLE_TRUCK_OWNER, DatabaseHelper.ROLE_WARD_ADMIN, DatabaseHelper.ROLE_MUNICIPAL_ADMIN
    };

    private DatabaseHelper dbHelper;
    private LinearLayout userListContainer;
    private LinearLayout filterChipRow;
    private String selectedRole; // null = All
    private int municipalityId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);

        dbHelper = new DatabaseHelper(this);
        UserAccount admin = dbHelper.getUserById(new SessionManager(this).getUserId());
        municipalityId = admin != null ? admin.municipalityId : -1;
        userListContainer = findViewById(R.id.userListContainer);
        filterChipRow = findViewById(R.id.filterChipRow);

        findViewById(R.id.btnAddUser).setOnClickListener(v -> showRegisterChoice());

        buildFilterChips();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsers();
    }

    private void buildFilterChips() {
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            String role = FILTER_ROLES[i];
            TextView chip = new TextView(this);
            chip.setText(FILTER_LABELS[i]);
            chip.setTextSize(13);
            chip.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);
            chip.setTag(role);
            chip.setOnClickListener(v -> {
                selectedRole = role;
                refreshChipStyles();
                loadUsers();
            });
            filterChipRow.addView(chip);
        }
        refreshChipStyles();
    }

    private void refreshChipStyles() {
        for (int i = 0; i < filterChipRow.getChildCount(); i++) {
            TextView chip = (TextView) filterChipRow.getChildAt(i);
            boolean selected = chip.getTag() == null ? selectedRole == null : chip.getTag().equals(selectedRole);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF555555);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showRegisterChoice() {
        String[] options = {"Register Waste Operator", "Register Ward Admin"};
        new AlertDialog.Builder(this)
                .setTitle("Register a new user")
                .setItems(options, (dialog, which) -> {
                    Intent intent = which == 0
                            ? new Intent(this, RegisterWasteOperatorActivity.class)
                            : new Intent(this, RegisterWardAdminActivity.class);
                    startActivity(intent);
                })
                .show();
    }

    private void loadUsers() {
        userListContainer.removeAllViews();
        List<UserAccount> users = municipalityId > 0
                ? dbHelper.getUsersInMunicipality(municipalityId)
                : dbHelper.getAllUsers();

        for (UserAccount user : users) {
            if (selectedRole != null && !selectedRole.equals(user.role)) {
                continue;
            }

            View row = LayoutInflater.from(this).inflate(R.layout.row_admin_user, userListContainer, false);

            TextView tvInitial = row.findViewById(R.id.tvUserInitial);
            TextView tvName = row.findViewById(R.id.tvUserName);
            TextView tvRole = row.findViewById(R.id.tvUserRole);
            TextView tvStatus = row.findViewById(R.id.tvUserStatus);
            ImageView ivMenu = row.findViewById(R.id.ivUserMenu);

            tvInitial.setText(user.name.substring(0, 1).toUpperCase());
            tvName.setText(user.name);
            tvRole.setText(user.role);
            tvStatus.setText(user.status);

            if ("Suspended".equals(user.status)) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFFC62828);
            } else {
                tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                tvStatus.setTextColor(0xFF2E7D32);
            }

            ivMenu.setOnClickListener(v -> showManageDialog(user));

            userListContainer.addView(row);
        }
    }

    private void showManageDialog(UserAccount user) {
        boolean suspended = "Suspended".equals(user.status);
        String[] options = {"View details", suspended ? "Activate account" : "Suspend account"};

        new AlertDialog.Builder(this)
                .setTitle(user.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showUserDetails(user);
                    } else {
                        dbHelper.updateUserStatus(user.id, suspended ? "Active" : "Suspended");
                        Toast.makeText(this,
                                user.name + (suspended ? " reactivated" : " suspended"),
                                Toast.LENGTH_SHORT).show();
                        loadUsers();
                    }
                })
                .show();
    }

    private void showUserDetails(UserAccount user) {
        String details = "Role: " + user.role +
                "\nEmail: " + user.email +
                "\nPhone: " + user.phone +
                "\nWard: " + (user.ward != null ? user.ward : "-") +
                "\nStatus: " + user.status;

        new AlertDialog.Builder(this)
                .setTitle(user.name)
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_users);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_users) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, MunicipalAdminHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_pickups) {
                startActivity(new Intent(this, AdminPickupsActivity.class));
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
