package com.takago.app.location.map;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import com.takago.app.location.LatLngPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral owner of map state and events. Activities describe the current business state
 * once; this class renders it through Google Maps or OSMDroid and replays it after a provider swap.
 */
public final class MapManager implements MapEngine {

    public interface Callback {
        void onMapReady(MapEngine map);
    }

    private final MapEngineController controller;
    private final Callback callback;
    private final Map<String, MarkerState> markers = new HashMap<>();

    private MapEngine renderer;
    private NoticeListener noticeListener;
    private String pendingNotice;
    private FailureListener failureListener;
    private OnCameraMoveListener cameraMoveListener;
    private LatLngPoint center;
    private float zoom;
    private List<LatLngPoint> route;
    private int routeColor;
    private int routeCasingColor;
    private LatLngPoint circleCenter;
    private double circleRadiusKm;
    private int circleFillColor;
    private int circleOutlineColor;

    public MapManager(Context context, ViewGroup container, Callback callback) {
        this.callback = callback;
        controller = new MapEngineController(context, container, this::providerReady,
                this::dispatchNotice);
    }

    public void create() {
        controller.create();
    }

    private void providerReady(MapEngine engine) {
        renderer = engine;
        renderer.setNoticeListener(this::dispatchNotice);
        if (cameraMoveListener != null) renderer.addOnCameraMoveListener(cameraMoveListener);
        replayState();
        callback.onMapReady(this);
    }

    private void replayState() {
        if (center != null) renderer.setCenter(center, zoom);
        for (Map.Entry<String, MarkerState> entry : markers.entrySet()) {
            MarkerState marker = entry.getValue();
            renderer.addOrUpdateMarker(entry.getKey(), marker.position, marker.icon,
                    marker.anchorU, marker.anchorV, marker.flat);
        }
        if (route != null) renderer.setRoute(route, routeColor, routeCasingColor);
        if (circleCenter != null) renderer.setRadiusCircle(circleCenter, circleRadiusKm,
                circleFillColor, circleOutlineColor);
    }

    private void dispatchNotice(String message) {
        if (noticeListener != null) {
            noticeListener.onNotice(message);
        } else {
            pendingNotice = message;
        }
    }

    @Override
    public void attach(ViewGroup container, Runnable onReady) {
        throw new UnsupportedOperationException("MapManager owns its container");
    }

    @Override public void onStart() { }
    @Override public void onStop() { }
    @Override public void onResume() { controller.onResume(); }
    @Override public void onPause() { controller.onPause(); }
    @Override public void onDestroy() { controller.onDestroy(); renderer = null; markers.clear(); }
    @Override public void onLowMemory() { controller.onLowMemory(); }

    @Override
    public void setCenter(LatLngPoint point, float zoom) {
        this.center = point;
        this.zoom = zoom;
        if (renderer != null) renderer.setCenter(point, zoom);
    }

    @Override
    public void animateCenter(LatLngPoint point) {
        center = point;
        if (renderer != null) renderer.animateCenter(point);
    }

    @Override
    public void addOnCameraMoveListener(OnCameraMoveListener listener) {
        cameraMoveListener = listener;
        if (renderer != null) renderer.addOnCameraMoveListener(listener);
    }

    @Override
    public void addOrUpdateMarker(String id, LatLngPoint position, Drawable icon,
                                  float anchorU, float anchorV, boolean flat) {
        markers.put(id, new MarkerState(position, icon, anchorU, anchorV, flat));
        if (renderer != null) renderer.addOrUpdateMarker(id, position, icon, anchorU, anchorV, flat);
    }

    @Override
    public void updateMovingMarker(String id, LatLngPoint position, Drawable icon, boolean flat) {
        MarkerState old = markers.get(id);
        float anchorU = old != null ? old.anchorU : 0.5f;
        float anchorV = old != null ? old.anchorV : 0.5f;
        markers.put(id, new MarkerState(position, icon, anchorU, anchorV, flat));
        if (renderer != null) renderer.updateMovingMarker(id, position, icon, flat);
    }

    @Override
    public void removeMarker(String id) {
        markers.remove(id);
        if (renderer != null) renderer.removeMarker(id);
    }

    @Override
    public void setRoute(List<LatLngPoint> points, int lineColorArgb, int casingColorArgb) {
        route = points;
        routeColor = lineColorArgb;
        routeCasingColor = casingColorArgb;
        if (renderer != null) renderer.setRoute(points, lineColorArgb, casingColorArgb);
    }

    @Override
    public void clearRoute() {
        route = null;
        if (renderer != null) renderer.clearRoute();
    }

    @Override
    public void setRadiusCircle(LatLngPoint center, double radiusKm, int fillColorArgb,
                                int outlineColorArgb) {
        circleCenter = center;
        circleRadiusKm = radiusKm;
        circleFillColor = fillColorArgb;
        circleOutlineColor = outlineColorArgb;
        if (renderer != null) renderer.setRadiusCircle(center, radiusKm, fillColorArgb, outlineColorArgb);
    }

    @Override
    public void clearRadiusCircle() {
        circleCenter = null;
        if (renderer != null) renderer.clearRadiusCircle();
    }

    @Override
    public void zoomToBounds(List<LatLngPoint> points, int paddingPx) {
        if (renderer != null) renderer.zoomToBounds(points, paddingPx);
    }

    @Override
    public void setNoticeListener(NoticeListener listener) {
        noticeListener = listener;
        if (listener != null && pendingNotice != null) {
            listener.onNotice(pendingNotice);
            pendingNotice = null;
        }
    }
    @Override public void setFailureListener(FailureListener listener) { failureListener = listener; }

    private static final class MarkerState {
        final LatLngPoint position;
        final Drawable icon;
        final float anchorU;
        final float anchorV;
        final boolean flat;

        MarkerState(LatLngPoint position, Drawable icon, float anchorU, float anchorV, boolean flat) {
            this.position = position;
            this.icon = icon;
            this.anchorU = anchorU;
            this.anchorV = anchorV;
            this.flat = flat;
        }
    }
}
