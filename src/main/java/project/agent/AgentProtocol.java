package project.agent;

/**
 * Shared protocol constants and helper methods for inter-agent communication.
 */
public final class AgentProtocol {

    private AgentProtocol() {
    }

    public static final class Ontology {
        public static final String ASSIGNMENT = "ASSIGNMENT";
        public static final String CFP = "CFP";
        public static final String CFP_REPLY = "CFP_REPLY";
        public static final String AWARD = "AWARD";
        public static final String STATUS = "STATUS";
        public static final String NEW_ORDER = "NEW_ORDER";
        public static final String TRAFFIC_UPDATE = "TRAFFIC_UPDATE";

        private Ontology() {
        }
    }

    public static final class Agents {
        public static final String MASTER = "MRA";
        public static final String TRAFFIC = "TRAFFIC";

        private Agents() {
        }

        public static String deliveryAgent(String vehicleId) {
            return "DA-" + vehicleId;
        }
    }

    public static final class Status {
        public static final String ROUTE_ACCEPTED = "ROUTE_ACCEPTED";

        private Status() {
        }

        public static String routeAccepted(int stopCount) {
            return ROUTE_ACCEPTED + ":" + stopCount;
        }
    }
}
