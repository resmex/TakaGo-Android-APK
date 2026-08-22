package com.takago.app.location.map;

import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import com.takago.app.location.LatLngPoint;

import java.util.List;

/**
 * Same marker/route/circle/camera surface for whichever map SDK is actually backing the screen
 * Callers remain isolated from the Google Maps rendering implementation.
 * Markers/routes/circles are keyed by a plain String id so a caller can "set current state"
 * idempotently (create-or-update) without holding an engine-specific handle.
 */
public interface MapEngine {

    /** Adds the underlying map view to {@code container} and reports readiness via {@code onReady}. */
    void attach(ViewGroup container, Runnable onReady);

    void onStart();

    void onResume();

    void onPause();

    void onStop();

    /** Must fully release the underlying view/renderer so no two engines are ever live at once. */
    void onDestroy();

    void onLowMemory();

    void setCenter(LatLngPoint point, float zoom);

    void animateCenter(LatLngPoint point);

    void addOnCameraMoveListener(OnCameraMoveListener listener);

    void addOrUpdateMarker(String id, LatLngPoint position, Drawable icon,
                            float anchorU, float anchorV, boolean flat);

    /** Creates the marker on first call, otherwise smoothly animates + rotates it towards {@code position}. */
    void updateMovingMarker(String id, LatLngPoint position, Drawable icon, boolean flat);

    void removeMarker(String id);

    void setRoute(List<LatLngPoint> points, int lineColorArgb, int casingColorArgb);

    void clearRoute();

    void setRadiusCircle(LatLngPoint center, double radiusKm, int fillColorArgb, int outlineColorArgb);

    void clearRadiusCircle();

    void zoomToBounds(List<LatLngPoint> points, int paddingPx);

    /** Lets the engine surface best-effort user notices, e.g. "map tiles unavailable offline". */
    void setNoticeListener(NoticeListener listener);

    /** Reports a provider initialization/rendering failure so the owner can switch providers. */
    void setFailureListener(FailureListener listener);

    interface OnCameraMoveListener {
        void onCameraMoved(LatLngPoint newCenter);
    }

    interface NoticeListener {
        void onNotice(String message);
    }

    interface FailureListener {
        void onFailure(String message, Throwable cause);
    }
}
