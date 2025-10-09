package project.model;

import project.util.DistanceService;

import java.io.Serializable;

public final class Vehicle implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final int capacity;
    private final double baseSpeedMetersPerSecond;
    private final int maxRouteSeconds;
    private final String depotId;

    public Vehicle(String id, int capacity) {
        this(id, capacity, DistanceService.DEFAULT_METERS_PER_SECOND, 8 * 60 * 60, "DEPOT");
    }

    public Vehicle(String id, int capacity, double baseSpeedMetersPerSecond, int maxRouteSeconds) {
        this(id, capacity, baseSpeedMetersPerSecond, maxRouteSeconds, "DEPOT");
    }

    public Vehicle(String id, int capacity, double baseSpeedMetersPerSecond, int maxRouteSeconds, String depotId) {
        this.id = id;
        this.capacity = capacity;
        double speed = baseSpeedMetersPerSecond > 0 ? baseSpeedMetersPerSecond : DistanceService.DEFAULT_METERS_PER_SECOND;
        this.baseSpeedMetersPerSecond = speed;
        this.maxRouteSeconds = Math.max(0, maxRouteSeconds);
        this.depotId = depotId != null && !depotId.isEmpty() ? depotId : "DEPOT";
    }

    public String getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getBaseSpeedMetersPerSecond() {
        return baseSpeedMetersPerSecond;
    }

    public int getMaxRouteSeconds() {
        return maxRouteSeconds;
    }

    public String getDepotId() {
        return depotId;
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id='" + id + '\'' +
                ", capacity=" + capacity +
                ", baseSpeed=" + baseSpeedMetersPerSecond +
                ", maxRouteSeconds=" + maxRouteSeconds +
                ", depotId='" + depotId + '\'' +
                '}';
    }
}

