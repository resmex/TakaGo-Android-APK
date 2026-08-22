package com.takago.app.location;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.MessageDigest;

/** Google Routes API v2 implementation. Provider details never leak into Activities. */
public final class GoogleRoutesService implements RouteService {
    private static final String TAG = "GoogleRoutesService";
    private static final String ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String FIELD_MASK =
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline," +
                    "routes.optimizedIntermediateWaypointIndex";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();

    public GoogleRoutesService(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void fetchRoute(Handler mainHandler, double fromLat, double fromLng,
                           double toLat, double toLng, Callback callback) {
        List<LatLngPoint> waypoints = new ArrayList<>();
        waypoints.add(new LatLngPoint(fromLat, fromLng));
        waypoints.add(new LatLngPoint(toLat, toLng));
        fetchRoute(mainHandler, waypoints, false, callback);
    }

    @Override
    public void fetchRoute(Handler mainHandler, List<LatLngPoint> waypoints,
                           boolean optimizeIntermediateOrder, Callback callback) {
        if (waypoints == null || waypoints.size() < 2) {
            mainHandler.post(callback::onRouteFailed);
            return;
        }
        for (LatLngPoint point : waypoints) {
            if (!RoutingService.isValidCoordinate(point.lat, point.lng)) {
                mainHandler.post(callback::onRouteFailed);
                return;
            }
        }
        String apiKey = readApiKey();
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Google Routes API key is missing");
            mainHandler.post(callback::onRouteFailed);
            return;
        }

        final int requestGeneration = generation.incrementAndGet();
        executor.execute(() -> performRequest(mainHandler, waypoints, optimizeIntermediateOrder,
                apiKey, requestGeneration, callback));
    }

    @Override
    public void cancelPendingRequests() {
        generation.incrementAndGet();
    }

    private void performRequest(Handler mainHandler, List<LatLngPoint> waypoints,
                                boolean optimize, String apiKey, int requestGeneration,
                                Callback callback) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = buildRequest(waypoints, optimize);
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("X-Goog-Api-Key", apiKey);
            connection.setRequestProperty("X-Goog-FieldMask", FIELD_MASK);
            connection.setRequestProperty("X-Android-Package", context.getPackageName());
            String certificate = signingCertificateSha1();
            if (!certificate.isEmpty()) connection.setRequestProperty("X-Android-Cert", certificate);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String responseBody = readBody(connection, status);
            Log.i(TAG, "Routes API HTTP status=" + status);
            if (status != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Routes API error response=" + responseBody);
                postFailure(mainHandler, requestGeneration, callback);
                return;
            }

            JSONObject response = new JSONObject(responseBody);
            JSONArray routes = response.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                Log.e(TAG, "Routes API returned no route");
                postFailure(mainHandler, requestGeneration, callback);
                return;
            }
            JSONObject route = routes.getJSONObject(0);
            String encoded = route.getJSONObject("polyline").getString("encodedPolyline");
            List<LatLngPoint> decoded = decodePolyline(encoded);
            int distanceMeters = route.getInt("distanceMeters");
            int durationSeconds = parseDurationSeconds(route.getString("duration"));
            if (decoded.size() < 2 || distanceMeters <= 0 || durationSeconds <= 0) {
                postFailure(mainHandler, requestGeneration, callback);
                return;
            }
            mainHandler.post(() -> {
                if (generation.get() == requestGeneration) {
                    callback.onRoute(decoded, distanceMeters / 1000.0,
                            Math.max(1, (int) Math.ceil(durationSeconds / 60.0)),
                            encoded, distanceMeters, durationSeconds);
                }
            });
        } catch (Exception error) {
            Log.e(TAG, "Routes API request failed", error);
            postFailure(mainHandler, requestGeneration, callback);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject buildRequest(List<LatLngPoint> points, boolean optimize) throws Exception {
        JSONObject request = new JSONObject();
        request.put("origin", waypoint(points.get(0)));
        request.put("destination", waypoint(points.get(points.size() - 1)));
        if (points.size() > 2) {
            JSONArray intermediates = new JSONArray();
            for (int i = 1; i < points.size() - 1; i++) intermediates.put(waypoint(points.get(i)));
            request.put("intermediates", intermediates);
            request.put("optimizeWaypointOrder", optimize);
        }
        request.put("travelMode", "DRIVE");
        request.put("routingPreference", "TRAFFIC_AWARE");
        request.put("computeAlternativeRoutes", false);
        request.put("languageCode", "en");
        request.put("units", "METRIC");
        return request;
    }

    private JSONObject waypoint(LatLngPoint point) throws Exception {
        JSONObject latLng = new JSONObject().put("latitude", point.lat).put("longitude", point.lng);
        return new JSONObject().put("location", new JSONObject().put("latLng", latLng));
    }

    private String readApiKey() {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            String key = info.metaData != null
                    ? info.metaData.getString("com.google.android.geo.API_KEY", "") : "";
            return key == null ? "" : key.trim();
        } catch (PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    private String signingCertificateSha1() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] signatures = info.signingInfo != null
                    ? info.signingInfo.getApkContentsSigners() : null;
            if (signatures == null || signatures.length == 0) return "";
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
            StringBuilder value = new StringBuilder();
            for (byte item : digest) value.append(String.format("%02X", item));
            return value.toString();
        } catch (Exception error) {
            Log.w(TAG, "Could not read app signing certificate", error);
            return "";
        }
    }

    private void postFailure(Handler handler, int requestGeneration, Callback callback) {
        handler.post(() -> {
            if (generation.get() == requestGeneration) callback.onRouteFailed();
        });
    }

    private static String readBody(HttpURLConnection connection, int status) throws IOException {
        java.io.InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    private static int parseDurationSeconds(String duration) {
        if (duration == null || !duration.endsWith("s")) return 0;
        return (int) Math.ceil(Double.parseDouble(duration.substring(0, duration.length() - 1)));
    }

    public static List<LatLngPoint> decodePolyline(String encoded) {
        List<LatLngPoint> points = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (index < encoded.length()) {
            int result = 0, shift = 0, value;
            do {
                value = encoded.charAt(index++) - 63;
                result |= (value & 0x1f) << shift;
                shift += 5;
            } while (value >= 0x20 && index < encoded.length());
            lat += (result & 1) != 0 ? ~(result >> 1) : result >> 1;
            result = 0;
            shift = 0;
            do {
                value = encoded.charAt(index++) - 63;
                result |= (value & 0x1f) << shift;
                shift += 5;
            } while (value >= 0x20 && index < encoded.length());
            lng += (result & 1) != 0 ? ~(result >> 1) : result >> 1;
            points.add(new LatLngPoint(lat / 1e5, lng / 1e5));
        }
        return points;
    }
}
