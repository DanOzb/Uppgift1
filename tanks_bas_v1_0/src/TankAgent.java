import processing.core.*;

import java.util.ArrayList;
import java.util.Objects;

public class TankAgent {
    PApplet parent;
    Tank tank;
    ExplorationManager explorationManager;

    ArrayList<SensorDetection> lastSensorDetections;


    enum AgentState {
        EXPLORING,
        ATTACKING, //no usage, time sink too large for scope of task
        DEFENDING, //no usage, time sink too large for scope of task
        RETURNING_HOME, //no usage, time sink too large for scope of task
        LOCKED_ON
    }
    AgentState currentState;
    /**
     * Constructor for tank agent with shared exploration manager.
     * @param parent The Processing PApplet instance
     * @param tank The tank this agent controls
     * @param explorationManager Shared exploration manager for team coordination
     */
    TankAgent(PApplet parent, Tank tank, ExplorationManager explorationManager) {
        this.parent = parent;
        this.tank = tank;

        this.explorationManager = explorationManager;

        this.explorationManager.addTank(tank);

        this.currentState = AgentState.EXPLORING;

        this.lastSensorDetections = new ArrayList<>();
    }
    /**
     * Sets up collision handling callbacks for this agent's tank.
     * @param collisions The collision manager to register handlers with
     */
    void setupCollisionHandler(Collisions collisions) {
        collisions.setCollisionHandler(new CollisionHandler() {
            @Override
            public void handleBorderCollision(Tank collidedTank) {
                if (collidedTank == tank && shouldHandleCollision()) {
                    borderCollisionHandle();
                }
            }
            @Override
            public void handleTreeCollision(Tank collidedTank, Tree tree) {
                if (collidedTank == tank && shouldHandleCollision()) {
                    if (tree == null) {
                        Integer counter = explorationManager.samePositionCounters.get(tank);
                        if (counter != null) {
                            explorationManager.samePositionCounters.put(tank, 60);
                        }
                    }
                }
            }

            @Override
            public boolean isReturningHome(Tank checkTank) {
                if (checkTank == tank) {
                    return explorationManager.isReturningHome(tank);
                }
                return false;
            }

            @Override
            public void handleEnemyBaseCollision(Tank collidedTank) {
                if (collidedTank == tank && shouldHandleCollision()) {
                    //System.out.println("Tank " + tank.name + " collided with enemy base");
                    //explorationManager.returnAllHome();
                    //TODO: reposition kanske around enemy base?
                }
            }

            @Override
            public void handleTankCollision(Tank tank, Tank tank2) {
                if (!Objects.equals(tank.navState, "idle") && !Objects.equals(tank2.navState, "idle") && shouldHandleCollision()) {
                    Node temp = explorationManager.targetNodes.get(tank);
                    explorationManager.targetNodes.put(tank, explorationManager.findClosestNode(tank.position));
                    explorationManager.moveTowardTarget(tank);
                    explorationManager.targetNodes.put(tank, temp);
                    explorationManager.navigation();
                }
            }
        });
    }
    /**
     * Updates sensor readings and processes detection data.
     * @param allTanks Array of all tanks for sensor scanning
     * @param allTrees Array of all trees for sensor scanning
     */
    void updateSensor(Tank[] allTanks, Tree[] allTrees) {
        lastSensorDetections = tank.scan(allTanks, allTrees);
        processSensorDetections();
    }
    /**
     * Processes sensor detections and makes tactical decisions.
     * Handles enemy detection, obstacle avoidance, and target locking.
     */
    void processSensorDetections() {
        if (!explorationManager.isAutoExploreActive()) return;

        boolean treeInWay = false;

        for (SensorDetection detection : lastSensorDetections) {
            switch (detection.type) {
                case ENEMY:


                    ExplorationManager.NavigationState navState = explorationManager.navStates.get(tank);
                    if ((navState == ExplorationManager.NavigationState.ATTACK_MODE) && !tank.losSensor.getIsLockedOn() && explorationManager.combatMode) {
                        if (detection.object instanceof Tank) {
                            tank.losSensor.lockedTarget = (Tank) detection.object;
                            tank.losSensor.setIsLockedOn(true);
                            currentState = AgentState.LOCKED_ON;
                            parent.println(tank.name + " LOCKED ONTO TARGET: " + tank.losSensor.lockedTarget.name);
                        }
                    }

                    if (currentState == AgentState.EXPLORING) {
                        //do something
                    }
                    break;

                case TREE:
                    treeInWay = true;
                    if (PVector.dist(tank.position, detection.position) < 50 && shouldHandleCollision()) {
                        explorationManager.handleStuckTank(tank);
                    }
                    break;

                case BASE:
                    if (parent instanceof tanks_bas_v1_0) {
                        tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
                        game.team0.reportEnemyBaseDetection(detection.position, tank);
                    }
                    break;

                case BORDER:
                    if (PVector.dist(tank.position, detection.position) < 30 && shouldHandleCollision()) {
                        borderCollisionHandle();
                    }
                    break;
            }
        }

        if (currentState == AgentState.EXPLORING && treeInWay) {
            explorationManager.expandRRT(tank);
        }
    }
    /**
     * Main update loop for agent behavior and decision making.
     * Handles lock-on behavior, combat mode, and firing decisions.
     */
    void update() {
        if (currentState == AgentState.LOCKED_ON && tank.losSensor.getIsLockedOn() && tank.losSensor.lockedTarget != null) {
            tank.state = 9;
            tank.navState = "LOCKED ON: " + tank.losSensor.lockedTarget.name;
        }

        if (tank.losSensor.isCombatMode() && tank.losSensor.isSpinning() && !tank.losSensor.getIsLockedOn()) {
            tank.state = 9;
            tank.navState = "SCANNING";
        }

        if (!tank.isDestroyed && explorationManager.isAutoExploreActive()) {
            ArrayList<SensorDetection> detections = tank.scan(
                    ((tanks_bas_v1_0) parent).allTanks,
                    ((tanks_bas_v1_0) parent).allTrees
            );
            ExplorationManager.NavigationState navState = explorationManager.navStates.get(tank);

            if (tank.losSensor.getIsLockedOn() && tank.losSensor.lockedTarget != null) {
                if (tank.losSensor.lockedTarget.isDestroyed) {
                    tank.losSensor.lockedTarget = null;
                    tank.losSensor.setIsLockedOn(false);
                    currentState = AgentState.EXPLORING;
                    return;
                }

                tank.state = 9;
                tank.fire();
            }
        }
    }
    /**
     * Sets tank direction to face a specific direction vector.
     * @param direction The direction vector to face toward
     */
    void faceDirection(PVector direction) { //this unfortunately collided with the tank states and the lock on, easiest solution was to simply not use it.
        direction.normalize();

        float angle = PApplet.atan2(direction.y, direction.x);

        int octant = (int) (8 * (angle + PApplet.PI) / (2 * PApplet.PI) + 0.5) % 8;

        switch (octant) {
            case 0:
                tank.state = 1;
                break; // Right
            case 1:
                tank.state = 5;
                break; // Right+Down
            case 2:
                tank.state = 3;
                break; // Down
            case 3:
                tank.state = 7;
                break; // Left+Down
            case 4:
                tank.state = 2;
                break; // Left
            case 5:
                tank.state = 8;
                break; // Left+Up
            case 6:
                tank.state = 4;
                break; // Up
            case 7:
                tank.state = 6;
                break; // Right+Up
        }
    }
    /**
     * Checks if this agent's tank is currently locked onto a target.
     * @return true if tank is locked onto an enemy target
     */
    public boolean isLockedOn() {
        return tank.losSensor.getIsLockedOn() && currentState == AgentState.LOCKED_ON;
    }
    /**
     * Gets the currently locked target for external monitoring.
     * @return The tank that is currently locked onto, or null
     */
    public Tank getLockedTarget() {
        return tank.losSensor.lockedTarget;
    }
    /**
     * Checks if auto-exploration mode is currently active.
     * @return true if auto-exploration is enabled
     */
    void borderCollisionHandle() {
        parent.println("Border collision detected - adjusting navigation");

        Integer stuckCounter = explorationManager.stuckCounters.get(tank);
        if (stuckCounter == null) {
            stuckCounter = 0;
        }

        ExplorationManager.NavigationState navState = explorationManager.navStates.get(tank);
        Node targetNode = explorationManager.targetNodes.get(tank);

        if (navState == ExplorationManager.NavigationState.WAITING_AT_HOME || currentState == AgentState.LOCKED_ON) {
            return;
        }

        if (navState == ExplorationManager.NavigationState.RETURNING_HOME || navState == ExplorationManager.NavigationState.POSITION_AROUND_ENEMY_BASE) {
            explorationManager.recalculatePathFromCurrentPosition(tank);
            return;
        }
        if (navState == ExplorationManager.NavigationState.MOVING_TO_TARGET) {
            if (targetNode != null) {
                stuckCounter++;
                if (stuckCounter > 3) {
                    parent.println("Giving up on current target after multiple collisions");
                    explorationManager.targetNodes.put(tank, null);
                    explorationManager.navStates.put(tank, ExplorationManager.NavigationState.EXPLORING);
                    stuckCounter = 0;
                } else {
                    float randomAngle = explorationManager.random.nextFloat() * PApplet.TWO_PI;
                    PVector escapeDirection = new PVector(PApplet.cos(randomAngle), PApplet.sin(randomAngle));

                    if (Math.abs(escapeDirection.x) > Math.abs(escapeDirection.y)) {
                        tank.state = escapeDirection.x > 0 ? 1 : 2;
                    } else {
                        tank.state = escapeDirection.y > 0 ? 3 : 4;
                    }
                }
            }
        } else {
            PVector center = new PVector(parent.width / 2, parent.height / 2);
            PVector directionToCenter = PVector.sub(center, tank.position);
            directionToCenter.normalize();

            if (Math.abs(directionToCenter.x) > Math.abs(directionToCenter.y)) {
                tank.state = directionToCenter.x > 0 ? 1 : 2;
            } else {
                tank.state = directionToCenter.y > 0 ? 3 : 4;
            }
        }

        explorationManager.stuckCounters.put(tank, stuckCounter);

        float padding = 30;
        float boundaryX = PApplet.constrain(tank.position.x, padding, parent.width - padding);
        float boundaryY = PApplet.constrain(tank.position.y, padding, parent.height - padding);

        if (explorationManager.isValidNodePosition(tank.position, tank)) {
            explorationManager.addNode(boundaryX, boundaryY);
        }
    }
    /**
     * Checks if auto-exploration mode is currently active.
     * @return true if auto-exploration is enabled
     */
    boolean isAutoExploreActive() {
        return explorationManager.isAutoExploreActive();
    }
    /**
     * Sets the pathfinding algorithm to use for navigation.
     * @param algorithm "Dijkstra" or "A*" algorithm selection
     */
    void setPathfindingAlgorithm(String algorithm) {
        if (algorithm.equals("Dijkstra")) {
            explorationManager.testDijkstra = true;
        } else {
            explorationManager.testDijkstra = false;
        }
    }
    /**
     * Determines if collision handling should be active based on current state.
     * @return true if collisions should be processed, false to ignore
     */
    boolean shouldHandleCollision() {
        ExplorationManager.NavigationState navState = explorationManager.navStates.get(tank);
        if (tank.state == 9) return false;
        if (navState == ExplorationManager.NavigationState.POSITION_AROUND_ENEMY_BASE) return false;
        else if (navState == ExplorationManager.NavigationState.WAITING_AT_HOME) return false;
        else if (navState == ExplorationManager.NavigationState.ATTACK_MODE) return false;
        return true;
    }
}