package com.takago.app.common;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.takago.app.db.SessionManager;
import com.takago.app.network.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Shared, server-backed payment history for residents and drivers. */
public class TransactionHistoryActivity extends AppCompatActivity {
    private LinearLayout list;
    private SessionManager session;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionManager(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(0xFFF4F6F7);
        TextView header = new TextView(this); header.setText("‹   Transactions"); header.setTextSize(20); header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(0xFFFFFFFF); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20),dp(26),dp(20),dp(14));
        header.setBackgroundColor(0xFF18B95A); header.setOnClickListener(v -> finish()); page.addView(header,new LinearLayout.LayoutParams(-1,dp(76)));
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(16),dp(12),dp(16),dp(24));
        scroll.addView(list); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(page); load();
    }

    private void load() {
        ApiClient.get("/payments", session.getApiToken(), new ApiClient.JsonCallback() {
            public void onSuccess(JSONObject json) { runOnUiThread(() -> bind(json.optJSONArray("data"))); }
            public void onError(String message) { runOnUiThread(() -> Toast.makeText(TransactionHistoryActivity.this,message,Toast.LENGTH_LONG).show()); }
        });
    }

    private void bind(JSONArray rows) {
        list.removeAllViews();
        if (rows == null || rows.length() == 0) { addText("No transactions yet.",16,0xFF666666); return; }
        for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i); if(row==null)continue;
            String method=row.optString("method",row.optString("provider","Payment"));
            String status=row.optString("status","pending").replace('_',' ');
            String cash=row.optString("cash_status","").replace('_',' ');
            String visibleStatus = transactionStatus(session.getRole(), method, status, cash);
            TextView card=addText(String.format(Locale.US,"TZS %,.0f  •  %s\n%s\n%s",
                    row.optDouble("amount"),method,visibleStatus,row.optString("created_at","")),15,0xFF17324D);
            card.setBackgroundColor(0xFFFFFFFF); card.setPadding(dp(16),dp(14),dp(16),dp(14));
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)card.getLayoutParams();p.setMargins(0,0,0,dp(10));card.setLayoutParams(p);
        }
    }

    private static String transactionStatus(String role, String method, String paymentStatus, String cashStatus) {
        boolean cash = "cash".equalsIgnoreCase(method);
        if (!cash) return title(paymentStatus);
        if ("resident".equalsIgnoreCase(role)) {
            if ("held by driver".equalsIgnoreCase(cashStatus)
                    || "submitted for remittance".equalsIgnoreCase(cashStatus)
                    || "remitted".equalsIgnoreCase(cashStatus)) return "Complete – cash paid";
            return "Pending – confirm cash handover";
        }
        if ("driver".equalsIgnoreCase(role)) {
            if ("held by driver".equalsIgnoreCase(cashStatus)) return "Pending – remit cash to operator";
            if ("submitted for remittance".equalsIgnoreCase(cashStatus)) return "Pending – operator verification";
            if ("remitted".equalsIgnoreCase(cashStatus)) return "Complete – operator received cash";
        }
        return title(paymentStatus) + (cashStatus.isEmpty() ? "" : " – " + cashStatus);
    }

    private static String title(String value) {
        if (value == null || value.isEmpty()) return "Pending";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private TextView addText(String value,float size,int color){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);list.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
