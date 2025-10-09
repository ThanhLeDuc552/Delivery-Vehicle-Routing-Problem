# Advanced VRP Implementation Summary

## Overview
This project implements a sophisticated multi-agent delivery vehicle routing system using **JADE** (Java Agent Development Framework) and **Choco Solver**, addressing complex real-world VRP variants with unique optimizations.

## Implemented Features ✅

### 1. **Contract Net Protocol (CNP) for Dynamic Order Allocation**
- **What**: Automated negotiation protocol where the Master Routing Agent broadcasts Call-for-Proposals (CFPs) to Delivery Agents
- **How**: When a new order arrives, all delivery agents compute incremental costs and bid; the lowest-cost agent wins the order
- **Why**: Enables decentralized decision-making and dynamic task allocation without full replanning
- **Location**: `MasterRoutingAgent.java` (lines 123-236)

### 2. **Time Window Constraints (VRPTW)**
- **What**: Each order has earliest/latest service time windows; vehicles must arrive within these bounds
- **How**: 
  - Order model stores `earliestStartSec`, `latestEndSec`, `serviceTimeSec`
  - Route simulation validates time-window feasibility
  - Insertion heuristic only inserts orders at time-feasible positions
- **Impact**: Ensures on-time delivery, respects customer availability
- **Location**: `Order.java`, `RouteHeuristics.java` (simulate method)

### 3. **Vehicle Capacity and Shift Constraints (CVRP + Driver Shifts)**
- **What**: Vehicles have maximum load capacity and maximum route duration (shift time)
- **How**:
  - `Vehicle` model includes `capacity` and `maxRouteSeconds`
  - Choco solver enforces capacity constraints during assignment
  - Route simulation rejects routes exceeding shift duration
- **Impact**: Prevents overloading and overtime violations
- **Location**: `Vehicle.java`, `ChocoAssignmentOptimizer.java`, `RouteHeuristics.java`

### 4. **Time-Dependent Travel Times with Traffic Prediction**
- **What**: Travel speed varies by location zone and time of day
- **How**:
  - `TrafficModel` maintains zone-specific speed multipliers
  - `TrafficPredictAgent` broadcasts dynamic traffic updates (simulates congestion events)
  - `DistanceService.travelSeconds` adjusts speed based on traffic
- **Impact**: More accurate ETAs, adaptive routing under congestion
- **Location**: `TrafficModel.java`, `TrafficPredictAgent.java`, `DistanceService.java`

### 5. **Advanced Routing Heuristics**
- **Time-Window-Aware Insertion**: Greedily inserts orders at the lowest-cost feasible position
- **2-Opt Local Search**: Iteratively reverses route segments to eliminate crossings and reduce tour length
- **Feasibility Checking**: Every candidate route is validated for time windows, capacity, shift limits, and PD precedence before acceptance
- **Location**: `RouteHeuristics.java`

### 6. **Pickup-Delivery Problem (PDP) Support**
- **What**: Some orders are pickup-delivery pairs; pickup must precede delivery
- **How**:
  - `Order` model extended with `OrderType` (PICKUP/DELIVERY) and `pairId`
  - Route validation ensures all deliveries have their pickups visited first
- **Impact**: Supports courier/transfer scenarios (e.g., parcel pickup from sender → delivery to recipient)
- **Location**: `Order.java`, `RouteHeuristics.isPickupDeliveryFeasible`

### 7. **Lock Mechanism for Awarded Orders**
- **What**: Orders awarded via negotiation are excluded from subsequent replanning
- **How**: `MasterRoutingAgent` maintains `awardedOrderIds` set; `planAndDispatch` filters these out
- **Impact**: Prevents double assignment and respects agent commitments
- **Location**: `MasterRoutingAgent.java` (lines 37, 234, 243-253)

### 8. **Comprehensive Metrics Tracking**
- **What**: Real-time logging of on-time delivery rate, route duration, distance, vehicle utilization
- **How**: `MetricsCollector` computes per-dispatch and cumulative metrics
- **Tracked Metrics**:
  - On-time delivery rate (% of deliveries within time windows)
  - Total route duration and distance
  - Average vehicle utilization (time used / max shift)
  - Feasible vs. infeasible routes
- **Location**: `MetricsCollector.java`, integrated into `MasterRoutingAgent`

### 9. **Dynamic Replanning with Throttling**
- **What**: System replans routes when traffic updates arrive, but throttles to avoid excessive computation
- **How**: Traffic updates trigger replanning only if >10 seconds elapsed since last replan
- **Impact**: Balances responsiveness with computational efficiency
- **Location**: `MasterRoutingAgent.handleTrafficUpdate`

### 10. **Choco Solver Integration for Assignment Optimization**
- **What**: Uses constraint programming to optimally assign orders to vehicles
- **Constraints**:
  - Each order assigned to exactly one vehicle
  - Vehicle capacity not exceeded
  - Approximate route duration within vehicle shift limit
  - Time-window penalty in objective (prefers feasible assignments)
- **Objective**: Minimize total assignment cost (distance + time-window penalty)
- **Location**: `ChocoAssignmentOptimizer.java`

---

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    JADE Platform                         │
├─────────────────────────────────────────────────────────┤
│  MasterRoutingAgent (MRA)                               │
│   ├─ Initial Planning: Choco Assignment + Heuristics    │
│   ├─ Negotiation: CNP for new orders                   │
│   ├─ Traffic Handling: Dynamic replanning              │
│   └─ Metrics: Performance tracking                      │
├─────────────────────────────────────────────────────────┤
│  DeliveryAgent (DA-V1, DA-V2, ...)                     │
│   ├─ CFP Response: Compute incremental cost            │
│   ├─ Award Acceptance: Append order to route           │
│   └─ Route Execution: Simulate deliveries              │
├─────────────────────────────────────────────────────────┤
│  TrafficPredictAgent                                    │
│   └─ Traffic Simulation: Broadcast zone multipliers    │
└─────────────────────────────────────────────────────────┘
           │                          │
           ▼                          ▼
    ChocoAssignmentOptimizer    RouteHeuristics
    (Capacity + Time)           (TW-Insertion + 2-Opt)
```

---

## Unique Innovations 🚀

### 1. **Hybrid Optimization: Choco + Heuristics**
- **Assignment Phase**: Choco Solver optimally clusters orders to vehicles (capacity-feasible, time-aware)
- **Sequencing Phase**: Time-window insertion + 2-opt refines the visit order
- **Advantage**: Leverages exact methods for assignment, fast heuristics for sequencing

### 2. **Agent Negotiation for Incremental Orders**
- Unlike batch replanning (expensive), CNP allows agents to bid on new orders
- Agents compute local impact (incremental cost) → decentralized, scalable
- Only falls back to full replan if no agent can accommodate the order

### 3. **Traffic-Aware Multi-Agent System**
- `TrafficPredictAgent` acts as an oracle, periodically updating traffic conditions
- `MasterRoutingAgent` listens for traffic updates and triggers adaptive replanning
- Enables simulation of rush hours, accidents, road closures

### 4. **Pickup-Delivery Precedence Enforcement**
- Most VRP solvers require manual ordering; this system auto-validates precedence
- Insertion heuristic naturally respects precedence (won't insert delivery before pickup)
- Supports courier/transfer scenarios (e.g., Amazon Locker pickup → home delivery)

---

## Comparison with Standard VRP Approaches

| Feature | Standard VRP | This Implementation |
|---------|-------------|---------------------|
| Order assignment | Static batch | Dynamic negotiation + batch |
| Time windows | Often ignored or soft | Hard constraints with feasibility checks |
| Traffic conditions | Static distance matrix | Dynamic, zone-based speed multipliers |
| Shift limits | Rarely modeled | Enforced per vehicle |
| Pickup-Delivery | Requires specialized solver | Built-in precedence validation |
| Metrics | Post-hoc analysis | Real-time tracking |
| Agent interaction | Centralized planner | JADE multi-agent with CNP |

---

## Requirements Coverage

### From `delivery-vehicle-routing-system-requirements.md`:
- ✅ **Master Routing Agent**: Implemented with Choco + heuristics
- ✅ **Delivery Agents**: Implemented with CFP handling and route simulation
- ✅ **Optimal Route Computation**: Choco for assignment, 2-opt for sequencing
- ✅ **Agent Interaction Protocols**: CNP, ASSIGNMENT, STATUS, TRAFFIC_UPDATE ontologies
- ✅ **Search/Optimization Engine**: Choco Solver + custom heuristics
- ✅ **Dynamic Adaptation**: Traffic updates trigger replanning
- ✅ **Automated Negotiation**: CNP for new orders
- ✅ **Delivery Completion**: Routes dispatched with ETAs to delivery agents

### From `VRP.md` (Sophisticated VRP Variants):
- ✅ **CVRP**: Capacity constraints in Choco
- ✅ **VRPTW**: Time windows validated in simulation and insertion
- ✅ **VRPPD**: Pickup-delivery precedence enforced
- ✅ **Dynamic VRP**: CNP negotiation + traffic replanning
- ✅ **Driver Shifts**: `maxRouteSeconds` constraint
- ⚠️ **MDVRP**: Partially (single depot currently, model supports extension)
- ⚠️ **IRP**: Not implemented (inventory routing)
- ⚠️ **SVRP**: Not implemented (stochastic demands)
- ⚠️ **VRPB**: Not implemented (backhaul)

---

## Running the System

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar target/Delivery_Vehicle_Routing_Problem-1.0-SNAPSHOT-shaded.jar
```

### Test Dynamic Orders
Send NEW_ORDER messages to MRA:
```java
// Format: id,lat,lon,demand,earliest,latest,service
"NEW_C9,10.82,106.68,15,36000,43200,600"
```

### Monitor Metrics
Watch console logs for:
- Dispatch metrics (on-time rate, utilization)
- Traffic updates
- Negotiation outcomes

---

## Future Enhancements (Remaining TODOs)

### 1. Multi-Depot Support
- Extend `Vehicle` to reference a home depot
- Modify Choco to assign orders near vehicle's depot
- Update routing to start/end at vehicle-specific depot

### 2. Dynamic Replan on Breakdowns
- Add DELAY/BREAKDOWN ontology
- Reassign affected orders to other vehicles
- Trigger immediate replanning with urgency flag

---

## Key Files

| Component | File | Lines | Purpose |
|-----------|------|-------|---------|
| Master Agent | `MasterRoutingAgent.java` | 300+ | Planning, negotiation, traffic handling |
| Delivery Agent | `DeliveryAgent.java` | 250+ | CFP bidding, route execution |
| Traffic Agent | `TrafficPredictAgent.java` | 100+ | Simulates traffic updates |
| Choco Optimizer | `ChocoAssignmentOptimizer.java` | 124 | Capacity-feasible assignment |
| Heuristics | `RouteHeuristics.java` | 260+ | TW-insertion, 2-opt, PD precedence |
| Models | `Order.java`, `Vehicle.java` | 100+ each | Data structures |
| Utilities | `TrafficModel.java`, `MetricsCollector.java` | 150+ each | Traffic state, metrics |

---

## Performance Characteristics

- **Assignment**: O(n × m × Choco time) where n=orders, m=vehicles
- **Insertion Heuristic**: O(n² × m) per vehicle
- **2-Opt**: O(n² × iterations) per vehicle
- **Negotiation**: O(m) agents respond in parallel, O(1) decision
- **Traffic Updates**: O(1) apply, triggers O(replan) if threshold passed

---

## Conclusion

This implementation demonstrates a **production-grade multi-agent VRP system** that:
1. Combines constraint programming (Choco) with metaheuristics (insertion, 2-opt)
2. Supports advanced constraints (time windows, capacity, shifts, pickup-delivery)
3. Adapts dynamically to traffic and new orders via agent negotiation
4. Tracks performance metrics in real-time
5. Follows software engineering best practices (immutability, separation of concerns, extensibility)

**Uniqueness**: The hybrid Choco+Heuristics+Agent approach is novel, balancing optimality with scalability and real-time adaptability — unlike pure OR solvers (slow for large instances) or pure heuristics (suboptimal assignments).

