import processing.core.PApplet;
import processing.core.PVector;

public class TankAgent {
    PApplet parent;
    Tank tank;
    ExplorationManager explorationManager;

    public void toggleAutoExplore() {
        explorationManager.toggleAutoExplore();
    }

    public void returnHome() {
        explorationManager.returnHome();
    }

    public void display() {
        explorationManager.display();
    }

    enum AgentState {
        EXPLORING,
        MOVING_TO_TARGET,
        BACKTRACKING,
        RETURNING_HOME
    }

    AgentState currentState;

    TankAgent(PApplet parent, Tank tank) {
        this.parent = parent;
        this.tank = tank;
        this.explorationManager = new ExplorationManager(parent, 100.0f);
        this.explorationManager.setTank(tank); //TODO: kanske ta till explorationManager
        this.explorationManager.initializeFog();
        this.currentState = AgentState.EXPLORING;
    }

    void setupCollisionHandler(Collisions collisions) {
        collisions.setCollisionHandler(new CollisionHandler() {
            @Override
            public void handleBorderCollision(Tank collidedTank) {
                if (collidedTank == tank) {
                    handleBorderCollision(tank);
                }
            }

            @Override
            public void handleTreeCollision(Tank collidedTank, Tree tree) {
                if (collidedTank == tank) {
                    if (tree == null) {
                        // This is a persistent collision notification
                        explorationManager.samePositionCounter = 60;
                    } else {
                        // Regular tree collision handling
                        handleTreeCollision(tank, tree);
                    }
                }
            }

            @Override
            public boolean isReturningHome(Tank checkTank) {
                if (checkTank == tank) {
                    return explorationManager.isReturningHome();
                }
                return false;
            }

            @Override
            public void handleEnemyBaseCollision(Tank collidedTank) {
                if (collidedTank == tank) {
                    System.out.println("Tank " + tank.name + " collided with enemy base");
                    explorationManager.returnHome();
                }
            }
        });
    }

    void update() {
        // Update based on agent state
        if (currentState == AgentState.EXPLORING) {
            explorationManager.updateTankPosition();
            explorationManager.navigation();
        } else if (currentState == AgentState.RETURNING_HOME) {
            explorationManager.updateTankPosition();
            explorationManager.navigation();
        }
    }

    void handleBorderCollision() {
        parent.println("Border collision detected - adjusting navigation");
        if (explorationManager.navState == ExplorationManager.NavigationState.MOVING_TO_TARGET) {
            if (explorationManager.targetNode != null) {
                explorationManager.stuckCounter++;
                if (explorationManager.stuckCounter > 3) {
                    parent.println("Giving up on current target after multiple collisions");
                    explorationManager.targetNode = null;
                    explorationManager.navState = ExplorationManager.NavigationState.EXPLORING;
                    explorationManager.stuckCounter = 0;
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

        float padding = 30;
        float boundaryX = PApplet.constrain(tank.position.x, padding, parent.width - padding);
        float boundaryY = PApplet.constrain(tank.position.y, padding, parent.height - padding);

        if (explorationManager.isValidNodePosition(tank.position)) {
            explorationManager.addNode(boundaryX, boundaryY);
        }
    }
    float getExplorationPercent() {
        return explorationManager.exploredPercent;
    }
}
