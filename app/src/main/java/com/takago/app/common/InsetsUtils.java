package com.takago.app.common;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Pads a header view by the real system status bar height instead of a hardcoded guess, so
 * headers sit correctly below the status bar on every device/API level - including Android 15+
 * where edge-to-edge is enforced by default and content would otherwise draw underneath it.
 */
public class InsetsUtils {

    private InsetsUtils() {
    }

    /** Adds the status bar's height on top of whatever top padding the view already has in XML. */
    public static void applyStatusBarTopPadding(View header) {
        if (header == null) {
            return;
        }
        int baseLeft = header.getPaddingLeft();
        int baseTop = header.getPaddingTop();
        int baseRight = header.getPaddingRight();
        int baseBottom = header.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(baseLeft, baseTop + statusBars.top, baseRight, baseBottom);
            return insets;
        });
    }

    /** Adds the status bar's height on top of whatever top margin the view already has in XML - for floating buttons positioned by margin instead of padding. */
    public static void applyStatusBarTopMargin(View floatingView) {
        if (floatingView == null || !(floatingView.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams)) {
            return;
        }
        android.view.ViewGroup.MarginLayoutParams params =
                (android.view.ViewGroup.MarginLayoutParams) floatingView.getLayoutParams();
        int baseTopMargin = params.topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(floatingView, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            android.view.ViewGroup.MarginLayoutParams p =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.topMargin = baseTopMargin + statusBars.top;
            v.setLayoutParams(p);
            return insets;
        });
    }
}
