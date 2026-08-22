package com.takago.app.common;

import com.takago.app.data.model.PickupRow;
import com.takago.app.location.ReadableAddress;

/** Formats pickup locations consistently without exposing municipality names in trip UI. */
public final class PickupAddressFormatter {
    private PickupAddressFormatter() {
    }

    public static String primary(PickupRow pickup) {
        if (pickup == null) return "";
        String label = ReadableAddress.cachedLabel(pickup.houseNumber, pickup.streetName,
                pickup.placeName, pickup.formattedAddress, pickup.plusCode, pickup.ward);
        if (label.isEmpty()) label = ReadableAddress.cachedLabel(null, null, null,
                pickup.address, pickup.plusCode, pickup.ward);
        return label;
    }

    public static String wardLine(PickupRow pickup) {
        String ward = pickup == null ? "" : clean(pickup.ward);
        return ward.isEmpty() ? "" : ward + " Ward";
    }

    public static String twoLine(PickupRow pickup) {
        String primary = primary(pickup);
        String ward = wardLine(pickup);
        if (ward.isEmpty()) return primary;
        return primary + "\n" + ward;
    }

    public static CharSequence styledTwoLine(PickupRow pickup) {
        return LocationTextStyle.twoLine(primary(pickup), wardLine(pickup));
    }

    private static String stripWardAndMunicipality(String value, String ward) {
        String text = clean(value);
        if (text.isEmpty()) return "";
        int newline = text.indexOf('\n');
        if (newline >= 0) text = text.substring(0, newline);
        int wardIndex = text.toLowerCase(java.util.Locale.US).indexOf("ward:");
        if (wardIndex >= 0) text = text.substring(0, wardIndex);
        String wardText = clean(ward);
        if (!wardText.isEmpty() && text.equalsIgnoreCase(wardText)) return "";
        String[] parts = text.split(",");
        if (parts.length > 1) {
            for (String part : parts) {
                String candidate = clean(part);
                if (candidate.isEmpty()
                        || candidate.equalsIgnoreCase(wardText)
                        || candidate.equalsIgnoreCase(wardText + " Ward")
                        || candidate.equalsIgnoreCase("Dar es Salaam")
                        || candidate.equalsIgnoreCase("Tanzania")) {
                    continue;
                }
                return candidate;
            }
        }
        return text.replaceAll("\\s*,\\s*$", "").trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static boolean isPlusCode(String value) {
        return value != null && value.matches("(?i).*\\b[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}\\b.*");
    }
}
