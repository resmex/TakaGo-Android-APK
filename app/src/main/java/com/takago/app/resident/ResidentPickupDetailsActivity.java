package com.takago.app.resident;

import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.common.ImageUtils;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.VehicleRow;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Locale;

/** Read-only resident view of a pickup, its assignment, progress, pricing and receipt. */
public class ResidentPickupDetailsActivity extends AppCompatActivity {
    private DatabaseHelper dbHelper;
    private LinearLayout content;
    private SessionManager session;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF4F6F7);

        TextView header = new TextView(this);
        header.setText("‹   Pickup details");
        header.setTextSize(20);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(0xFFFFFFFF);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(26), dp(20), dp(14));
        header.setBackgroundColor(0xFF18B95A);
        header.setOnClickListener(v -> finish());
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(76)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);

        int pickupId = getIntent().getIntExtra("pickupId", -1);
        PickupRow pickup = dbHelper.getTripById(pickupId);
        if (pickup == null) { finish(); return; }
        bind(pickup);
    }

    private void bind(PickupRow pickup) {
        UserAccount driver = pickup.driverId > 0 ? dbHelper.getUserById(pickup.driverId) : null;
        VehicleRow vehicle = pickup.assignedVehicleId > 0
                ? dbHelper.getVehicleById(pickup.assignedVehicleId) : null;
        int[] stops = dbHelper.getRouteStopSummary(pickup.groupId, pickup.id);

        heading(pickup.code != null ? pickup.code : "Request #" + pickup.id);
        row("Status", value(pickup.status));
        row("Pickup location", PickupAddressFormatter.primary(pickup));
        row("Ward", PickupAddressFormatter.wardLine(pickup));
        row("Requested", value(pickup.createdAt));
        row("Pickup date", value(pickup.pickupDate));
        if (pickup.routeDistanceMeters > 0)
            row("Distance", String.format(Locale.US, "%.1f km", pickup.routeDistanceMeters / 1000d));
        if (pickup.routeDurationSeconds > 0)
            row("ETA", Math.max(1, pickup.routeDurationSeconds / 60) + " min");

        if (pickup.groupId > 0 && stops[1] > 0) {
            heading("Grouped pickup");
            row("Current driver stop", String.valueOf(stops[0]));
            row("Your stop", stops[2] + " of " + stops[1]);
            row("Stops before you", String.valueOf(stops[3]));
        }

        heading("Waste and request");
        row("Waste type", value(pickup.wasteType));
        row("Estimated size", value(pickup.category));
        row("Expected range", sizeRange(pickup.category));
        body("Estimated size is for planning only. Final price uses actual measured kilograms.");
        if (pickup.weightKg > 0) row("Estimated weight", kg(pickup.weightKg));
        if (pickup.measuredWeightKg > 0) row("Measured weight", kg(pickup.measuredWeightKg));
        if ("Weight recorded".equalsIgnoreCase(pickup.status)) addConfirmationActions(pickup);
        if (pickup.photoPath != null && !pickup.photoPath.trim().isEmpty()) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setContentDescription("Waste photo");
            ImageUtils.loadAvatar(photo, pickup.photoPath);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(180));
            params.topMargin = dp(10);
            content.addView(photo, params);
        }
        if (pickup.proofPhotoPath != null && !pickup.proofPhotoPath.trim().isEmpty()) {
            heading("Driver collection proof");
            ImageView proof = new ImageView(this);
            proof.setScaleType(ImageView.ScaleType.CENTER_CROP);
            proof.setContentDescription("Driver collection proof photo");
            ImageUtils.loadAvatar(proof, pickup.proofPhotoPath);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(180));
            params.topMargin = dp(10); content.addView(proof, params);
        }

        heading("Driver and vehicle");
        row("Driver", driver != null ? value(driver.name) : "Not assigned");
        row("Phone", driver != null ? value(driver.phone) : "—");
        row("Vehicle plate", vehicle != null ? value(vehicle.plate)
                : driver != null ? value(driver.driverPlate) : "—");
        row("Vehicle type", vehicle != null ? value(vehicle.model)
                : driver != null ? value(driver.vehicleInfo) : "—");

        heading("Price and payment");
        if (pickup.estimatedPriceMax > 0) row("Estimated price", String.format(Locale.US,
                "TZS %,.0f – %,.0f", pickup.estimatedPriceMin, pickup.estimatedPriceMax));
        if (pickup.bookingFee > 0) row("Booking fee", tzs(pickup.bookingFee));
        if (pickup.distanceFee > 0) row("Distance fee", tzs(pickup.distanceFee));
        if (pickup.finalPrice > 0) row("Final price", tzs(pickup.finalPrice));
        row("Payment status", value(pickup.paymentStatus));
        String normalizedStatus = pickup.status == null ? ""
                : pickup.status.trim().toLowerCase(Locale.US).replace(' ', '_');
        if ("price_confirmed".equals(normalizedStatus)) addPaymentMethodAction(pickup);
        if ("payment_pending".equals(normalizedStatus)) loadPendingPaymentAction(pickup);

        heading("Status timeline");
        String[] timeline = {"Request submitted", "Driver assigned", "Accepted", "On the way",
                "Arrived", "Collected", "Pending resident confirmation", "Completed"};
        int reached = timelineProgress(pickup.status);
        for (int i = 0; i < timeline.length; i++) body((i <= reached ? "✓  " : "○  ") + timeline[i]);

        heading("Receipt");
        if (pickup.finalPrice > 0 || "Completed".equalsIgnoreCase(pickup.status)) {
            Button receipt = new Button(this);
            receipt.setText("View receipt");
            receipt.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReceiptActivity.class);
                intent.putExtra("pickupId", pickup.id);
                startActivity(intent);
            });
            content.addView(receipt, new LinearLayout.LayoutParams(-1, dp(48)));
        } else {
            body("Receipt will be available after pricing is finalized.");
        }
    }

    private void heading(String text) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(17); view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(0xFF1A1A1A); view.setPadding(0, dp(16), 0, dp(6));
        content.addView(view);
    }

    private void row(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + "\n" + value); view.setTextSize(14); view.setTextColor(0xFF333333);
        view.setPadding(0, dp(5), 0, dp(5)); content.addView(view);
    }

    private void body(String text) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(14); view.setTextColor(0xFF555555);
        view.setPadding(0, dp(4), 0, dp(4)); content.addView(view);
    }

    private void addConfirmationActions(PickupRow pickup) {
        Button confirm = new Button(this); confirm.setText("Confirm collection");
        confirm.setOnClickListener(v -> ApiClient.post("/pickups/" + pickup.id + "/confirm-collection", session.getApiToken(), new JSONObject(), new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) { runOnUiThread(() -> {
                Toast.makeText(ResidentPickupDetailsActivity.this, "Collection confirmed and final price calculated", Toast.LENGTH_LONG).show();
                com.takago.app.network.ServerSyncManager.syncTracking(ResidentPickupDetailsActivity.this,
                        ResidentPickupDetailsActivity.this::recreate);
            }); }
            public void onError(String message) { runOnUiThread(() -> Toast.makeText(ResidentPickupDetailsActivity.this, message, Toast.LENGTH_LONG).show()); }
        })); content.addView(confirm, new LinearLayout.LayoutParams(-1, dp(48)));
        Button problem = new Button(this); problem.setText("Report problem"); problem.setOnClickListener(v -> { EditText input = new EditText(this); input.setHint("Describe the weight or collection problem"); new AlertDialog.Builder(this).setTitle("Report collection problem").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Submit",(d,w)->{try{JSONObject body=new JSONObject().put("message",input.getText().toString().trim());ApiClient.post("/pickups/"+pickup.id+"/report-problem",session.getApiToken(),body,new ApiClient.JsonCallback(){public void onSuccess(JSONObject j){runOnUiThread(()->Toast.makeText(ResidentPickupDetailsActivity.this,"Problem sent to the operator",Toast.LENGTH_LONG).show());}public void onError(String m){runOnUiThread(()->Toast.makeText(ResidentPickupDetailsActivity.this,m,Toast.LENGTH_LONG).show());}});}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}).show(); }); content.addView(problem, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addPaymentMethodAction(PickupRow pickup) {
        Button pay = new Button(this); pay.setText("Choose payment method");
        pay.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Choose payment method")
                .setItems(new String[]{"Cash", "Mobile money", "Card"}, (dialog, which) ->
                        startPayment(pickup, which == 0 ? "cash" : which == 1 ? "mobile_money" : "card"))
                .show());
        content.addView(pay, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void startPayment(PickupRow pickup, String method) {
        try {
            JSONObject body = new JSONObject().put("method", method);
            ApiClient.post("/pickups/" + pickup.id + "/payments", session.getApiToken(), body,
                    new ApiClient.JsonCallback() {
                        public void onSuccess(JSONObject json) { runOnUiThread(() -> {
                            String checkout = json.optString("checkout_url", "");
                            Toast.makeText(ResidentPickupDetailsActivity.this,
                                    json.optString("message", "Payment started"), Toast.LENGTH_LONG).show();
                            if (!checkout.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(checkout)));
                            com.takago.app.network.ServerSyncManager.syncTracking(ResidentPickupDetailsActivity.this,
                                    ResidentPickupDetailsActivity.this::recreate);
                        }); }
                        public void onError(String message) { runOnUiThread(() ->
                                Toast.makeText(ResidentPickupDetailsActivity.this, message, Toast.LENGTH_LONG).show()); }
                    });
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void loadPendingPaymentAction(PickupRow pickup) {
        ApiClient.get("/payments", session.getApiToken(), new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) { runOnUiThread(() -> {
                JSONArray rows = json.optJSONArray("data");
                for (int i = 0; rows != null && i < rows.length(); i++) {
                    JSONObject payment = rows.optJSONObject(i);
                    if (payment != null && payment.optInt("pickup_id") == pickup.id
                            && "CASH".equalsIgnoreCase(payment.optString("provider"))
                            && "pending".equalsIgnoreCase(payment.optString("status"))) {
                        addCashConfirmationAction(pickup); return;
                    }
                }
                body("Payment verification is in progress. This page updates automatically after the provider confirms it.");
            }); }
            public void onError(String message) { runOnUiThread(() -> body("Payment status will refresh when the server reconnects.")); }
        });
    }

    private void addCashConfirmationAction(PickupRow pickup) {
        body("After handing the cash to the driver, confirm it here to complete the pickup and create the receipt.");
        Button confirm = new Button(this); confirm.setText("I handed cash to the driver");
        confirm.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Confirm cash handover")
                .setMessage("Confirm only after you have physically handed the full amount to the driver.")
                .setNegativeButton("Not yet", null)
                .setPositiveButton("Confirm", (dialog, which) -> confirmCashPayment(pickup))
                .show());
        content.addView(confirm, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void confirmCashPayment(PickupRow pickup) {
        try {
            ApiClient.post("/pickups/" + pickup.id + "/payments/cash/confirm", session.getApiToken(),
                    new JSONObject().put("cash_given", true), new ApiClient.JsonCallback() {
                        public void onSuccess(JSONObject json) { runOnUiThread(() -> {
                            Toast.makeText(ResidentPickupDetailsActivity.this,
                                    json.optString("message", "Payment confirmed"), Toast.LENGTH_LONG).show();
                            com.takago.app.network.ServerSyncManager.syncTracking(ResidentPickupDetailsActivity.this,
                                    ResidentPickupDetailsActivity.this::recreate);
                        }); }
                        public void onError(String message) { runOnUiThread(() ->
                                Toast.makeText(ResidentPickupDetailsActivity.this, message, Toast.LENGTH_LONG).show()); }
                    });
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private static String sizeRange(String size) { if (size == null) return "Not estimated"; if (size.equalsIgnoreCase("Small")) return "Up to 20 kg (about 1–2 normal bags)"; if (size.equalsIgnoreCase("Medium")) return "21–50 kg (about 3–5 normal bags)"; if (size.equalsIgnoreCase("Large")) return "Above 50 kg (large or bulky waste)"; return "Not estimated"; }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String value(String value) { return value == null || value.trim().isEmpty() ? "—" : value.trim(); }
    private static String kg(double value) { return String.format(Locale.US, "%.1f kg", value); }
    private static String tzs(double value) { return String.format(Locale.US, "TZS %,.0f", value); }

    private static int timelineProgress(String status) {
        if (status == null) return 0;
        String value = status.trim().toLowerCase(Locale.US).replace('_', ' ');
        if (value.equals("completed") || value.equals("paid")) return 7;
        if (value.equals("price confirmed") || value.equals("payment pending")) return 6;
        if (value.contains("confirmation")) return 6;
        if (value.equals("collected") || value.equals("collecting") || value.equals("weight recorded")) return 5;
        if (value.equals("arrived")) return 4;
        if (value.equals("on the way")) return 3;
        if (value.equals("accepted")) return 2;
        if (value.equals("assigned")) return 1;
        return 0;
    }
}
