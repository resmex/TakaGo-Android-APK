package com.takago.app.admin;

import com.takago.app.data.model.VehicleRow;
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
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.util.List;

public class VehicleApprovalsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private LinearLayout vehicleListContainer;
    private int municipalityId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_approvals);

        dbHelper = new DatabaseHelper(this);
        UserAccount admin = dbHelper.getUserById(new SessionManager(this).getUserId());
        municipalityId = admin != null ? admin.municipalityId : -1;
        vehicleListContainer = findViewById(R.id.vehicleListContainer);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadVehicles();
    }

    private void loadVehicles() {
        vehicleListContainer.removeAllViews();
        List<VehicleRow> vehicles = municipalityId > 0
                ? dbHelper.getVehiclesInMunicipality(municipalityId)
                : dbHelper.getAllVehicles();

        for (VehicleRow vehicle : vehicles) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_vehicle_approval, vehicleListContainer, false);

            TextView tvPlate = row.findViewById(R.id.tvVehiclePlate);
            TextView tvModel = row.findViewById(R.id.tvVehicleModel);
            TextView tvStatus = row.findViewById(R.id.tvVehicleStatus);
            LinearLayout actionsRow = row.findViewById(R.id.approvalActionsRow);

            tvPlate.setText(vehicle.plate);
            tvModel.setText(vehicle.model);
            tvStatus.setText(vehicle.status);
            applyStatusStyle(tvStatus, vehicle.status);

            if ("Pending".equals(vehicle.status)) {
                actionsRow.setVisibility(View.VISIBLE);
                row.findViewById(R.id.btnApprove).setOnClickListener(v -> {
                    dbHelper.updateVehicleStatus(vehicle.id, "Approved");
                    loadVehicles();
                });
                row.findViewById(R.id.btnReject).setOnClickListener(v -> promptRejectReason(vehicle));
            }

            vehicleListContainer.addView(row);
        }
    }

    private void promptRejectReason(VehicleRow vehicle) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Reason for rejecting " + vehicle.plate);

        new AlertDialog.Builder(this)
                .setTitle("Reject vehicle")
                .setMessage("A reason is required so the Waste Operator knows what to fix.")
                .setView(input)
                .setPositiveButton("Reject", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dbHelper.updateVehicleStatus(vehicle.id, "Rejected", reason);
                    loadVehicles();
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
}
