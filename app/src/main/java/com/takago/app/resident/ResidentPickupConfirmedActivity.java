package com.takago.app.resident;

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

import androidx.appcompat.app.AppCompatActivity;

public class ResidentPickupConfirmedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pickup_confirmed);

        findViewById(R.id.btnTrackNow).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentTrackActivity.class));
            finish();
        });

        findViewById(R.id.btnBackHome).setOnClickListener(v -> {
            startActivity(new Intent(this, ResidentHomeActivity.class));
            finish();
        });
    }
}
