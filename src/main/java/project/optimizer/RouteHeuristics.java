package project.optimizer;

import project.model.Location;
import project.model.Order;
import project.model.Vehicle;
import project.util.DistanceService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RouteHeuristics {
    private RouteHeuristics() {
    }

    public static List<Order> nearestNeighbor(Location depot, List<Order> orders, DistanceService distanceService) {
        List<Order> route = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Location current = depot;
        for (int i = 0; i < orders.size(); i++) {
            Order next = null;
            int best = Integer.MAX_VALUE;
            for (Order candidate : orders) {
                if (visited.contains(candidate.getId())) {
                    continue;
                }
                int d = distanceService.distanceMeters(current, candidate.getLocation());
                if (d < best) {
                    best = d;
                    next = candidate;
                }
            }
            if (next != null) {
                route.add(next);
                visited.add(next.getId());
                current = next.getLocation();
            }
        }
        return route;
    }

    public static RoutePlan timeWindowAwareRoute(Location depot,
                                                 List<Order> orders,
                                                 DistanceService distanceService,
                                                 Vehicle vehicle,
                                                 int startTimeSec) {
        List<Order> route = timeWindowInsertion(depot, orders, distanceService, vehicle, startTimeSec);
        route = twoOptFeasible(depot, route, distanceService, vehicle, startTimeSec);
        return simulate(depot, route, distanceService, vehicle, startTimeSec);
    }

    private static List<Order> timeWindowInsertion(Location depot,
                                                   List<Order> orders,
                                                   DistanceService distanceService,
                                                   Vehicle vehicle,
                                                   int startTimeSec) {
        List<Order> route = new ArrayList<>();
        List<Order> remaining = new ArrayList<>(orders);
        while (!remaining.isEmpty()) {
            Order bestOrder = null;
            int bestPos = -1;
            int bestCost = Integer.MAX_VALUE;

            for (Order candidate : remaining) {
                for (int pos = 0; pos <= route.size(); pos++) {
                    List<Order> trial = new ArrayList<>(route);
                    trial.add(pos, candidate);
                    RoutePlan plan = simulate(depot, trial, distanceService, vehicle, startTimeSec);
                    if (!plan.isFeasible()) {
                        continue;
                    }
                    int cost = plan.getTotalDurationSec();
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestOrder = candidate;
                        bestPos = pos;
                    }
                }
            }

            if (bestOrder == null) {
                break; // No feasible insertion found; avoid building an infeasible sequence.
            }
            route.add(bestPos, bestOrder);
            remaining.remove(bestOrder);
        }
        return route;
    }

    private static List<Order> twoOptFeasible(Location depot,
                                              List<Order> route,
                                              DistanceService distanceService,
                                              Vehicle vehicle,
                                              int startTimeSec) {
        if (route.size() < 3) {
            return route;
        }
        boolean improved = true;
        RoutePlan currentPlan = simulate(depot, route, distanceService, vehicle, startTimeSec);
        int currentCost = currentPlan.getTotalDurationSec();

        while (improved) {
            improved = false;
            for (int i = 0; i < route.size() - 1; i++) {
                for (int j = i + 1; j < route.size(); j++) {
                    List<Order> trial = new ArrayList<>(route);
                    Collections.reverse(trial.subList(i, j + 1));
                    RoutePlan candidatePlan = simulate(depot, trial, distanceService, vehicle, startTimeSec);
                    if (candidatePlan.isFeasible() && candidatePlan.getTotalDurationSec() < currentCost) {
                        route = trial;
                        currentPlan = candidatePlan;
                        currentCost = candidatePlan.getTotalDurationSec();
                        improved = true;
                        break;
                    }
                }
                if (improved) {
                    break;
                }
            }
        }
        return route;
    }

    public static RoutePlan simulate(Location depot,
                                     List<Order> route,
                                     DistanceService distanceService,
                                     Vehicle vehicle,
                                     int startTimeSec) {
        List<Order> routeSnapshot = Collections.unmodifiableList(new ArrayList<>(route));
        List<Visit> visits = new ArrayList<>(route.size());
        boolean feasible = true;
        int time = startTimeSec;
        Location current = depot;

        // Check pickup-delivery precedence constraints
        if (!isPickupDeliveryFeasible(routeSnapshot)) {
            feasible = false;
        }

        for (Order order : routeSnapshot) {
            int travel = distanceService.travelSeconds(current, order.getLocation(), time, vehicle);
            time += travel;
            int arrivalSec = time;
            int serviceStartSec = Math.max(arrivalSec, order.getEarliestStartSec());
            if (serviceStartSec > order.getLatestEndSec()) {
                feasible = false;
            }
            time = serviceStartSec + order.getServiceTimeSec();
            visits.add(new Visit(order, arrivalSec, serviceStartSec, time, travel));
            current = order.getLocation();
        }

        int returnTravel = distanceService.travelSeconds(current, depot, time, vehicle);
        time += returnTravel;
        int totalDuration = time - startTimeSec;
        if (vehicle != null && vehicle.getMaxRouteSeconds() > 0 && totalDuration > vehicle.getMaxRouteSeconds()) {
            feasible = false;
        }

        return new RoutePlan(routeSnapshot,
                Collections.unmodifiableList(visits),
                totalDuration,
                returnTravel,
                feasible);
    }

    /**
     * Check if pickup-delivery precedence is respected: for each paired order,
     * the pickup must appear before the delivery in the route.
     */
    private static boolean isPickupDeliveryFeasible(List<Order> route) {
        Set<String> seenPickups = new HashSet<>();
        for (Order order : route) {
            if (order.isPickup() && order.isPaired()) {
                seenPickups.add(order.getPairId());
            } else if (order.isDelivery() && order.isPaired()) {
                // Check if corresponding pickup has been visited
                if (!seenPickups.contains(order.getPairId())) {
                    return false; // delivery before pickup
                }
            }
        }
        return true;
    }

    public static final class RoutePlan {
        private final List<Order> route;
        private final List<Visit> visits;
        private final int totalDurationSec;
        private final int returnTravelSeconds;
        private final boolean feasible;

        RoutePlan(List<Order> route,
                  List<Visit> visits,
                  int totalDurationSec,
                  int returnTravelSeconds,
                  boolean feasible) {
            this.route = route;
            this.visits = visits;
            this.totalDurationSec = totalDurationSec;
            this.returnTravelSeconds = returnTravelSeconds;
            this.feasible = feasible;
        }

        public List<Order> getRoute() {
            return route;
        }

        public List<Visit> getVisits() {
            return visits;
        }

        public int getTotalDurationSec() {
            return totalDurationSec;
        }

        public int getReturnTravelSeconds() {
            return returnTravelSeconds;
        }

        public boolean isFeasible() {
            return feasible;
        }
    }

    public static final class Visit {
        private final Order order;
        private final int arrivalSec;
        private final int serviceStartSec;
        private final int departureSec;
        private final int travelFromPreviousSec;

        Visit(Order order,
              int arrivalSec,
              int serviceStartSec,
              int departureSec,
              int travelFromPreviousSec) {
            this.order = order;
            this.arrivalSec = arrivalSec;
            this.serviceStartSec = serviceStartSec;
            this.departureSec = departureSec;
            this.travelFromPreviousSec = travelFromPreviousSec;
        }

        public Order getOrder() {
            return order;
        }

        public int getArrivalSec() {
            return arrivalSec;
        }

        public int getServiceStartSec() {
            return serviceStartSec;
        }

        public int getDepartureSec() {
            return departureSec;
        }

        public int getTravelFromPreviousSec() {
            return travelFromPreviousSec;
        }
    }
}
