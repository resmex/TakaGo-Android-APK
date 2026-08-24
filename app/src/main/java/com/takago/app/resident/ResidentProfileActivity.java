package com.takago.app.resident;

import com.takago.app.data.model.UserAccount;
import com.takago.app.common.ImageUtils;
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.io.File;
import java.util.Locale;

public class ResidentProfileActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private ImageView ivProfileAvatar;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    saveProfileImage(ImageUtils.copyUriToAppFile(this, uri, "profile"));
                }
            });

    private final ActivityResultLauncher<String[]> filesLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    saveProfileImage(ImageUtils.copyUriToAppFile(this, uri, "profile"));
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    saveProfileImage(pendingCameraUri.getPath());
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
        setContentView(R.layout.activity_resident_profile);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        ivProfileAvatar = findViewById(R.id.ivProfileAvatar);

        loadProfile();
        setupClicks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        int residentId = session.getUserId();
        UserAccount resident = dbHelper.getUserById(residentId);

        TextView tvProfileName = findViewById(R.id.tvProfileName);
        TextView tvProfilePhone = findViewById(R.id.tvProfilePhone);
        TextView tvProfileLocation = findViewById(R.id.tvProfileLocation);
        TextView tvPickupCount = findViewById(R.id.tvProfilePickupCount);
        TextView tvRecycledKg = findViewById(R.id.tvProfileRecycledKg);

        tvProfileName.setText(session.getName());

        if (resident != null) {
            tvProfilePhone.setText(formatTanzaniaPhone(resident.phone));
            tvProfileLocation.setText(com.takago.app.location.ReadableLocationManager.primary(resident)
                    + "\n" + com.takago.app.location.ReadableLocationManager.wardLine(resident));
            ImageUtils.loadAvatar(ivProfileAvatar, resident.profileImagePath);
        }

        tvPickupCount.setText(String.valueOf(dbHelper.getTotalPickupsForResident(residentId)));
        double recycledKg = dbHelper.getRecycledKgForResident(residentId);
        tvRecycledKg.setText(String.format(Locale.US, "%.0f kg", recycledKg));

        loadNotificationBadge(residentId);
    }

    private void loadNotificationBadge(int residentId) {
        int unread = dbHelper.getUnreadNotificationCount(residentId);
        TextView tvBadge = findViewById(R.id.tvProfileNotificationBadge);
        if (unread > 0) {
            tvBadge.setVisibility(View.VISIBLE);
            tvBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            tvBadge.setVisibility(View.GONE);
        }
    }

    /** Displays a Tanzanian phone number in +255 format, regardless of how it was originally entered. */
    private String formatTanzaniaPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "+255";
        }
        if (phone.startsWith("+255")) {
            return phone;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return "+255" + digits;
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

    private void saveProfileImage(String path) {
        final String preparedPath=ImageUtils.prepareImageForUpload(this,path,"profile",ImageUtils.MAX_PROFILE_IMAGE_BYTES);
        if (preparedPath == null) {
            Toast.makeText(this, "Could not load that photo", Toast.LENGTH_SHORT).show();
            return;
        }
        UserAccount resident = dbHelper.getUserById(session.getUserId());
        if (resident == null) return;
        ImageUtils.loadAvatar(ivProfileAvatar, preparedPath);
        com.takago.app.network.ApiClient.updateProfile(session.getApiToken(), resident.name,
                resident.phone, resident.email, "", preparedPath,
                new com.takago.app.network.ApiClient.JsonCallback() {
                    public void onSuccess(org.json.JSONObject json) { runOnUiThread(() -> {
                        org.json.JSONObject user = json.optJSONObject("user");
                        String remotePhoto = user != null ? user.optString("profile_image_url", preparedPath) : preparedPath;
                        dbHelper.updateProfileImage(session.getUserId(), remotePhoto);
                        Toast.makeText(ResidentProfileActivity.this, "Profile photo updated", Toast.LENGTH_SHORT).show();
                    }); }
                    public void onError(String message) { runOnUiThread(() -> {
                        loadProfile();
                        Toast.makeText(ResidentProfileActivity.this, message, Toast.LENGTH_LONG).show();
                    }); }
                });
    }

    private void setupClicks() {
        addTransactionsShortcut();
        addComplaintsShortcut();
        ivProfileAvatar.setOnClickListener(v -> showPhotoChooser());

        findViewById(R.id.btnSignOut).setOnClickListener(v -> {
            session.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnEdit).setOnClickListener(v ->
                startActivity(new Intent(this, ResidentEditProfileActivity.class)));

        findViewById(R.id.rowNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationActivity.class)));
        findViewById(R.id.rowLanguage).setOnClickListener(v ->
                Toast.makeText(this, "Language screen coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.rowPrivacy).setOnClickListener(v ->
                Toast.makeText(this, "Privacy & security screen coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.rowHelp).setOnClickListener(v ->
                startActivity(new Intent(this, InfoActivity.class)
                        .putExtra(InfoActivity.EXTRA_TITLE, "Help Centre")
                        .putExtra(InfoActivity.EXTRA_BODY, "Need help with takaGo?\n\n• For pickup or collection problems, open Complaints from your profile and submit the details.\n• To contact an assigned driver, open the active pickup and tap Call or Message. takaGo opens your phone dialer or SMS app; it never places a call or sends a message without you.\n• For schedule questions, open Upcoming schedules and tap the driver details to open the dialer.\n• Check Notifications for assignment, arrival, payment, complaint and completion updates.")));

        findViewById(R.id.navHome).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentHomeActivity.class));
            finish();
        });
        findViewById(R.id.navTrack).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentTrackActivity.class));
            finish();
        });
        findViewById(R.id.navHistory).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentHistoryActivity.class));
            finish();
        });
        findViewById(R.id.navProfile).setOnClickListener(v -> {
            // already on Profile
        });
    }

    private void addTransactionsShortcut() {
        View notifications = findViewById(R.id.rowNotifications);
        android.view.ViewGroup parent = (android.view.ViewGroup) notifications.getParent();
        TextView row = new TextView(this); row.setText("Transactions                                      ›");
        row.setTextSize(14); row.setTextColor(0xFF1A1A1A); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(16,0,16,0); row.setOnClickListener(v -> startActivity(new Intent(this, TransactionHistoryActivity.class)));
        parent.addView(row, parent.indexOfChild(notifications), new android.view.ViewGroup.LayoutParams(-1, (int)(56*getResources().getDisplayMetrics().density)));
    }
    private void addComplaintsShortcut() {
        View notifications=findViewById(R.id.rowNotifications);android.view.ViewGroup parent=(android.view.ViewGroup)notifications.getParent();
        TextView row=new TextView(this);row.setText("Complaints                                      ›");row.setTextSize(14);row.setTextColor(0xFF1A1A1A);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(16,0,16,0);row.setOnClickListener(v->startActivity(new Intent(this,ResidentComplaintsActivity.class)));parent.addView(row,parent.indexOfChild(notifications),new android.view.ViewGroup.LayoutParams(-1,(int)(56*getResources().getDisplayMetrics().density)));
    }
}
