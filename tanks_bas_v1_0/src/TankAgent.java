import processing.core.*;

public class TankAgent {
    PApplet parent;
    Tank tank;
    ExplorationManager explorationManager; // This is now a reference to a shared instance

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

    void update() {
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