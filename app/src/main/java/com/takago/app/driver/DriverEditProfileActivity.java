package com.takago.app.driver;

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
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

/**
 * Driver's own profile editor - photo, phone, email, password only. Name and ward are NOT
 * editable here (ward is assigned by a Waste Operator/Admin, not the driver).
 */
public class DriverEditProfileActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private ImageView ivEditAvatar;
    private TextInputEditText etEditPhone, etEditEmail, etEditPassword, etEditConfirmPassword;
    private String currentPhotoPath;
    private String currentPhone;
    private String currentEmail;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    onPhotoSelected(ImageUtils.copyUriToAppFile(this, uri, "profile"));
                }
            });

    private final ActivityResultLauncher<String[]> filesLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    onPhotoSelected(ImageUtils.copyUriToAppFile(this, uri, "profile"));
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    onPhotoSelected(pendingCameraUri.getPath());
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_edit_profile);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        ivEditAvatar = findViewById(R.id.ivEditAvatar);
        etEditPhone = findViewById(R.id.etEditPhone);
        etEditEmail = findViewById(R.id.etEditEmail);
        etEditPassword = findViewById(R.id.etEditPassword);
        etEditConfirmPassword = findViewById(R.id.etEditConfirmPassword);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        loadCurrentProfile();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> showPhotoChooser());
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> saveProfile());
    }

    private void loadCurrentProfile() {
        UserAccount driver = dbHelper.getUserById(session.getUserId());
        if (driver == null) {
            return;
        }
        currentPhone = driver.phone;
        currentEmail = driver.email;
        etEditPhone.setHint(currentPhone != null && !currentPhone.trim().isEmpty() ? currentPhone : "+255 7XX XXX XXX");
        etEditEmail.setHint(currentEmail != null && !currentEmail.trim().isEmpty() ? currentEmail : "example@gmail.com");
        currentPhotoPath = driver.profileImagePath;
        ImageUtils.loadAvatar(ivEditAvatar, currentPhotoPath);
    }

    private void showPhotoChooser() {
        String[] options = {"Take photo", "Choose from Gallery", "Choose from Files"};
        new AlertDialog.Builder(this)
                .setTitle("Update profile photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraAndLaunch();
                    } else if (which == 1) {
                        galleryLauncher.launch("image/*");
                    } else {
                        filesLauncher.launch(new String[]{"image/*"});
                    }
                })
                .show();
    }

    private void requestCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        File photoFile = new File(getExternalFilesDir("Pictures"), "profile_" + System.currentTimeMillis() + ".jpg");
        pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        cameraLauncher.launch(pendingCameraUri);
    }

    private void onPhotoSelected(String path) {
        if (path == null) {
            Toast.makeText(this, "Could not load that photo", Toast.LENGTH_SHORT).show();
            return;
        }
        currentPhotoPath = path;
        ImageUtils.loadAvatar(ivEditAvatar, currentPhotoPath);
    }

    private void saveProfile() {
        String phone = etEditPhone.getText().toString().trim();
        String email = etEditEmail.getText().toString().trim();
        String password = etEditPassword.getText().toString().trim();
        String confirmPassword = etEditConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(phone)) phone = currentPhone;
        if (TextUtils.isEmpty(email)) email = currentEmail;

        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            etEditPhone.setError("Enter a valid phone number");
            etEditPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEditEmail.setError("Enter a valid email");
            etEditEmail.requestFocus();
            return;
        }

        if (!password.isEmpty() || !confirmPassword.isEmpty()) {
            if (password.length() < 8) {
                etEditPassword.setError("Password must be at least 8 characters");
                etEditPassword.requestFocus();
                return;
            }
            if (!password.equals(confirmPassword)) {
                etEditConfirmPassword.setError("Passwords do not match");
                etEditConfirmPassword.requestFocus();
                return;
            }
        }

        int driverId = session.getUserId();
        UserAccount driver = dbHelper.getUserById(driverId);
        String name = driver != null ? driver.name : session.getName();
        String finalPhone = phone, finalEmail = email, finalPassword = password;
        findViewById(R.id.btnSaveProfile).setEnabled(false);
        com.takago.app.network.ApiClient.updateProfile(session.getApiToken(), name, phone, email,
                password, currentPhotoPath, new com.takago.app.network.ApiClient.JsonCallback() {
            public void onSuccess(org.json.JSONObject json) { runOnUiThread(() -> {
                dbHelper.updateDriverProfile(driverId, finalPhone, finalEmail, finalPassword);
                String remotePhoto = json.optJSONObject("user") != null ? json.optJSONObject("user").optString("profile_image_url", currentPhotoPath) : currentPhotoPath;
                dbHelper.updateProfileImage(driverId, remotePhoto);
                Toast.makeText(DriverEditProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show(); finish();
            }); }
            public void onError(String message) { runOnUiThread(() -> { findViewById(R.id.btnSaveProfile).setEnabled(true); Toast.makeText(DriverEditProfileActivity.this, message, Toast.LENGTH_LONG).show(); }); }
        });
    }
}
