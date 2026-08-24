package com.takago.app.resident;

import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.WardRow;
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
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.location.ReadableLocationManager;
import com.takago.app.network.ApiClient;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ResidentRequestPickupActivity extends AppCompatActivity {

    private static final String[] WASTE_TYPES = {"Household", "Garden", "Recyclables", "Construction", "Electronic", "Hazardous"};

    private DatabaseHelper dbHelper;
    private SessionManager session;
    private FusedLocationProviderClient locationClient;

    private TextView tvLocationTitle, tvLocationSubtitle, tvPhotoLabel, tvPriceEstimate, tvPriceEstimateNote;
    private ImageView ivWasteSmall, ivWasteMedium, ivWasteLarge, ivPhotoThumbnail;
    private TextView tvLabelSmall, tvLabelMedium, tvLabelLarge;
    private LinearLayout wasteTypeChipRow;

    private String selectedSize = "Small";
    private String selectedWasteType = "Household";
    private double selectedLat = Double.NaN;
    private double selectedLng = Double.NaN;
    private boolean locationResolved;
    private String selectedWard;
    private int selectedWardId = -1;
    private String selectedPlaceId;
    private String selectedHouseNumber;
    private String selectedStreetName;
    private String selectedFormattedAddress;
    private String selectedPlaceName;
    private String selectedPlusCode;
    private int routeInvitationId;
    private String residentWard;
    private String pendingPhotoPath;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<Intent> locationPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Intent data = result.getData();
                selectedLat = data.getDoubleExtra(ResidentPickupLocationActivity.EXTRA_RESULT_LAT, selectedLat);
                selectedLng = data.getDoubleExtra(ResidentPickupLocationActivity.EXTRA_RESULT_LNG, selectedLng);
                selectedWard = data.getStringExtra(ResidentPickupLocationActivity.EXTRA_RESULT_WARD);
                selectedWardId = data.getIntExtra(
                        ResidentPickupLocationActivity.EXTRA_RESULT_WARD_ID, -1);
                selectedPlaceId = data.getStringExtra(
                        ResidentPickupLocationActivity.EXTRA_RESULT_PLACE_ID);
                selectedHouseNumber = data.getStringExtra(
                        ResidentPickupLocationActivity.EXTRA_RESULT_HOUSE_NUMBER);
                selectedStreetName = data.getStringExtra(
                        ResidentPickupLocationActivity.EXTRA_RESULT_STREET_NAME);
                selectedFormattedAddress = data.getStringExtra(
                        ResidentPickupLocationActivity.EXTRA_RESULT_FORMATTED_ADDRESS);
                selectedPlaceName = data.getStringExtra(ResidentPickupLocationActivity.EXTRA_RESULT_PLACE_NAME);
                selectedPlusCode = data.getStringExtra(ResidentPickupLocationActivity.EXTRA_RESULT_PLUS_CODE);
                locationResolved = RoutingService.isValidCoordinate(selectedLat, selectedLng);
                if (selectedWard == null) {
                    selectedWard = residentWard;
                }
                String address = data.getStringExtra(ResidentPickupLocationActivity.EXTRA_RESULT_ADDRESS);
                if (address != null) {
                    tvLocationTitle.setText(address.trim());
                    tvLocationSubtitle.setText(selectedWard != null ? selectedWard + " Ward" : "");
                }
                dbHelper.saveReadableUserLocation(session.getUserId(), selectedLat, selectedLng,
                        selectedHouseNumber, selectedStreetName, selectedPlaceName,
                        selectedFormattedAddress, selectedPlusCode, selectedWard);
            });

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String path = ImageUtils.copyUriToAppFile(this, uri, "pickup");
                    if (path != null) {
                        onPhotoSelected(path);
                    }
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

    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    resolveInitialLocation();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_request_pickup);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);
        routeInvitationId = getIntent().getIntExtra("route_invitation_id", 0);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        UserAccount resident = dbHelper.getUserById(session.getUserId());
        residentWard = resident != null && resident.ward != null && !resident.ward.trim().isEmpty()
                ? resident.ward.trim() : null;
        selectedWard = residentWard;
        selectedWardId = dbHelper.findWardIdByName(residentWard);

        tvLocationTitle = findViewById(R.id.tvLocationTitle);
        tvLocationSubtitle = findViewById(R.id.tvLocationSubtitle);
        tvPhotoLabel = findViewById(R.id.tvPhotoLabel);
        ivPhotoThumbnail = findViewById(R.id.ivPhotoThumbnail);
        tvPriceEstimate = findViewById(R.id.tvPriceEstimate);
        tvPriceEstimateNote = findViewById(R.id.tvPriceEstimateNote);
        ivWasteSmall = findViewById(R.id.ivWasteSmall);
        ivWasteMedium = findViewById(R.id.ivWasteMedium);
        ivWasteLarge = findViewById(R.id.ivWasteLarge);
        tvLabelSmall = findViewById(R.id.tvLabelSmall);
        tvLabelMedium = findViewById(R.id.tvLabelMedium);
        tvLabelLarge = findViewById(R.id.tvLabelLarge);
        wasteTypeChipRow = findViewById(R.id.wasteTypeChipRow);

        buildWasteTypeChips();
        setupClicks();
        updatePriceEstimate();

        tvLocationTitle.setText(ReadableLocationManager.primary(resident));
        tvLocationSubtitle.setText(ReadableLocationManager.wardLine(resident));
        resolveInitialLocation();
    }

    private void resolveInitialLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            tvLocationTitle.setText("Choose pickup location");
                            tvLocationSubtitle.setText("Ward unavailable");
                            return;
                        }
                        selectedLat = location.getLatitude();
                        selectedLng = location.getLongitude();
                        locationResolved = RoutingService.isValidCoordinate(selectedLat, selectedLng);
                        reverseGeocode(selectedLat, selectedLng);
                    })
                    .addOnFailureListener(error -> {
                        tvLocationTitle.setText("Choose pickup location");
                        tvLocationSubtitle.setText("Ward unavailable");
                    });
        } catch (SecurityException e) {
            tvLocationTitle.setText("Choose pickup location");
            tvLocationSubtitle.setText("Ward unavailable");
        }
    }

    /** One-shot reverse geocode for the initial address shown on this screen (no live map here). */
    private void reverseGeocode(double lat, double lng) {
        new Thread(() -> {
            Address address = null;
            String ward = null;
            int wardId = -1;
            try {
                WardRow polygonWard = dbHelper.findMappedWardContaining(lat, lng);
                if (polygonWard != null) {
                    ward = polygonWard.name;
                    wardId = polygonWard.id;
                }
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results != null && !results.isEmpty()) {
                    address = results.get(0);
                }
            } catch (IOException e) {
                // No internet or geocoder service unavailable - handled below.
            }

            Address finalAddress = address;
            String finalWard = ward;
            int finalWardId = wardId;
            runOnUiThread(() -> NearbyPoiResolver.resolve(this, lat, lng, poiName -> {
                ReadableAddress readable = ReadableAddress.from(finalAddress, finalWard, poiName);
                String resolvedWard = finalWard != null ? finalWard : residentWard;
                tvLocationTitle.setText(!readable.label.isEmpty() ? readable.label
                        : (resolvedWard != null ? resolvedWard : "Choose pickup location"));
                tvLocationSubtitle.setText(resolvedWard != null ? resolvedWard + " Ward" : "Ward unavailable");
                selectedWard = resolvedWard;
                selectedWardId = finalWardId > 0 ? finalWardId : selectedWardId;
                selectedHouseNumber = readable.houseNumber;
                selectedStreetName = readable.streetName;
                selectedPlaceName = !readable.placeName.isEmpty()
                        ? readable.placeName : readable.neighbourhood;
                selectedFormattedAddress = readable.formattedAddress;
                selectedPlusCode = readable.plusCode;
            }));
        }).start();
    }

    private void buildWasteTypeChips() {
        for (String type : WASTE_TYPES) {
            TextView chip = new TextView(this);
            chip.setText(type);
            chip.setTextSize(13);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);
            chip.setTag(type);
            chip.setOnClickListener(v -> {
                selectedWasteType = type;
                refreshWasteTypeChipStyles();
                updatePriceEstimate();
            });
            wasteTypeChipRow.addView(chip);
        }
        refreshWasteTypeChipStyles();
    }

    private void refreshWasteTypeChipStyles() {
        for (int i = 0; i < wasteTypeChipRow.getChildCount(); i++) {
            TextView chip = (TextView) wasteTypeChipRow.getChildAt(i);
            boolean selected = chip.getTag().equals(selectedWasteType);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF555555);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updatePriceEstimate() {
        if ("Hazardous".equals(selectedWasteType)) {
            tvPriceEstimate.setText("Manual pricing");
            tvPriceEstimateNote.setText("Hazardous waste needs manual pricing approval from the Municipal " +
                    "Admin after collection - no automatic estimate is shown.");
            return;
        }
        double[] range = dbHelper.computeEstimatedPriceRange(selectedSize, selectedWasteType);
        tvPriceEstimate.setText(String.format(Locale.US, "TZS %,.0f - %,.0f", range[0], range[1]));
        tvPriceEstimateNote.setText("Final price is calculated after the driver measures the waste weight.");
    }

    private void setupClicks() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.optionSmall).setOnClickListener(v -> selectSize("Small"));
        findViewById(R.id.optionMedium).setOnClickListener(v -> selectSize("Medium"));
        findViewById(R.id.optionLarge).setOnClickListener(v -> selectSize("Large"));

        findViewById(R.id.tvChangeLocation).setOnClickListener(v -> openLocationPicker());

        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> showPhotoChooser());

        findViewById(R.id.btnConfirmPickup).setOnClickListener(v -> confirmPickup());
    }

    private void openLocationPicker() {
        Intent intent = new Intent(this, ResidentPickupLocationActivity.class);
        if (locationResolved) {
            intent.putExtra(ResidentPickupLocationActivity.EXTRA_INITIAL_LAT, selectedLat);
            intent.putExtra(ResidentPickupLocationActivity.EXTRA_INITIAL_LNG, selectedLng);
        }
        locationPickerLauncher.launch(intent);
    }

    private void selectSize(String size) {
        selectedSize = size;
        applyOptionStyle(ivWasteSmall, tvLabelSmall, size.equals("Small"));
        applyOptionStyle(ivWasteMedium, tvLabelMedium, size.equals("Medium"));
        applyOptionStyle(ivWasteLarge, tvLabelLarge, size.equals("Large"));
        updatePriceEstimate();
    }

    private void applyOptionStyle(ImageView icon, TextView label, boolean selected) {
        View option = (View) icon.getParent();
        option.setBackgroundResource(selected
                ? R.drawable.bg_size_option_selected : R.drawable.bg_size_option_unselected);
        if (selected) {
            icon.setBackgroundResource(R.drawable.bg_circle_green_solid);
            icon.setColorFilter(0xFFFFFFFF);
            label.setTextColor(0xFF1A1A1A);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            icon.setBackgroundResource(0);
            icon.setColorFilter(0xFF999999);
            label.setTextColor(0xFF999999);
            label.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void showPhotoChooser() {
        String[] options = {"Take photo", "Choose from gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Add photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCameraAndLaunch();
                    } else {
                        galleryLauncher.launch("image/*");
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
        File photoFile = new File(getExternalFilesDir("Pictures"), "pickup_" + System.currentTimeMillis() + ".jpg");
        pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        cameraLauncher.launch(pendingCameraUri);
    }

    private void onPhotoSelected(String path) {
        String prepared=ImageUtils.prepareImageForUpload(this,path,"pickup",ImageUtils.MAX_PICKUP_IMAGE_BYTES);
        if(prepared==null){pendingPhotoPath=null;Toast.makeText(this,"Choose a valid JPG, PNG or WebP image up to 4 MB.",Toast.LENGTH_LONG).show();return;}
        pendingPhotoPath = prepared;
        tvPhotoLabel.setText("Waste photo added (maximum 4 MB)");
        tvPhotoLabel.setTextColor(0xFF2E7D32);
        ivPhotoThumbnail.setVisibility(View.VISIBLE);
        ImageUtils.loadAvatar(ivPhotoThumbnail, prepared);
    }

    private void confirmPickup() {
        String activePickup = dbHelper.getActivePickupSummaryForResident(session.getUserId());
        if (activePickup != null) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Pickup already in progress")
                    .setMessage(activePickup + ". Complete or cancel this pickup before requesting another one.")
                    .setPositiveButton("View current pickup", (dialog, which) -> {
                        startActivity(new Intent(this, ResidentTrackActivity.class));
                        finish();
                    })
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }
        if (!locationResolved || !RoutingService.isValidCoordinate(selectedLat, selectedLng)) {
            Toast.makeText(this, "Choose a valid pickup location first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedWardId <= 0) {
            Toast.makeText(this, "Confirm the pickup ward from the location picker.", Toast.LENGTH_LONG).show();
            openLocationPicker();
            return;
        }
        String address = tvLocationTitle.getText().toString();
        String ward = selectedWard != null ? selectedWard : residentWard;

        long pickupId = dbHelper.createPickupRequest(session.getUserId(), selectedSize,
                selectedWasteType, selectedLat, selectedLng, address, selectedPlaceId,
                selectedWardId, ward, pendingPhotoPath, selectedHouseNumber, selectedStreetName,
                selectedFormattedAddress);
        if (pickupId <= 0) {
            Toast.makeText(this,
                    "This pickup location is outside the currently supported service area.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        dbHelper.savePickupReadableAddress(pickupId, selectedPlaceName, selectedPlusCode);
        try {
            JSONObject body = new JSONObject().put("waste_type", selectedWasteType)
                    .put("size", selectedSize).put("address", address).put("ward_name", selectedWard)
                    .put("latitude", selectedLat).put("longitude", selectedLng);
            if (routeInvitationId > 0) body.put("route_invitation_id", routeInvitationId);
            ApiClient.post("/pickups", session.getApiToken(), body, new ApiClient.JsonCallback() {
                @Override public void onSuccess(JSONObject json) {
                    JSONObject serverPickup = json.optJSONObject("data");
                    int serverPickupId = serverPickup != null ? serverPickup.optInt("id", -1) : -1;
                    if (serverPickupId > 0 && pendingPhotoPath != null && !pendingPhotoPath.trim().isEmpty()) {
                        ApiClient.uploadPickupImage(session.getApiToken(), serverPickupId, "request",
                                pendingPhotoPath, new ApiClient.JsonCallback() {
                                    public void onSuccess(JSONObject uploaded) { com.takago.app.network.ServerSyncManager.syncAll(ResidentRequestPickupActivity.this); }
                                    public void onError(String message) { runOnUiThread(() -> Toast.makeText(ResidentRequestPickupActivity.this, "Pickup saved, but photo upload failed: " + message, Toast.LENGTH_LONG).show()); }
                                });
                    } else com.takago.app.network.ServerSyncManager.syncAll(ResidentRequestPickupActivity.this);
                }
                @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(ResidentRequestPickupActivity.this, "Request saved offline; sync will need retry", Toast.LENGTH_LONG).show()); }
            });
        } catch (Exception ignored) { }

        Intent intent = new Intent(this, FindingDriverActivity.class);
        intent.putExtra("pickupId", (int) pickupId);
        startActivity(intent);
        finish();
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
