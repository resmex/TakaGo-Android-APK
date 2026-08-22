package com.takago.app.resident;

import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.common.ImageUtils;
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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ResidentHistoryActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private LinearLayout historyListContainer;
    private TextView tvNoHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_history);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        historyListContainer = findViewById(R.id.historyListContainer);
        tvNoHistory = findViewById(R.id.tvNoHistory);

        ImageUtils.loadAvatar(findViewById(R.id.ivUserAvatar), null);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();

        UserAccount resident = dbHelper.getUserById(session.getUserId());
        if (resident != null) {
            ImageUtils.loadAvatar(findViewById(R.id.ivUserAvatar), resident.profileImagePath);
        }
    }

    private void loadHistory() {
        historyListContainer.removeAllViews();
        List<PickupRow> all = dbHelper.getAllPickupsForResident(session.getUserId());

        if (all.isEmpty()) {
            tvNoHistory.setVisibility(View.VISIBLE);
            return;
        }
        tvNoHistory.setVisibility(View.GONE);

        for (PickupRow pickup : all) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_recent_request, historyListContainer, false);
            TextView tvAddress = row.findViewById(R.id.tvRequestAddress);
            TextView tvMeta = row.findViewById(R.id.tvRequestMeta);
            TextView tvStatus = row.findViewById(R.id.tvRequestStatus);

            tvAddress.setText(PickupAddressFormatter.twoLine(pickup));
            tvMeta.setText(buildRequestMeta(pickup));
            tvStatus.setText(pickup.status);
            applyStatusStyle(tvStatus, pickup.status);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResidentPickupDetailsActivity.class);
                intent.putExtra("pickupId", pickup.id);
                startActivity(intent);
            });
            historyListContainer.addView(row);
        }
    }

    private String buildRequestMeta(PickupRow pickup) {
        String wasteSize = pickup.category != null ? pickup.category : "Mixed";
        String meta = formatDate(pickup.pickupDate) + "  •  " + wasteSize;
        if (pickup.distanceKm > 0) {
            meta += String.format(Locale.US, "  •  %.1f km", pickup.distanceKm);
        }
        return meta;
    }

    private void applyStatusStyle(TextView tvStatus, String status) {
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

    private String formatDate(String isoDate) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat output = new SimpleDateFormat("d MMMM", Locale.US);
            return output.format(input.parse(isoDate));
        } catch (ParseException e) {
            return isoDate;
        }
    }

    private void setupBottomNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentHomeActivity.class));
            finish();
        });
        findViewById(R.id.navTrack).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentTrackActivity.class));
            finish();
        });
        findViewById(R.id.navHistory).setOnClickListener(v -> {
            // already on History
        });
        findViewById(R.id.navProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentProfileActivity.class));
            finish();
        });
    }
}
