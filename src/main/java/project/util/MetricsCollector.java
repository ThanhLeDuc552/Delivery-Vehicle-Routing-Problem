package project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.model.Order;
import project.model.Vehicle;
import project.optimizer.RouteHeuristics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects and reports routing metrics: on-time delivery rate, distance, utilization, etc.
 */
public final class MetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    private final List<DispatchMetrics> dispatchHistory = new ArrayList<>();
    private final AtomicInteger totalOrdersDispatched = new AtomicInteger(0);
    private final AtomicInteger totalOrdersOnTime = new AtomicInteger(0);
    private final AtomicInteger totalReplans = new AtomicInteger(0);
    private final AtomicInteger totalNegotiations = new AtomicInteger(0);

    public void recordDispatch(Map<Vehicle, RouteHeuristics.RoutePlan> assignment) {
        DispatchMetrics metrics = computeDispatchMetrics(assignment);
        dispatchHistory.add(metrics);
        totalOrdersDispatched.addAndGet(metrics.totalOrders);
        totalOrdersOnTime.addAndGet(metrics.onTimeOrders);
        totalReplans.incrementAndGet();

        log.info("=== Dispatch Metrics ===");
        log.info("Total orders: {}, On-time: {}, On-time rate: {}%",
                metrics.totalOrders,
                metrics.onTimeOrders,
                String.format("%.1f", metrics.onTimeRate * 100.0));
        log.info("Total route duration: {} min, Total distance: {} km",
                String.format("%.1f", metrics.totalDurationSec / 60.0),
                String.format("%.1f", metrics.totalDistanceMeters / 1000.0));
        log.info("Average vehicle utilization: {}%", String.format("%.1f", metrics.avgUtilization * 100.0));
        log.info("Vehicles with feasible routes: {}/{}", metrics.feasibleVehicles, metrics.totalVehicles);
        log.info("========================");
    }

    public void recordNegotiation() {
        totalNegotiations.incrementAndGet();
    }

    public void logSummary() {
        log.info("=== CUMULATIVE METRICS SUMMARY ===");
        log.info("Total replans: {}", totalReplans.get());
        log.info("Total negotiations: {}", totalNegotiations.get());
        log.info("Total orders dispatched: {}", totalOrdersDispatched.get());
        log.info("Total on-time orders: {}", totalOrdersOnTime.get());
        double cumulativeOnTimeRate = totalOrdersDispatched.get() > 0
                ? (double) totalOrdersOnTime.get() / totalOrdersDispatched.get()
                : 0.0;
        log.info("Cumulative on-time rate: {}%", String.format("%.1f", cumulativeOnTimeRate * 100.0));
        
        if (!dispatchHistory.isEmpty()) {
            double avgDuration = dispatchHistory.stream()
                    .mapToDouble(d -> d.totalDurationSec)
                    .average()
                    .orElse(0.0);
            double avgDistance = dispatchHistory.stream()
                    .mapToDouble(d -> d.totalDistanceMeters)
                    .average()
                    .orElse(0.0);
            log.info("Average total route duration: {} min", String.format("%.1f", avgDuration / 60.0));
            log.info("Average total distance: {} km", String.format("%.1f", avgDistance / 1000.0));
        }
        log.info("===================================");
    }

    private DispatchMetrics computeDispatchMetrics(Map<Vehicle, RouteHeuristics.RoutePlan> assignment) {
        int totalOrders = 0;
        int onTimeOrders = 0;
        int totalDurationSec = 0;
        int totalDistanceMeters = 0;
        double totalUtilization = 0.0;
        int feasibleVehicles = 0;
        int totalVehicles = assignment.size();

        for (Map.Entry<Vehicle, RouteHeuristics.RoutePlan> entry : assignment.entrySet()) {
            Vehicle vehicle = entry.getKey();
            RouteHeuristics.RoutePlan plan = entry.getValue();

            if (plan.isFeasible()) {
                feasibleVehicles++;
            }

            totalOrders += plan.getRoute().size();
            totalDurationSec += plan.getTotalDurationSec();

            // Estimate distance from duration and average speed
            totalDistanceMeters += (int) Math.round(plan.getTotalDurationSec() * DistanceService.DEFAULT_METERS_PER_SECOND);

            // Count on-time deliveries (service starts within time window)
            for (RouteHeuristics.Visit visit : plan.getVisits()) {
                Order order = visit.getOrder();
                if (visit.getServiceStartSec() >= order.getEarliestStartSec()
                        && visit.getServiceStartSec() <= order.getLatestEndSec()) {
                    onTimeOrders++;
                }
            }

            // Compute vehicle utilization (duration used / max duration)
            if (vehicle.getMaxRouteSeconds() > 0) {
                double utilization = (double) plan.getTotalDurationSec() / vehicle.getMaxRouteSeconds();
                totalUtilization += Math.min(1.0, utilization);
            } else {
                // If no max route constraint, assume 8-hour shift
                totalUtilization += Math.min(1.0, (double) plan.getTotalDurationSec() / (8 * 60 * 60));
            }
        }

        double avgUtilization = totalVehicles > 0 ? totalUtilization / totalVehicles : 0.0;
        double onTimeRate = totalOrders > 0 ? (double) onTimeOrders / totalOrders : 1.0;

        return new DispatchMetrics(
                totalOrders,
                onTimeOrders,
                onTimeRate,
                totalDurationSec,
                totalDistanceMeters,
                avgUtilization,
                feasibleVehicles,
                totalVehicles
        );
    }

    private static final class DispatchMetrics {
        final int totalOrders;
        final int onTimeOrders;
        final double onTimeRate;
        final int totalDurationSec;
        final int totalDistanceMeters;
        final double avgUtilization;
        final int feasibleVehicles;
        final int totalVehicles;

        DispatchMetrics(int totalOrders, int onTimeOrders, double onTimeRate,
                        int totalDurationSec, int totalDistanceMeters,
                        double avgUtilization, int feasibleVehicles, int totalVehicles) {
            this.totalOrders = totalOrders;
            this.onTimeOrders = onTimeOrders;
            this.onTimeRate = onTimeRate;
            this.totalDurationSec = totalDurationSec;
            this.totalDistanceMeters = totalDistanceMeters;
            this.avgUtilization = avgUtilization;
            this.feasibleVehicles = feasibleVehicles;
            this.totalVehicles = totalVehicles;
        }
    }
}

