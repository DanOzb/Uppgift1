import processing.core.*;
import java.util.ArrayList;

public class TankAgent {
    PApplet parent;
    Tank tank;
    ExplorationManager explorationManager; // This is now a reference to a shared instance

    ArrayList<SensorDetection> lastSensorDetections;

    enum AgentState {
        EXPLORING,
        ATTACKING,
        DEFENDING,
        RETURNING_HOME
    }

    AgentState currentState;

    // 2-argument constructor with shared exploration manager
    TankAgent(PApplet parent, Tank tank, ExplorationManager explorationManager) {
        this.parent = parent;
        this.tank = tank;

        // Store the shared exploration manager
        this.explorationManager = explorationManager;

        // Add this tank to the exploration manager
        this.explorationManager.addTank(tank);

        this.currentState = AgentState.EXPLORING;

        this.lastSensorDetections = new ArrayList<>();
    }


    void setupCollisionHandler(Collisions collisions) {
        collisions.setCollisionHandler(new CollisionHandler() {
            @Override
            public void handleBorderCollision(Tank collidedTank) {
                if (collidedTank == tank) {
                    borderCollisionHandle();
                }
            }

            @Override
            public void handleTreeCollision(Tank collidedTank, Tree tree) {
                if (collidedTank == tank) {
                    if (tree == null) {
                        // Notify exploration manager about persistent collision
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
                if (collidedTank == tank) {
                    System.out.println("Tank " + tank.name + " collided with enemy base");
                    explorationManager.returnAllHome();
                }
            }
        });
    }

    void updateSensor(Tank[] allTanks, Tree[] allTrees) {
        // Get the latest sensor readings
        lastSensorDetections = tank.scan(allTanks, allTrees);

        // Process the detections based on the current state
        processSensorDetections();
    }

    void processSensorDetections() {
        // If not in auto-explore mode, don't make autonomous decisions
        if (!explorationManager.isAutoExploreActive()) return;

        boolean enemyDetected = false;
        boolean treeInWay = false;

        for (SensorDetection detection : lastSensorDetections) {
            switch (detection.type) {
                case ENEMY:
                    enemyDetected = true;
                    // Handle enemy detection logic
                    // For example, change state to ATTACKING if appropriate
                    if (currentState == AgentState.EXPLORING) {
                        currentState = AgentState.ATTACKING;
                        // Could add attack behavior here
                    }
                    break;

                case TREE:
                    treeInWay = true;
                    // If we're about to hit a tree, try to find a way around
                    if (PVector.dist(tank.position, detection.position) < 50) {
                        explorationManager.handleStuckTank(tank);
                    }
                    break;

                case BASE:
                    // If we detect an enemy base, mark it
                    // This could be used for strategic planning
                    break;

                case BORDER:
                    // If we're about to hit a border, adjust course
                    if (PVector.dist(tank.position, detection.position) < 30) {
                        borderCollisionHandle();
                    }
                    break;
            }
        }
        if (currentState == AgentState.EXPLORING && treeInWay) {
            // Try to find a clearer path
            explorationManager.expandRRT(tank);
        }
    }


    void update() { //TODO: ????
        // No need to call updateTankPosition or navigation directly
        // These are handled by the exploration manager for all tanks
    }

    void borderCollisionHandle() {
        parent.println("Border collision detected - adjusting navigation");

        Integer stuckCounter = explorationManager.stuckCounters.get(tank);
        if (stuckCounter == null) {
            stuckCounter = 0;
        }

        ExplorationManager.NavigationState navState = explorationManager.navStates.get(tank);
        Node targetNode = explorationManager.targetNodes.get(tank);

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
            PVector center = new PVector(parent.width/2, parent.height/2);
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

    void toggleAutoExplore() {
        // This now toggles auto-explore for all tanks
        explorationManager.toggleAutoExplore();
    }

    boolean isAutoExploreActive() {
        return explorationManager.isAutoExploreActive();
    }

    void setPathfindingAlgorithm(String algorithm) {
        if (algorithm.equals("Dijkstra")) {
            explorationManager.testDijkstra = true;
        } else {
            explorationManager.testDijkstra = false;
        }
    }

    void returnHome() {
        explorationManager.returnHome(tank);
    }

    // No need for a separate display method, as the exploration manager
    // will handle displaying for all tanks

    float getExplorationPercent() {
        return explorationManager.exploredPercent;
    }
}