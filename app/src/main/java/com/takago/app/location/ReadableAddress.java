package com.takago.app.location;

import android.location.Address;

import java.util.Locale;

/** Selects a user-facing label without changing the coordinate it describes. */
public final class ReadableAddress {
    public final String label, houseNumber, streetName, placeName, neighbourhood;
    public final String formattedAddress, plusCode;

    private ReadableAddress(String label, String houseNumber, String streetName,
                            String placeName, String neighbourhood,
                            String formattedAddress, String plusCode) {
        this.label = label;
        this.houseNumber = houseNumber;
        this.streetName = streetName;
        this.placeName = placeName;
        this.neighbourhood = neighbourhood;
        this.formattedAddress = formattedAddress;
        this.plusCode = plusCode;
    }

    public static ReadableAddress from(Address address, String ward, String preferredPlace) {
        String house = clean(address == null ? null : address.getSubThoroughfare());
        String street = clean(address == null ? null : address.getThoroughfare());
        String feature = clean(address == null ? null : address.getFeatureName());
        String neighbourhood = clean(address == null ? null : address.getSubLocality());
        String formatted = clean(address == null ? null : address.getAddressLine(0));
        String preferred = clean(preferredPlace);
        String plus = firstPlus(feature, preferred, formatted);

        if (isPlusCode(feature) || feature.equalsIgnoreCase(house) || feature.equalsIgnoreCase(street)) feature = "";
        if (isPlusCode(preferred)) preferred = "";
        String place = !preferred.isEmpty() ? preferred : feature;
        if (place.equalsIgnoreCase(neighbourhood) || place.equalsIgnoreCase(clean(ward))) place = "";

        String label;
        boolean nearbyPlace = place.toLowerCase(Locale.US).startsWith("near ");
        if (!place.isEmpty() && !nearbyPlace) label = !street.isEmpty() ? place + ", " + street : place;
        else if (!house.isEmpty() && !street.isEmpty()) label = "House No. " + house + ", " + street;
        else if (!street.isEmpty()) label = street;
        else if (nearbyPlace) label = withoutNear(place);
        else if (!neighbourhood.isEmpty() && !neighbourhood.equalsIgnoreCase(clean(ward))) label = neighbourhood;
        else {
            String nearby = usefulFormattedPart(formatted, ward, neighbourhood);
            if (!nearby.isEmpty()) label = nearby;
            else label = plus;
        }
        return new ReadableAddress(label, house, street, place, neighbourhood, formatted, plus);
    }

    public static String cachedLabel(String house, String street, String place,
                                     String formatted, String plus, String ward) {
        house = clean(house); street = clean(street); place = clean(place);
        boolean nearbyPlace = place.toLowerCase(Locale.US).startsWith("near ");
        if (!place.isEmpty() && !isPlusCode(place) && !nearbyPlace)
            return !street.isEmpty() ? place + ", " + street : place;
        if (!house.isEmpty() && !street.isEmpty()) return "House No. " + house + ", " + street;
        if (!street.isEmpty()) return street;
        if (nearbyPlace) return withoutNear(place);
        String readable = usefulFormattedPart(formatted, ward, "");
        if (!readable.isEmpty()) return readable;
        return clean(plus);
    }

    private static String usefulFormattedPart(String formatted, String ward, String neighbourhood) {
        for (String raw : clean(formatted).split(",")) {
            String value = clean(raw);
            if (value.isEmpty() || isPlusCode(value) || value.equalsIgnoreCase(clean(ward))
                    || value.equalsIgnoreCase(clean(ward) + " Ward")
                    || value.equalsIgnoreCase(clean(neighbourhood))
                    || value.equalsIgnoreCase("Dar es Salaam")
                    || value.equalsIgnoreCase("Tanzania")
                    || value.toLowerCase(Locale.US).contains("municipal")) continue;
            return value;
        }
        return "";
    }

    private static String firstPlus(String... values) {
        for (String value : values) {
            String clean = clean(value);
            if (isPlusCode(clean)) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "(?i)[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}").matcher(clean);
                if (matcher.find()) return matcher.group();
            }
        }
        return "";
    }
    public static boolean isPlusCode(String value) {
        return value != null && value.matches("(?i).*\\b[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}\\b.*");
    }
    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
    private static String withoutNear(String value) {
        return clean(value).replaceFirst("(?i)^near\\s+", "");
    }
}
