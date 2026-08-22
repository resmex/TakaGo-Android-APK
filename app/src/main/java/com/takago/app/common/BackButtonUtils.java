package com.takago.app.common;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.takago.app.R;

public final class BackButtonUtils {
    private BackButtonUtils() {}

    public static ImageView addMapBackButton(Activity activity, FrameLayout root) {
        ImageView back = new ImageView(activity);
        back.setId(R.id.btnBack);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription("Back");
        back.setColorFilter(Color.rgb(24, 34, 29));
        back.setBackgroundResource(R.drawable.bg_circle_white_solid);
        back.setElevation(dp(activity, 8));
        back.setPadding(dp(activity, 11), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(activity, 46), dp(activity, 46));
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMargins(dp(activity, 16), dp(activity, 48), 0, 0);
        root.addView(back, params);
        back.setOnClickListener(v -> activity.finish());
        return back;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
