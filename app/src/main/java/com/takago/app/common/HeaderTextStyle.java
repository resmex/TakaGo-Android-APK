package com.takago.app.common;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;

public final class HeaderTextStyle {
    private HeaderTextStyle() { }

    public static CharSequence residentWelcome(String name) {
        String prefix = "Welcome ";
        SpannableString text = new SpannableString(prefix + firstName(name));
        text.setSpan(new StyleSpan(Typeface.NORMAL), 0, prefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD), prefix.length(), text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** Keeps home headers friendly and private by displaying only the first name. */
    public static String firstName(String name) {
        String value = clean(name);
        if (value.isEmpty()) return "there";
        int separator = value.indexOf(' ');
        return separator > 0 ? value.substring(0, separator) : value;
    }

    public static String locationAndWard(String location, String ward) {
        String first = clean(location).replace("Near ", "");
        String second = clean(ward);
        if (first.isEmpty()) return second;
        if (second.isEmpty() || first.equalsIgnoreCase(second)) return first;
        return first + " · " + second;
    }

    private static String clean(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) return "";
        return value.trim();
    }
}
