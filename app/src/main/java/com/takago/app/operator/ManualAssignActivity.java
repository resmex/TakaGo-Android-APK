package com.takago.app.operator;

import com.takago.app.data.model.PickupRow;
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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.network.ServerSyncManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown to a Ward Admin (ward-restricted) or a Waste Operator (own-drivers-restricted) so they
 * can manually assign a driver to a request that's overdue - either still Pending too long, or
 * Assigned but never accepted.
 */
public class ManualAssignActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private LinearLayout overdueListContainer;
    private TextView tvNoOverdue;

    /** If set (>0), restricts assignment to this Waste Operator's own drivers only. */
    private int operatorId = -1;
    /** If set, restricts the request list to one ward (Ward Admin mode). */
    private String ward;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_assign);

        dbHelper = new DatabaseHelper(this);
        overdueListContainer = findViewById(R.id.overdueListContainer);
        tvNoOverdue = findViewById(R.id.tvNoOverdue);

        operatorId = getIntent().getIntExtra("operatorId", -1);
        ward = getIntent().getStringExtra("ward");

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOverdueRequests();
    }

    private void loadOverdueRequests() {
        overdueListContainer.removeAllViews();

        List<PickupRow> candidates = ward != null
                ? dbHelper.getActionablePickupsInWard(ward)
                : dbHelper.getAllActionablePickups();

        List<PickupRow> overdue = new ArrayList<>();
        for (PickupRow pickup : candidates) {
            if (dbHelper.isOverdue(pickup)) {
                overdue.add(pickup);
            }
        }

        if (overdue.isEmpty()) {
            tvNoOverdue.setVisibility(View.VISIBLE);
            return;
        }
        tvNoOverdue.setVisibility(View.GONE);

        for (PickupRow pickup : overdue) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_overdue_pickup, overdueListContainer, false);
            ((TextView) row.findViewById(R.id.tvOverdueWard)).setText(pickup.ward);
            ((TextView) row.findViewById(R.id.tvOverdueMeta)).setText(pickup.code + " · " + pickup.status);
            row.findViewById(R.id.btnAssignDriver).setOnClickListener(v -> showDriverPicker(pickup));
            overdueListContainer.addView(row);
        }
    }

    private void showDriverPicker(PickupRow pickup) {
        List<UserAccount> candidates = operatorId > 0
                ? dbHelper.getDriversForOperator(operatorId)
                : dbHelper.getDriversInWard(pickup.ward);

        List<UserAccount> eligible = new ArrayList<>();
        for (UserAccount driver : candidates) {
            if (pickup.ward.equals(driver.ward)) {
                eligible.add(driver);
            }
        }

        if (eligible.isEmpty()) {
            Toast.makeText(this, "No eligible drivers in this ward", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            names[i] = eligible.get(i).name + " (" + eligible.get(i).availabilityStatus + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Assign driver to " + pickup.code)
                .setItems(names, (dialog, which) -> {
                    UserAccount selected = eligible.get(which);
                    ServerSyncManager.assignPickup(this, pickup.id, selected.id, error -> runOnUiThread(() -> {
                        if (error != null) {
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        dbHelper.assignDriverManually(pickup.id, selected.id);
                        Toast.makeText(this, "Assigned to " + selected.name, Toast.LENGTH_SHORT).show();
                        loadOverdueRequests();
                    }));
                })
                .show();
    }
}
