package project;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import project.agent.AgentProtocol;
import project.agent.DeliveryAgent;
import project.agent.MasterRoutingAgent;
import project.agent.TrafficPredictAgent;
import project.model.Vehicle;
import project.util.JsonDataLoader;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Vehicle> vehicles = JsonDataLoader.loadVehicles();
        Runtime rt = Runtime.instance();
        ProfileImpl profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, System.getProperty("jade.gui", "false"));

        AgentContainer container = rt.createMainContainer(profile);
        try {
            AgentController traffic = container.createNewAgent(AgentProtocol.Agents.TRAFFIC, TrafficPredictAgent.class.getName(), null);
            traffic.start();

            // Launch DeliveryAgents first
            for (Vehicle v : vehicles) {
                String agentName = AgentProtocol.Agents.deliveryAgent(v.getId());
                AgentController da = container.createNewAgent(agentName, DeliveryAgent.class.getName(), new Object[]{v.getId()});
                da.start();
            }
            // Launch MasterRoutingAgent
            AgentController mra = container.createNewAgent(AgentProtocol.Agents.MASTER, MasterRoutingAgent.class.getName(), null);
            mra.start();
        } catch (StaleProxyException e) {
            throw new RuntimeException(e);
        }
    }
}
