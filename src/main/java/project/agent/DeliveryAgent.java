package project.agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.model.Location;
import project.model.Order;
import project.model.Vehicle;
import project.optimizer.RouteHeuristics;
import project.util.DistanceService;
import project.util.JsonDataLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jade.core.AID;

public class DeliveryAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(DeliveryAgent.class);

    private static final int BASE_START_TIME_SEC = 8 * 60 * 60;

    private Vehicle vehicle;
    private Location depot;
    private final DistanceService distanceService = new DistanceService();
    private final List<String> routeOrderIds = new ArrayList<>();
    private final List<RouteStop> routePlan = new ArrayList<>();
    private final Map<String, Order> orderCatalog = new HashMap<>();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        String vehicleId = (args != null && args.length > 0) ? String.valueOf(args[0]) : null;
        Map<String, Location> depots = JsonDataLoader.loadDepots();
        for (Vehicle v : JsonDataLoader.loadVehicles()) {
            if (v.getId().equals(vehicleId)) {
                vehicle = v;
                depot = depots.getOrDefault(v.getDepotId(), JsonDataLoader.loadDepot());
                break;
            }
        }
        if (vehicle == null) {
            log.warn("Vehicle not found for agent {}, id arg= {}", getLocalName(), vehicleId);
            depot = JsonDataLoader.loadDepot();
        } else {
            log.info("DeliveryAgent {} online for vehicle {} at depot {}", getLocalName(), vehicle.getId(), depot.getId());
        }
        JsonDataLoader.loadOrders().forEach(order -> orderCatalog.put(order.getId(), order));

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate any = MessageTemplate.MatchAll();
                ACLMessage msg = receive(any);
                if (msg == null) {
                    block();
                    return;
                }
                String ontology = msg.getOntology();
                if ("ASSIGNMENT".equals(ontology)) {
                    applyAssignment(msg.getContent());
                } else if ("CFP".equals(ontology)) {
                    handleCfp(msg);
                } else if ("AWARD".equals(ontology)) {
                    // Optional: update route after award
                    applyAssignmentAppend(msg.getContent());
                } else if ("SIMULATE_BREAKDOWN".equals(ontology)) {
                    simulateBreakdown();
                } else {
                    // ignore
                }
            }
        });
    }

    private void applyAssignment(String content) {
        // content format: vehicleId|orderId1@eta1,orderId2@eta2,...
        routeOrderIds.clear();
        routePlan.clear();
        if (content == null || content.isEmpty()) return;
        String[] parts = content.split("\\|");
        if (parts.length >= 2) {
            if (!parts[0].equals(vehicle.getId())) return; // not for me
            if (!parts[1].isEmpty()) {
                for (String token : parts[1].split(",")) {
                    if (token.isEmpty()) continue;
                    String[] descriptor = token.split("@");
                    String id = descriptor[0];
                    routeOrderIds.add(id);
                    int eta = descriptor.length > 1 ? safeParseInt(descriptor[1], -1) : -1;
                    routePlan.add(new RouteStop(id, eta));
                }
            }
        }
        log.info("{} received route with {} stops: {}", getLocalName(), routePlan.size(), summariseRoute());
        sendStatus("ROUTE_ACCEPTED:" + routePlan.size());
    }

    private void applyAssignmentAppend(String orderId) {
        if (orderId != null && !orderId.isEmpty()) {
            routeOrderIds.add(orderId);
            routePlan.add(new RouteStop(orderId, -1));
            log.info("{} appended awarded order {}. Route size {}", getLocalName(), orderId, routePlan.size());
        }
    }

    private void handleCfp(ACLMessage cfp) {
        try {
            Order order = parseOrderDescriptor(cfp.getContent());
            orderCatalog.put(order.getId(), order);

            int projectedLoad = currentLoad() + order.getDemand();
            if (projectedLoad > vehicle.getCapacity()) {
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setOntology("CFP_REPLY");
                refuse.setContent("CAPACITY");
                send(refuse);
                return;
            }

            List<Order> currentOrders = buildCurrentOrders();
            RouteHeuristics.RoutePlan basePlan = RouteHeuristics.simulate(depot, currentOrders, distanceService, vehicle, BASE_START_TIME_SEC);

            List<Order> augmentedOrders = new ArrayList<>(currentOrders);
            augmentedOrders.add(order);
            RouteHeuristics.RoutePlan augmentedPlan = RouteHeuristics.timeWindowAwareRoute(depot, augmentedOrders, distanceService, vehicle, BASE_START_TIME_SEC);

            if (!augmentedPlan.isFeasible()) {
                ACLMessage refuse = cfp.createReply();
                refuse.setPerformative(ACLMessage.REFUSE);
                refuse.setOntology("CFP_REPLY");
                refuse.setContent("TIME_WINDOW");
                send(refuse);
                return;
            }

            int inc = augmentedPlan.getTotalDurationSec() - basePlan.getTotalDurationSec();
            if (inc < 0) {
                inc = augmentedPlan.getTotalDurationSec();
            }

            ACLMessage propose = cfp.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setOntology("CFP_REPLY");
            propose.setContent(String.valueOf(inc));
            send(propose);
        } catch (Exception e) {
            ACLMessage fail = cfp.createReply();
            fail.setPerformative(ACLMessage.FAILURE);
            fail.setOntology("CFP_REPLY");
            fail.setContent("ERROR");
            send(fail);
        }
    }

    private void sendStatus(String status) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setOntology("STATUS");
        msg.setContent(status);
        msg.addReceiver(new AID("MRA", AID.ISLOCALNAME));
        send(msg);
    }

    private int safeParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String summariseRoute() {
        if (routePlan.isEmpty()) {
            return "<empty>";
        }
        return routePlan.stream()
                .map(stop -> stop.orderId + etaSuffix(stop))
                .collect(Collectors.joining(" -> "));
    }

    private String etaSuffix(RouteStop stop) {
        if (stop.etaSec < 0) {
            return "";
        }
        return "@" + formatSeconds(stop.etaSec);
    }

    private String formatSeconds(int seconds) {
        if (seconds < 0) {
            return "??:??";
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return String.format("%02d:%02d", h, m);
    }

    private int currentLoad() {
        int load = 0;
        for (String id : routeOrderIds) {
            Order order = orderCatalog.get(id);
            if (order != null) {
                load += order.getDemand();
            }
        }
        return load;
    }

    private List<Order> buildCurrentOrders() {
        List<Order> orders = new ArrayList<>();
        for (String id : routeOrderIds) {
            Order existing = orderCatalog.get(id);
            if (existing != null) {
                orders.add(existing);
            }
        }
        return orders;
    }

    private Order parseOrderDescriptor(String descriptor) {
        String[] parts = descriptor.split(",");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Malformed CFP content: " + descriptor);
        }
        String id = parts[0];
        double lat = Double.parseDouble(parts[1]);
        double lon = Double.parseDouble(parts[2]);
        int demand = Integer.parseInt(parts[3]);
        int earliest = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int latest = parts.length > 5 ? Integer.parseInt(parts[5]) : 24 * 60 * 60 - 1;
        int service = parts.length > 6 ? Integer.parseInt(parts[6]) : 5 * 60;
        Location location = new Location(id, lat, lon);
        return new Order(id, demand, location, earliest, latest, service);
    }

    private void simulateBreakdown() {
        if (routeOrderIds.isEmpty()) {
            log.info("{} received BREAKDOWN simulation but has no orders to report", getLocalName());
            return;
        }
        // Report all remaining orders as affected
        String affectedOrders = String.join(",", routeOrderIds);
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setOntology("BREAKDOWN");
        msg.setContent(affectedOrders);
        msg.addReceiver(new AID("MRA", AID.ISLOCALNAME));
        send(msg);
        
        log.warn("{} simulated BREAKDOWN. Reported {} affected orders to MRA: {}", 
                getLocalName(), routeOrderIds.size(), affectedOrders);
        
        // Clear local route since we've reported the breakdown
        routeOrderIds.clear();
        routePlan.clear();
    }

    private static final class RouteStop {
        final String orderId;
        final int etaSec;

        RouteStop(String orderId, int etaSec) {
            this.orderId = orderId;
            this.etaSec = etaSec;
        }
    }
}
