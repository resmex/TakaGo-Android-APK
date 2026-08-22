package com.takago.app.common;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

/** Keeps location text at two lines and makes the ward line easier to read. */
public final class LocationTextStyle {
    private LocationTextStyle() { }

    public static CharSequence twoLine(String primary, String ward) {
        String first = primary == null ? "" : primary.replace("Near ", "").trim();
        String second = ward == null ? "" : ward.trim();
        if (second.isEmpty()) return first;
        SpannableString value = new SpannableString(first + "\n" + second);
        int start = first.length() + 1;
        value.setSpan(new RelativeSizeSpan(1.15f), start, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new StyleSpan(Typeface.BOLD), start, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
}
