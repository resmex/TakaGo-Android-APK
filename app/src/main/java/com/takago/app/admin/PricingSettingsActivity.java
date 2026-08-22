package com.takago.app.admin;

import com.takago.app.data.model.PricingSettings;
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
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;

import java.util.Locale;

/** Municipal Admin configures the pricing engine's rates - nothing here is hardcoded in the app. */
public class PricingSettingsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    private EditText etBookingFee, etIncludedWeight, etRatePerKg, etDistanceFreeKm, etDistanceFeePerKm;
    private EditText etMultHousehold, etMultGarden, etMultRecyclables, etMultConstruction, etMultElectronic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pricing_settings);

        dbHelper = new DatabaseHelper(this);

        etBookingFee = findViewById(R.id.etBookingFee);
        etIncludedWeight = findViewById(R.id.etIncludedWeight);
        etRatePerKg = findViewById(R.id.etRatePerKg);
        etDistanceFreeKm = findViewById(R.id.etDistanceFreeKm);
        etDistanceFeePerKm = findViewById(R.id.etDistanceFeePerKm);
        etMultHousehold = findViewById(R.id.etMultHousehold);
        etMultGarden = findViewById(R.id.etMultGarden);
        etMultRecyclables = findViewById(R.id.etMultRecyclables);
        etMultConstruction = findViewById(R.id.etMultConstruction);
        etMultElectronic = findViewById(R.id.etMultElectronic);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSavePricing).setOnClickListener(v -> save());

        loadSettings();
    }

    private void loadSettings() {
        PricingSettings s = dbHelper.getPricingSettings();
        etBookingFee.setText(formatNumber(s.bookingFee));
        etIncludedWeight.setText(formatNumber(s.includedWeightKg));
        etRatePerKg.setText(formatNumber(s.ratePerKg));
        etDistanceFreeKm.setText(formatNumber(s.distanceFreeKm));
        etDistanceFeePerKm.setText(formatNumber(s.distanceFeePerKm));
        etMultHousehold.setText(formatNumber(s.multHousehold));
        etMultGarden.setText(formatNumber(s.multGarden));
        etMultRecyclables.setText(formatNumber(s.multRecyclables));
        etMultConstruction.setText(formatNumber(s.multConstruction));
        etMultElectronic.setText(formatNumber(s.multElectronic));
    }

    private void save() {
        Double bookingFee = parse(etBookingFee);
        Double includedWeight = parse(etIncludedWeight);
        Double ratePerKg = parse(etRatePerKg);
        Double distanceFreeKm = parse(etDistanceFreeKm);
        Double distanceFeePerKm = parse(etDistanceFeePerKm);
        Double multHousehold = parse(etMultHousehold);
        Double multGarden = parse(etMultGarden);
        Double multRecyclables = parse(etMultRecyclables);
        Double multConstruction = parse(etMultConstruction);
        Double multElectronic = parse(etMultElectronic);

        if (bookingFee == null || includedWeight == null || ratePerKg == null || distanceFreeKm == null ||
                distanceFeePerKm == null || multHousehold == null || multGarden == null ||
                multRecyclables == null || multConstruction == null || multElectronic == null) {
            Toast.makeText(this, "Please enter a valid number in every field", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bookingFee < 0 || includedWeight < 0 || ratePerKg < 0 || distanceFreeKm < 0 || distanceFeePerKm < 0) {
            Toast.makeText(this, "Fees, weight and distance cannot be negative", Toast.LENGTH_SHORT).show();
            return;
        }

        PricingSettings s = new PricingSettings();
        s.bookingFee = bookingFee;
        s.includedWeightKg = includedWeight;
        s.ratePerKg = ratePerKg;
        s.distanceFreeKm = distanceFreeKm;
        s.distanceFeePerKm = distanceFeePerKm;
        s.multHousehold = multHousehold;
        s.multGarden = multGarden;
        s.multRecyclables = multRecyclables;
        s.multConstruction = multConstruction;
        s.multElectronic = multElectronic;

        dbHelper.updatePricingSettings(s);
        Toast.makeText(this, "Pricing settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private Double parse(EditText field) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
