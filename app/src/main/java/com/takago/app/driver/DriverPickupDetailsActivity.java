package com.takago.app.driver;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.common.InsetsUtils;
import com.takago.app.common.PickupAddressFormatter;
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
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.util.Locale;

/** Read-only detail view for a single pickup, reached by tapping a request card. */
public class DriverPickupDetailsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private int tripId;
    private String tripStatus;
    private int tripResidentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_pickup_details);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        tripId = getIntent().getIntExtra("tripId", -1);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCallResident).setOnClickListener(v -> callResident());
        findViewById(R.id.btnDetailsAction).setOnClickListener(v -> onActionClicked());
    }

    private void callResident() {
        UserAccount resident = tripResidentId > 0 ? dbHelper.getUserById(tripResidentId) : null;
        if (resident == null || resident.phone == null || resident.phone.trim().isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + resident.phone)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.takago.app.network.ServerSyncManager.syncTracking(this, this::loadDetails);
    }

    private void loadDetails() {
        PickupRow trip = dbHelper.getTripById(tripId);
        if (trip == null) {
            finish();
            return;
        }
        tripStatus = trip.status;
        tripResidentId = trip.residentId;
        ImageView wastePhoto=findViewById(R.id.ivResidentWastePhoto);
        if(trip.photoPath!=null&&!trip.photoPath.trim().isEmpty()){wastePhoto.setVisibility(View.VISIBLE);ImageUtils.loadAvatar(wastePhoto,trip.photoPath);}else wastePhoto.setVisibility(View.GONE);

        ((TextView) findViewById(R.id.tvDetailsCode)).setText("Pickup " + trip.code);
        ((TextView) findViewById(R.id.tvDetailsResidentName)).setText(
                trip.residentDisplayName != null ? trip.residentDisplayName : "Resident");
        ((TextView) findViewById(R.id.tvDetailsWard)).setText(
                PickupAddressFormatter.styledTwoLine(trip));
        ((TextView) findViewById(R.id.tvDetailsWasteSize)).setText(
                trip.category != null ? trip.category : "Mixed");
        ((TextView) findViewById(R.id.tvDetailsDistanceEta)).setText(
                String.format(Locale.US, "%.1f km  •  ETA %d min", trip.distanceKm, trip.etaMin));
        ((TextView) findViewById(R.id.tvDetailsCreatedAt)).setText(
                trip.createdAt != null ? trip.createdAt : trip.pickupDate);

        TextView tvStatus = findViewById(R.id.tvDetailsStatus);
        tvStatus.setText(trip.status);
        applyStatusStyle(tvStatus, trip.status);

        updateActionButton(trip.status);
    }

    private void updateActionButton(String status) {
        View btnAction = findViewById(R.id.btnDetailsAction);
        TextView tvActionLabel = findViewById(R.id.tvDetailsActionLabel);
        btnAction.setVisibility(View.VISIBLE);
        PickupStatusUi.DriverAction action = PickupStatusUi.driverAction(status);
        tvActionLabel.setText(PickupStatusUi.driverLabel(status));
        btnAction.setEnabled(action != PickupStatusUi.DriverAction.NONE);
        btnAction.setAlpha(action == PickupStatusUi.DriverAction.NONE ? 0.62f : 1f);
    }

    private void onActionClicked() {
        switch (PickupStatusUi.driverAction(tripStatus)) {
            case ACCEPT: transition("accepted", false); break;
            case START_TRIP: transition("on_the_way", true); break;
            case MARK_ARRIVED: openTripNavigation(); break;
            case START_COLLECTION: case RECORD_WEIGHT:
                startActivity(new Intent(this, DriverStartTripActivity.class).putExtra("tripId", tripId)); break;
            case FINISH: transition("completed", false); break;
            default: Toast.makeText(this, PickupStatusUi.driverLabel(tripStatus), Toast.LENGTH_SHORT).show();
        }
    }

    private void transition(String nextStatus, boolean openMap) {
        com.takago.app.network.ServerSyncManager.transition(this, tripId, nextStatus, null, null, null,
                error -> runOnUiThread(() -> {
                    if (error != null) { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); return; }
                    com.takago.app.network.ServerSyncManager.syncTracking(this,
                            () -> { loadDetails(); if (openMap) openTripNavigation(); });
                }));
    }

    private void openTripNavigation() {
        Intent intent = new Intent(this, DriverNavigationActivity.class);
        intent.putExtra("tripId", tripId);
        startActivity(intent);
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        switch (status) {
            case "pending":
            case "Pending":
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "Assigned":
                tvStatus.setBackgroundResource(R.drawable.bg_status_assigned);
                tvStatus.setTextColor(0xFF1565C0);
                break;
            case "On the way":
            case "Collected":
                tvStatus.setBackgroundResource(R.drawable.bg_status_green);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            case "Cancelled":
            case "Rejected":
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFFC62828);
                break;
            default: // Completed
                tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
        }
    }
}
