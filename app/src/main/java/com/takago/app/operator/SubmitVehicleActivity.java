package com.takago.app.operator;

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
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import org.json.JSONObject;

/** A Waste Operator submits a new vehicle for Municipal Admin approval. */
public class SubmitVehicleActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit_vehicle);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSubmitVehicle).setOnClickListener(v -> submit());
    }

    private void submit() {
        String plate = text(R.id.etVehiclePlate);
        String model = text(R.id.etVehicleModel);
        String capacity = text(R.id.etVehicleCapacity);
        String ward = text(R.id.etVehicleWard);

        if (plate.isEmpty() || model.isEmpty() || capacity.isEmpty() || ward.isEmpty()) {
            Toast.makeText(this, "Please fill in every field", Toast.LENGTH_SHORT).show();
            return;
        }

        dbHelper.submitVehicle(plate, model, capacity, session.getUserId(), ward);
        try {
            JSONObject body = new JSONObject().put("registration_number", plate)
                    .put("type", model).put("capacity_kg", Double.parseDouble(capacity));
            ApiClient.post("/vehicles", session.getApiToken(), body, new ApiClient.JsonCallback() {
                @Override public void onSuccess(JSONObject json) { runOnUiThread(() -> { Toast.makeText(SubmitVehicleActivity.this, "Vehicle submitted for approval", Toast.LENGTH_SHORT).show(); finish(); }); }
                @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(SubmitVehicleActivity.this, "Saved locally; server sync failed: " + message, Toast.LENGTH_LONG).show()); }
            });
        } catch (Exception e) { Toast.makeText(this, "Enter a numeric capacity", Toast.LENGTH_SHORT).show(); }
    }

    private String text(int id) {
        TextInputEditText field = findViewById(id);
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
