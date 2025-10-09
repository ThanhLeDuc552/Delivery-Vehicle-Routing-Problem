package project.optimizer;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.BoolVar;
import project.model.Location;
import project.model.Order;
import project.model.Vehicle;
import project.util.DistanceService;
import project.util.JsonDataLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChocoAssignmentOptimizer {

    public Map<Vehicle, List<Order>> assignOrders(Map<String, Location> depots,
                                                  Location defaultDepot,
                                                  List<Vehicle> vehicles,
                                                  List<Order> orders,
                                                  DistanceService distanceService) {
        int nV = vehicles.size();
        int nO = orders.size();
        if (nV == 0 || nO == 0) {
            return new HashMap<>();
        }

        int[][] cost = new int[nO][nV];
        int[][] timeCost = new int[nO][nV];
        int[] demand = new int[nO];
        int[] capacity = new int[nV];
        int planningStartSec = 8 * 60 * 60;

        for (int i = 0; i < nO; i++) {
            Order o = orders.get(i);
            demand[i] = o.getDemand();
            for (int k = 0; k < nV; k++) {
                Vehicle vehicle = vehicles.get(k);
                Location depotForVehicle = JsonDataLoader.resolveDepotForVehicle(depots, defaultDepot, vehicle);
                int outbound = distanceService.travelSeconds(depotForVehicle, o.getLocation(), planningStartSec, vehicle);
                int service = o.getServiceTimeSec();
                int returnTrip = distanceService.travelSeconds(o.getLocation(), depotForVehicle, planningStartSec + outbound + service, vehicle);
                int approxTour = outbound + service + returnTrip;
                int windowPenalty = windowPenalty(planningStartSec + outbound, o);
                cost[i][k] = approxTour + windowPenalty;
                timeCost[i][k] = approxTour;
            }
        }
        for (int k = 0; k < nV; k++) {
            capacity[k] = vehicles.get(k).getCapacity();
        }

        Model model = new Model("CVRP-Assignment");
        BoolVar[][] x = model.boolVarMatrix("x", nO, nV);

        // Each order assigned exactly to one vehicle
        for (int i = 0; i < nO; i++) {
            IntVar[] xi = new IntVar[nV];
            for (int k = 0; k < nV; k++) xi[k] = x[i][k];
            model.sum(xi, "=", 1).post();
        }
        // Vehicle capacity constraints
        for (int k = 0; k < nV; k++) {
            IntVar[] col = new IntVar[nO];
            int[] coeff = new int[nO];
            for (int i = 0; i < nO; i++) {
                col[i] = x[i][k];
                coeff[i] = demand[i];
            }
            model.scalar(col, coeff, "<=", capacity[k]).post();
        }
        // Vehicle maximum route duration approximations
        for (int k = 0; k < nV; k++) {
            int maxRouteSeconds = vehicles.get(k).getMaxRouteSeconds();
            if (maxRouteSeconds <= 0) continue;
            IntVar[] col = new IntVar[nO];
            int[] coeff = new int[nO];
            for (int i = 0; i < nO; i++) {
                col[i] = x[i][k];
                coeff[i] = timeCost[i][k];
            }
            model.scalar(col, coeff, "<=", maxRouteSeconds).post();
        }

        // Objective: minimize assignment cost
        IntVar totalCost = model.intVar("totalCost", 0, 1_000_000_000);
        IntVar[] flat = new IntVar[nO * nV];
        int[] weights = new int[nO * nV];
        int idx = 0;
        for (int i = 0; i < nO; i++) {
            for (int k = 0; k < nV; k++) {
                flat[idx] = x[i][k];
                weights[idx] = cost[i][k];
                idx++;
            }
        }
        model.scalar(flat, weights, "=", totalCost).post();
        model.setObjective(Model.MINIMIZE, totalCost);

        Solver solver = model.getSolver();
        if (!solver.solve()) {
            throw new IllegalStateException("No feasible assignment found by Choco");
        }

        Map<Vehicle, List<Order>> result = new HashMap<>();
        for (int k = 0; k < nV; k++) {
            result.put(vehicles.get(k), new ArrayList<>());
        }
        for (int i = 0; i < nO; i++) {
            for (int k = 0; k < nV; k++) {
                if (x[i][k].getValue() == 1) {
                    result.get(vehicles.get(k)).add(orders.get(i));
                    break;
                }
            }
        }
        return result;
    }

    private int windowPenalty(int arrivalEstimateSec, Order order) {
        int latePenalty = Math.max(0, arrivalEstimateSec - order.getLatestEndSec());
        int earlyPenalty = Math.max(0, order.getEarliestStartSec() - arrivalEstimateSec);
        // Weight lateness more heavily than early arrival (which implies waiting).
        return latePenalty * 15 + earlyPenalty * 5;
    }

}
