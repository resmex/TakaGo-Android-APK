package com.takago.app.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.R;
import com.takago.app.common.InsetsUtils;
import com.takago.app.data.model.ComplaintRow;
import com.takago.app.data.model.UserAccount;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.util.List;

public class ComplaintsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private LinearLayout complaintListContainer;
    private String scopedWard;
    private int municipalityId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaints);

        dbHelper = new DatabaseHelper(this);
        complaintListContainer = findViewById(R.id.complaintListContainer);
        scopedWard = getIntent().getStringExtra("ward");
        UserAccount admin = dbHelper.getUserById(new SessionManager(this).getUserId());
        municipalityId = admin != null ? admin.municipalityId : -1;
        if (scopedWard != null && !scopedWard.trim().isEmpty()) {
            ((TextView) findViewById(R.id.tvComplaintsTitle)).setText(scopedWard + " complaints");
        }

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadComplaints();
    }

    private void loadComplaints() {
        complaintListContainer.removeAllViews();
        List<ComplaintRow> complaints = scopedWard != null && !scopedWard.trim().isEmpty()
                ? dbHelper.getComplaintsInWard(scopedWard)
                : municipalityId > 0 ? dbHelper.getComplaintsInMunicipality(municipalityId) : dbHelper.getAllComplaints();

        if (complaints.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(scopedWard == null || scopedWard.trim().isEmpty()
                    ? "No complaints found." : "No complaints in this ward.");
            empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            empty.setTextColor(getResources().getColor(R.color.color_text_hint));
            empty.setTextSize(13);
            empty.setPadding(0, 48, 0, 48);
            complaintListContainer.addView(empty);
            return;
        }

        for (ComplaintRow complaint : complaints) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_complaint, complaintListContainer, false);

            TextView tvSubject = row.findViewById(R.id.tvComplaintSubject);
            TextView tvMeta = row.findViewById(R.id.tvComplaintMeta);
            TextView tvStatus = row.findViewById(R.id.tvComplaintStatus);
            View btnResolve = row.findViewById(R.id.btnResolve);

            tvSubject.setText(complaint.subject);
            String wardText = complaint.ward == null || complaint.ward.trim().isEmpty()
                    ? "" : " - " + complaint.ward;
            tvMeta.setText(complaint.reporter + " - " + complaint.dateText + wardText);
            tvStatus.setText(complaint.status);

            boolean isOpen = "Open".equals(complaint.status);
            if (isOpen) {
                tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvStatus.setTextColor(0xFFE65100);
                btnResolve.setVisibility(View.VISIBLE);
                btnResolve.setOnClickListener(v -> {
                    dbHelper.resolveComplaint(complaint.id);
                    loadComplaints();
                });
            } else {
                tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                tvStatus.setTextColor(0xFF2E7D32);
                btnResolve.setVisibility(View.GONE);
            }

            complaintListContainer.addView(row);
        }
    }
}
