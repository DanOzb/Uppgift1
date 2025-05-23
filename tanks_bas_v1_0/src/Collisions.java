import processing.core.*;
import java.util.*;
/**
 * Manages all collision detection and resolution between game entities.
 * This class handles collisions between tanks, trees, base boundaries, and screen borders.
 */

public class Collisions {
    PApplet parent;
    CollisionHandler collisionHandler;
    Tree[] trees;

    public Collisions(PApplet parent) {
        this.parent = parent;
        this.trees = null;
    }

    /**
     * Checks if a tank can see another entity directly.
     * Uses the tank's line of sight sensor for verification.
     *
     * @param tank Tank to check from
     * @param otherPosition Position of the entity to check visibility to
     * @return true if the entity is visible to the tank, false if obstructed
     */
    public boolean tankCanSee(Tank tank, PVector otherPosition) {
        if (tank == null) return false;

        ArrayList<SensorDetection> detections = tank.scan(null, getTrees());
        PVector tankToOther = PVector.sub(otherPosition, tank.position);
        tankToOther.normalize();

        // Get tank's direction
        PVector tankDirection = new PVector();
        switch(tank.state) {
            case 1: tankDirection.set(1, 0); break;
            case 2: tankDirection.set(-1, 0); break;
            case 3: tankDirection.set(0, 1); break;
            case 4: tankDirection.set(0, -1); break;
            case 5: tankDirection.set(1, 1).normalize(); break;
            case 6: tankDirection.set(1, -1).normalize(); break;
            case 7: tankDirection.set(-1, 1).normalize(); break;
            case 8: tankDirection.set(-1, -1).normalize(); break;
            default: tankDirection.set(1, 0); break;
        }

        // Check if otherPosition is in the tank's field of view
        float dotProduct = tankDirection.dot(tankToOther);
        float angle = PApplet.acos(dotProduct);

        if (angle > tank.losSensor.viewAngle / 2) {
            return false; // Not in field of view
        }

        // Check if any detection is closer than the otherPosition
        float distToOther = PVector.dist(tank.position, otherPosition);

        for (SensorDetection detection : detections) {
            if (detection.type == SensorDetection.ObjectType.TREE ||
                    detection.type == SensorDetection.ObjectType.BORDER) {
                float distToDetection = PVector.dist(tank.position, detection.position);
                if (distToDetection < distToOther) {
                    return false; // View is obstructed
                }
            }
        }

        return true;
    }

    public void setCollisionHandler(CollisionHandler handler) {
        this.collisionHandler = handler;
    }

    public void setTrees(Tree[] trees) {
        this.trees = trees;
    }

    public Tree[] getTrees() {
        return trees;
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
        for (Tank tank : allTanks) {
            if (tank != null) {
                checkBaseCollisions(tank);
            }
        }

        for (int i = 0; i < allTanks.length; i++) {
            if (allTanks[i] == null) continue;

            for (int j = i + 1; j < allTanks.length; j++) {
                if (allTanks[j] != null) {
                    checkTankCollision(allTanks[i], allTanks[j]);
                }
            }

            boolean treeCollision = checkTreeCollisions(allTanks[i], allTrees);

            if (treeCollision && collisionHandler != null && !collisionHandler.isReturningHome(allTanks[i])) {
                // Notify the handler about a persistent tree collision
                collisionHandler.handleTreeCollision(allTanks[i], null);  // Pass null to indicate a persistent collision
            }

            checkBorderCollisions(allTanks[i]);
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
        if (tank.col == parent.color(204, 50, 50)) {
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


        if (tank.col == parent.color(204, 50, 50)) {
            if (positionIsEnemyBase.x >= parent.width - 151 && tank.position.y >= parent.height - 351) {
                tank.velocity.x = 0;
                tank.position.x = parent.width - 151 - 1;
                collided = true;
            }
            if (positionIsEnemyBase.y >= parent.height - 351 && tank.position.x >= parent.width - 151) {
                tank.velocity.y = 0;
                tank.position.y = parent.height - 351 - 1;
                collided = true;
            }
            // Handle corner approach
            if (positionIsEnemyBase.x >= parent.width - 151 && positionIsEnemyBase.y >= parent.height - 351 &&
                    tank.position.x < parent.width - 151 && tank.position.y < parent.height - 351) {
                tank.velocity.mult(0);
                collided = true;
            }
        }
        else if (tank.col == parent.color(0, 150, 200)) {
            if (positionIsEnemyBase.x <= 150 && tank.position.y <= 350) {
                tank.velocity.x = 0;
                tank.position.x = 150 + 1;
                collided = true;
            }
            if (positionIsEnemyBase.y <= 350 && tank.position.x <= 150) {
                tank.velocity.y = 0;
                tank.position.y = 350 + 1;
                collided = true;
            }
            if (positionIsEnemyBase.x <= 150 && positionIsEnemyBase.y <= 350 &&
                    tank.position.x > 150 && tank.position.y > 350) {
                tank.velocity.mult(0);
                collided = true;
            }
        }

        if (collided) {
            if (collisionHandler != null) {
                collisionHandler.handleEnemyBaseCollision(tank);
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
        if (tank == otherTank) return;

        boolean sameTeam = tank.col == otherTank.col;

        PVector distanceVect = PVector.sub(tank.position, otherTank.position);
        float distanceVecMag = distanceVect.mag();
        float minDistance = tank.diameter/2 + otherTank.diameter/2;

        if (distanceVecMag < minDistance) {
            collisionHandler.handleTankCollision(tank, otherTank);
            }
            //parent.println("Tank collision detected between " + tank.name + " and " + otherTank.name);
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

                if (collisionHandler != null) {
                    if (!collisionHandler.isReturningHome(tank)) {
                        collisionHandler.handleTreeCollision(tank, tree);
                    } else {
                        tank.velocity.mult(0.5f);
                    }
                }
            }
        }

        if (collisionDetected) {
            tank.velocity.mult(0.5f);
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
            float overlap = minDistance - distanceVecMag;

            if (distanceVecMag < 1f) {
                distanceVect = new PVector(1, 0);
            } else {
                distanceVect.normalize();
            }

            tank.position.x += distanceVect.x * overlap;
            tank.position.y += distanceVect.y * overlap;

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
            tank.velocity.x = -tank.velocity.x * 0.5f;
            collision = true;
        }

        if (tank.position.y + r > parent.height) { // Bottom border
            tank.position.y = parent.height - r;
            tank.velocity.y = -tank.velocity.y * 0.5f;
            collision = true;
        }

        if (tank.position.x - r < 0) { // Left border
            tank.position.x = r;
            tank.velocity.x = -tank.velocity.x * 0.5f;
            collision = true;
        }

        if (tank.position.y - r < 0) {// Top border
            tank.position.y = r;
            tank.velocity.y = -tank.velocity.y * 0.5f;
            collision = true;
        }

        if (collision) {
            // Instead of directly calling explManager methods, use the handler
            handleBorderCollision(tank);
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
        PVector d = PVector.sub(end, start);
        PVector f = PVector.sub(start, treeCenter);

        float a = d.dot(d);
        float b = 2 * f.dot(d);
        float c = f.dot(f) - treeRadius * treeRadius;

        float discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return false;
        } else {
            discriminant = (float) Math.sqrt(discriminant);

            float t1 = (-b - discriminant) / (2 * a);
            float t2 = (-b + discriminant) / (2 * a);

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
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.allTrees != null) {
                for (Tree tree : game.allTrees) {
                    if (tree != null) {
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
        if (position.x >= 0 && position.x <= 150 &&
                position.y >= 0 && position.y <= 350) {
            return true;
        }

        if (position.x >= parent.width - 151 && position.x <= parent.width &&
                position.y >= parent.height - 351 && position.y <= parent.height) {
            return true;
        }

        return false;
    }

    /**
     * Handles a collision with the map border.
     * Adjusts navigation strategy and creates a new node near the border.
     */
    void handleBorderCollision(Tank tank) {
        if (collisionHandler != null) {
            collisionHandler.handleBorderCollision(tank);
        } else {
            // Default behavior if no handler is set
            //parent.println("Border collision detected - adjusting navigation");
        }
    }
}