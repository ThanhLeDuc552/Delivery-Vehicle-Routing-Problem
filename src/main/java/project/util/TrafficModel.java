package project.util;

import project.model.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates time-of-day and zone-specific traffic conditions affecting vehicle speeds.
 */
public final class TrafficModel {
    private static final Map<String, Double> zoneMultipliers = new ConcurrentHashMap<>();
    private static final Map<String, Long> zoneUpdateTimestamps = new ConcurrentHashMap<>();
    private static final long STALE_THRESHOLD_MS = 60_000L;

    static {
        // Initialize default traffic zones with normal conditions
        zoneMultipliers.put("default", 1.0);
        zoneMultipliers.put("urban", 0.7);
        zoneMultipliers.put("suburban", 0.85);
        zoneMultipliers.put("highway", 1.2);
    }

    private TrafficModel() {}

    /**
     * Get current traffic multiplier for a location (multiplier applies to speed: < 1.0 = slower, > 1.0 = faster).
     */
    public static double currentMultiplier(Location loc) {
        if (loc == null) return 1.0;
        String zone = zoneFor(loc);
        Double multiplier = zoneMultipliers.get(zone);
        if (multiplier == null) return 1.0;
        
        // Check if update is stale
        Long timestamp = zoneUpdateTimestamps.get(zone);
        if (timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            if (age > STALE_THRESHOLD_MS) {
                // Gradually revert to default if stale
                return (multiplier + 1.0) / 2.0;
            }
        }
        return multiplier;
    }

    /**
     * Apply traffic updates from an agent or external source.
     * @param updates map of zone -> speed multiplier
     * @param source identifier for logging/debugging
     */
    public static void applyUpdate(Map<String, Double> updates, int source) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Double> entry : updates.entrySet()) {
            String zone = entry.getKey();
            double multiplier = entry.getValue();
            // Clamp multiplier to reasonable bounds
            multiplier = Math.max(0.1, Math.min(3.0, multiplier));
            zoneMultipliers.put(zone, multiplier);
            zoneUpdateTimestamps.put(zone, now);
        }
    }

    /**
     * Determine traffic zone from location coordinates. Simple latitude-based heuristic.
     */
    public static String zoneFor(Location loc) {
        if (loc == null) return "default";
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        
        // Simple heuristic: zone based on lat/lon ranges
        if (lat >= 10.78 && lat < 10.81 && lon >= 106.65 && lon < 106.70) {
            return "urban";
        } else if (lat >= 10.81 && lat < 10.85) {
            return "suburban";
        } else {
            return "default";
        }
    }

    /**
     * Get a snapshot of all current traffic conditions (for debugging/monitoring).
     */
    public static Map<String, Double> snapshot() {
        return new ConcurrentHashMap<>(zoneMultipliers);
    }

    /**
     * Reset all traffic conditions to default.
     */
    public static void reset() {
        zoneMultipliers.clear();
        zoneUpdateTimestamps.clear();
        zoneMultipliers.put("default", 1.0);
        zoneMultipliers.put("urban", 0.7);
        zoneMultipliers.put("suburban", 0.85);
        zoneMultipliers.put("highway", 1.2);
    }
}
