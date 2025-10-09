package project.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import project.model.Location;
import project.model.Order;
import project.model.Vehicle;
import project.util.DistanceService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonDataLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonDataLoader() {}

    public static Location loadDepot() {
        JsonNode node = readResourceJson("data/depot.json");
        return new Location(
                node.path("id").asText("DEPOT"),
                node.path("latitude").asDouble(),
                node.path("longitude").asDouble()
        );
    }

    public static Map<String, Location> loadDepots() {
        Map<String, Location> depots = new HashMap<>();
        // Try loading depots.json (multi-depot), fallback to depot.json (single)
        try {
            JsonNode node = readResourceJson("data/depots.json");
            if (node.isArray()) {
                for (JsonNode d : node) {
                    String id = d.path("id").asText();
                    Location loc = new Location(
                            id,
                            d.path("latitude").asDouble(),
                            d.path("longitude").asDouble()
                    );
                    depots.put(id, loc);
                }
            }
        } catch (Exception e) {
            // Fallback to single depot
            Location depot = loadDepot();
            depots.put(depot.getId(), depot);
        }
        if (depots.isEmpty()) {
            Location depot = loadDepot();
            depots.put(depot.getId(), depot);
        }
        return depots;
    }

    public static List<Vehicle> loadVehicles() {
        JsonNode node = readResourceJson("data/vehicles.json");
        if (!node.isArray()) return Collections.emptyList();
        List<Vehicle> vehicles = new ArrayList<>();
        for (JsonNode v : node) {
            String id = v.path("id").asText();
            int capacity = v.path("capacity").asInt(0);
            double baseSpeed = v.path("baseSpeedMetersPerSecond").asDouble(DistanceService.DEFAULT_METERS_PER_SECOND);
            int maxRoute = v.path("maxRouteSeconds").asInt(8 * 60 * 60);
            String depotId = v.path("depotId").asText("DEPOT");
            vehicles.add(new Vehicle(id, capacity, baseSpeed, maxRoute, depotId));
        }
        return vehicles;
    }

    public static List<Order> loadOrders() {
        JsonNode node = readResourceJson("data/orders.json");
        if (!node.isArray()) return Collections.emptyList();
        List<Order> orders = new ArrayList<>();
        for (JsonNode o : node) {
            Location loc = new Location(
                    o.path("id").asText(),
                    o.path("latitude").asDouble(),
                    o.path("longitude").asDouble()
            );
            int earliest = o.path("earliestStartSec").asInt(0);
            int latest = o.path("latestEndSec").asInt(24 * 60 * 60 - 1);
            int service = o.path("serviceTimeSec").asInt(5 * 60);
            orders.add(new Order(
                    o.path("id").asText(),
                    o.path("demand").asInt(1),
                    loc,
                    earliest,
                    latest,
                    service
            ));
        }
        return orders;
    }

    public static Location resolveDefaultDepot(Map<String, Location> depots) {
        if (depots == null || depots.isEmpty()) {
            return loadDepot();
        }
        Location explicitDefault = depots.get("DEPOT");
        if (explicitDefault != null) {
            return explicitDefault;
        }
        return depots.values().stream()
                .findFirst()
                .orElseGet(JsonDataLoader::loadDepot);
    }

    public static Location resolveDepotForVehicle(Map<String, Location> depots, Vehicle vehicle) {
        return resolveDepotForVehicle(depots, resolveDefaultDepot(depots), vehicle);
    }

    public static Location resolveDepotForVehicle(Map<String, Location> depots, Location fallback, Vehicle vehicle) {
        Location defaultDepot = fallback != null ? fallback : resolveDefaultDepot(depots);
        if (vehicle == null) {
            return defaultDepot;
        }
        if (depots != null) {
            Location depot = depots.get(vehicle.getDepotId());
            if (depot != null) {
                return depot;
            }
        }
        return defaultDepot;
    }

    private static JsonNode readResourceJson(String path) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return MAPPER.readTree(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON resource: " + path, e);
        }
    }
}
