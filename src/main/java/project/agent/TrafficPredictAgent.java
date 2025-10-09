package project.agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.util.TrafficModel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;

/**
 * Simulates traffic prediction by periodically broadcasting traffic zone multipliers.
 * Uses simple stochastic patterns to model time-of-day and random congestion events.
 */
public class TrafficPredictAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(TrafficPredictAgent.class);
    private static final long UPDATE_INTERVAL_MS = 15_000L; // Broadcast every 15 seconds

    private final Random random = new Random();
    private int tick = 0;

    @Override
    protected void setup() {
        log.info("TrafficPredictAgent starting...");

        addBehaviour(new TickerBehaviour(this, UPDATE_INTERVAL_MS) {
            @Override
            protected void onTick() {
                tick++;
                Map<String, Double> trafficUpdate = generateTrafficUpdate();
                broadcastTrafficUpdate(trafficUpdate);
            }
        });
    }

    /**
     * Generate synthetic traffic conditions based on simple patterns.
     */
    private Map<String, Double> generateTrafficUpdate() {
        Map<String, Double> update = new HashMap<>();

        // Simulate time-of-day patterns (oscillate every ~10 ticks)
        double cycle = Math.sin(tick * Math.PI / 10.0);

        // Urban zone: rush hour patterns with random congestion
        double urbanBase = 0.6 + 0.2 * cycle;
        double urbanNoise = random.nextGaussian() * 0.1;
        update.put("urban", Math.max(0.3, Math.min(1.0, urbanBase + urbanNoise)));

        // Suburban zone: lighter traffic with some variability
        double suburbanBase = 0.8 + 0.15 * cycle;
        double suburbanNoise = random.nextGaussian() * 0.05;
        update.put("suburban", Math.max(0.6, Math.min(1.1, suburbanBase + suburbanNoise)));

        // Highway: generally faster, occasional slowdowns
        double highwayBase = 1.2;
        if (random.nextDouble() < 0.15) { // 15% chance of slowdown
            highwayBase = 0.8;
        }
        update.put("highway", highwayBase);

        // Random event: occasionally create severe congestion in a zone
        if (random.nextDouble() < 0.05) {
            String affectedZone = random.nextBoolean() ? "urban" : "suburban";
            update.put(affectedZone, 0.3); // severe congestion
            log.info("Simulating congestion event in zone: {}", affectedZone);
        }

        return update;
    }

    /**
     * Broadcast traffic update to MasterRoutingAgent.
     */
    private void broadcastTrafficUpdate(Map<String, Double> update) {
        StringJoiner payload = new StringJoiner(";");
        for (Map.Entry<String, Double> entry : update.entrySet()) {
            String formatted = String.format(Locale.US, "%.2f", entry.getValue());
            payload.add(entry.getKey() + "=" + formatted);
        }

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setOntology(AgentProtocol.Ontology.TRAFFIC_UPDATE);
        msg.setContent(payload.toString());
        msg.addReceiver(new AID(AgentProtocol.Agents.MASTER, AID.ISLOCALNAME));
        send(msg);

        log.debug("Broadcast traffic update (tick {}): {}", tick, payload);
    }

    @Override
    protected void takeDown() {
        log.info("TrafficPredictAgent shutting down.");
    }
}
