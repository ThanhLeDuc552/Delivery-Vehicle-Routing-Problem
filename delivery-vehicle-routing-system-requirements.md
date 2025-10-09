# Delivery Vehicle Routing System — Requirements

## Project Summary
Design and implement a **delivery vehicle routing system** with two agent roles: **delivery agents** and a **master routing agent** responsible for finding optimal routes so parcels reach receivers.

## Functional Requirements
1. **Master Routing Agent (MRA):**
   - The system **shall include** a Master Routing Agent responsible for global route computation and coordination.
2. **Delivery Agents (DAs):**
   - The system **shall include** multiple Delivery Agents that execute routes and deliver parcels to receivers.
3. **Optimal Route Computation:**
   - The MRA **shall compute** optimal routes for DAs to deliver all assigned parcels to their receivers.
4. **Agent Interaction Protocols:**
   - The system **shall implement** clear interaction protocols between the MRA and DAs for task assignment, status updates, and exception handling.
5. **Search/Optimization Engine:**
   - The system **shall use** search/optimization methods to generate and improve routing plans.
6. **Dynamic Adaptation:**
   - The system **shall adapt** routing plans dynamically in response to runtime changes (e.g., new parcels, delays, or agent availability).
7. **Automated Negotiation:**
   - The system **shall support** automated negotiation among agents for task/route allocation and conflict resolution.
8. **Delivery Completion:**
   - The system **shall ensure** parcels are delivered from senders to the correct receivers following the computed routes.

## Out-of-Scope (inferred for clarity)
- Specific optimization algorithms, data formats, UI design, or map sources are **not specified** on the slide and can be chosen during implementation.
