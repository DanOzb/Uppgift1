import processing.core.*;

import java.util.ArrayList;

/**
 * Represents a tank entity in the game.
 * Handles movement, rendering, and state of tanks.
 */

class Tank {
    private PApplet parent;

    PVector acceleration;
    PVector velocity;
    PVector position;

    PVector startpos;
    String name;

    String navState = "Idle";
    PImage img;
    int col;
    float diameter;
    float speed;
    float maxspeed;

    float fieldOfView;
    int state;
    boolean isInTransition;
    boolean collisionDetected;

    int health = 3;
    boolean isDestroyed = false;
    Projectile projectile;
    int reloadTime = 180; // 3 seconds at 60 fps
    int reloadCounter = 0;
    boolean canFire = true;
    int hits = 0;

    Sensor losSensor;

    /**
     * Constructor for creating a new tank.
     * @param parent Reference to the Processing applet
     * @param _name Unique identifier name for the tank
     * @param _startpos Initial position vector
     * @param _size Diameter of the tank
     * @param _col Color representing team affiliation
     */
    Tank(PApplet parent, String _name, PVector _startpos, float _size, int _col) {
        this.parent = parent;
        parent.println("*** Tank.Tank()");
        this.name = _name;
        this.diameter = _size;
        this.col = _col;

        this.startpos = new PVector(_startpos.x, _startpos.y);
        this.position = new PVector(this.startpos.x, this.startpos.y);
        this.velocity = new PVector(0, 0);
        this.acceleration = new PVector(0, 0);

        this.state = 0;
        this.speed = 1;
        this.maxspeed = 2;
        this.isInTransition = false;
        this.collisionDetected = false;

        this.fieldOfView = 100.0f;

        this.losSensor = new Sensor(parent, this, 160.0f, PApplet.radians(45));

        this.projectile = new Projectile(parent, this);
    }

    void registerHit() {
        hits++;
        parent.println(name + " scored a hit! Total hits: " + hits);
    }

    ArrayList<SensorDetection> scan(Tank[] allTanks, Tree[] allTrees) {
        return losSensor.scan(allTanks, allTrees);
    }

    /**
     * Records a successful hit by this tank's projectile.
     * Increments hit counter and logs the achievement.
     */
    void handleHit() {
        if (isDestroyed) return;

        health--;
        parent.println(name + " was hit! Health: " + health);

        if (health <= 0) {
            isDestroyed = true;
            velocity.set(0, 0);
            acceleration.set(0, 0);
            parent.println(name + " was destroyed!");
        }

    }
    /**
     * Fires the tank's projectile in the current facing direction.
     * Handles both normal firing and locked-on target firing.
     */
    void fire() {
        if (!canFire || isDestroyed) return;

        PVector direction = new PVector();

        if (state == 9 && losSensor.getIsLockedOn() && losSensor.lockedTarget != null) {
            direction = PVector.sub(losSensor.lockedTarget.position, position);
            direction.normalize();
        } else {
            direction.set(this.velocity);
            direction.normalize();
        }

        PVector cannonEnd = PVector.add(position, PVector.mult(direction, diameter / 2 + 5));

        projectile.fire(cannonEnd, direction);
        canFire = false;
        reloadCounter = reloadTime;

        parent.println(name + " fired!");
    }

    /**
     * Immediately stops all tank movement by zeroing velocity and acceleration.
     */
    void stopMoving() {
        this.velocity.set(0, 0);  // Use set instead of mult
        this.acceleration.set(0, 0);
    }

    /**
     * Updates tank position, velocity, and all systems based on current state.
     * Handles movement, projectile updates, and reload timing.
     */
    void update() {
        // Update movement based on current state
        if (!isDestroyed) {
            float effectiveMaxSpeed = maxspeed;
            switch (state) {
                case 0:
                    // Stop moving
                    stopMoving();
                    break;
                case 1:
                    // Move right
                    velocity.x = accelerateTowards(velocity.x, effectiveMaxSpeed);
                    break;
                case 2:
                    // Move left
                    velocity.x = accelerateTowards(velocity.x, -effectiveMaxSpeed);
                    break;
                case 3:
                    // Move down
                    velocity.y = accelerateTowards(velocity.y, effectiveMaxSpeed);
                    break;
                case 4:
                    // Move up
                    velocity.y = accelerateTowards(velocity.y, -effectiveMaxSpeed);
                    break;
                case 5:  // Right + Down (diagonal)
                    velocity.x = accelerateTowards(velocity.x, effectiveMaxSpeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, effectiveMaxSpeed * 0.7071f);
                    break;
                case 6:  // Right + Up (diagonal)
                    velocity.x = accelerateTowards(velocity.x, effectiveMaxSpeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, -effectiveMaxSpeed * 0.7071f);
                    break;
                case 7:  // Left + Down (diagonal)
                    velocity.x = accelerateTowards(velocity.x, -effectiveMaxSpeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, effectiveMaxSpeed * 0.7071f);
                    break;
                case 8:  // Left + Up (diagonal)
                    velocity.x = accelerateTowards(velocity.x, -effectiveMaxSpeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, -effectiveMaxSpeed * 0.7071f);
                    break;
                case 9:  // Combat mode - tactical movement towards enemy
                    float combatSpeed = maxspeed * 0.2f;
                    if (losSensor.lockedTarget != null) {
                        PVector toEnemy = PVector.sub(losSensor.lockedTarget.position, position);
                        float distanceToEnemy = toEnemy.mag();
                        toEnemy.normalize();
                        PVector moveDirection;

                        if (distanceToEnemy > 240) {
                            moveDirection = toEnemy.copy();
                        } else if (distanceToEnemy < 160) {
                            moveDirection = PVector.mult(toEnemy, -0.5f);
                        } else {
                            PVector strafe = new PVector(-toEnemy.y, toEnemy.x);
                            float time = parent.millis() * 0.002f;
                            float strafeAmount = PApplet.sin(time + name.hashCode()) * 0.7f; // Use tank name for unique pattern

                            moveDirection = PVector.mult(strafe, strafeAmount);
                            if (PApplet.sin(time * 0.5f + name.hashCode()) > 0.3f) {
                                moveDirection.add(PVector.mult(toEnemy, 0.3f)); // Slight advance
                            }
                        }
                        moveDirection.normalize();
                        velocity.x = accelerateTowards(velocity.x, moveDirection.x * combatSpeed);
                        velocity.y = accelerateTowards(velocity.y, moveDirection.y * combatSpeed);
                    } else {
                        Tank leadTank = findTeammateWithTarget();
                        if (leadTank != null) {
                            PVector toLeader = PVector.sub(leadTank.position, position);
                            float distance = toLeader.mag();

                            if (distance > 100) {
                                toLeader.normalize();
                                velocity.x = accelerateTowards(velocity.x, toLeader.x * combatSpeed);
                                velocity.y = accelerateTowards(velocity.y, toLeader.y * combatSpeed);
                            } else {
                                stopMoving();
                            }
                        } else {
                            stopMoving();
                        }
                    }
                    break;
            }
            position.add(velocity);
        }
        projectile.update();

        if (!canFire) {
            reloadCounter--;
            if (reloadCounter <= 0) {
                canFire = true;
            }
        }
    }
    /**
     * Finds a teammate that has a locked target for coordination.
     * @return Tank with locked target, or null if none found
     */
    Tank findTeammateWithTarget() {
        tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
        for (Tank otherTank : game.allTanks) {
            if (otherTank != null && otherTank != this && otherTank.col == this.col) {
                if (otherTank.losSensor.getIsLockedOn() &&
                        otherTank.losSensor.lockedTarget != null &&
                        !otherTank.losSensor.lockedTarget.isDestroyed) {
                    return otherTank;
                }
            }
        }
        return null;
    }

    /**
     * Draws the tank's visual representation at specified local coordinates.
     * @param x X-coordinate offset from tank position
     * @param y Y-coordinate offset from tank position
     */
    void drawTank(float x, float y) {
        parent.fill(this.col, 50);
        parent.ellipse(x, y, 50, 50);

        // Determine cannon direction based on state
        PVector cannonDir = new PVector();
        switch (state) {
            case 0:
                cannonDir.set(1, 0);
                break; // Default right when stationary
            case 1:
                cannonDir.set(1, 0);
                break; // Right
            case 2:
                cannonDir.set(-1, 0);
                break; // Left
            case 3:
                cannonDir.set(0, 1);
                break; // Down
            case 4:
                cannonDir.set(0, -1);
                break; // Up
            case 5:
                cannonDir.set(1, 1).normalize();
                break; // Right+Down
            case 6:
                cannonDir.set(1, -1).normalize();
                break; // Right+Up
            case 7:
                cannonDir.set(-1, 1).normalize();
                break; // Left+Down
            case 8:
                cannonDir.set(-1, -1).normalize();
                break; // Left+Up
            case 9:
                if (losSensor.getIsLockedOn() && losSensor.lockedTarget != null) {
                    // Face the locked target
                    PVector toTarget = PVector.sub(losSensor.lockedTarget.position, position);
                    toTarget.normalize();
                    cannonDir.set(toTarget.x, toTarget.y);
                } else {
                    cannonDir.set(1, 0); // Default right if no target
                }
                break;
        }

        // Draw cannon
        parent.strokeWeight(3);
        float cannonLength = this.diameter / 2 + 5;
        parent.line(x, y, x + cannonDir.x * cannonLength, y + cannonDir.y * cannonLength);

        // Cannon turret
        parent.ellipse(x, y, 25, 25);
        parent.strokeWeight(1);
    }

    /**
     * Renders the complete tank display including health, reload status, and info.
     * Shows tank body, field of view, status information, and projectiles.
     */
    void display() {
        parent.fill(this.col);
        parent.strokeWeight(1);

        parent.pushMatrix();
        parent.translate(this.position.x, this.position.y);

        // Display field of view
        displayFOV();

        parent.imageMode(parent.CENTER);

        // Draw the tank differently if destroyed
        if (isDestroyed) {
            parent.fill(100, 100, 100); // Gray color for destroyed tank
            parent.ellipse(0, 0, diameter, diameter);

            // Draw X on destroyed tank
            parent.stroke(0);
            parent.strokeWeight(3);
            parent.line(-diameter / 4, -diameter / 4, diameter / 4, diameter / 4);
            parent.line(-diameter / 4, diameter / 4, diameter / 4, -diameter / 4);
        } else {
            // Use the modified drawTank method
            drawTank(0, 0);

            // Draw health bar
            parent.noStroke();
            parent.fill(255, 0, 0); // Red background
            parent.rect(-diameter / 2, -diameter / 2 - 10, diameter, 5);

            parent.fill(0, 255, 0); // Green health
            float healthWidth = (health / 3.0f) * diameter;
            parent.rect(-diameter / 2, -diameter / 2 - 10, healthWidth, 5);

            // Draw reload indicator
            if (!canFire) {
                parent.fill(255, 255, 0); // Yellow
                float reloadWidth = ((reloadTime - reloadCounter) / (float) reloadTime) * diameter;
                parent.rect(-diameter / 2, -diameter / 2 - 5, reloadWidth, 3);
            }
        }

        // Display tank info
        parent.strokeWeight(1);
        parent.fill(230, 50f);
        parent.rect(0 + 25, 0 - 25, 100, 60);
        parent.fill(30);
        parent.textSize(15);
        parent.text(navState + "\n" + this.name + "\n( " + (int) this.position.x + ", " + (int) this.position.y + " )", 25 + 5, -5 - 5);

        parent.popMatrix();

        // Display projectile
        projectile.display();
    }

    /**
     * Renders the circular field of view indicator around the tank.
     */
    void displayFOV() {
        parent.noFill();
        parent.stroke(col, 100);
        parent.strokeWeight(2);
        parent.ellipse(0, 0, fieldOfView, fieldOfView);

        parent.fill(col, 20);
        parent.ellipse(0, 0, fieldOfView, fieldOfView);
    }

    /**
     * Smoothly accelerates a value toward a target for fluid movement.
     * @param current Current value
     * @param target Target value to accelerate toward
     * @return New value after applying acceleration
     */
    float accelerateTowards(float current, float target) {
        float acceleration = 0.2f;
        float difference = target - current;

        // If we're very close to the target, snap to it
        if (Math.abs(difference) <= acceleration) {
            return target;
        }

        if (current < target) {
            return current + acceleration;
        } else if (current > target) {
            return current - acceleration;
        }
        return current;
    }
}