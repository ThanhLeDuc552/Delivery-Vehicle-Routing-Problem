package project.model;

import java.io.Serializable;

public final class Location implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final double latitude;
    private final double longitude;

    public Location(String id, double latitude, double longitude) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() {
        return id;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public String toString() {
        return "Location{" +
                "id='" + id + '\'' +
                ", lat=" + latitude +
                ", lon=" + longitude +
                '}';
    }
}
