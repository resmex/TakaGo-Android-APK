package com.takago.app.location;

/** Engine-neutral lat/lng pair so business logic does not depend on Google Maps types. */
public final class LatLngPoint {

    public final double lat;
    public final double lng;

    public LatLngPoint(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }
}
