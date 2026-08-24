package com.takago.app.auth;

import com.takago.app.data.model.UserAccount;
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
import com.takago.app.network.ApiClient;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import com.google.android.material.checkbox.MaterialCheckBox;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    TextInputEditText etEmailPhone, etPassword;
    MaterialCheckBox checkboxRememberMe;
    Button btnLogin;
    TextView tvCreateAccount;
    TextView tvForgotPassword;
    DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        dbHelper = new DatabaseHelper(this);

        etEmailPhone = findViewById(R.id.etEmailPhone);
        etPassword = findViewById(R.id.etPassword);
        checkboxRememberMe = findViewById(R.id.checkboxRememberMe);
        android.content.SharedPreferences loginPrefs = getSharedPreferences("login_preferences", MODE_PRIVATE);
        checkboxRememberMe.setChecked(loginPrefs.getBoolean("remember_me", false));
        if (checkboxRememberMe.isChecked()) etEmailPhone.setText(loginPrefs.getString("login", ""));
        btnLogin = findViewById(R.id.btnLogin);
        tvCreateAccount = findViewById(R.id.tvCreateAccount);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        MyFirebaseMessagingService.requestNotificationPermission(this);

        btnLogin.setOnClickListener(v -> validateLogin());

        tvCreateAccount.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void validateLogin() {
        String emailPhone = etEmailPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(emailPhone)) {
            etEmailPhone.setError("Email or phone is required");
            etEmailPhone.requestFocus();
            return;
        }

        if (emailPhone.contains("@")) {
            if (!Patterns.EMAIL_ADDRESS.matcher(emailPhone).matches()) {
                etEmailPhone.setError("Enter a valid email");
                etEmailPhone.requestFocus();
                return;
            }
        } else {
            if (emailPhone.length() < 10) {
                etEmailPhone.setError("Enter a valid phone number");
                etEmailPhone.requestFocus();
                return;
            }
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 8) {
            etPassword.setError("Password must be at least 8 characters");
            etPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Signing in...");
        ApiClient.login(emailPhone, password, new ApiClient.LoginCallback() {
            @Override public void onSuccess(UserAccount account, String token) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Login");
                    new SessionManager(LoginActivity.this).saveApiSession(account.id, account.name, account.role, token);
                    MyFirebaseMessagingService.registerAuthenticatedDevice(LoginActivity.this);
                    getSharedPreferences("login_preferences", MODE_PRIVATE).edit()
                            .putBoolean("remember_me", checkboxRememberMe.isChecked())
                            .putString("login", checkboxRememberMe.isChecked() ? emailPhone : "").apply();
                    dbHelper.upsertApiProfile(account.id, account.name, account.email, account.phone,
                            account.profileImagePath, account.role, account.ward, account.operatorId);
                    openHome(account);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Login");
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openHome(UserAccount account) {
        Toast.makeText(this, "Welcome, " + account.name, Toast.LENGTH_SHORT).show();

        String role = account.role == null ? "" : account.role.trim().toLowerCase(Locale.US);
        Intent intent;
        if ("municipal_admin".equals(role)) {
            intent = new Intent(LoginActivity.this, MunicipalAdminHomeActivity.class);
        } else if ("resident".equals(role)) {
            intent = new Intent(LoginActivity.this, ResidentHomeActivity.class);
        } else if ("driver".equals(role)) {
            intent = new Intent(LoginActivity.this, DriverHomeActivity.class);
        } else if ("operator".equals(role) || "truck_owner".equals(role)) {
            intent = new Intent(LoginActivity.this, TruckOwnerHomeActivity.class);
        } else if ("ward_admin".equals(role)) {
            intent = new Intent(LoginActivity.this, WardAdminHomeActivity.class);
        } else {
            Toast.makeText(this, "This account role is not supported in the Android app.", Toast.LENGTH_LONG).show();
            new SessionManager(this).clearSession();
            return;
        }
        startActivity(intent);
        finish();
    }
}
