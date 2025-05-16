import processing.core.*;
import java.util.ArrayList;

class Team {
    PApplet parent;
    ArrayList<TankAgent> agents;
    ArrayList<Tank> tanks;
    int teamColor;
    PVector basePosition;
    PVector baseSize;

    // Shared exploration manager for all tanks in the team
    ExplorationManager explorationManager;

    Team(PApplet parent, int color, PVector basePos, PVector baseSize) {
        this.parent = parent;
        this.agents = new ArrayList<>();
        this.tanks = new ArrayList<>();
        this.teamColor = color;
        this.basePosition = basePos;
        this.baseSize = baseSize;

        // Create a single exploration manager for the team
        this.explorationManager = new ExplorationManager(parent, 100.0f);
        this.explorationManager.initializeFog();
    }

    void addTank(Tank tank) {
        tanks.add(tank);
        // Create TankAgent with the shared explorationManager
        TankAgent agent = new TankAgent(parent, tank, explorationManager);
        agents.add(agent);
    }

    void setupCollisionHandlers(Collisions collisions) {
        for (TankAgent agent : agents) {
            agent.setupCollisionHandler(collisions);
        }
    }

    void update() {
        // Update the shared exploration manager for all tanks
        explorationManager.updateTankPositions();
        explorationManager.navigation();

        // Still call update on each agent for any agent-specific logic
        for (TankAgent agent : agents) {
            agent.update();
        }
    }

    void toggleAutoExplore() {
        // Now this toggles auto-explore for all tanks at once
        explorationManager.toggleAutoExplore();
    }

    void returnAllHome() {
        explorationManager.returnAllHome();
    }

    float getExplorationPercent() {
        return explorationManager.exploredPercent;
    }

    void displayHomeBase() {
        parent.strokeWeight(1);
        parent.fill(teamColor, 15);
        parent.rect(basePosition.x, basePosition.y, baseSize.x, baseSize.y);
    }

    void display() {
        displayHomeBase();

        // Display the shared exploration graph
        explorationManager.display();
    }
}