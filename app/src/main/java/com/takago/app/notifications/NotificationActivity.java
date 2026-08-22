package com.takago.app.notifications;

import com.takago.app.data.model.NotificationRow;
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.DatabaseHelper;
import com.takago.app.db.SessionManager;

import java.util.List;
import java.util.Locale;

public class NotificationActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        dbHelper = new DatabaseHelper(this);
        session = new SessionManager(this);

        InsetsUtils.applyStatusBarTopPadding(findViewById(R.id.headerContainer));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearNotifications).setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear notifications?").setMessage("This removes all old notifications from your account.")
                .setPositiveButton("Clear all", (d, w) -> clearAll()).setNegativeButton("Cancel", null).show());

        loadNotifications();
        dbHelper.markAllNotificationsRead(session.getUserId());
        String token = session.getApiToken();
        for (NotificationRow item : dbHelper.getNotificationsForUser(session.getUserId())) {
            try { com.takago.app.network.ApiClient.patch("/notifications/" + item.id, token, new org.json.JSONObject().put("read_at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date())), new com.takago.app.network.ApiClient.JsonCallback() { public void onSuccess(org.json.JSONObject json) {} public void onError(String message) {} }); } catch (Exception ignored) {}
        }
    }

    private void clearAll() {
        dbHelper.clearNotificationsForUser(session.getUserId());
        loadNotifications();
        com.takago.app.network.ApiClient.delete("/notifications", session.getApiToken(),
                new com.takago.app.network.ApiClient.JsonCallback() {
                    public void onSuccess(org.json.JSONObject json) {}
                    public void onError(String message) { runOnUiThread(() -> android.widget.Toast.makeText(NotificationActivity.this, message, android.widget.Toast.LENGTH_LONG).show()); }
                });
    }
    private void loadNotifications() {
        LinearLayout container = findViewById(R.id.notificationListContainer);
        TextView tvNoNotifications = findViewById(R.id.tvNoNotifications);
        container.removeAllViews();

        List<NotificationRow> notifications = dbHelper.getNotificationsForUser(session.getUserId());

        if (notifications.isEmpty()) {
            tvNoNotifications.setVisibility(View.VISIBLE);
            return;
        }
        tvNoNotifications.setVisibility(View.GONE);

        for (NotificationRow notification : notifications) {
            View row = LayoutInflater.from(this).inflate(R.layout.row_notification, container, false);
            ((ImageView) row.findViewById(R.id.ivNotificationIcon)).setImageResource(iconFor(notification.title));
            ((TextView) row.findViewById(R.id.tvNotificationTitle)).setText(notification.title);
            ((TextView) row.findViewById(R.id.tvNotificationMessage)).setText(notification.message);
            ((TextView) row.findViewById(R.id.tvNotificationTime)).setText(notification.createdAt);
            container.addView(row);
        }
    }

    private int iconFor(String title) {
        String t = title.toLowerCase(Locale.US);
        if (t.contains("submitted")) {
            return R.drawable.ic_clipboard;
        } else if (t.contains("assigned") || t.contains("way")) {
            return R.drawable.ic_truck_outline;
        } else if (t.contains("completed")) {
            return R.drawable.ic_check;
        } else if (t.contains("cancelled")) {
            return R.drawable.ic_warning_circle;
        }
        return R.drawable.ic_recycle;
    }
}
