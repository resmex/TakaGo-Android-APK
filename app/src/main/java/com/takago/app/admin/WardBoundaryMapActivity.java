package com.takago.app.admin;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolygonOptions;
import com.takago.app.common.BackButtonUtils;
import com.takago.app.location.LatLngPoint;
import com.takago.app.location.WardBoundaryUtils;

import java.util.List;

/** Read-only Google Map preview for an imported ward polygon. */
public class WardBoundaryMapActivity extends AppCompatActivity {
    private MapView mapView;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setTitle(getIntent().getStringExtra("name"));
        FrameLayout root = new FrameLayout(this);
        mapView = new MapView(this);
        root.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        BackButtonUtils.addMapBackButton(this, root);
        setContentView(root);
        mapView.onCreate(state);
        mapView.getMapAsync(this::drawBoundary);
    }
    private void drawBoundary(GoogleMap map) {
        try {
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            List<List<LatLngPoint>> rings = WardBoundaryUtils.exteriorRings(getIntent().getStringExtra("geojson"));
            for (List<LatLngPoint> ring : rings) {
                PolygonOptions options = new PolygonOptions()
                        .strokeColor(0xFF168B45).strokeWidth(6f).fillColor(0x33168B45).zIndex(3f);
                for (LatLngPoint point : ring) {
                    LatLng latLng = new LatLng(point.lat, point.lng);
                    options.add(latLng);
                    bounds.include(latLng);
                }
                map.addPolygon(options);
            }
            mapView.post(() -> map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)));
        } catch (Exception e) { Toast.makeText(this, "Invalid ward boundary", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onStart(){super.onStart();mapView.onStart();}
    @Override protected void onResume(){super.onResume();mapView.onResume();}
    @Override protected void onPause(){mapView.onPause();super.onPause();}
    @Override protected void onStop(){mapView.onStop();super.onStop();}
    @Override protected void onDestroy(){mapView.onDestroy();super.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();mapView.onLowMemory();}
}
