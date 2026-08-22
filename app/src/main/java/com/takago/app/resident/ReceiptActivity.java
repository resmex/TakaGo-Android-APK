package com.takago.app.resident;

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
import android.content.Intent;
import android.net.Uri;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.widget.Button;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import org.json.JSONObject;

import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;

/** Shows the full pricing breakdown and pickup details after a driver finalizes (or an admin manually sets) a price. */
public class ReceiptActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        int pickupId = getIntent().getIntExtra("pickupId", -1);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadReceipt(pickupId);
    }

    private void loadReceipt(int pickupId) {
        PickupRow pickup = dbHelper.getTripById(pickupId);
        if (pickup == null) {
            finish();
            return;
        }

        UserAccount resident = dbHelper.getUserById(pickup.residentId);
        UserAccount driver = pickup.driverId != 0 ? dbHelper.getUserById(pickup.driverId) : null;

        ((TextView) findViewById(R.id.tvReceiptCode)).setText(pickup.code);
        ((TextView) findViewById(R.id.tvReceiptFinalPrice)).setText(formatTzs(pickup.finalPrice));

        TextView tvStatus = findViewById(R.id.tvReceiptPaymentStatus);
        tvStatus.setText(pickup.paymentStatus != null ? pickup.paymentStatus : "Unpaid");

        LinearLayout detailsContainer = findViewById(R.id.receiptDetailsContainer);
        addRow(detailsContainer, "Request ID", pickup.code);
        addRow(detailsContainer, "Resident", resident != null ? resident.name : (pickup.residentDisplayName != null ? pickup.residentDisplayName : "-"));
        addRow(detailsContainer, "Driver", driver != null ? driver.name : "-");
        addRow(detailsContainer, "Ward", pickup.ward);
        addRow(detailsContainer, "Pickup address", PickupAddressFormatter.twoLine(pickup));
        addRow(detailsContainer, "Waste type", pickup.wasteType != null ? pickup.wasteType : "-");
        addRow(detailsContainer, "Date/time", pickup.completedAt != null ? pickup.completedAt : pickup.createdAt);

        LinearLayout priceContainer = findViewById(R.id.receiptPriceContainer);
        double extraWeight = Math.max(0, pickup.measuredWeightKg - pickup.includedWeightKg);
        addRow(priceContainer, "Measured weight", formatKg(pickup.measuredWeightKg));
        addRow(priceContainer, "Included weight", formatKg(pickup.includedWeightKg));
        addRow(priceContainer, "Extra weight charged", formatKg(extraWeight));
        addRow(priceContainer, "Rate per kg", formatTzs(pickup.ratePerKg));
        addRow(priceContainer, "Waste multiplier", String.format(Locale.US, "%.2fx", pickup.wasteTypeMultiplier));
        addRow(priceContainer, "Weight charge", formatTzs(extraWeight * pickup.ratePerKg * pickup.wasteTypeMultiplier));
        addRow(priceContainer, "Booking fee", formatTzs(pickup.bookingFee));
        addRow(priceContainer, "Distance fee", formatTzs(pickup.distanceFee));
        addDivider(priceContainer);
        addRow(priceContainer, "Final price", formatTzs(pickup.finalPrice));

        if (pickup.finalPrice > 0 && !"Paid".equalsIgnoreCase(pickup.paymentStatus)) {
            Button pay = new Button(this);
            pay.setText("Choose payment method");
            pay.setTextColor(0xFFFFFFFF);
            pay.setTextSize(14);
            pay.setAllCaps(false);
            pay.setBackgroundResource(R.drawable.bg_button_primary);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
            params.topMargin = dp(18);
            pay.setLayoutParams(params);
            pay.setOnClickListener(v -> showPaymentMethods(pickup.id));
            priceContainer.addView(pay);
        }

        Button save = new Button(this);
        save.setText("Save or share receipt PDF");
        save.setTextColor(0xFF123A32);
        save.setTextSize(14);
        save.setAllCaps(false);
        save.setBackgroundResource(R.drawable.bg_button_secondary);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(50));
        saveParams.topMargin = dp(10);
        save.setLayoutParams(saveParams);
        save.setOnClickListener(v -> saveReceiptPdf(pickup));
        priceContainer.addView(save);
    }

    private void saveReceiptPdf(PickupRow pickup) {
        try {
            File dir = new File(getExternalFilesDir(null), "Reports");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "TakaGo_Receipt_" + pickup.code + ".pdf");
            PdfDocument document = new PdfDocument();
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            int y = 64;
            paint.setColor(0xFF123A32); paint.setTextSize(24); paint.setFakeBoldText(true);
            page.getCanvas().drawText("TakaGo Collection Receipt", 48, y, paint);
            paint.setFakeBoldText(false); paint.setTextSize(13); paint.setColor(0xFF26332F);
            String[] lines = {
                    "Pickup: " + pickup.code, "Ward: " + pickup.ward,
                    "Location: " + (pickup.address == null ? "-" : pickup.address),
                    "Waste type: " + pickup.wasteType,
                    "Measured weight: " + formatKg(pickup.measuredWeightKg),
                    "Distance: " + formatDistance(pickup.distanceKm),
                    "Booking fee: " + formatTzs(pickup.bookingFee),
                    "Distance fee: " + formatTzs(pickup.distanceFee),
                    "Final price: " + formatTzs(pickup.finalPrice),
                    "Payment status: " + (pickup.paymentStatus == null ? "Unpaid" : pickup.paymentStatus),
                    "Completed: " + (pickup.completedAt == null ? "-" : pickup.completedAt)
            };
            for (String line : lines) { y += 34; page.getCanvas().drawText(line, 48, y, paint); }
            document.finishPage(page);
            try (FileOutputStream out = new FileOutputStream(file)) { document.writeTo(out); }
            document.close();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Save or share receipt"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not create receipt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String formatDistance(double km) {
        return km > 0 && km < 1 ? String.format(Locale.US, "%.0f m", km * 1000)
                : String.format(Locale.US, "%.1f km", Math.max(0, km));
    }
    private void showPaymentMethods(int pickupId) {
        String[] labels = {"Mobile money (BiasharaPay)", "Debit or credit card (Flutterwave)", "Cash"};
        String[] methods = {"mobile_money", "card", "cash"};
        new AlertDialog.Builder(this)
                .setTitle("Choose payment method")
                .setItems(labels, (dialog, which) -> submitPayment(pickupId, methods[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitPayment(int pickupId, String method) {
        Toast.makeText(this, "Recording payment...", Toast.LENGTH_SHORT).show();
        try {
            JSONObject body = new JSONObject().put("method", method);
            ApiClient.post("/pickups/" + pickupId + "/payments", session.getApiToken(), body,
                    new ApiClient.JsonCallback() {
                        @Override public void onSuccess(JSONObject json) {
                            runOnUiThread(() -> {
                                String checkout = json.optString("checkout_url", "");
                                String message = json.optString("message", "Payment recorded.");
                                String cashCode = json.optString("cash_confirmation_code", "");
                                JSONObject existingPayment = json.optJSONObject("data");
                                if (cashCode.isEmpty() && existingPayment != null) cashCode = existingPayment.optString("cash_confirmation_code", "");
                                if (!cashCode.isEmpty()) message += "\\n\\nCash confirmation code: " + cashCode + "\\nGive it to the assigned driver only after handing over the cash.";
                                if (!checkout.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(checkout)));
                                new AlertDialog.Builder(ReceiptActivity.this)
                                        .setTitle(checkout.isEmpty() ? "Payment recorded" : "Continue payment")
                                        .setMessage(message)
                                        .setPositiveButton("OK", null)
                                        .setNeutralButton(method.equals("cash") ? "I have paid the driver" : null,
                                                method.equals("cash") ? (d,w) -> confirmCash(pickupId) : null).show();
                                JSONObject payment = json.optJSONObject("data");
                                ((TextView) findViewById(R.id.tvReceiptPaymentStatus)).setText(
                                        payment != null ? payment.optString("status", "Pending") : "Pending");
                            });
                        }
                        @Override public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(ReceiptActivity.this, message, Toast.LENGTH_LONG).show());
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void confirmCash(int pickupId) {
        try { JSONObject body = new JSONObject().put("cash_given", true); ApiClient.post("/pickups/" + pickupId + "/payments/cash/confirm", session.getApiToken(), body, new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) { runOnUiThread(() -> { Toast.makeText(ReceiptActivity.this, "Cash payment confirmed. Receipt is ready.", Toast.LENGTH_LONG).show(); recreate(); }); }
            public void onError(String message) { runOnUiThread(() -> Toast.makeText(ReceiptActivity.this, message, Toast.LENGTH_LONG).show()); }
        }); } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    private void addRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(8);
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF888888);
        tvLabel.setTextSize(13);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(0xFF1A1A1A);
        tvValue.setTextSize(13);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvValue.setLayoutParams(valueParams);
        tvValue.setGravity(android.view.Gravity.END);

        row.addView(tvLabel);
        row.addView(tvValue);
        container.addView(row);
    }

    private void addDivider(LinearLayout container) {
        android.view.View divider = new android.view.View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(10);
        params.bottomMargin = dp(2);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0xFFF0F0F0);
        container.addView(divider);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatTzs(double amount) {
        return String.format(Locale.US, "TZS %,.0f", amount);
    }

    private String formatKg(double kg) {
        return String.format(Locale.US, "%.1f kg", kg);
    }
}
