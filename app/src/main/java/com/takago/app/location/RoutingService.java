package com.takago.app.location;

import android.os.Handler;

import java.util.List;

/**
 * Compatibility facade for existing screens. All provider-specific work lives in
 * Google Routes API; no second routing implementation is used.
 */
public final class RoutingService {
    private static RouteService service;

    public interface RouteCallback {
        void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes);
        default void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                             String encodedPolyline, int distanceMeters, int durationSeconds) {
            onRoute(points, distanceKm, etaMinutes);
        }
        void onRouteFailed(String userMessage);
    }

    private RoutingService() { }

    public static void initialize(android.content.Context context) {
        service = new GoogleRoutesService(context);
    }

    public static void fetchRoute(Handler mainHandler, double fromLat, double fromLng,
                                  double toLat, double toLng, RouteCallback callback) {
        if (service == null) {
            callback.onRouteFailed("Street route unavailable. Tap to retry.");
            return;
        }
        service.fetchRoute(mainHandler, fromLat, fromLng, toLat, toLng,
                new RouteService.Callback() {
                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes) {
                        callback.onRoute(points, distanceKm, etaMinutes);
                    }

                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                                        String encodedPolyline, int distanceMeters, int durationSeconds) {
                        callback.onRoute(points, distanceKm, etaMinutes, encodedPolyline,
                                distanceMeters, durationSeconds);
                    }

                    @Override
                    public void onRouteFailed() {
                        callback.onRouteFailed("Road route is temporarily unavailable.");
                    }
                });
    }

    public static void fetchRoute(Handler mainHandler, List<LatLngPoint> waypoints,
                                  boolean optimizeIntermediateOrder, RouteCallback callback) {
        if (service == null) {
            callback.onRouteFailed("Street route unavailable. Tap to retry.");
            return;
        }
        service.fetchRoute(mainHandler, waypoints, optimizeIntermediateOrder,
                new RouteService.Callback() {
                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes) {
                        callback.onRoute(points, distanceKm, etaMinutes);
                    }

                    @Override
                    public void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                                        String encodedPolyline, int distanceMeters, int durationSeconds) {
                        callback.onRoute(points, distanceKm, etaMinutes, encodedPolyline,
                                distanceMeters, durationSeconds);
                    }

                    @Override
                    public void onRouteFailed() {
                        callback.onRouteFailed("Road route is temporarily unavailable.");
                    }
                });
    }

    public static void cancelPendingRequests() {
        if (service != null) service.cancelPendingRequests();
    }

    public static boolean isValidCoordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90.0 && lat <= 90.0
                && lng >= -180.0 && lng <= 180.0
                && !(lat == 0.0 && lng == 0.0);
    }

    public static boolean areSameLocation(double firstLat, double firstLng,
                                          double secondLat, double secondLng) {
        return haversineKm(firstLat, firstLng, secondLat, secondLng) < 0.005;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
