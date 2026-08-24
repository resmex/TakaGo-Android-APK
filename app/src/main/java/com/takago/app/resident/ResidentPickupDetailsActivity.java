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
import androidx.core.content.ContextCompat;

import com.takago.app.R;
import com.takago.app.common.ImageUtils;
import com.takago.app.common.PickupAddressFormatter;
import com.takago.app.common.PickupStatusUi;
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
        try { PickupRow pickup = dbHelper.getTripById(pickupId);if(pickup==null){showLoadError("This pickup is no longer available. Refresh the dashboard and try again.");return;}bind(pickup); }
        catch(RuntimeException error){android.util.Log.e("ResidentPickupDetails","Could not display pickup "+pickupId,error);showLoadError("Pickup details could not be displayed. Return to the dashboard and refresh.");}
    }

    private void showLoadError(String message){content.removeAllViews();heading("Unable to open pickup");body(message);Button close=new Button(this);close.setText("Back to dashboard");close.setOnClickListener(v->finish());content.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));}

    private void bind(PickupRow pickup) {
        UserAccount driver = pickup.driverId > 0 ? dbHelper.getUserById(pickup.driverId) : null;
        VehicleRow vehicle = pickup.assignedVehicleId > 0
                ? dbHelper.getVehicleById(pickup.assignedVehicleId) : null;
        int[] stops = dbHelper.getRouteStopSummary(pickup.groupId, pickup.id);

        heading(pickup.code != null ? pickup.code : "Request #" + pickup.id);
        heading("Pickup information");
        row("Waste type", value(pickup.wasteType));
        row("Waste size", value(pickup.category));
        if (pickup.weightKg > 0) row("Estimated weight", kg(pickup.weightKg));
        if (pickup.measuredWeightKg > 0) row("Measured weight", kg(pickup.measuredWeightKg));

        heading("Location");
        row("Pickup location", PickupAddressFormatter.primary(pickup));
        row("Ward", PickupAddressFormatter.wardLine(pickup));
        if (pickup.routeDistanceMeters > 0)
            row("Distance", String.format(Locale.US, "%.1f km", pickup.routeDistanceMeters / 1000d));
        if (pickup.routeDurationSeconds > 0)
            row("ETA", Math.max(1, pickup.routeDurationSeconds / 60) + " min");

        heading("Status");
        row("Status", PickupStatusUi.display(pickup.status));
        row("Requested", value(pickup.createdAt));
        row("Pickup date", value(pickup.pickupDate));

        if (pickup.groupId > 0 && stops[1] > 0) {
            heading("Grouped pickup");
            row("Current driver stop", String.valueOf(stops[0]));
            row("Your stop", stops[2] + " of " + stops[1]);
            row("Stops before you", String.valueOf(stops[3]));
        }

        heading("Collection information");
        row("Expected range", sizeRange(pickup.category));
        if ("weight_recorded".equals(PickupStatusUi.normalize(pickup.status))) addConfirmationActions(pickup);
        if (pickup.photoPath != null && !pickup.photoPath.trim().isEmpty()) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setContentDescription("Waste photo");
            ImageUtils.loadAvatar(photo, pickup.photoPath);
            photo.setOnClickListener(v -> showFullPhoto("Waste photo", pickup.photoPath));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(96));
            params.topMargin = dp(10);
            content.addView(photo, params);
        }
        if (pickup.proofPhotoPath != null && !pickup.proofPhotoPath.trim().isEmpty()) {
            heading("Driver collection proof");
            ImageView proof = new ImageView(this);
            proof.setScaleType(ImageView.ScaleType.CENTER_CROP);
            proof.setContentDescription("Driver collection proof photo");
            ImageUtils.loadAvatar(proof, pickup.proofPhotoPath);
            proof.setOnClickListener(v -> showFullPhoto("Collection proof", pickup.proofPhotoPath));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(96));
            params.topMargin = dp(10); content.addView(proof, params);
        }

        heading("Driver"); /*
        row("Driver", driver != null ? value(driver.name) : "Not assigned");
        row("Phone", driver != null ? value(driver.phone) : "—");
        row("Vehicle plate", vehicle != null ? value(vehicle.plate)
                : driver != null ? value(driver.driverPlate) : "—");
        row("Vehicle type", vehicle != null ? value(vehicle.model)
                : driver != null ? value(driver.vehicleInfo) : "—");

        */ addDriverSummary(driver, vehicle);

        heading("Payment / transaction");
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

        addTimeline(pickup.status); /*
        String[] timeline = {"Request submitted", "Driver assigned", "Accepted", "On the way",
                "Arrived", "Collected", "Pending resident confirmation", "Completed"};
        int reached = timelineProgress(pickup.status);
        for (int i = 0; i < timeline.length; i++) body((i <= reached ? "✓  " : "○  ") + timeline[i]);

        */ heading("Receipt");
        if (pickup.finalPrice > 0 || "Completed".equalsIgnoreCase(pickup.status)) {
            Button receipt = new Button(this);
            receipt.setText("View receipt");
            receipt.setTextColor(ContextCompat.getColor(this, R.color.color_secondary));
            receipt.setBackgroundColor(0x00000000);
            receipt.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReceiptActivity.class);
                intent.putExtra("pickupId", pickup.id);
                startActivity(intent);
            });
            content.addView(receipt, new LinearLayout.LayoutParams(-1, dp(48)));
            if ("completed".equals(PickupStatusUi.normalize(pickup.status)) && pickup.driverId > 0) addRatingAction(pickup);
        } else {
            body("Receipt will be available after pricing is finalized.");
        }
    }

    private void addRatingAction(PickupRow pickup) {
        Button rate = new Button(this);
        rate.setText("Rate driver");
        rate.setTextColor(0xFFFFFFFF);
        rate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.color_secondary)));
        rate.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Rate your driver")
                .setItems(new String[]{"★★★★★  Excellent", "★★★★☆  Very good", "★★★☆☆  Good", "★★☆☆☆  Fair", "★☆☆☆☆  Poor"},
                        (dialog, which) -> submitRating(pickup.id, 5 - which)).show());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(8); content.addView(rate, params);
    }

    private void submitRating(int pickupId, int rating) {
        try { ApiClient.post("/pickups/" + pickupId + "/rating", session.getApiToken(),
                new JSONObject().put("rating", rating), new ApiClient.JsonCallback() {
                    public void onSuccess(JSONObject json) { runOnUiThread(() -> Toast.makeText(
                            ResidentPickupDetailsActivity.this,
                            json.optString("message", "Rating saved"), Toast.LENGTH_LONG).show()); }
                    public void onError(String message) { runOnUiThread(() -> Toast.makeText(
                            ResidentPickupDetailsActivity.this, message, Toast.LENGTH_LONG).show()); }
                });
        } catch (Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void addDriverSummary(UserAccount driver, VehicleRow vehicle) {
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(0, dp(6), 0, dp(6));

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackgroundResource(R.drawable.bg_circle_green_light);
        ImageUtils.loadAvatar(avatar, driver != null ? driver.profileImagePath : null);
        summary.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = compactText(driver != null ? value(driver.name) : "Not assigned", 15, true);
        String vehicleType = vehicle != null ? value(vehicle.model) : driver != null ? value(driver.vehicleInfo) : "—";
        String plate = vehicle != null ? value(vehicle.plate) : driver != null ? value(driver.driverPlate) : "—";
        TextView vehicleLine = compactText(vehicleType + " · " + plate, 13, false);
        TextView phone = compactText(driver != null ? value(driver.phone) : "—", 13, false);
        text.addView(name); text.addView(vehicleLine); text.addView(phone);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1);
        textParams.leftMargin = dp(12); summary.addView(text, textParams);

        if (driver != null && driver.phone != null && !driver.phone.trim().isEmpty()) {
            TextView call = compactText("Call", 14, true);
            call.setTextColor(0xFF18B95A);
            call.setPadding(dp(10), dp(10), 0, dp(10));
            call.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + driver.phone.trim()))));
            summary.addView(call);
        }
        content.addView(summary);
    }

    private void addTimeline(String status) {
        heading("Status");
        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setVisibility(View.GONE);
        String[] labels = {"Request submitted", "Driver assigned", "Accepted", "On the way",
                "Arrived", "Collected", "Resident confirmed", "Completed"};
        int reached = timelineProgress(status);
        for (int index = 0; index < labels.length; index++) {
            TextView step = compactText((index <= reached ? "✓  " : "○  ") + labels[index], 14, false);
            step.setPadding(0, dp(5), 0, dp(5)); steps.addView(step);
        }
        TextView toggle = compactText(PickupStatusUi.display(status) + "     View timeline >", 14, true);
        toggle.setTextColor(0xFF18B95A);
        toggle.setPadding(0, dp(7), 0, dp(7));
        toggle.setOnClickListener(v -> {
            boolean show = steps.getVisibility() != View.VISIBLE;
            steps.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(PickupStatusUi.display(status) + (show ? "     Hide timeline ↑" : "     View timeline >"));
        });
        content.addView(toggle); content.addView(steps);
    }

    private TextView compactText(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(0xFF333333);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void showFullPhoto(String title, String path) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageUtils.loadAvatar(image, path);
        new AlertDialog.Builder(this).setTitle(title).setView(image)
                .setPositiveButton("Close", null).show();
    }

    private void heading(String text) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(17); view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(0xFF1A1A1A); view.setPadding(0, dp(16), 0, dp(6));
        content.addView(view);
    }

    private void row(String label, String value) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(5), 0, dp(5));
        TextView left = new TextView(this);
        left.setText(label); left.setTextSize(14); left.setTextColor(0xFF666666);
        TextView right = new TextView(this);
        String displayValue = value(value);
        if ("null".equalsIgnoreCase(displayValue.trim())) displayValue = "—";
        right.setText(displayValue); right.setTextSize(14); right.setTextColor(0xFF222222);
        right.setGravity(Gravity.END); right.setMaxLines(2);
        line.addView(left, new LinearLayout.LayoutParams(0, -2, 0.42f));
        line.addView(right, new LinearLayout.LayoutParams(0, -2, 0.58f));
        content.addView(line);
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
                com.takago.app.network.ServerSyncManager.applyPickupResponse(ResidentPickupDetailsActivity.this, json);
                recreate();
                com.takago.app.network.ServerSyncManager.syncTracking(ResidentPickupDetailsActivity.this, null);
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
                            dbHelper.updatePickupWorkflowState(pickup.id, "payment_pending", "pending");
                            recreate();
                            com.takago.app.network.ServerSyncManager.syncTracking(ResidentPickupDetailsActivity.this, null);
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
                            com.takago.app.network.ServerSyncManager.applyPickupResponse(ResidentPickupDetailsActivity.this, json);
                            recreate();
                            com.takago.app.network.ServerSyncManager.syncTracking(ResidentPickupDetailsActivity.this, null);
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
