package com.takago.app.location.map;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

/** Lifecycle owner for the app's single Google Maps renderer. */
public final class MapEngineController {
    private static final long READY_TIMEOUT_MS = 12_000L;

    public interface Callback { void onMapReady(MapEngine engine); }

    private final Context context;
    private final ViewGroup container;
    private final Callback callback;
    private final MapEngine.NoticeListener noticeListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MapEngine engine;
    private boolean resumed;
    private boolean destroyed;
    private boolean ready;

    public MapEngineController(Context context, ViewGroup container, Callback callback,
                               MapEngine.NoticeListener noticeListener) {
        this.context = context;
        this.container = container;
        this.callback = callback;
        this.noticeListener = noticeListener;
    }

    public void create() {
        if (!MapEngineAvailability.hasConfiguredApiKey(context)) {
            showError("Google Maps is not configured.");
            return;
        }
        if (!MapEngineAvailability.isPlayServicesAvailable(context)) {
            showError("Google Play Services is unavailable on this device.");
            return;
        }
        engine = new GoogleMapEngine(context);
        engine.setNoticeListener(noticeListener);
        engine.setFailureListener((message, cause) -> handler.post(() -> showError(message)));
        engine.attach(container, () -> {
            if (destroyed) return;
            ready = true;
            if (resumed) {
                engine.onStart();
                engine.onResume();
            }
            callback.onMapReady(engine);
        });
        handler.postDelayed(() -> {
            if (!destroyed && !ready) showError("Google Maps could not load. Check your connection and API configuration.");
        }, READY_TIMEOUT_MS);
    }

    private void showError(String message) {
        noticeListener.onNotice(message);
        release();
        container.removeAllViews();
        TextView view = new TextView(context);
        view.setText(message);
        view.setTextColor(Color.DKGRAY);
        view.setGravity(Gravity.CENTER);
        view.setPadding(48, 48, 48, 48);
        view.setBackgroundColor(0xFFF5F5F5);
        container.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void onResume() {
        resumed = true;
        if (engine != null && ready) { engine.onStart(); engine.onResume(); }
    }

    public void onPause() {
        if (engine != null && ready) { engine.onPause(); engine.onStop(); }
        resumed = false;
    }

    public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        release();
    }

    public void onLowMemory() { if (engine != null) engine.onLowMemory(); }

    private void release() {
        if (engine == null) return;
        if (resumed && ready) { engine.onPause(); engine.onStop(); }
        engine.onDestroy();
        engine = null;
        ready = false;
    }
}
