package com.takago.app.resident;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.common.ImageUtils;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.location.ReadableLocationManager;
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
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.data.model.VehicleRow;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import com.takago.app.network.ApiClient;

public class ResidentHomeActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    private TextView tvUserName, tvUserWard, tvActiveStatus, tvActivePickupAddress, tvNoRecentRequests;
    private View cardActivePickup, cardRequestPickup, residentContentContainer;
    private LinearLayout recentRequestsContainer;
    private ImageView ivUserAvatar;
    private PickupRow activePickup;
    private boolean routeInvitationShown;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            com.takago.app.network.ServerSyncManager.syncAll(ResidentHomeActivity.this);
            liveHandler.postDelayed(() -> { loadActivePickup(); loadRecentRequests(); loadNotificationBadge(); }, 700);
            liveHandler.postDelayed(this, 2500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_home);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        tvUserName = findViewById(R.id.tvUserName);
        tvUserWard = findViewById(R.id.tvUserWard);
        tvActiveStatus = findViewById(R.id.tvActiveStatus);
        tvActivePickupAddress = findViewById(R.id.tvActivePickupAddress);
        cardActivePickup = findViewById(R.id.cardActivePickup);
        cardRequestPickup = findViewById(R.id.cardRequestPickup);
        residentContentContainer = findViewById(R.id.residentContentContainer);
        recentRequestsContainer = findViewById(R.id.recentRequestsContainer);
        tvNoRecentRequests = findViewById(R.id.tvNoRecentRequests);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);

        setupClicks();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvUserName.setText(session.getName());
        UserAccount resident = dbHelper.getUserById(session.getUserId());
        ReadableLocationManager.refresh(this, dbHelper, session.getUserId(),
                (primary, ward) -> tvUserWard.setText(LocationTextStyle.twoLine(primary, ward)));
        loadActivePickup();
        loadRecentRequests();
        loadNotificationBadge();
        loadRouteInvitation();

        ImageUtils.loadAvatar(ivUserAvatar, resident != null ? resident.profileImagePath : null);
        liveHandler.removeCallbacks(liveRefresh);
        liveHandler.post(liveRefresh);
    }

    @Override protected void onPause() {
        liveHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void loadNotificationBadge() {
        int unread = dbHelper.getUnreadNotificationCount(session.getUserId());
        TextView tvBadge = findViewById(R.id.tvNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
    }

    private void loadActivePickup() {
        activePickup = dbHelper.getActivePickupForResident(session.getUserId());
        if (activePickup == null) {
            cardActivePickup.setVisibility(View.GONE);
            applyDashboardCardSpacing(false);
            return;
        }
        cardActivePickup.setVisibility(View.VISIBLE);
        applyDashboardCardSpacing(true);
        tvActiveStatus.setText(residentStatus(activePickup.status));
        applyStatusStyle(tvActiveStatus, activePickup.status);
        tvActivePickupAddress.setText(PickupAddressFormatter.styledTwoLine(activePickup));

        UserAccount driver = activePickup.driverId > 0
                ? dbHelper.getUserById(activePickup.driverId) : null;
        VehicleRow vehicle = activePickup.assignedVehicleId > 0
                ? dbHelper.getVehicleById(activePickup.assignedVehicleId) : null;
        findViewById(R.id.layoutDriverInfo).setVisibility(driver != null ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvDriverName)).setText(driver != null ? safe(driver.name) : "Driver not assigned");
        ImageUtils.loadAvatar((ImageView) findViewById(R.id.ivDriverAvatar),
                driver != null ? driver.profileImagePath : null);
        String plate = vehicle != null ? safe(vehicle.plate) : (driver != null ? safe(driver.driverPlate) : "");
        String type = vehicle != null ? safe(vehicle.model) : (driver != null ? safe(driver.vehicleInfo) : "");
        ((TextView) findViewById(R.id.tvDriverDetails)).setText(joinMeta(plate, type));

        double distanceKm = activePickup.routeDistanceMeters > 0
                ? activePickup.routeDistanceMeters / 1000d : activePickup.distanceKm;
        int eta = activePickup.routeDurationSeconds > 0
                ? Math.max(1, activePickup.routeDurationSeconds / 60) : activePickup.etaMin;
        boolean driverArrived = driver != null
                && RoutingService.isValidCoordinate(driver.latitude, driver.longitude)
                && RoutingService.isValidCoordinate(activePickup.latitude, activePickup.longitude)
                && RoutingService.haversineKm(driver.latitude, driver.longitude,
                        activePickup.latitude, activePickup.longitude) <= 0.05d;
        if (driverArrived) { distanceKm = 0d; eta = 0; }
        String distance = driverArrived ? "At pickup location"
                : distanceKm > 0 ? String.format(Locale.US, "%.1f km away", distanceKm) : "Distance pending";
        String etaText = driverArrived ? "Driver arrived" : eta > 0 ? "ETA " + eta + " min" : "ETA pending";
        ((TextView) findViewById(R.id.tvEta)).setText(distance + "   " + etaText);

        TextView currentStop = findViewById(R.id.tvCurrentStop);
        int[] stops = dbHelper.getRouteStopSummary(activePickup.groupId, activePickup.id);
        if (activePickup.groupId > 0 && stops[1] > 0) {
            currentStop.setText("Current stop " + stops[0] + " - Your stop " + stops[2] + " of " + stops[1]);
            currentStop.setVisibility(View.VISIBLE);
        } else {
            currentStop.setVisibility(View.GONE);
        }
    }

    private void loadRecentRequests() {
        recentRequestsContainer.removeAllViews();
        List<PickupRow> recent = dbHelper.getRecentPickupsForResident(session.getUserId(), 3);

        if (recent.isEmpty()) {
            tvNoRecentRequests.setVisibility(View.VISIBLE);
            return;
        }
        tvNoRecentRequests.setVisibility(View.GONE);

        for (PickupRow pickup : recent) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_recent_request, recentRequestsContainer, false);
            TextView tvAddress = row.findViewById(R.id.tvRequestAddress);
            TextView tvMeta = row.findViewById(R.id.tvRequestMeta);
            TextView tvStatus = row.findViewById(R.id.tvRequestStatus);

            tvAddress.setText(PickupAddressFormatter.primary(pickup));
            tvMeta.setText(buildRequestMeta(pickup));
            tvStatus.setText(residentStatus(pickup.status));
            applyStatusStyle(tvStatus, pickup.status);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResidentPickupDetailsActivity.class);
                intent.putExtra("pickupId", pickup.id);
                startActivity(intent);
            });
            recentRequestsContainer.addView(row);
        }
    }

    private String buildRequestMeta(PickupRow pickup) {
        String wasteSize = pickup.category != null ? pickup.category : "Mixed";
        String meta = formatDate(pickup.pickupDate) + " - " + wasteSize;
        double kilograms = pickup.measuredWeightKg > 0 ? pickup.measuredWeightKg : pickup.weightKg;
        if (kilograms > 0) {
            meta += String.format(Locale.US, " - %.1f kg", kilograms);
        }
        if (pickup.distanceKm > 0) {
            meta += pickup.distanceKm < 1
                    ? String.format(Locale.US, " - %.0f m", pickup.distanceKm * 1000)
                    : String.format(Locale.US, " - %.1f km", pickup.distanceKm);
        }
        return meta;
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
        if (status == null) status = "Pending";
        switch (status) {
            case "Pending":
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                break;
            case "Assigned":
                tvStatus.setBackgroundResource(R.drawable.bg_status_assigned);
                tvStatus.setTextColor(0xFF1565C0);
                break;
            case "Cancelled":
                tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled);
                tvStatus.setTextColor(0xFFC62828);
                break;
            case "Completed":
                tvStatus.setBackgroundResource(R.drawable.bg_status_completed);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
            default: // On the way
                tvStatus.setBackgroundResource(R.drawable.bg_status_green);
                tvStatus.setTextColor(0xFF2E7D32);
                break;
        }
    }

    private void loadRouteInvitation() {
        if (routeInvitationShown || activePickup != null || session.getApiToken() == null) return;
        ApiClient.get("/route-invitations", session.getApiToken(), new ApiClient.JsonCallback() {
            @Override public void onSuccess(JSONObject json) {
                runOnUiThread(() -> {
                    JSONArray rows = json.optJSONArray("data");
                    if (routeInvitationShown || rows == null || rows.length() == 0) return;
                    JSONObject row = rows.optJSONObject(0); if (row == null) return;
                    routeInvitationShown = true;
                    int invitationId = row.optInt("id");
                    int distance = (int) Math.round(row.optDouble("distance_metres"));
                    new androidx.appcompat.app.AlertDialog.Builder(ResidentHomeActivity.this)
                            .setTitle("Collection vehicle nearby")
                            .setMessage("A waste collection vehicle is about " + distance + " metres away. Add your waste to this route to reduce waiting time and extra fuel use.")
                            .setPositiveButton("Request on this route", (dialog, which) -> startActivity(new Intent(ResidentHomeActivity.this, ResidentRequestPickupActivity.class).putExtra("route_invitation_id", invitationId)))
                            .setNegativeButton("Not now", null).show();
                });
            }
            @Override public void onError(String message) { }
        });
    }

    private static String residentStatus(String status) { if(status==null)return "Finding a driver...";switch(status.toLowerCase()){case "pending":return "Finding a driver...";case "assigned":return "Driver has been assigned";case "accepted":return "Driver accepted your request";case "on the way":return "Driver is on the way";case "arrived":return "Driver has arrived";case "collecting":return "Waste collection in progress";case "weight recorded":return "Actual weight recorded";case "resident confirmation":return "Confirm the collection";case "price confirmed":return "Final price is ready";case "payment pending":return "Choose payment method";case "paid":return "Payment successful";case "completed":return "Pickup completed";default:return status;}}

    private String formatDate(String isoDate) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat output = new SimpleDateFormat("d MMMM", Locale.US);
            return output.format(input.parse(isoDate));
        } catch (ParseException e) {
            return isoDate;
        }
    }

    private void setupClicks() {
        cardRequestPickup.setOnClickListener(v ->
                startActivity(new Intent(this, ResidentRequestPickupActivity.class)));

        findViewById(R.id.btnTrackDriver).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentTrackActivity.class)));

        findViewById(R.id.btnCall).setOnClickListener(v -> callAssignedDriver());
        findViewById(R.id.btnSms).setOnClickListener(v -> messageAssignedDriver());

        findViewById(R.id.btnViewDetails).setOnClickListener(v -> {
            if (activePickup == null) return;
            Intent intent = new Intent(this, ResidentPickupDetailsActivity.class);
            intent.putExtra("pickupId", activePickup.id);
            startActivity(intent);
        });

        findViewById(R.id.btnNotification).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_track) {
                startActivity(new Intent(this, ResidentTrackActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, ResidentHistoryActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ResidentProfileActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void callAssignedDriver() {
        UserAccount driver = activePickup != null && activePickup.driverId > 0
                ? dbHelper.getUserById(activePickup.driverId) : null;
        if (driver == null || driver.phone == null || driver.phone.trim().isEmpty()) {
            Toast.makeText(this, "Driver phone number is not available", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + driver.phone.trim())));
    }

    private void messageAssignedDriver() {
        UserAccount driver = activePickup != null && activePickup.driverId > 0
                ? dbHelper.getUserById(activePickup.driverId) : null;
        if (driver == null || driver.phone == null || driver.phone.trim().isEmpty()) {
            Toast.makeText(this, "Driver phone number is not available", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + driver.phone.trim()));
        intent.putExtra("sms_body", "Hello, I am contacting you about pickup "
                + (activePickup.code == null ? "" : activePickup.code) + ".");
        startActivity(intent);
    }

    private void applyDashboardCardSpacing(boolean hasActivePickup) {
        setTopMargin(residentContentContainer, hasActivePickup ? -28 : 0);
        setTopMargin(cardRequestPickup, hasActivePickup ? 18 : 24);
    }

    private void setTopMargin(View view, int dpValue) {
        if (view == null || !(view.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        int marginPx = dp(dpValue);
        if (params.topMargin == marginPx) return;
        params.topMargin = marginPx;
        view.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static String joinMeta(String first, String second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + " - " + second;
    }
}
