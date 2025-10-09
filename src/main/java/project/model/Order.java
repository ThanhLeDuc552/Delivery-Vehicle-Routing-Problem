package project.model;

import java.io.Serializable;

public final class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final int demand;
    private final Location location;
    // Time window (seconds from start of day) and service duration (seconds)
    private final int earliestStartSec;
    private final int latestEndSec;
    private final int serviceTimeSec;
    // Pickup-Delivery support: type and optional pair ID
    private final OrderType type;
    private final String pairId; // null if standalone, otherwise ID of paired pickup/delivery

    public Order(String id, int demand, Location location) {
        this(id, demand, location, 0, 24 * 60 * 60 - 1, 5 * 60, OrderType.DELIVERY, null);
    }

    public Order(String id, int demand, Location location, int earliestStartSec, int latestEndSec, int serviceTimeSec) {
        this(id, demand, location, earliestStartSec, latestEndSec, serviceTimeSec, OrderType.DELIVERY, null);
    }

    public Order(String id, int demand, Location location, int earliestStartSec, int latestEndSec, int serviceTimeSec,
                 OrderType type, String pairId) {
        this.id = id;
        this.demand = demand;
        this.location = location;
        // Normalize values to sane bounds
        int est = Math.max(0, earliestStartSec);
        int let = Math.max(est, latestEndSec); // ensure latest >= earliest
        this.earliestStartSec = est;
        this.latestEndSec = let;
        this.serviceTimeSec = Math.max(0, serviceTimeSec);
        this.type = type != null ? type : OrderType.DELIVERY;
        this.pairId = pairId;
    }

    public String getId() {
        return id;
    }

    public int getDemand() {
        return demand;
    }

    public Location getLocation() {
        return location;
    }

    public int getEarliestStartSec() {
        return earliestStartSec;
    }

    public int getLatestEndSec() {
        return latestEndSec;
    }

    public int getServiceTimeSec() {
        return serviceTimeSec;
    }

    public OrderType getType() {
        return type;
    }

    public String getPairId() {
        return pairId;
    }

    public boolean isPickup() {
        return type == OrderType.PICKUP;
    }

    public boolean isDelivery() {
        return type == OrderType.DELIVERY;
    }

    public boolean isPaired() {
        return pairId != null && !pairId.isEmpty();
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", type=" + type +
                (isPaired() ? ", pair=" + pairId : "") +
                ", demand=" + demand +
                ", location=" + location +
                ", earliestStartSec=" + earliestStartSec +
                ", latestEndSec=" + latestEndSec +
                ", serviceTimeSec=" + serviceTimeSec +
                '}';
    }

    public enum OrderType {
        PICKUP,
        DELIVERY
    }
}
