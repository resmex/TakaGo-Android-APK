package com.takago.app.app;

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
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        MyFirebaseMessagingService.requestNotificationPermission(this);

        // Safety net so the 5 demo accounts always exist, even on an older install.
        new DatabaseHelper(this).ensureDemoAccountsSeeded();

        new Handler(Looper.getMainLooper()).postDelayed(this::goToLogin, SPLASH_DELAY_MS);
    }

    private void goToLogin() {
        SessionManager session = new SessionManager(this);
        boolean remember = getSharedPreferences("login_preferences", MODE_PRIVATE).getBoolean("remember_me", false);
        if (!remember || !session.isLoggedIn()) {
            if (!remember) session.clearSession();
            startActivity(new Intent(this, LoginActivity.class)); finish(); return;
        }
        MyFirebaseMessagingService.registerAuthenticatedDevice(this);
        String role = session.getRole() == null ? "" : session.getRole().toLowerCase(java.util.Locale.US);
        Class<?> target;
        if ("resident".equals(role)) target=ResidentHomeActivity.class;
        else if ("driver".equals(role)) target=DriverHomeActivity.class;
        else if ("operator".equals(role)||"truck_owner".equals(role)) target=TruckOwnerHomeActivity.class;
        else if ("ward_admin".equals(role)) target=WardAdminHomeActivity.class;
        else if ("municipal_admin".equals(role)) target=MunicipalAdminHomeActivity.class;
        else target=LoginActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
