package com.takago.app.admin;

import com.takago.app.ui.CircularProgressView;
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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.takago.app.db.DatabaseHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminReportsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    private int totalPickups;
    private double recycledTons;
    private double avgRating;
    private int slaPercent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_reports);

        dbHelper = new DatabaseHelper(this);

        findViewById(R.id.btnDownloadReport).setOnClickListener(v -> downloadReport());

        setupBottomNav();
        loadStats();
        drawMonthChart();
    }

    private void loadStats() {
        TextView tvTotalPickups = findViewById(R.id.tvTotalPickups);
        TextView tvRecycled = findViewById(R.id.tvRecycled);
        TextView tvAvgRating = findViewById(R.id.tvAvgRating);
        TextView tvSlaStat = findViewById(R.id.tvSlaStat);

        totalPickups = dbHelper.getTotalPickupsAllTime();
        recycledTons = dbHelper.getRecycledTonsMonth();
        avgRating = dbHelper.getAvgRating();
        slaPercent = dbHelper.getSlaPercent();

        tvTotalPickups.setText(String.format(Locale.US, "%,d", totalPickups));
        tvRecycled.setText(formatTons(recycledTons) + " t");
        tvAvgRating.setText(String.format(Locale.US, "%.1f", avgRating));
        tvSlaStat.setText(slaPercent + "%");

        setupRing(R.id.ringSla, R.id.tvRingSla, slaPercent);
        setupRing(R.id.ringRating, R.id.tvRingRating, (int) Math.round(avgRating / 5 * 100));
        setupRing(R.id.ringUptime, R.id.tvRingUptime, 94);
    }

    private void downloadReport() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(new Date());
        String fileName = "takago_report_" + timestamp + ".csv";

        StringBuilder csv = new StringBuilder();
        csv.append("takaGo - Reports & analytics\n");
        csv.append("Generated,").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())).append("\n\n");
        csv.append("Metric,Value\n");
        csv.append("Total pickups,").append(totalPickups).append("\n");
        csv.append("Recycled this month (t),").append(formatTons(recycledTons)).append("\n");
        csv.append("Average rating,").append(String.format(Locale.US, "%.1f", avgRating)).append("\n");
        csv.append("SLA (%),").append(slaPercent).append("\n");

        try {
            File reportsDir = new File(getExternalFilesDir(null), "Reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }
            File file = new File(reportsDir, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(csv.toString().getBytes());
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "takaGo report - " + timestamp);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Save or share report"));
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't generate report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRing(int ringId, int labelId, int percent) {
        CircularProgressView ring = findViewById(ringId);
        ring.setColors(Color.parseColor("#EEEEEE"), Color.parseColor("#43A047"));
        ring.setProgress(percent);
        TextView label = findViewById(labelId);
        label.setText(percent + "%");
    }

    private void drawMonthChart() {
        int[] counts = {6, 8, 7, 9, 12};
        View[] bars = {
                findViewById(R.id.monthBar1), findViewById(R.id.monthBar2), findViewById(R.id.monthBar3),
                findViewById(R.id.monthBar4), findViewById(R.id.monthBar5)
        };

        int maxCount = 1;
        for (int count : counts) {
            if (count > maxCount) {
                maxCount = count;
            }
        }

        float density = getResources().getDisplayMetrics().density;
        float maxBarHeightDp = 100f;

        for (int i = 0; i < bars.length; i++) {
            float heightDp = Math.max(4f, (counts[i] / (float) maxCount) * maxBarHeightDp);
            android.view.ViewGroup.LayoutParams params = bars[i].getLayoutParams();
            params.height = Math.round(heightDp * density);
            bars[i].setLayoutParams(params);
        }
    }

    private String formatTons(double tons) {
        if (tons == Math.floor(tons)) {
            return String.valueOf((int) tons);
        }
        return String.format(Locale.US, "%.1f", tons);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_reports);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_reports) {
                return true;
            } else if (id == R.id.nav_home) {
                startActivity(new Intent(this, MunicipalAdminHomeActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_users) {
                startActivity(new Intent(this, AdminUsersActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_pickups) {
                startActivity(new Intent(this, AdminPickupsActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}
