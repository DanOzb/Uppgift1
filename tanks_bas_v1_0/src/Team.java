import processing.core.*;

import java.util.ArrayList;

class Team {
    PApplet parent;
    ArrayList<TankAgent> agents;
    ArrayList<Tank> tanks;
    int teamColor;
    PVector basePosition;
    PVector baseSize;


    ExplorationManager explorationManager;
    PVector detectionPosition = null;
    PVector detectedEnemyBase = null;
    boolean enemyBaseDetected = false;
    long enemyDetectionTime = 0;
    /**
     * Constructor for creating a team with shared exploration management.
     * @param parent The Processing PApplet instance
     * @param color Team color identifier
     * @param basePos Position of the team's home base
     * @param baseSize Dimensions of the home base area
     */
    Team(PApplet parent, int color, PVector basePos, PVector baseSize) {
        this.parent = parent;
        this.agents = new ArrayList<>();
        this.tanks = new ArrayList<>();
        this.teamColor = color;
        this.basePosition = basePos;
        this.baseSize = baseSize;

        this.explorationManager = new ExplorationManager(parent, 100.0f);
        this.explorationManager.initializeFog();
    }
    /**
     * Adds a tank to this team and creates its associated agent.
     * @param tank The tank to add to the team
     */
    void addTank(Tank tank) {
        tanks.add(tank);
        TankAgent agent = new TankAgent(parent, tank, explorationManager);
        agents.add(agent);
    }
    /**
     * Sets up collision handlers for all agents in this team.
     * @param collisions The collision manager to register with
     */
    void setupCollisionHandlers(Collisions collisions) {
        for (TankAgent agent : agents) {
            agent.setupCollisionHandler(collisions);
        }
    }
    /**
     * Updates the team's exploration manager and all agent behaviors.
     * Coordinates team-wide exploration and tactical decisions.
     */
    void update() {
        explorationManager.updateTankPositions();
        explorationManager.navigation();

        for (TankAgent agent : agents) {
            agent.update();
        }

        if (enemyBaseDetected)
            explorationManager.enemyDetected = true;
    }
    /**
     * Toggles auto-exploration mode for all tanks in the team.
     */
    void toggleAutoExplore() {
        explorationManager.toggleAutoExplore();
    }
    /**
     * Commands all tanks to return to their home base.
     */
    void returnAllHome() {
        explorationManager.returnAllHome();
    }
    /**
     * Gets the current exploration completion percentage.
     * @return Percentage of map explored by this team
     */
    float getExplorationPercent() {
        return explorationManager.exploredPercent;
    }
    /**
     * Reports enemy base detection and coordinates team response.
     * @param tankPos Position where enemy base was spotted from
     * @param reportingTank Tank that made the detection
     */
    void reportEnemyBaseDetection(PVector tankPos, Tank reportingTank) {
        if (!enemyBaseDetected) {

            if (parent instanceof tanks_bas_v1_0) {
                tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
                if (teamColor == game.team0.teamColor) {
                    explorationManager.detectedEnemyBase = tankPos.copy();
                }
            }
            setEnemyBaseDetected(true);
            enemyDetectionTime = parent.millis();
            explorationManager.enemyDetected = true;

            parent.println("TEAM ALERT: " + reportingTank + " spotted enemy base from position " + tankPos);
            parent.println("Enemy base located at: " + explorationManager.detectedEnemyBase);
            explorationManager.returnAllHome();
        }
    }
    /**
     * Sets the team's home base position.
     * @param pos New base position coordinates
     */
    public void setBasePosition(PVector pos) {
        basePosition = pos;
    }
    /**
     * Renders the team's home base area.
     */
    void displayHomeBase() {
        parent.strokeWeight(1);
        parent.fill(teamColor, 15);
        parent.rect(basePosition.x, basePosition.y, baseSize.x, baseSize.y);

    }
    /**
     * Renders the team's home base and exploration visualization.
     * Shows base area and delegates to exploration manager for graph display.
     */
    void display() {
        displayHomeBase();
        explorationManager.display();
    }
    /**
     * Sets the enemy base detection status for this team.
     * @param detected true if enemy base has been detected
     */
    public void setEnemyBaseDetected(boolean detected) {
        this.enemyBaseDetected = detected;
    }
    /**
     * Gets the current enemy base detection status.
     * @return true if enemy base has been detected by this team
     */
    public boolean getEnemyBaseDetected() {
        return enemyBaseDetected;
    }

}