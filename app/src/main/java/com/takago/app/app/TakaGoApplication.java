package com.takago.app.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.takago.app.location.RoutingService;
import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import com.takago.app.network.ServerSyncManager;
import org.json.JSONObject;
import com.google.android.libraries.places.api.Places;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/** Application-wide UI configuration. */
public class TakaGoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // The layouts use light surfaces. Keep Material input text/icons on the matching
        // light palette so entered text stays readable when the phone uses dark mode.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        RoutingService.initialize(this);
        initializePlaces();
        refreshSharedProfile();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityResumed(android.app.Activity activity) { ServerSyncManager.syncAll(activity); }
            public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {} public void onActivityStarted(android.app.Activity a) {} public void onActivityPaused(android.app.Activity a) {} public void onActivityStopped(android.app.Activity a) {} public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {} public void onActivityDestroyed(android.app.Activity a) {}
        });

    }

    private void refreshSharedProfile() {
        SessionManager session = new SessionManager(this);
        String token = session.getApiToken();
        if (!session.isLoggedIn() || token.isEmpty()) {
            return;
        }
        ApiClient.get("/me", token, new ApiClient.JsonCallback() {
            @Override public void onSuccess(JSONObject response) {
                JSONObject user = response.optJSONObject("user");
                if (user == null) return;
                int id = user.optInt("id", session.getUserId());
                String name = user.optString("name", session.getName());
                String role = user.optString("role", session.getRole());
                String email = user.optString("email", "");
                String phone = user.optString("phone", "");
                String imageUrl = user.optString("profile_image_url", "");
                new DatabaseHelper(TakaGoApplication.this)
                        .updateSharedProfile(id, name, email, phone, imageUrl);
                session.saveApiSession(id, name, role, token);
            }
            @Override public void onError(String message) {
                // Keep cached profile details while the server is unavailable.
            }
        });
    }

    private void initializePlaces() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            String key = info.metaData != null
                    ? info.metaData.getString("com.google.android.geo.API_KEY", "") : "";
            if (key != null && !key.trim().isEmpty() && !Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(this, key.trim());
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            Log.e("TakaGoApplication", "Places SDK initialization failed", error);
        }
    }
}
