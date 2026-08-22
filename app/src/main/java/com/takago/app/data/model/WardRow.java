package com.takago.app.data.model;

public final class WardRow {
    public final int id;
    public final String name;
    public final String municipality;
    public final int municipalityId;
    public final String boundaryGeoJson;
    public final String boundaryStatus;
    public final String sourceShapeId;
    public final boolean active;
    public final int assignedOperatorId;

    public WardRow(int id, String name) {
        this(id, name, null, -1, null, null, null, true, -1);
    }

    public WardRow(int id, String name, String municipality, String boundaryGeoJson,
                   boolean active, int assignedOperatorId) {
        this(id, name, municipality, -1, boundaryGeoJson, null, null, active, assignedOperatorId);
    }

    public WardRow(int id, String name, String municipality, int municipalityId,
                   String boundaryGeoJson, String boundaryStatus, String sourceShapeId,
                   boolean active, int assignedOperatorId) {
        this.id = id;
        this.name = name;
        this.municipality = municipality;
        this.municipalityId = municipalityId;
        this.boundaryGeoJson = boundaryGeoJson;
        this.boundaryStatus = boundaryStatus;
        this.sourceShapeId = sourceShapeId;
        this.active = active;
        this.assignedOperatorId = assignedOperatorId;
    }
}
