package com.takago.app.location.map;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.takago.app.location.LatLngPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Default map engine - used whenever Play Services, network, and an API key are all available. */
class GoogleMapEngine implements MapEngine {

    private final Context context;
    private MapView mapView;
    private GoogleMap googleMap;

    private OnCameraMoveListener cameraMoveListener;
    private NoticeListener noticeListener;
    private FailureListener failureListener;

    private final Map<String, Marker> markers = new HashMap<>();
    private final Map<String, ValueAnimator> markerAnimators = new HashMap<>();
    private Polyline routeLineCasing;
    private Polyline routeLine;
    private Circle radiusCircle;

    GoogleMapEngine(Context context) {
        this.context = context;
    }

    @Override
    public void attach(ViewGroup container, Runnable onReady) {
        try {
            MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST, renderer -> { });
            mapView = new MapView(context);
            container.addView(mapView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            mapView.onCreate(null);
            mapView.getMapAsync(map -> {
                googleMap = map;
                googleMap.setOnCameraIdleListener(() -> {
                    if (cameraMoveListener != null) {
                        LatLng target = googleMap.getCameraPosition().target;
                        cameraMoveListener.onCameraMoved(new LatLngPoint(target.latitude, target.longitude));
                    }
                });
                onReady.run();
            });
        } catch (RuntimeException error) {
            if (failureListener != null) {
                failureListener.onFailure("Google Maps could not be initialized.", error);
            }
        }
    }

    @Override
    public void onStart() {
        if (mapView != null) mapView.onStart();
    }

    @Override
    public void onResume() {
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onStop() {
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onDestroy() {
        for (ValueAnimator animator : markerAnimators.values()) {
            animator.cancel();
        }
        markerAnimators.clear();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    public void setCenter(LatLngPoint point, float zoom) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(point.lat, point.lng), zoom));
    }

    @Override
    public void animateCenter(LatLngPoint point) {
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(point.lat, point.lng)));
    }

    @Override
    public void addOnCameraMoveListener(OnCameraMoveListener listener) {
        this.cameraMoveListener = listener;
    }

    @Override
    public void addOrUpdateMarker(String id, LatLngPoint position, Drawable icon,
                                   float anchorU, float anchorV, boolean flat) {
        LatLng latLng = new LatLng(position.lat, position.lng);
        Marker marker = markers.get(id);
        if (marker == null) {
            MarkerOptions options = new MarkerOptions().position(latLng).anchor(anchorU, anchorV).flat(flat);
            if (icon != null) {
                options.icon(toBitmapDescriptor(icon));
            }
            marker = googleMap.addMarker(options);
            markers.put(id, marker);
        } else {
            marker.setPosition(latLng);
            marker.setFlat(flat);
            if (icon != null) {
                marker.setIcon(toBitmapDescriptor(icon));
            }
        }
    }

    @Override
    public void updateMovingMarker(String id, LatLngPoint position, Drawable icon, boolean flat) {
        Marker marker = markers.get(id);
        if (marker == null) {
            addOrUpdateMarker(id, position, icon, 0.5f, 0.5f, flat);
            return;
        }

        LatLng from = marker.getPosition();
        LatLng to = new LatLng(position.lat, position.lng);

        float[] results = new float[1];
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results);
        if (results[0] > 3) {
            float bearing = bearingBetween(from, to);
            marker.setRotation(bearing);
        }

        ValueAnimator previous = markerAnimators.get(id);
        if (previous != null) {
            previous.cancel();
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        Marker finalMarker = marker;
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            double lat = from.latitude + (to.latitude - from.latitude) * fraction;
            double lng = from.longitude + (to.longitude - from.longitude) * fraction;
            finalMarker.setPosition(new LatLng(lat, lng));
        });
        markerAnimators.put(id, animator);
        animator.start();
    }

    private float bearingBetween(LatLng from, LatLng to) {
        float[] results = new float[2];
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results);
        return results[1];
    }

    @Override
    public void removeMarker(String id) {
        Marker marker = markers.remove(id);
        if (marker != null) {
            marker.remove();
        }
        ValueAnimator animator = markerAnimators.remove(id);
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    public void setRoute(List<LatLngPoint> points, int lineColorArgb, int casingColorArgb) {
        List<LatLng> latLngs = toLatLngList(points);

        if (routeLineCasing == null) {
            routeLineCasing = googleMap.addPolyline(new PolylineOptions()
                    .width(22f)
                    .color(casingColorArgb)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .jointType(JointType.ROUND)
                    .zIndex(4f));
        } else {
            routeLineCasing.setColor(casingColorArgb);
        }
        routeLineCasing.setPoints(latLngs);

        if (routeLine == null) {
            routeLine = googleMap.addPolyline(new PolylineOptions()
                    .width(16f)
                    .color(lineColorArgb)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .jointType(JointType.ROUND)
                    .zIndex(5f));
        } else {
            routeLine.setColor(lineColorArgb);
        }
        routeLine.setPoints(latLngs);
    }

    @Override
    public void clearRoute() {
        if (routeLineCasing != null) {
            routeLineCasing.remove();
            routeLineCasing = null;
        }
        if (routeLine != null) {
            routeLine.remove();
            routeLine = null;
        }
    }

    @Override
    public void setRadiusCircle(LatLngPoint center, double radiusKm, int fillColorArgb, int outlineColorArgb) {
        LatLng latLng = new LatLng(center.lat, center.lng);
        double radiusMeters = radiusKm * 1000.0;
        if (radiusCircle == null) {
            radiusCircle = googleMap.addCircle(new CircleOptions()
                    .center(latLng)
                    .radius(radiusMeters)
                    .fillColor(fillColorArgb)
                    .strokeColor(outlineColorArgb)
                    .strokeWidth(4f));
        } else {
            radiusCircle.setCenter(latLng);
            radiusCircle.setRadius(radiusMeters);
            radiusCircle.setFillColor(fillColorArgb);
            radiusCircle.setStrokeColor(outlineColorArgb);
        }
    }

    @Override
    public void clearRadiusCircle() {
        if (radiusCircle != null) {
            radiusCircle.remove();
            radiusCircle = null;
        }
    }

    @Override
    public void zoomToBounds(List<LatLngPoint> points, int paddingPx) {
        if (points == null || points.isEmpty()) {
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLngPoint p : points) {
            builder.include(new LatLng(p.lat, p.lng));
        }
        LatLngBounds bounds = builder.build();
        mapView.post(() -> {
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx));
            } catch (IllegalStateException e) {
                // Map not laid out yet - safe to skip, the next state-driven redraw will retry.
            }
        });
    }

    @Override
    public void setNoticeListener(NoticeListener listener) {
        this.noticeListener = listener;
    }

    @Override
    public void setFailureListener(FailureListener listener) {
        this.failureListener = listener;
    }

    private List<LatLng> toLatLngList(List<LatLngPoint> points) {
        List<LatLng> latLngs = new java.util.ArrayList<>();
        for (LatLngPoint p : points) {
            latLngs.add(new LatLng(p.lat, p.lng));
        }
        return latLngs;
    }

    private BitmapDescriptor toBitmapDescriptor(Drawable drawable) {
        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 1;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 1;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}
