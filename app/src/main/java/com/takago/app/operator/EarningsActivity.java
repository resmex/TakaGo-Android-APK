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
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shows a Waste Operator's earnings, computed from their own drivers' completed pickups. There's
 * no real billing/pricing system in the app, so each transaction's amount is a simple illustrative
 * estimate (a flat rate per kg collected), not a stored monetary value.
 */
public class EarningsActivity extends AppCompatActivity {

    private static final double RATE_PER_KG_TZS = 500.0;

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LinearLayout transactionListContainer;
    private TextView tvNoTransactions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earnings);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        transactionListContainer = findViewById(R.id.transactionListContainer);
        tvNoTransactions = findViewById(R.id.tvNoTransactions);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnExportWallet).setOnClickListener(v -> exportWalletReport());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEarnings();
    }

    private void loadEarnings() {
        List<PickupRow> completed = dbHelper.getCompletedPickupsForOperator(session.getUserId());

        double weekTotal = 0;
        double monthTotal = 0;
        Date now = new Date();

        transactionListContainer.removeAllViews();

        if (completed.isEmpty()) {
            tvNoTransactions.setVisibility(View.VISIBLE);
        } else {
            tvNoTransactions.setVisibility(View.GONE);
        }

        for (PickupRow pickup : completed) {
            double amount = pickup.finalPrice;
            Date completedDate = parseTimestamp(pickup.completedAt);

            if (completedDate != null) {
                if ("Paid".equalsIgnoreCase(pickup.paymentStatus) && daysBetween(completedDate, now) <= 7) {
                    weekTotal += amount;
                }
                if ("Paid".equalsIgnoreCase(pickup.paymentStatus) && daysBetween(completedDate, now) <= 30) {
                    monthTotal += amount;
                }
            }

            addTransactionRow(pickup, amount);
        }

        ((TextView) findViewById(R.id.tvEarningsWeekTotal)).setText(formatTzs(weekTotal));
        ((TextView) findViewById(R.id.tvEarningsMonthTotal)).setText(formatTzs(monthTotal));
        ((TextView) findViewById(R.id.tvCompletedPickupsCount)).setText(String.valueOf(completed.size()));
    }

    private void addTransactionRow(PickupRow pickup, double amount) {
        View row = LayoutInflater.from(this).inflate(R.layout.row_transaction, transactionListContainer, false);

        TextView tvAddress = row.findViewById(R.id.tvTxAddress);
        TextView tvMeta = row.findViewById(R.id.tvTxMeta);
        TextView tvAmount = row.findViewById(R.id.tvTxAmount);
        TextView tvStatus = row.findViewById(R.id.tvTxStatus);

        tvAddress.setText(pickup.address != null && !pickup.address.isEmpty() ? pickup.address : pickup.ward);

        UserAccount driver = dbHelper.getUserById(pickup.driverId);
        String driverName = driver != null ? driver.name : "Driver";
        String resident = pickup.residentDisplayName == null || pickup.residentDisplayName.isEmpty() ? "Resident" : pickup.residentDisplayName;
        double kg = pickup.measuredWeightKg > 0 ? pickup.measuredWeightKg : pickup.weightKg;
        String distance = pickup.distanceKm > 0 && pickup.distanceKm < 1
                ? String.format(Locale.US, "%.0f m", pickup.distanceKm * 1000)
                : String.format(Locale.US, "%.1f km", pickup.distanceKm);
        tvMeta.setMaxLines(2);
        tvMeta.setText(resident + " · " + String.format(Locale.US, "%.1f kg", kg) + " · " + distance + "\n" + formatDate(pickup.completedAt) + " · " + driverName);

        tvAmount.setText(formatTzs(amount));
        tvStatus.setText(pickup.paymentStatus == null ? "Unpaid" : pickup.paymentStatus);

        transactionListContainer.addView(row);
    }

    private void exportWalletReport() {
        List<PickupRow> rows = dbHelper.getCompletedPickupsForOperator(session.getUserId());
        StringBuilder csv = new StringBuilder("Pickup ID,Resident,Location,Latitude,Longitude,Waste type,Weight kg,Distance km,Final price TZS,Payment status,Completed at\n");
        for (PickupRow p : rows) {
            double kg = p.measuredWeightKg > 0 ? p.measuredWeightKg : p.weightKg;
            csv.append(csvCell(p.code)).append(',').append(csvCell(p.residentDisplayName)).append(',')
                    .append(csvCell(p.address)).append(',').append(p.latitude).append(',').append(p.longitude).append(',')
                    .append(csvCell(p.wasteType)).append(',').append(String.format(Locale.US, "%.1f", kg)).append(',')
                    .append(String.format(Locale.US, "%.2f", p.distanceKm)).append(',')
                    .append(String.format(Locale.US, "%.0f", p.finalPrice)).append(',')
                    .append(csvCell(p.paymentStatus)).append(',').append(csvCell(p.completedAt)).append('\n');
        }
        try {
            File dir = new File(getExternalFilesDir(null), "Reports"); if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "TakaGo_Operator_Wallet_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".csv");
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(csv.toString().getBytes(StandardCharsets.UTF_8)); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Save or share wallet report"));
        } catch (Exception e) { Toast.makeText(this, "Could not export report: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private String csvCell(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
    private String formatTzs(double amount) {
        return String.format(Locale.US, "TZS %,.0f", amount);
    }

    private Date parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            return format.parse(timestamp);
        } catch (ParseException e) {
            return null;
        }
    }

    private long daysBetween(Date from, Date to) {
        long diffMillis = to.getTime() - from.getTime();
        return diffMillis / (24L * 60 * 60 * 1000);
    }

    private String formatDate(String timestamp) {
        Date date = parseTimestamp(timestamp);
        if (date == null) {
            return "";
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        SimpleDateFormat output = new SimpleDateFormat("d MMMM", Locale.US);
        return output.format(cal.getTime());
    }
}
