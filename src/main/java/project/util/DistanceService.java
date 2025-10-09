package project.util;

import project.model.Location;
import project.model.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DistanceService {
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    public static final double DEFAULT_METERS_PER_SECOND = 13.89; // ~50 km/h

    private final Map<String, Integer> cache = new HashMap<>();

    public int distanceMeters(Location a, Location b) {
        String key = cacheKey(a, b);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        double lat1 = Math.toRadians(a.getLatitude());
        double lon1 = Math.toRadians(a.getLongitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double lon2 = Math.toRadians(b.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double hav = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double c = 2 * Math.atan2(Math.sqrt(hav), Math.sqrt(1 - hav));
        int meters = (int) Math.round(EARTH_RADIUS_METERS * c);
        cache.put(key, meters);
        return meters;
    }

    public int travelSeconds(Location a, Location b) {
        return travelSeconds(a, b, 0, null);
    }

    public int travelSeconds(Location a, Location b, int departureTimeSec, Vehicle vehicle) {
        int meters = distanceMeters(a, b);
        double baseSpeed = DEFAULT_METERS_PER_SECOND;
        if (vehicle != null && vehicle.getBaseSpeedMetersPerSecond() > 0) {
            baseSpeed = vehicle.getBaseSpeedMetersPerSecond();
        }
        double multiplierA = TrafficModel.currentMultiplier(a);
        double multiplierB = TrafficModel.currentMultiplier(b);
        double effectiveSpeed = Math.max(0.1, baseSpeed * ((multiplierA + multiplierB) / 2.0));
        return Math.max(0, (int) Math.round(meters / effectiveSpeed));
    }

    public int travelSeconds(Location a, Location b, double metersPerSecond) {
        int meters = distanceMeters(a, b);
        double speed = metersPerSecond > 0 ? metersPerSecond : DEFAULT_METERS_PER_SECOND;
        return Math.max(0, (int) Math.round(meters / speed));
    }

    private String cacheKey(Location a, Location b) {
        String idA = a.getId();
        String idB = b.getId();
        if (Objects.compare(idA, idB, String::compareTo) <= 0) {
            return idA + "|" + idB;
        }
        return idB + "|" + idA;
    }
}
