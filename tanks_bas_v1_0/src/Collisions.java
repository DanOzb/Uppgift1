import processing.core.*;

/**
 * Manages all collision detection and resolution between game entities.
 * This class handles collisions between tanks, trees, base boundaries, and screen borders.
 */

public class Collisions {
    private PApplet parent;
    private ExplorationManager explorationManager;

    /**
     * Constructs a new Collisions manager.
     *
     * @param parent The Processing PApplet that this collision system belongs to
     * @param explorationManager The exploration manager to notify about collisions
     */

    public Collisions(PApplet parent, ExplorationManager explorationManager) {
        this.parent = parent;
        this.explorationManager = explorationManager;
    }

    /**
     * Main collision check method checking all possible collisions.
     * Checks for base collisions, tank-to-tank collisions, tank-to-tree collisions,
     * and border collisions.
     *
     * @param allTanks Array of Tank objects to check for collisions
     * @param allTrees Array of Tree objects to check for collisions with tanks
     */
    public void checkAllCollisions(Tank[] allTanks, Tree[] allTrees) {
        for (Tank tank : allTanks) { // check for collisions
            if (tank != null) {
                checkBaseCollisions(tank); // first check if the collision is with a base
            }
        }

        for (int i = 0; i < allTanks.length; i++) { // then check the rest of the collisions
            if (allTanks[i] == null) continue;

            for (int j = i + 1; j < allTanks.length; j++) {
                if (allTanks[j] != null) {
                    checkTankCollision(allTanks[i], allTanks[j]);
                }
            }

            boolean treeCollision = checkTreeCollisions(allTanks[i], allTrees);
            if (treeCollision && i == 0 && explorationManager != null && !explorationManager.isReturningHome()) {
                explorationManager.samePositionCounter = 60; //stuck detection
            }
            checkBorderCollisions(allTanks[i]); //check for border collisions
        }
    }

    /**
     * Checks if tank instance is in enemy base.
     * Used by checkAllCollisions method.
     *
     * @param tank Tank object to check for enemy base collision
     * @return true if in enemy base, otherwise false
     */
    public boolean isInEnemyBase(Tank tank) {
        if (tank.col == parent.color(204, 50, 50)) { // red base
            return (tank.position.x >= parent.width - 151 && tank.position.y >= parent.height - 351);
        }
        else if (tank.col == parent.color(0, 150, 200)) { // blue base
            return (tank.position.x <= 150 && tank.position.y <= 350);
        }
        return false;
    }

    /**
     * Checks and handles collisions between tanks and enemy bases.
     * If a tank is inside an enemy base, it will be moved to a safe position
     * outside the base and its momentum will be stopped.
     *
     * @param tank Tank object to check for base collisions
     */
    public void checkBaseCollisions(Tank tank) {
        
        PVector positionIsEnemyBase = PVector.add(tank.position, tank.velocity);
        boolean collided = false;

        // Team 0 tanks (red) hitting Team 1 base (blue)
        if (tank.col == parent.color(204, 50, 50)) {
            if (positionIsEnemyBase.x >= parent.width - 151 && tank.position.y >= parent.height - 351) {
                // Will hit left boundary of blue base
                tank.velocity.x = 0;
                tank.position.x = parent.width - 151 - 1;
                collided = true;
            }
            if (positionIsEnemyBase.y >= parent.height - 351 && tank.position.x >= parent.width - 151) {
                // Will hit top boundary of blue base
                tank.velocity.y = 0;
                tank.position.y = parent.height - 351 - 1;
                collided = true;
            }
            // Handle corner approach
            if (positionIsEnemyBase.x >= parent.width - 151 && positionIsEnemyBase.y >= parent.height - 351 &&
                    tank.position.x < parent.width - 151 && tank.position.y < parent.height - 351) {
                // Approaching the corner - stop both directions
                tank.velocity.mult(0);
                collided = true;
            }
        }
        // Team 1 tanks (blue) hitting Team 0 base (red)
        else if (tank.col == parent.color(0, 150, 200)) {
            if (positionIsEnemyBase.x <= 150 && tank.position.y <= 350) {
                // Will hit right boundary of red base
                tank.velocity.x = 0;
                tank.position.x = 150 + 1;
                collided = true;
            }
            if (positionIsEnemyBase.y <= 350 && tank.position.x <= 150) {
                // Will hit bottom boundary of red base
                tank.velocity.y = 0;
                tank.position.y = 350 + 1;
                collided = true;
            }
            // Handle corner approach
            if (positionIsEnemyBase.x <= 150 && positionIsEnemyBase.y <= 350 &&
                    tank.position.x > 150 && tank.position.y > 350) {
                // Approaching the corner - stop both directions
                tank.velocity.mult(0);
                collided = true;
            }
        }

        if (collided) {
            // If exploration manager exists, tell it to return home
            if (explorationManager != null && parent instanceof tanks_bas_v1_0) {
                System.out.println("Collided with enemy base");
                explorationManager.returnHome();
            }
            parent.println(tank.name + " detected enemy base - returning home");
        }
    }

    /**
     * Handles collision between two tanks.
     * For tanks of the same team, stops movement and changes direction.
     * For enemy tanks, applies physics-based collision response.
     *
     * @param tank First tank in the collision
     * @param otherTank Second tank in the collision
     */
    public void checkTankCollision(Tank tank, Tank otherTank) {
        if (tank == otherTank) return; // Skip self-collision

        // Check if tanks are from the same team (same color)
        boolean sameTeam = tank.col == otherTank.col;

        PVector distanceVect = PVector.sub(tank.position, otherTank.position);
        float distanceVecMag = distanceVect.mag();
        float minDistance = tank.diameter/2 + otherTank.diameter/2;

        if (distanceVecMag < minDistance) {
            // If same team, stop and change direction instead of pushing
            if (sameTeam) {
                // Stop moving
                tank.velocity.mult(0);

                // Change direction - move away from friendly tank
                if (Math.abs(distanceVect.x) > Math.abs(distanceVect.y)) {
                    tank.state = distanceVect.x > 0 ? 1 : 2; // Right or Left
                } else {
                    tank.state = distanceVect.y > 0 ? 3 : 4; // Down or Up
                }

                parent.println(tank.name + " detected friendly " + otherTank.name + " and changed direction");
                return;
            }

            // For enemy tanks, continue with normal collision response
            // Calculate overlap and push direction
            float overlap = minDistance - distanceVecMag;

            // If tanks are directly on top of each other, use a default direction
            if (distanceVecMag < 0.1f) {
                distanceVect = new PVector(1, 0);  // arbitrary default direction
            } else {
                distanceVect.normalize();
            }

            // Push both tanks away from each other
            tank.position.x += distanceVect.x * overlap * 0.5f;
            tank.position.y += distanceVect.y * overlap * 0.5f;
            otherTank.position.x -= distanceVect.x * overlap * 0.5f;
            otherTank.position.y -= distanceVect.y * overlap * 0.5f;

            // Zero out the velocity component in the collision direction
            float dotProduct = tank.velocity.x * distanceVect.x + tank.velocity.y * distanceVect.y;
            if (dotProduct < 0) {
                tank.velocity.x -= dotProduct * distanceVect.x;
                tank.velocity.y -= dotProduct * distanceVect.y;
            }

            parent.println("Tank collision detected between " + tank.name + " and " + otherTank.name);
        }
    }

    /**
     * Checks and handles collisions between a tank and all trees.
     * Reduces tank velocity if collision occurs.
     *
     * @param tank Tank to check for tree collisions
     * @param trees Array of Tree objects to check against
     * @return true if any tree collision was detected, false otherwise
     */

    public boolean checkTreeCollisions(Tank tank, Tree[] trees) {
        boolean collisionDetected = false;

        for (Tree tree : trees) {
            if (tree != null && checkTreeCollision(tank, tree)) {
                collisionDetected = true;

                // If auto-explore is active, let the exploration manager know
                if (parent instanceof tanks_bas_v1_0) {
                    tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
                    if (game.explorationManager != null && game.explorationManager.isAutoExploreActive()) {
                        if (!game.explorationManager.isReturningHome()) {
                            game.explorationManager.handleBorderCollision();
                        } else {
                            tank.velocity.mult(0.5f);
                        }
                    }
                }
            }
        }

        // If a collision was detected, temporarily slow the tank's movement
        if (collisionDetected) {
            tank.velocity.mult(0.5f);  // Significant velocity reduction but not complete stop
        }
        return collisionDetected;
    }

    /**
     * Checks collision between a single tank and a single tree.
     * If collision is detected, the tank is pushed away from the tree.
     *
     * @param tank Tank to check for tree collision
     * @param tree Tree to check against
     * @return true if collision was detected, false otherwise
     */

    public boolean checkTreeCollision(Tank tank, Tree tree) {
        PVector distanceVect = PVector.sub(tank.position, tree.position);
        float distanceVecMag = distanceVect.mag();
        float minDistance = tree.radius + tank.diameter/2;

        if (distanceVecMag < minDistance) {
            // Calculate overlap and push direction
            float overlap = minDistance - distanceVecMag;

            // If distanceVecMag is too small (zero or near zero), use a default direction
            if (distanceVecMag < 1f) {
                // Default direction away from the center of the tree
                distanceVect = new PVector(1, 0);  // arbitrary default direction
            } else {
                // Normalize the direction vector
                distanceVect.normalize();
            }

            // Push the tank away from the tree
            tank.position.x += distanceVect.x * overlap;
            tank.position.y += distanceVect.y * overlap;

            // Zero out the velocity component in the collision direction
            float dotProduct = tank.velocity.x * distanceVect.x + tank.velocity.y * distanceVect.y;
            if (dotProduct < 0) {
                tank.velocity.x -= dotProduct * distanceVect.x;
                tank.velocity.y -= dotProduct * distanceVect.y;
            }

            return true;
        }
        return false;
    }

    /**
     * Checks and handles collisions between a tank and the screen borders.
     * If collision occurs, the tank bounces off the border with reduced velocity.
     *
     * @param tank Tank to check for border collisions
     */

    public void checkBorderCollisions(Tank tank) {
        float r = tank.diameter / 2;
        boolean collision = false;

        if (tank.position.x + r > parent.width) { // Right border
            tank.position.x = parent.width - r;
            tank.velocity.x = -tank.velocity.x * 0.5f; // Bounce with friction
            collision = true;
        }

        if (tank.position.y + r > parent.height) { // Bottom border
            tank.position.y = parent.height - r;
            tank.velocity.y = -tank.velocity.y * 0.5f; // Bounce with friction
            collision = true;
        }

        if (tank.position.x - r < 0) { // Left border
            tank.position.x = r;
            tank.velocity.x = -tank.velocity.x * 0.5f; // Bounce with friction
            collision = true;
        }

        if (tank.position.y - r < 0) { // Top border
            tank.position.y = r;
            tank.velocity.y = -tank.velocity.y * 0.5f; // Bounce with friction
            collision = true;
        }

        if (collision && parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.explorationManager != null) {
                if (!game.explorationManager.isReturningHome()) {
                    game.explorationManager.handleBorderCollision();
                } else {
                    System.out.println("explorationmanager hit border but still returning home");
                }
            }
        }
    }

    /**
     * Checks if a line between two points intersects with a tree.
     * Used for line-of-sight and path planning calculations.
     *
     * @param start Starting point of the line
     * @param end Ending point of the line
     * @param treeCenter Center position of the tree
     * @param treeRadius Radius of the tree
     * @return true if the line intersects with the tree, false otherwise
     */

    public boolean lineIntersectsTree(PVector start, PVector end, PVector treeCenter, float treeRadius) {
        // Vector from start to end
        PVector d = PVector.sub(end, start);
        // Vector from start to circle center
        PVector f = PVector.sub(start, treeCenter);

        float a = d.dot(d);
        float b = 2 * f.dot(d);
        float c = f.dot(f) - treeRadius * treeRadius;

        float discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return false; // No intersection
        } else {
            discriminant = (float) Math.sqrt(discriminant);

            // Calculate the two intersection points
            float t1 = (-b - discriminant) / (2 * a);
            float t2 = (-b + discriminant) / (2 * a);

            // Check if at least one intersection point is within the line segment
            return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
        }
    }

    /**
     * Checks if there's clear visibility between two points (no obstacles).
     * Used for path planning and line-of-sight calculations.
     *
     * @param from Starting point for visibility check
     * @param to Ending point for visibility check
     * @return true if there is clear visibility between points, false if obstructed
     */

    public boolean canSee(PVector from, PVector to) {
        // Check if line between points intersects with any obstacles
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.allTrees != null) {
                for (Tree tree : game.allTrees) {
                    if (tree != null) {
                        // Use line-circle intersection test
                        if (lineIntersectsTree(from, to, tree.position, tree.radius + 10)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks if a position is within a home base (either team).
     *
     * @param position Position to check
     * @return true if position is in any home base, false otherwise
     */

    public boolean isInHomeBase(PVector position) {
        // Check if position is in Team 0 base (red)
        if (position.x >= 0 && position.x <= 150 &&
                position.y >= 0 && position.y <= 350) {
            return true;
        }

        // Check if position is in Team 1 base (blue)
        if (position.x >= parent.width - 151 && position.x <= parent.width &&
                position.y >= parent.height - 351 && position.y <= parent.height) {
            return true;
        }

        return false;
    }
}