import processing.core.*;
import java.util.ArrayList;
import java.util.Objects;

public class TankAgent {
    PApplet parent;
    Tank tank;
    ExplorationManager explorationManager; // This is now a reference to a shared instance
    int counter;

    ArrayList<SensorDetection> lastSensorDetections;

    enum AgentState {
        EXPLORING,
        ATTACKING,
        DEFENDING, // Wanted to create tanks that patrol and defend their base, but would take too much time.
        RETURNING_HOME,

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
                    //explorationManager.returnAllHome();
                    //TODO: reposition kanske around enemy base?
                }
            }
            @Override
            public void handleTankCollision(Tank tank, Tank tank2) {
                if (!Objects.equals(tank.navState, "idle") && !Objects.equals(tank2.navState, "idle")) {
                    Node temp = explorationManager.targetNodes.get(tank);
                    explorationManager.targetNodes.put(tank, explorationManager.findClosestNode(tank.position));
                    explorationManager.moveTowardTarget(tank);
                    explorationManager.targetNodes.put(tank, temp);
                    explorationManager.navigation();
                    System.out.println("Moved tanks");
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

                    if (currentState == AgentState.EXPLORING) {
                        //do something
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
                    if (parent instanceof tanks_bas_v1_0) {
                        tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
                        game.team0.reportEnemyBaseDetection(detection.position, tank);
                    }
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


    void update() {
        // Determine if we should shoot based on sensor data
        if (!tank.isDestroyed && explorationManager.isAutoExploreActive()) {
            ArrayList<SensorDetection> detections = tank.scan(
                    ((tanks_bas_v1_0)parent).allTanks,
                    ((tanks_bas_v1_0)parent).allTrees
            );

            // Process detections - look for enemies
            for (SensorDetection detection : detections) {
                if (detection.type == SensorDetection.ObjectType.ENEMY) {
                    // Enemy detected - determine if we should attack
                    float distance = PVector.dist(tank.position, detection.position);
                    if (distance < 200) { // Within attack range
                        currentState = AgentState.ATTACKING;

                        // Face the enemy
                        PVector direction = PVector.sub(detection.position, tank.position);
                        faceDirection(direction);

                        // Try to shoot
                        tank.fire();
                        break;
                    }
                }
            }
        }
    }

    private void faceDirection(PVector direction) {
        direction.normalize();

        // Determine which of the 8 directions is closest
        float angle = PApplet.atan2(direction.y, direction.x);

        // Convert to 8-way direction (0-7)
        int octant = (int)(8 * (angle + PApplet.PI) / (2 * PApplet.PI) + 0.5) % 8;

        // Map octant to tank state
        switch (octant) {
            case 0: tank.state = 1; break; // Right
            case 1: tank.state = 5; break; // Right+Down
            case 2: tank.state = 3; break; // Down
            case 3: tank.state = 7; break; // Left+Down
            case 4: tank.state = 2; break; // Left
            case 5: tank.state = 8; break; // Left+Up
            case 6: tank.state = 4; break; // Up
            case 7: tank.state = 6; break; // Right+Up
        }
    }

    void borderCollisionHandle() {
        parent.println("Border collision detected - adjusting navigation");

        Integer stuckCounter = explorationManager.stuckCounters.get(tank);
        if (stuckCounter == null) {
            stuckCounter = 0;
        }

        ExplorationManager.NavigationState navState = explorationManager.navStates.get(tank);
        Node targetNode = explorationManager.targetNodes.get(tank);

        // Don't handle border collisions if tank is waiting at home
        if (navState == ExplorationManager.NavigationState.WAITING_AT_HOME) {
            return;
        }

        if (navState == ExplorationManager.NavigationState.RETURNING_HOME) {
            // If returning home and hitting a border, recalculate path to individual base node
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
}