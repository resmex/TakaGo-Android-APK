package com.takago.app.location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class WardBoundaryUtils {
    private WardBoundaryUtils() {}

    public static String normalizeBoundary(String geoJson) throws JSONException {
        JSONObject geometry = resolveGeometry(new JSONObject(geoJson));
        String type = geometry.optString("type");
        if (!"Polygon".equals(type) && !"MultiPolygon".equals(type)) {
            throw new JSONException("Boundary must be a Polygon or MultiPolygon");
        }
        geometry.getJSONArray("coordinates");
        return geometry.toString();
    }

    public static boolean containsPoint(String geoJson, double latitude, double longitude) {
        if (geoJson == null || geoJson.trim().isEmpty()) return false;
        try {
            JSONObject geometry = resolveGeometry(new JSONObject(geoJson));
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            if ("MultiPolygon".equals(geometry.optString("type"))) {
                for (int i = 0; i < coordinates.length(); i++) {
                    if (containsPolygon(coordinates.getJSONArray(i), latitude, longitude)) return true;
                }
                return false;
            }
            return "Polygon".equals(geometry.optString("type"))
                    && containsPolygon(coordinates, latitude, longitude);
        } catch (JSONException ignored) {
            return false;
        }
    }

    public static List<List<LatLngPoint>> exteriorRings(String geoJson) throws JSONException {
        JSONObject geometry = resolveGeometry(new JSONObject(geoJson));
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        List<List<LatLngPoint>> rings = new ArrayList<>();
        if ("MultiPolygon".equals(geometry.optString("type"))) {
            for (int i = 0; i < coordinates.length(); i++) {
                rings.add(readExteriorRing(coordinates.getJSONArray(i)));
            }
        } else if ("Polygon".equals(geometry.optString("type"))) {
            rings.add(readExteriorRing(coordinates));
        } else {
            throw new JSONException("Boundary must be a Polygon or MultiPolygon");
        }
        return rings;
    }

    private static JSONObject resolveGeometry(JSONObject root) throws JSONException {
        String type = root.optString("type");
        if ("Feature".equals(type)) {
            return root.getJSONObject("geometry");
        }
        if ("FeatureCollection".equals(type)) {
            JSONArray features = root.getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                JSONObject geometry = features.getJSONObject(i).optJSONObject("geometry");
                if (geometry == null) continue;
                String geometryType = geometry.optString("type");
                if ("Polygon".equals(geometryType) || "MultiPolygon".equals(geometryType)) {
                    return geometry;
                }
            }
            throw new JSONException("No polygon feature found");
        }
        return root;
    }

    private static List<LatLngPoint> readExteriorRing(JSONArray polygon) throws JSONException {
        JSONArray ring = polygon.getJSONArray(0);
        List<LatLngPoint> points = new ArrayList<>();
        for (int i = 0; i < ring.length(); i++) {
            JSONArray point = ring.getJSONArray(i);
            points.add(new LatLngPoint(point.getDouble(1), point.getDouble(0)));
        }
        return points;
    }

    private static boolean containsPolygon(JSONArray polygon, double lat, double lng)
            throws JSONException {
        if (polygon.length() == 0) return false;
        if (!containsRing(polygon.getJSONArray(0), lat, lng)) return false;
        for (int i = 1; i < polygon.length(); i++) {
            if (containsRing(polygon.getJSONArray(i), lat, lng)) return false;
        }
        return true;
    }

    private static boolean containsRing(JSONArray ring, double lat, double lng)
            throws JSONException {
        boolean inside = false;
        for (int i = 0, j = ring.length() - 1; i < ring.length(); j = i++) {
            JSONArray a = ring.getJSONArray(i);
            JSONArray b = ring.getJSONArray(j);
            double xi = a.getDouble(0), yi = a.getDouble(1);
            double xj = b.getDouble(0), yj = b.getDouble(1);
            if ((yi > lat) != (yj > lat)
                    && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }
}
