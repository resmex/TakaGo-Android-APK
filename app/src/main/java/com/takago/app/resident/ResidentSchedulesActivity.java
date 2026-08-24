package com.takago.app.resident;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class ResidentSchedulesActivity extends AppCompatActivity {
    private LinearLayout list;
    private SessionManager session;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { load(); refreshHandler.postDelayed(this, 2500); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionManager(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        TextView title = new TextView(this);
        title.setText("Upcoming collection schedules");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 70, 55));
        root.addView(title);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refresh);
        refreshHandler.post(refresh);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refresh);
        super.onPause();
    }

    private void load() {
        ApiClient.get("/resident/schedules", session.getApiToken(), new ApiClient.JsonCallback() {
            @Override public void onSuccess(JSONObject json) { runOnUiThread(() -> show(json.optJSONArray("data"))); }
            @Override public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ResidentSchedulesActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void show(JSONArray schedules) {
        list.removeAllViews();
        if (schedules == null || schedules.length() == 0) {
            add("No upcoming collection is scheduled for your ward yet.", null);
            return;
        }
        for (int index = 0; index < schedules.length(); index++) {
            JSONObject schedule = schedules.optJSONObject(index);
            if (schedule == null) continue;
            String phone = schedule.optString("driver_phone");
            add(schedule.optString("street") + " - " + schedule.optString("ward_name")
                    + "\nDate/time: " + schedule.optString("scheduled_at").replace('T', ' ')
                    + "\nDriver: " + schedule.optString("driver_name", "To be assigned")
                    + "\nPhone: " + (phone.isEmpty() ? "Not available" : phone), phone);
        }
    }

    private void add(String value, String phone) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(Color.DKGRAY);
        view.setBackgroundColor(Color.WHITE);
        view.setPadding(24, 24, 24, 24);
        if (phone != null && !phone.isEmpty()) {
            view.setOnClickListener(ignored -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 12, 0, 12);
        list.addView(view, params);
    }
}
