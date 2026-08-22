package com.takago.app.location.map;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

/** Reports whether Google Maps can be used right now. */
public final class MapEngineAvailability {

    private static final String API_KEY_META_NAME = "com.google.android.geo.API_KEY";

    private MapEngineAvailability() {
    }

    public static boolean shouldUseGoogleMaps(Context context) {
        return isPlayServicesAvailable(context) && hasConfiguredApiKey(context)
                && isNetworkConnected(context);
    }

    public static boolean isPlayServicesAvailable(Context context) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static boolean hasConfiguredApiKey(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            if (info.metaData == null) {
                return false;
            }
            String key = info.metaData.getString(API_KEY_META_NAME);
            if (key == null) return false;
            String value = key.trim();
            return !value.isEmpty()
                    && !value.equalsIgnoreCase("YOUR_API_KEY_HERE")
                    && !value.contains("${");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
