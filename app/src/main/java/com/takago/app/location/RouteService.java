package com.takago.app.location;

import android.os.Handler;

import java.util.List;

/** Provider-independent contract for real road-following routes. */
public interface RouteService {
    void fetchRoute(Handler mainHandler, double fromLat, double fromLng,
                    double toLat, double toLng, Callback callback);

    default void fetchRoute(Handler mainHandler, List<LatLngPoint> waypoints,
                            boolean optimizeIntermediateOrder, Callback callback) {
        if (waypoints == null || waypoints.size() < 2) {
            mainHandler.post(callback::onRouteFailed);
            return;
        }
        LatLngPoint first = waypoints.get(0);
        LatLngPoint last = waypoints.get(waypoints.size() - 1);
        fetchRoute(mainHandler, first.lat, first.lng, last.lat, last.lng, callback);
    }

    default void cancelPendingRequests() { }

    interface Callback {
        void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes);
        default void onRoute(List<LatLngPoint> points, double distanceKm, int etaMinutes,
                             String encodedPolyline, int distanceMeters, int durationSeconds) {
            onRoute(points, distanceKm, etaMinutes);
        }
        void onRouteFailed();
    }
}
