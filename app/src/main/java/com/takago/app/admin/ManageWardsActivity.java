package com.takago.app.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.R;
import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.WardRow;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Municipal ward editor for operational assignment. Boundary data is managed from app assets. */
public class ManageWardsActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private LinearLayout wardList;
    private EditText etSearchWards;
    private TextView tvNoticeTitle;
    private TextView tvNoticeAction;
    private TextView tvSectionCount;
    private View noticeCard;
    private WardRow firstMissingBoundaryWard;
    private List<WardRow> allWards = new ArrayList<>();
    private int municipalityId = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_manage_wards);
        db = new DatabaseHelper(this);
        UserAccount admin = db.getUserById(new SessionManager(this).getUserId());
        municipalityId = admin != null ? admin.municipalityId : -1;
        bindViews();
        refresh();
    }

    private void bindViews() {
        wardList = findViewById(R.id.wardList);
        etSearchWards = findViewById(R.id.etSearchWards);
        tvNoticeTitle = findViewById(R.id.tvNoticeTitle);
        tvNoticeAction = findViewById(R.id.tvNoticeAction);
        tvSectionCount = findViewById(R.id.tvSectionCount);
        noticeCard = findViewById(R.id.noticeCard);

        View addWard = findViewById(R.id.btnAddWard);
        addWard.setOnClickListener(v -> addWard());
        tvNoticeAction.setVisibility(View.GONE);
        etSearchWards.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderWardList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void refresh() {
        allWards = municipalityId > 0 ? db.getWardsInMunicipality(municipalityId) : db.getAllWards();
        int active = 0;
        int mapped = 0;
        firstMissingBoundaryWard = null;
        for (WardRow ward : allWards) {
            if (ward.active) {
                active++;
                if (hasBoundary(ward)) {
                    mapped++;
                } else if (firstMissingBoundaryWard == null) {
                    firstMissingBoundaryWard = ward;
                }
            }
        }

        tvSectionCount.setText(mapped + " of " + active + " mapped");
        updateNotice(active - mapped);
        renderWardList();
    }

    private void renderWardList() {
        wardList.removeAllViews();
        String query = etSearchWards.getText().toString().trim().toLowerCase(Locale.US);
        List<WardRow> visible = new ArrayList<>();
        for (WardRow ward : allWards) {
            if (query.isEmpty() || ward.name.toLowerCase(Locale.US).contains(query)) {
                visible.add(ward);
            }
        }

        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(allWards.isEmpty() ? "No wards added yet." : "No wards match your search.");
            empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            empty.setTextColor(getResources().getColor(R.color.color_text_hint));
            empty.setTextSize(13);
            empty.setPadding(0, dp(28), 0, dp(28));
            wardList.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < visible.size(); i++) {
            WardRow ward = visible.get(i);
            View row = inflater.inflate(R.layout.row_manage_ward, wardList, false);
            bindWardRow(row, ward);
            wardList.addView(row);
            if (i < visible.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(230, 235, 231));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                params.setMargins(dp(15), 0, dp(15), 0);
                wardList.addView(divider, params);
            }
        }
    }

    private void bindWardRow(View row, WardRow ward) {
        TextView name = row.findViewById(R.id.tvWardName);
        TextView boundary = row.findViewById(R.id.tvBoundaryStatus);
        TextView operator = row.findViewById(R.id.tvWardOperator);
        name.setText(ward.name);
        if (hasBoundary(ward)) {
            boundary.setText("Mapped - " + (ward.active ? "Active" : "Inactive"));
            boundary.setTextColor(Color.rgb(46, 125, 50));
        } else {
            boundary.setText("Not mapped - " + (ward.active ? "Active" : "Inactive"));
            boundary.setTextColor(Color.rgb(217, 130, 0));
        }
        String operatorName = assignedOperatorName(ward.assignedOperatorId);
        operator.setText(operatorName == null ? "No operator assigned" : operatorName);
        row.setAlpha(ward.active ? 1f : 0.62f);
        row.setOnClickListener(v -> editWard(ward));
    }

    private void updateNotice(int missingCount) {
        if (missingCount <= 0) {
            noticeCard.setVisibility(View.GONE);
            return;
        }
        noticeCard.setVisibility(View.VISIBLE);
        tvNoticeTitle.setText(missingCount + (missingCount == 1
                ? " ward needs boundary" : " wards need boundaries"));
    }

    private void addWard() {
        EditText name = new EditText(this);
        name.setHint("Ward name");
        new AlertDialog.Builder(this)
                .setTitle("Add ward")
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    long id = db.addWard(name.getText().toString(), "Dar es Salaam");
                    Toast.makeText(this, id > 0 ? "Ward added" : "Ward already exists or name is invalid",
                            Toast.LENGTH_LONG).show();
                    refresh();
                }).show();
    }

    private void editWard(WardRow ward) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), 0, dp(18), 0);

        EditText name = new EditText(this);
        name.setHint("Ward name");
        name.setText(ward.name);
        form.addView(name);

        Switch active = new Switch(this);
        active.setText("Active");
        active.setChecked(ward.active);
        form.addView(active);

        List<UserAccount> operators = db.getWasteOperatorsForWardManagement(municipalityId);
        List<String> names = optionNames("No assigned operator", operators);
        int selected = selectedUserIndex(operators, ward.assignedOperatorId);
        Spinner operator = new Spinner(this);
        operator.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        operator.setSelection(selected);
        form.addView(operator);

        TextView adminLabel = new TextView(this);
        adminLabel.setText("Ward admin");
        adminLabel.setTextColor(getResources().getColor(R.color.color_text_hint));
        adminLabel.setTextSize(12);
        adminLabel.setPadding(0, dp(12), 0, 0);
        form.addView(adminLabel);

        List<UserAccount> wardAdmins = db.getWardAdminsForWardManagement(municipalityId);
        Spinner wardAdmin = new Spinner(this);
        wardAdmin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                optionNames("No assigned ward admin", wardAdmins)));
        wardAdmin.setSelection(selectedWardAdminIndex(wardAdmins, ward.id));
        form.addView(wardAdmin);

        new AlertDialog.Builder(this)
                .setTitle("Edit ward")
                .setView(form)
                .setNeutralButton(hasBoundary(ward) ? "View boundary" : "No boundary", (d, w) -> {
                    if (hasBoundary(ward)) {
                        startActivity(new Intent(this, WardBoundaryMapActivity.class)
                                .putExtra("name", ward.name)
                                .putExtra("geojson", ward.boundaryGeoJson));
                    }
                })
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    int operatorId = operator.getSelectedItemPosition() > 0
                            ? operators.get(operator.getSelectedItemPosition() - 1).id : -1;
                    int wardAdminId = wardAdmin.getSelectedItemPosition() > 0
                            ? wardAdmins.get(wardAdmin.getSelectedItemPosition() - 1).id : -1;
                    boolean ok = db.updateWard(ward.id, name.getText().toString(), active.isChecked(),
                            operatorId, null) && db.assignWardAdmin(ward.id, wardAdminId);
                    Toast.makeText(this, ok ? "Ward updated" : "Could not update this ward",
                            Toast.LENGTH_LONG).show();
                    refresh();
                }).show();
    }

    private String assignedOperatorName(int operatorId) {
        if (operatorId <= 0) return null;
        for (UserAccount operator : db.getWasteOperatorsForWardManagement(municipalityId)) {
            if (operator.id == operatorId) return operator.name;
        }
        return null;
    }

    private List<String> optionNames(String emptyLabel, List<UserAccount> users) {
        List<String> names = new ArrayList<>();
        names.add(emptyLabel);
        for (UserAccount user : users) names.add(user.name);
        return names;
    }

    private int selectedUserIndex(List<UserAccount> users, int selectedUserId) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).id == selectedUserId) return i + 1;
        }
        return 0;
    }

    private int selectedWardAdminIndex(List<UserAccount> users, int wardId) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).wardId == wardId) return i + 1;
        }
        return 0;
    }

    private boolean hasBoundary(WardRow ward) {
        return ward.boundaryGeoJson != null && !ward.boundaryGeoJson.trim().isEmpty()
                && (ward.boundaryStatus == null || "MAPPED".equalsIgnoreCase(ward.boundaryStatus));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
