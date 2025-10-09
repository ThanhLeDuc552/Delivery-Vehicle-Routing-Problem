package project.agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.model.Location;
import project.model.Order;
import project.model.Vehicle;
import project.optimizer.ChocoAssignmentOptimizer;
import project.optimizer.RouteHeuristics;
import project.optimizer.RouteHeuristics.RoutePlan;
import project.util.DistanceService;
import project.util.JsonDataLoader;
import project.util.MetricsCollector;
import project.util.TrafficModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

public class MasterRoutingAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(MasterRoutingAgent.class);

    private Location depot;
    private Map<String, Location> depots;
    private List<Vehicle> vehicles;
    private List<Order> orders;

    private final Map<String, NegotiationState> cnpStates = new HashMap<>();
    private final MetricsCollector metrics = new MetricsCollector();
    private final HashSet<String> awardedOrderIds = new HashSet<>();
    private long lastTrafficReplanMs = 0L;

    private static final long CNP_DEADLINE_MS = 2000L;
    private static final long TRAFFIC_REPLAN_THROTTLE_MS = 10_000L;
    private static final int PLANNING_START_TIME_SEC = 8 * 60 * 60;

    private static final class NegotiationState {
        final Order order;
        final long deadlineEpochMs;
        final Map<String, Integer> proposalsByAgent = new HashMap<>();
        final HashSet<String> respondedAgents = new HashSet<>();
        final int expectedAgents;

        NegotiationState(Order order, long deadlineEpochMs, int expectedAgents) {
            this.order = order;
            this.deadlineEpochMs = deadlineEpochMs;
            this.expectedAgents = expectedAgents;
        }
    }

    @Override
    protected void setup() {
        log.info("MasterRoutingAgent starting...");
        depot = JsonDataLoader.loadDepot();
        depots = JsonDataLoader.loadDepots();
        vehicles = JsonDataLoader.loadVehicles();
        orders = new ArrayList<>(JsonDataLoader.loadOrders());
        
        log.info("Loaded {} depots, {} vehicles, {} orders", depots.size(), vehicles.size(), orders.size());

        planAndDispatch();

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
                if ("STATUS".equals(ontology)) {
                    log.info("Status from {}: {}", msg.getSender().getLocalName(), msg.getContent());
                } else if ("NEW_ORDER".equals(ontology)) {
                    log.info("New order received, triggering negotiation");
                    // Expected format: id,lat,lon,demand[,earliestSec,latestSec,serviceSec]
                    try {
                        String[] parts = msg.getContent().split(",");
                        String id = parts[0];
                        double lat = Double.parseDouble(parts[1]);
                        double lon = Double.parseDouble(parts[2]);
                        int demand = Integer.parseInt(parts[3]);
                        int earliest = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
                        int latest = parts.length > 5 ? Integer.parseInt(parts[5]) : 24 * 60 * 60 - 1;
                        int service = parts.length > 6 ? Integer.parseInt(parts[6]) : 5 * 60;
                        Order o = new Order(id, demand, new Location(id, lat, lon), earliest, latest, service);
                        orders.removeIf(existing -> existing.getId().equals(id));
                        orders.add(o);
                        startNegotiation(o);
                    } catch (Exception e) {
                        log.warn("Failed to parse NEW_ORDER: {}", msg.getContent(), e);
                    }
                } else if ("CFP_REPLY".equals(ontology)) {
                    handleCfpReply(msg);
                } else if ("TRAFFIC_UPDATE".equals(ontology)) {
                    handleTrafficUpdate(msg);
                } else if ("DELAY".equals(ontology) || "BREAKDOWN".equals(ontology)) {
                    handleVehicleFailure(msg);
                } else {
                    log.debug("Unhandled message {} with ontology {}", msg.getConversationId(), ontology);
                }
            }
        });

        addBehaviour(new TickerBehaviour(this, 500) {
            @Override
            protected void onTick() {
                long now = System.currentTimeMillis();
                List<String> toFinalize = new ArrayList<>();
                for (Map.Entry<String, NegotiationState> e : cnpStates.entrySet()) {
                    NegotiationState st = e.getValue();
                    if (st.respondedAgents.size() >= st.expectedAgents || now >= st.deadlineEpochMs) {
                        toFinalize.add(e.getKey());
                    }
                }
                for (String orderId : toFinalize) {
                    finalizeNegotiation(orderId);
                }
            }
        });
    }

    private void startNegotiation(Order order) {
        String content = serializeOrderForCfp(order);
        long deadline = System.currentTimeMillis() + CNP_DEADLINE_MS;
        NegotiationState state = new NegotiationState(order, deadline, vehicles.size());
        cnpStates.put(order.getId(), state);

        for (Vehicle v : vehicles) {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setOntology("CFP");
            cfp.setConversationId(order.getId());
            cfp.addReceiver(new AID("DA-" + v.getId(), AID.ISLOCALNAME));
            cfp.setContent(content);
            send(cfp);
        }
        metrics.recordNegotiation();
        log.info("Broadcasted CFP for order {} to {} agents", order.getId(), vehicles.size());
    }

    private void handleCfpReply(ACLMessage reply) {
        String orderId = reply.getConversationId();
        NegotiationState st = cnpStates.get(orderId);
        if (st == null) {
            return; // no active negotiation (late message)
        }
        String sender = reply.getSender().getLocalName();
        if (st.respondedAgents.contains(sender)) {
            return; // already handled this agent
        }
        st.respondedAgents.add(sender);

        if (reply.getPerformative() == ACLMessage.PROPOSE) {
            try {
                int cost = Integer.parseInt(reply.getContent());
                st.proposalsByAgent.put(sender, cost);
                log.info("Received proposal {} from {} for order {}", cost, sender, orderId);
            } catch (NumberFormatException ignored) {
                log.debug("Malformed proposal from {}: {}", sender, reply.getContent());
            }
        } else if (reply.getPerformative() == ACLMessage.REFUSE) {
            log.info("{} refused order {}: {}", sender, orderId, reply.getContent());
        } else if (reply.getPerformative() == ACLMessage.FAILURE) {
            log.info("{} failed to compute proposal for {}", sender, orderId);
        }
    }

    private void handleTrafficUpdate(ACLMessage msg) {
        Map<String, Double> update = parseTrafficPayload(msg.getContent());
        if (update.isEmpty()) {
            log.debug("Ignored empty traffic update from {}", msg.getSender().getLocalName());
            return;
        }
        TrafficModel.applyUpdate(update, 0);
        long now = System.currentTimeMillis();
        if (now - lastTrafficReplanMs >= TRAFFIC_REPLAN_THROTTLE_MS) {
            lastTrafficReplanMs = now;
            log.info("Traffic update from {} affecting {} zones. Replanning assignments.", msg.getSender().getLocalName(), update.size());
            planAndDispatch();
        } else {
            log.debug("Traffic update received but throttled to avoid excessive replanning.");
        }
    }

    private Map<String, Double> parseTrafficPayload(String content) {
        Map<String, Double> parsed = new HashMap<>();
        if (content == null || content.isEmpty()) {
            return parsed;
        }
        String[] tokens = content.split(";");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty() || !trimmed.contains("=")) continue;
            String[] kv = trimmed.split("=", 2);
            try {
                parsed.put(kv[0], Double.parseDouble(kv[1]));
            } catch (NumberFormatException ignored) {
                log.debug("Failed to parse traffic multiplier '{}'", token);
            }
        }
        return parsed;
    }

    private void handleVehicleFailure(ACLMessage msg) {
        String vehicleId = msg.getSender().getLocalName().replace("DA-", "");
        String ontology = msg.getOntology();
        String content = msg.getContent();
        
        log.warn("Vehicle {} reported {}: {}", vehicleId, ontology, content);
        
        // Parse affected orders from content (format: "orderId1,orderId2,...")
        List<String> affectedOrderIds = new ArrayList<>();
        if (content != null && !content.isEmpty()) {
            for (String orderId : content.split(",")) {
                String trimmed = orderId.trim();
                if (!trimmed.isEmpty()) {
                    affectedOrderIds.add(trimmed);
                }
            }
        }
        
        if (affectedOrderIds.isEmpty()) {
            log.info("No orders affected by {} on vehicle {}", ontology, vehicleId);
            return;
        }
        
        log.info("Reassigning {} affected orders from vehicle {}", affectedOrderIds.size(), vehicleId);
        
        // Remove from awarded orders to allow reassignment
        awardedOrderIds.removeAll(affectedOrderIds);
        
        // Find affected orders in the order list
        List<Order> affectedOrders = new ArrayList<>();
        for (Order order : orders) {
            if (affectedOrderIds.contains(order.getId())) {
                affectedOrders.add(order);
            }
        }
        
        if (affectedOrders.isEmpty()) {
            log.warn("Could not find order objects for affected IDs: {}", affectedOrderIds);
            return;
        }
        
        // Try emergency negotiation for each affected order
        for (Order order : affectedOrders) {
            log.info("Initiating emergency reassignment for order {}", order.getId());
            startNegotiation(order);
        }
        
        // If negotiation fails for some orders, they'll be caught by replan fallback
        log.info("Emergency reassignment initiated for {} orders", affectedOrders.size());
    }

    private void finalizeNegotiation(String orderId) {
        NegotiationState st = cnpStates.remove(orderId);
        if (st == null) return;
        if (st.proposalsByAgent.isEmpty()) {
            log.warn("No proposals for order {}. Falling back to replan.", orderId);
            planAndDispatch();
            return;
        }
        String bestAgent = null;
        int bestCost = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : st.proposalsByAgent.entrySet()) {
            if (e.getValue() < bestCost) {
                bestCost = e.getValue();
                bestAgent = e.getKey();
            }
        }
        if (bestAgent == null) {
            log.warn("No valid proposals for order {}. Replanning.");
            planAndDispatch();
            return;
        }
        ACLMessage award = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        award.setOntology("AWARD");
        award.setConversationId(orderId);
        award.addReceiver(new AID(bestAgent, AID.ISLOCALNAME));
        award.setContent(orderId);
        send(award);
        awardedOrderIds.add(orderId);
        log.info("Awarded order {} to {} (inc cost {}). Total awarded orders: {}", orderId, bestAgent, bestCost, awardedOrderIds.size());
    }

    private void planAndDispatch() {
        DistanceService distanceService = new DistanceService();
        ChocoAssignmentOptimizer optimizer = new ChocoAssignmentOptimizer();
        
        // Filter out orders that were already awarded via negotiation to prevent double assignment
        List<Order> ordersToAssign = new ArrayList<>();
        for (Order order : orders) {
            if (!awardedOrderIds.contains(order.getId())) {
                ordersToAssign.add(order);
            }
        }
        
        if (!awardedOrderIds.isEmpty()) {
            log.info("Excluding {} awarded orders from replan. Assigning {} remaining orders.",
                    awardedOrderIds.size(), ordersToAssign.size());
        }
        
        Map<Vehicle, List<Order>> assignment = optimizer.assignOrders(depot, vehicles, ordersToAssign, distanceService);

        Map<Vehicle, RoutePlan> plansByVehicle = new HashMap<>();
        for (Vehicle v : vehicles) {
            List<Order> assigned = assignment.getOrDefault(v, new ArrayList<>());
            Location vehicleDepot = depots.getOrDefault(v.getDepotId(), depot);
            RoutePlan plan = RouteHeuristics.timeWindowAwareRoute(vehicleDepot, assigned, distanceService, v, PLANNING_START_TIME_SEC);
            plansByVehicle.put(v, plan);
            
            if (!plan.isFeasible()) {
                log.warn("Route for vehicle {} violates constraints ({} stops). Dispatching best-effort plan.", v.getId(), plan.getRoute().size());
            }
            String payload = buildAssignmentPayload(v, plan);
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.setOntology("ASSIGNMENT");
            msg.addReceiver(new AID("DA-" + v.getId(), AID.ISLOCALNAME));
            msg.setContent(payload);
            send(msg);
            double durationMin = plan.getTotalDurationSec() / 60.0;
            log.info("Sent assignment to {}: {} stops, est duration {} min",
                    v.getId(),
                    plan.getRoute().size(),
                    String.format("%.1f", durationMin));
        }
        
        metrics.recordDispatch(plansByVehicle);
    }

    private String buildAssignmentPayload(Vehicle v, RoutePlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getId()).append('|');
        List<RouteHeuristics.Visit> visits = plan.getVisits();
        for (int i = 0; i < visits.size(); i++) {
            RouteHeuristics.Visit visit = visits.get(i);
            sb.append(visit.getOrder().getId()).append('@').append(visit.getServiceStartSec());
            if (i + 1 < visits.size()) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    private String serializeOrderForCfp(Order order) {
        return order.getId() + "," +
                order.getLocation().getLatitude() + "," +
                order.getLocation().getLongitude() + "," +
                order.getDemand() + "," +
                order.getEarliestStartSec() + "," +
                order.getLatestEndSec() + "," +
                order.getServiceTimeSec();
    }
}
