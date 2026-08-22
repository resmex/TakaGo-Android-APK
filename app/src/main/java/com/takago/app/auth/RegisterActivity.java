package com.takago.app.auth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.textfield.TextInputEditText;
import com.takago.app.R;
import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.WardRow;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import com.takago.app.resident.ResidentHomeActivity;

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText etUsername, etEmail, etPhone, etPassword, etConfirmPassword;
    private CheckBox checkboxTerms;
    private Button btnCreateAccount;
    private DatabaseHelper dbHelper;
    private String pendingName, pendingEmail, pendingPhone, pendingPassword;

    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) detectWardAndRegister();
                else resetButton("Location permission is required to detect your ward.");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        dbHelper = new DatabaseHelper(this);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        checkboxTerms = findViewById(R.id.checkboxTerms);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        TextView tvSignIn = findViewById(R.id.tvSignIn);
        btnCreateAccount.setOnClickListener(v -> validateRegister());
        tvSignIn.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
    }

    private void validateRegister() {
        pendingName = value(etUsername);
        pendingEmail = value(etEmail);
        pendingPhone = value(etPhone);
        pendingPassword = value(etPassword);
        String confirm = value(etConfirmPassword);
        if (pendingName.length() < 3) { error(etUsername, "Enter your full name"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(pendingEmail).matches()) { error(etEmail, "Enter a valid email"); return; }
        if (pendingPhone.length() < 10) { error(etPhone, "Enter a valid phone number"); return; }
        if (pendingPassword.length() < 8) { error(etPassword, "Password must be at least 8 characters"); return; }
        if (!pendingPassword.equals(confirm)) { error(etConfirmPassword, "Passwords do not match"); return; }
        if (!checkboxTerms.isChecked()) {
            Toast.makeText(this, "Please agree to the Terms and Privacy Policy", Toast.LENGTH_SHORT).show();
            return;
        }
        btnCreateAccount.setEnabled(false);
        btnCreateAccount.setText("Detecting your ward...");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else detectWardAndRegister();
    }

    private void detectWardAndRegister() {
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            resetButton("Turn on phone location and try again.");
                            return;
                        }
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        WardRow ward = dbHelper.findMappedWardContaining(latitude, longitude);
                        if (ward == null || TextUtils.isEmpty(ward.name)) {
                            resetButton("Your current ward could not be detected. Move outdoors and try again.");
                            return;
                        }
                        createAccount(ward.name, latitude, longitude);
                    })
                    .addOnFailureListener(error -> resetButton("Could not read your location. Try again."));
        } catch (SecurityException error) {
            resetButton("Location permission is required to detect your ward.");
        }
    }

    private void createAccount(String ward, double latitude, double longitude) {
        btnCreateAccount.setText("Creating account...");
        ApiClient.register(pendingName, pendingEmail, pendingPhone, ward, latitude, longitude,
                pendingPassword, new ApiClient.LoginCallback() {
            @Override public void onSuccess(UserAccount account, String token) {
                runOnUiThread(() -> {
                    btnCreateAccount.setEnabled(true);
                    btnCreateAccount.setText("Create Account");
                    new SessionManager(RegisterActivity.this)
                            .saveApiSession(account.id, account.name, account.role, token);
                    dbHelper.upsertApiProfile(account.id, account.name, account.email, account.phone,
                            account.profileImagePath, account.role, account.ward, account.operatorId);
                    Toast.makeText(RegisterActivity.this,
                            "Account created in " + ward + " Ward", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, ResidentHomeActivity.class));
                    finish();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> resetButton(message));
            }
        });
    }

    private void resetButton(String message) {
        btnCreateAccount.setEnabled(true);
        btnCreateAccount.setText("Create Account");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    private static String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
    private static void error(TextInputEditText input, String message) {
        input.setError(message); input.requestFocus();
    }
}
