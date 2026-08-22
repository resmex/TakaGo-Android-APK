package com.takago.app.auth;

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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

import com.takago.app.db.DatabaseHelper;
import java.util.List;

/** Municipal Admin registers a new Ward Admin. */
public class RegisterWardAdminActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    private String pickedWard;
    private double pickedLat;
    private double pickedLng;
    private double pickedRadiusKm;

    private final ActivityResultLauncher<Intent> pickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Intent data = result.getData();
                pickedWard = data.getStringExtra(WardLocationPickerActivity.EXTRA_RESULT_WARD);
                pickedLat = data.getDoubleExtra(WardLocationPickerActivity.EXTRA_RESULT_LAT, 0);
                pickedLng = data.getDoubleExtra(WardLocationPickerActivity.EXTRA_RESULT_LNG, 0);
                pickedRadiusKm = data.getDoubleExtra(WardLocationPickerActivity.EXTRA_RESULT_RADIUS_KM, 0);

                TextView tvWard = findViewById(R.id.tvRegWard);
                tvWard.setText(pickedWard);
                tvWard.setTextColor(0xFF1A1A1A);

                TextView tvRadius = findViewById(R.id.tvRegRadius);
                tvRadius.setText(String.format("Operating radius: %.0f km", pickedRadiusKm));
                tvRadius.setVisibility(android.view.View.VISIBLE);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_person);

        dbHelper = new DatabaseHelper(this);

        ((TextView) findViewById(R.id.tvRegisterTitle)).setText("Register ward admin");
        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.rowPickWard).setOnClickListener(v -> openPicker());
        findViewById(R.id.btnRegisterSubmit).setOnClickListener(v -> submit());
    }

    private void openPicker() {
        List<String> municipalities = dbHelper.getActiveMunicipalityNames();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select municipality")
                .setItems(municipalities.toArray(new String[0]), (dialog, which) ->
                        showWardPicker(municipalities.get(which)))
                .show();
    }

    private void showWardPicker(String municipality) {
        List<String> wards = dbHelper.getWardNamesInMunicipality(municipality);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(municipality + " wards")
                .setItems(wards.toArray(new String[0]), (dialog, which) -> {
                    pickedWard = wards.get(which);
                    pickedLat = 0;
                    pickedLng = 0;
                    pickedRadiusKm = 0;
                    TextView tvWard = findViewById(R.id.tvRegWard);
                    tvWard.setText(municipality + " - " + pickedWard);
                    tvWard.setTextColor(0xFF1A1A1A);
                    findViewById(R.id.tvRegRadius).setVisibility(android.view.View.GONE);
                })
                .show();
    }

    private void submit() {
        String name = text(R.id.etRegName);
        String email = text(R.id.etRegEmail);
        String phone = text(R.id.etRegPhone);
        String password = text(R.id.etRegPassword);

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in every field", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pickedWard == null) {
            Toast.makeText(this, "Please pick this admin's ward on the map", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dbHelper.getUserByEmail(email) != null) {
            Toast.makeText(this, "That email is already registered", Toast.LENGTH_SHORT).show();
            return;
        }

        dbHelper.registerWardAdmin(name, email, phone, password, pickedWard, pickedLat, pickedLng, pickedRadiusKm);
        Toast.makeText(this, "Ward Admin registered", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String text(int id) {
        TextInputEditText field = findViewById(id);
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
