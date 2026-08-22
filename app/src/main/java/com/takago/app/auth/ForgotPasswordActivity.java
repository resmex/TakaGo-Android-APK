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
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordActivity extends AppCompatActivity {

    TextInputEditText etResetEmail;
    Button btnSendResetLink;
    TextView tvBackToSignIn;
    DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        dbHelper = new DatabaseHelper(this);

        etResetEmail = findViewById(R.id.etResetEmail);
        btnSendResetLink = findViewById(R.id.btnSendResetLink);
        tvBackToSignIn = findViewById(R.id.tvBackToSignIn);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvBackToSignIn.setOnClickListener(v -> finish());

        btnSendResetLink.setOnClickListener(v -> sendResetLink());
    }

    private void sendResetLink() {
        String email = etResetEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etResetEmail.setError("Email is required");
            etResetEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etResetEmail.setError("Enter a valid email");
            etResetEmail.requestFocus();
            return;
        }

        UserAccount account = dbHelper.getUserByEmail(email);
        if (account == null) {
            etResetEmail.setError("No account found with this email");
            etResetEmail.requestFocus();
            return;
        }

        Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_SHORT).show();
        finish();
    }
}
