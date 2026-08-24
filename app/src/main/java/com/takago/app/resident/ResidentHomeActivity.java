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
import android.Manifest;
import android.content.pm.PackageManager;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

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

    private TextView tvUserName, tvUserLocation, tvUserWard, tvActiveStatus, tvActivePickupAddress, tvNoRecentRequests;
    private View cardActivePickup, cardRequestPickup, residentContentContainer;
    private LinearLayout recentRequestsContainer;
    private ImageView ivUserAvatar;
    private PickupRow activePickup;
    private boolean routeInvitationShown;
    private TextView tvNextSchedule;
    private String lastHeaderLocation = "", lastHeaderWard = "";
    private String lastAvatarPath = null;
    private boolean locationPermissionRequested;
    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) refreshHeaderLocation();
                else if (lastHeaderLocation.isEmpty()) tvUserLocation.setText("Allow location access to detect your address");
            });
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            com.takago.app.network.ServerSyncManager.syncAll(ResidentHomeActivity.this);
            liveHandler.postDelayed(() -> { loadHeader(); loadActivePickup(); loadRecentRequests(); loadNotificationBadge(); loadNextSchedule(); }, 700);
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
        tvUserLocation = findViewById(R.id.tvUserLocation);
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
        addNextScheduleCard();
        com.takago.app.notifications.MyFirebaseMessagingService.registerAuthenticatedDevice(this);
        com.takago.app.notifications.MyFirebaseMessagingService.requestNotificationPermission(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHeader();
        UserAccount resident = dbHelper.getUserById(session.getUserId());
        ensureHeaderLocation();
        loadActivePickup();
        loadRecentRequests();
        loadNotificationBadge();
        loadRouteInvitation();
        loadNextSchedule();

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
        tvActivePickupAddress.setText(joinMeta(PickupAddressFormatter.primary(activePickup), PickupAddressFormatter.wardLine(activePickup)));
        updateResidentActionButton(activePickup.status);

        UserAccount driver = activePickup.driverId > 0
                ? dbHelper.getUserById(activePickup.driverId) : null;
        VehicleRow vehicle = activePickup.assignedVehicleId > 0
                ? dbHelper.getVehicleById(activePickup.assignedVehicleId) : null;
        findViewById(R.id.layoutDriverInfo).setVisibility(driver != null ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvDriverName)).setText(driver != null ? safe(driver.name) : "Driver not assigned");
        ImageUtils.loadAvatar((ImageView) findViewById(R.id.ivDriverAvatar),
                driver != null ? driver.profileImagePath : null);
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
        ((TextView) findViewById(R.id.tvDriverDetails)).setText(etaText + " · " + distance);

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

            tvAddress.setText(nonEmpty(PickupAddressFormatter.primary(pickup), "Pickup " + nonEmpty(pickup.code, "request")));
            tvMeta.setText(buildRequestMeta(pickup));
            tvStatus.setText(residentStatus(pickup.status));
            applyStatusStyle(tvStatus, pickup.status);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResidentPickupDetailsActivity.class);
                intent.putExtra("pickupId", pickup.id);
                startActivity(intent);
            });
            TextView receipt = row.findViewById(R.id.tvViewReceipt);
            boolean completed = "completed".equals(PickupStatusUi.normalize(pickup.status));
            receipt.setVisibility(completed ? View.VISIBLE : View.GONE);
            receipt.setOnClickListener(v -> startActivity(new Intent(this, ReceiptActivity.class).putExtra("pickupId", pickup.id)));
            recentRequestsContainer.addView(row);
        }
    }

    private String buildRequestMeta(PickupRow pickup) {
        String location = clean(PickupAddressFormatter.wardLine(pickup));
        String wasteSize = nonEmpty(clean(pickup.wasteType), nonEmpty(clean(pickup.category), "Mixed waste"));
        StringBuilder meta = new StringBuilder();
        if (!location.isEmpty()) meta.append(location).append(" · ");
        meta.append(wasteSize);
        double kilograms = pickup.measuredWeightKg > 0 ? pickup.measuredWeightKg : pickup.weightKg;
        if (kilograms > 0) meta.append(String.format(Locale.US, " · %.1f kg", kilograms));
        return meta.toString();
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

    private void ensureHeaderLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            refreshHeaderLocation();
        } else if (!locationPermissionRequested) {
            locationPermissionRequested = true;
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void refreshHeaderLocation() {
        ReadableLocationManager.refresh(this, dbHelper, session.getUserId(), this::updateHeaderLocation);
    }

    private void loadHeader() {
        tvUserName.setText(HeaderTextStyle.residentWelcome(session.getName()));
        UserAccount resident = dbHelper.getUserById(session.getUserId());
        if (resident == null) return;
        updateHeaderLocation(ReadableLocationManager.primary(resident),
                ReadableLocationManager.wardLine(resident));
        String path = resident.profileImagePath == null ? "" : resident.profileImagePath.trim();
        if (!path.equals(lastAvatarPath)) {
            lastAvatarPath = path;
            ImageUtils.loadAvatar(ivUserAvatar, path);
        }
    }

    private void updateHeaderLocation(String location, String ward) {
        String nextLocation = location == null ? "" : location.trim();
        String nextWard = ward == null ? "" : ward.trim();
        if (!nextLocation.isEmpty() && !nextLocation.equals(lastHeaderLocation)) {
            lastHeaderLocation = nextLocation;
            tvUserLocation.setText(nextLocation);
        }
        if (!nextWard.isEmpty() && !nextWard.equals(lastHeaderWard)) {
            lastHeaderWard = nextWard;
            tvUserWard.setText(nextWard);
        }
    }

    private void updateResidentActionButton(String status) {
        TextView button = findViewById(R.id.btnTrackDriver);
        PickupStatusUi.ResidentAction action = PickupStatusUi.residentAction(status);
        button.setText(PickupStatusUi.residentLabel(status));
        button.setEnabled(action != PickupStatusUi.ResidentAction.NONE);
        button.setAlpha(action == PickupStatusUi.ResidentAction.NONE ? 0.62f : 1f);
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

        findViewById(R.id.btnTrackDriver).setOnClickListener(v -> {
            if (activePickup == null) return;
            switch (PickupStatusUi.residentAction(activePickup.status)) {
                case FIND_DRIVER:
                    startActivity(new Intent(this, FindingDriverActivity.class)
                            .putExtra("pickupId", activePickup.id));
                    break;
                case TRACK:
                    startActivity(new Intent(this, ResidentTrackActivity.class));
                    break;
                case REVIEW_COLLECTION:
                case PAY:
                    startActivity(new Intent(this, ResidentPickupDetailsActivity.class)
                            .putExtra("pickupId", activePickup.id));
                    break;
                case RECEIPT:
                    startActivity(new Intent(this, ReceiptActivity.class).putExtra("pickupId", activePickup.id));
                    break;
                default:
                    Toast.makeText(this, PickupStatusUi.residentLabel(activePickup.status), Toast.LENGTH_SHORT).show();
            }
        });

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

    private void addNextScheduleCard() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackgroundResource(R.drawable.bg_card);
        TextView heading = new TextView(this); heading.setText("Next collection schedule"); heading.setTextSize(18); heading.setTextColor(0xFF123B32);
        LinearLayout scheduleRow=new LinearLayout(this);scheduleRow.setOrientation(LinearLayout.HORIZONTAL);scheduleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);scheduleRow.setPadding(0,dp(6),0,0);
        tvNextSchedule = new TextView(this); tvNextSchedule.setText("Checking your ward schedule..."); tvNextSchedule.setTextSize(14);tvNextSchedule.setMaxLines(2);
        TextView all = new TextView(this); all.setText("View all schedules  ›"); all.setTextColor(0xFF087F5B); all.setTextSize(15);
        all.setText("View schedules >");all.setTextSize(14);
        scheduleRow.addView(tvNextSchedule,new LinearLayout.LayoutParams(0,-2,1));scheduleRow.addView(all);
        card.addView(heading); card.addView(scheduleRow);
        card.setOnClickListener(v -> startActivity(new Intent(this, ResidentSchedulesActivity.class)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(14), 0, dp(4));
        ((LinearLayout) residentContentContainer).addView(card, 2, p);
    }

    private void loadNextSchedule() {
        if (tvNextSchedule == null || session.getApiToken().isEmpty()) return;
        ApiClient.get("/resident/schedules", session.getApiToken(), new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) { runOnUiThread(() -> { JSONArray rows=json.optJSONArray("data");
                if(rows==null||rows.length()==0){tvNextSchedule.setText("No upcoming collection for your ward.");return;}
                JSONObject row=rows.optJSONObject(0);String phone=row.optString("driver_phone");
                tvNextSchedule.setText(row.optString("street")+"\n"+row.optString("scheduled_at").replace('T',' ')+"\nDriver: "+row.optString("driver_name","To be assigned")+(phone.isEmpty()?"":" — "+phone)); }); }
            public void onError(String message) { runOnUiThread(() -> tvNextSchedule.setText("Connect to view the latest ward schedule.")); }
        });
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

    private static String safe(String value) { return clean(value); }
    private static String clean(String value) {
        if (value == null) return "";
        String result = value.trim();
        return "null".equalsIgnoreCase(result) ? "" : result;
    }
    private static String nonEmpty(String value, String fallback) {
        String result = clean(value);
        return result.isEmpty() ? fallback : result;
    }

    private static String joinMeta(String first, String second) {
        first = clean(first); second = clean(second);
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + " · " + second;
    }
}
