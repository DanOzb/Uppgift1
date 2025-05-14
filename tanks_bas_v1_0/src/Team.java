import processing.core.*;
import java.util.ArrayList;

class Team {
    //ExplorationManager explorationManager;
    PApplet parent;
    ArrayList<TankAgent> agents;
    ArrayList<Tank> tanks;
    int teamColor;
    PVector basePosition;
    PVector baseSize;

    Team(PApplet parent, int color, PVector basePos, PVector baseSize) {
        this.parent = parent;
        this.agents = new ArrayList<>();
        this.tanks = new ArrayList<>();
        this.teamColor = color;
        this.basePosition = basePos;
        this.baseSize = baseSize;
    }

    void addTank(Tank tank) {
        tanks.add(tank);
        TankAgent agent = new TankAgent(parent, tank);
        agents.add(agent);
    }

    void setupCollisionHandlers(Collisions collisions) {
        for (TankAgent agent : agents) {
            agent.setupCollisionHandler(collisions);
        }
    }

    void update() {
        // Update all agents
        for (TankAgent agent : agents) {
            agent.update();
        }
    }

    void toggleAutoExplore() {
        for (TankAgent agent : agents) {
            agent.toggleAutoExplore();
        }
    }

    void returnAllHome() {
        for (TankAgent agent : agents) {
            agent.returnHome();
        }
    }

    void displayHomeBase() {
        parent.strokeWeight(1);
        parent.fill(teamColor, 15);
        parent.rect(basePosition.x, basePosition.y, baseSize.x, baseSize.y);
    }

    void display() {
        displayHomeBase();

        // Display all agents' exploration info
        for (TankAgent agent : agents) {
            agent.display();
        }
    }

    float getExplorationPercent() {
        float maxPercent = 0;
        for (TankAgent agent : agents) {
            float percent = agent.getExplorationPercent();
            if (percent > maxPercent) {
                maxPercent = percent;
            }
        }
        return maxPercent;
    }
}