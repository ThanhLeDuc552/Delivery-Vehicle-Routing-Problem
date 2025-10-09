# Advanced Multi-Agent Vehicle Routing System

A sophisticated delivery vehicle routing system using **JADE** (multi-agent framework) and **Choco Solver** (constraint programming), solving complex VRP variants with real-time adaptation.

## Key Features 🚀

- **🤝 Contract Net Protocol**: Automated agent negotiation for dynamic order allocation
- **⏰ Time Windows (VRPTW)**: Hard constraints for customer availability
- **📦 Capacity & Shifts (CVRP)**: Vehicle load limits and driver work-time constraints
- **🚦 Traffic-Aware Routing**: Dynamic travel times based on real-time traffic predictions
- **🔄 Pickup-Delivery (PDP)**: Precedence constraints for courier scenarios
- **🏢 Multi-Depot Support**: Vehicles assigned to specific home depots
- **⚠️ Breakdown Handling**: Emergency reassignment on vehicle failures/delays
- **📊 Real-Time Metrics**: On-time rate, utilization, distance tracking
- **🧠 Hybrid Optimization**: Choco Solver for assignment + heuristics (insertion, 2-opt) for sequencing
- **🔒 Order Locking**: Prevents double assignment of negotiated orders

## Quick Start

### Prerequisites
- Java 11+
- Maven 3.6+

### Build
```bash
cd Delivery-Vehicle-Routing-Problem
mvn clean package
```

### Run
```bash
java -jar target/Delivery_Vehicle_Routing_Problem-1.0-SNAPSHOT-shaded.jar
```

### Agents Launched
- **MRA** (Master Routing Agent): Global planner and coordinator
- **DA-V1, DA-V2, ...** (Delivery Agents): One per vehicle
- **TRAFFIC** (Traffic Predict Agent): Simulates dynamic traffic conditions

## Data Files

Edit `src/main/resources/data/` to customize:
- **depot.json**: Warehouse location
- **vehicles.json**: Fleet (capacity, speed, max route duration)
- **orders.json**: Customer orders (location, demand, time windows)

### Example Order with Time Windows
```json
{
  "id": "C1",
  "demand": 10,
  "latitude": 10.7870,
  "longitude": 106.6800,
  "earliestStartSec": 30600,
  "latestEndSec": 39600,
  "serviceTimeSec": 600
}
```

## System Architecture

```
┌───────────────┐  Traffic Updates   ┌──────────────────┐
│ TrafficPredict├──────────────────→│ MasterRouting    │
│ Agent         │                    │ Agent (MRA)      │
└───────────────┘                    │  - Choco Solver  │
                                     │  - CNP Manager   │
                                     │  - Metrics       │
                                     └────────┬─────────┘
                                              │ CFP/AWARD
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
                    ┌───────────┐       ┌───────────┐       ┌───────────┐
                    │ Delivery  │       │ Delivery  │       │ Delivery  │
                    │ Agent V1  │       │ Agent V2  │       │ Agent V3  │
                    └───────────┘       └───────────┘       └───────────┘
```

## How It Works

### 1. Initial Planning
- Choco Solver assigns orders to vehicles (capacity-feasible, time-aware)
- Per-vehicle: time-window insertion heuristic + 2-opt local search
- Routes dispatched with ETAs to delivery agents

### 2. Dynamic Order Arrival
- MRA broadcasts CFP (Call for Proposal) to all delivery agents
- Each agent computes incremental cost of adding the order
- MRA awards order to lowest-cost bidder
- Awarded orders locked from replanning

### 3. Traffic Adaptation
- Traffic agent simulates congestion (time-of-day patterns, random events)
- MRA receives traffic updates, triggers replanning (throttled to 10s interval)
- Routes re-optimized with updated travel times

## Metrics Output Example

```
=== Dispatch Metrics ===
Total orders: 8, On-time: 7, On-time rate: 87.5%
Total route duration: 245.3 min, Total distance: 42.1 km
Average vehicle utilization: 68.2%
Vehicles with feasible routes: 2/2
========================
```

## Advanced Features

### Pickup-Delivery Pairs
Orders can be linked as pickup-delivery pairs:
```java
new Order("P1", 10, loc1, ..., OrderType.PICKUP, "PAIR1");
new Order("D1", 10, loc2, ..., OrderType.DELIVERY, "PAIR1");
```
The system enforces that `P1` is visited before `D1`.

### Traffic Zones
`TrafficModel` defines zones with speed multipliers:
- **urban**: 0.7× base speed (congested)
- **suburban**: 0.85× base speed
- **highway**: 1.2× base speed (fast)

Dynamic events can reduce multipliers further (e.g., accidents → 0.3×).

## Technologies

- **JADE 4.6.0**: Multi-agent platform (FIPA-compliant)
- **Choco Solver 4.10.14**: Constraint programming
- **SLF4J + Logback**: Logging
- **Jackson**: JSON parsing
- **Maven**: Build automation

## Documentation

- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)**: Detailed feature descriptions and design decisions
- **[VRP.md](VRP.md)**: VRP theory and requirements
- **[delivery-vehicle-routing-system-requirements.md](delivery-vehicle-routing-system-requirements.md)**: Original project requirements

## Testing

### Send a New Order at Runtime
Agents listen for `NEW_ORDER` messages. Example (via JADE GUI or programmatically):
```
Ontology: NEW_ORDER
Content: NEW_C9,10.82,106.68,15,36000,43200,600
         (id, lat, lon, demand, earliest, latest, service)
```

### Monitor Agent Activity
Enable JADE GUI:
```bash
java -Djade.gui=true -jar target/...jar
```

## Performance

- **Small instances** (8 orders, 2 vehicles): <1s planning
- **Medium instances** (50 orders, 5 vehicles): 2-5s planning
- **CNP negotiation**: 100-500ms (parallel agent bids)
- **2-Opt improvement**: 10-50ms per vehicle

## Comparison with OR-Tools

| Aspect | OR-Tools | This System |
|--------|----------|-------------|
| Assignment | Meta-heuristic | Choco Solver (exact) |
| Sequencing | Local search | Insertion + 2-opt |
| Dynamic orders | Full replan | Agent negotiation |
| Traffic | Static matrix | Real-time updates |
| Agent-based | No | Yes (JADE) |

## Testing Dynamic Features

### Test Breakdown Handling
Send a breakdown message to any delivery agent (via JADE GUI):
```
Ontology: SIMULATE_BREAKDOWN
Receiver: DA-V1
Content: (empty, will report all assigned orders)
```

Watch the logs for emergency reassignment flow.

### Test Multi-Depot
Create `src/main/resources/data/depots.json`:
```json
[
  {"id": "DEPOT_NORTH", "latitude": 10.8500, "longitude": 106.6800},
  {"id": "DEPOT_SOUTH", "latitude": 10.7500, "longitude": 106.6500}
]
```

Update `vehicles.json` to reference depots:
```json
[
  {"id": "V1", "capacity": 100, "depotId": "DEPOT_NORTH"},
  {"id": "V2", "capacity": 120, "depotId": "DEPOT_SOUTH"}
]
```

---

## Future Enhancements

- **Route Execution Simulation**: Simulate actual driving with delays
- **GUI Dashboard**: Visualize routes and metrics in real-time
- **Stochastic Demands**: Handle uncertain order sizes
- **Inventory Routing**: Coordinate with customer inventory levels

## License

MIT

## References

- [JADE Documentation](https://jade.tilab.com/)
- [Choco Solver Guide](https://choco-solver.org/)
- [VRP Overview](https://gosmartlog.com/vehicle-routing-problem/)
