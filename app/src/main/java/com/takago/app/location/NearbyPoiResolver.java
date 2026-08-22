package com.takago.app.location;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;

import java.util.Arrays;

/** Finds a nearby Google Maps POI name; it never changes the supplied pin coordinates. */
public final class NearbyPoiResolver {
    private static final double SEARCH_RADIUS_METERS = 100.0;
    private static final double ON_PREMISE_DISTANCE_METERS = 25.0;
    public interface Callback { void onResult(String poiName); }
    private NearbyPoiResolver() { }

    public static void resolve(Context context, double latitude, double longitude, Callback callback) {
        if (!Places.isInitialized()) { callback.onResult(null); return; }
        PlacesClient client = Places.createClient(context.getApplicationContext());
        CircularBounds bounds = CircularBounds.newInstance(
                new LatLng(latitude, longitude), SEARCH_RADIUS_METERS);
        SearchNearbyRequest request = SearchNearbyRequest.builder(bounds,
                        Arrays.asList(Place.Field.DISPLAY_NAME, Place.Field.LOCATION))
                .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
                .setMaxResultCount(1)
                .build();
        client.searchNearby(request).addOnSuccessListener(response -> {
            String name = null;
            if (!response.getPlaces().isEmpty()) {
                Place place = response.getPlaces().get(0);
                name = place.getDisplayName();
                LatLng poi = place.getLocation();
                if (name != null && poi != null && RoutingService.haversineKm(
                        latitude, longitude, poi.latitude, poi.longitude) * 1000.0
                        > ON_PREMISE_DISTANCE_METERS) name = "Near " + name;
            }
            callback.onResult(name);
        }).addOnFailureListener(error -> callback.onResult(null));
    }
}
