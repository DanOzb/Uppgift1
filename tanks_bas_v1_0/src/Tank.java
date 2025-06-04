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
     * Creates a new Tank with the specified properties.
     *
     * @param parent Reference to the Processing applet
     * @param _name Name identifier for the tank
     * @param _startpos Starting position vector
     * @param _size Diameter of the tank
     * @param _col Color of the tank (team identifier)
     */
    Tank(PApplet parent, String _name, PVector _startpos, float _size, int _col ) {
        this.parent = parent;
        parent.println("*** Tank.Tank()");
        this.name         = _name;
        this.diameter     = _size;
        this.col          = _col;

        this.startpos     = new PVector(_startpos.x, _startpos.y);
        this.position     = new PVector(this.startpos.x, this.startpos.y);
        this.velocity     = new PVector(0, 0);
        this.acceleration = new PVector(0, 0);

        this.state        = 0;
        this.speed        = 1;
        this.maxspeed     = 2;
        this.isInTransition = false;
        this.collisionDetected = false;

        this.fieldOfView = 100.0f;

        this.losSensor = new Sensor(parent, this, 100.0f, PApplet.radians(45));

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
     * Handles being hit by a projectile.
     *
     * @return true if the hit was registered, false if tank was already destroyed
     */
    boolean handleHit() {
        if (isDestroyed) return false;

        health--;
        parent.println(name + " was hit! Health: " + health);

        if (health <= 0) {
            isDestroyed = true;
            velocity.set(0, 0);
            acceleration.set(0, 0);
            parent.println(name + " was destroyed!");
        }

        return true;
    }

    void fire() {
        if (!canFire || isDestroyed) return;

        // Calculate firing direction based on tank state
        PVector direction = new PVector();

        direction.set(this.velocity);
        direction.normalize();

        // Calculate starting position at the end of the cannon
        PVector cannonEnd = PVector.add(position, PVector.mult(direction, diameter/2 + 5));

        // Fire projectile
        projectile.fire(cannonEnd, direction);
        canFire = false;
        reloadCounter = reloadTime;

        parent.println(name + " fired!");
    }

    /**
     * Stops the tank's movement by setting velocity to zero.
     */
    void stopMoving() {
        this.velocity.set(0, 0);  // Use set instead of mult
        this.acceleration.set(0, 0);
    }
    /**
     * Updates the tank's position and velocity based on its current state.
     * Handles different movement directions including diagonals.
     */
    void update() {
        // Update movement based on current state
        if (!isDestroyed) {
            switch (state) {
                case 0:
                    // Stop moving
                    stopMoving();
                    break;
                case 1:
                    // Move right
                    velocity.x = accelerateTowards(velocity.x, maxspeed);
                    break;
                case 2:
                    // Move left
                    velocity.x = accelerateTowards(velocity.x, -maxspeed);
                    break;
                case 3:
                    // Move down
                    velocity.y = accelerateTowards(velocity.y, maxspeed);
                    break;
                case 4:
                    // Move up
                    velocity.y = accelerateTowards(velocity.y, -maxspeed);
                    break;
                case 5:  // Right + Down (diagonal)
                    velocity.x = accelerateTowards(velocity.x, maxspeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, maxspeed * 0.7071f);
                    break;
                case 6:  // Right + Up (diagonal)
                    velocity.x = accelerateTowards(velocity.x, maxspeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, -maxspeed * 0.7071f);
                    break;
                case 7:  // Left + Down (diagonal)
                    velocity.x = accelerateTowards(velocity.x, -maxspeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, maxspeed * 0.7071f);
                    break;
                case 8:  // Left + Up (diagonal)
                    velocity.x = accelerateTowards(velocity.x, -maxspeed * 0.7071f);
                    velocity.y = accelerateTowards(velocity.y, -maxspeed * 0.7071f);
                    break;
            }
            // Apply velocity to position
            position.add(velocity);
        }
        projectile.update();

        // Handle reload timer
        if (!canFire) {
            reloadCounter--;
            if (reloadCounter <= 0) {
                canFire = true;
            }
        }
    }

    /**
     * Draws the tank's visual representation at the specified coordinates.
     * Coordinates are relative to the tank's position (local coordinates).
     *
     * @param x X-coordinate offset from tank's position
     * @param y Y-coordinate offset from tank's position
     */
    void drawTank(float x, float y) {
        parent.fill(this.col, 50);
        parent.ellipse(x, y, 50, 50);

        // Determine cannon direction based on state
        PVector cannonDir = new PVector();
        switch (state) {
            case 0: cannonDir.set(1, 0); break; // Default right when stationary
            case 1: cannonDir.set(1, 0); break; // Right
            case 2: cannonDir.set(-1, 0); break; // Left
            case 3: cannonDir.set(0, 1); break; // Down
            case 4: cannonDir.set(0, -1); break; // Up
            case 5: cannonDir.set(1, 1).normalize(); break; // Right+Down
            case 6: cannonDir.set(1, -1).normalize(); break; // Right+Up
            case 7: cannonDir.set(-1, 1).normalize(); break; // Left+Down
            case 8: cannonDir.set(-1, -1).normalize(); break; // Left+Up
        }

        // Draw cannon
        parent.strokeWeight(3);
        float cannonLength = this.diameter/2 + 5;
        parent.line(x, y, x + cannonDir.x * cannonLength, y + cannonDir.y * cannonLength);

        // Cannon turret
        parent.ellipse(x, y, 25, 25);
        parent.strokeWeight(1);
    }

    /**
     * Displays the tank on the screen with appropriate transformations.
     * Shows the tank body, field of view, and information (name, position, state).
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
            parent.line(-diameter/4, -diameter/4, diameter/4, diameter/4);
            parent.line(-diameter/4, diameter/4, diameter/4, -diameter/4);
        } else {
            // Use the modified drawTank method (see below)
            drawTank(0, 0);

            // Draw health bar
            parent.noStroke();
            parent.fill(255, 0, 0); // Red background
            parent.rect(-diameter/2, -diameter/2 - 10, diameter, 5);

            parent.fill(0, 255, 0); // Green health
            float healthWidth = (health / 3.0f) * diameter;
            parent.rect(-diameter/2, -diameter/2 - 10, healthWidth, 5);

            // Draw reload indicator
            if (!canFire) {
                parent.fill(255, 255, 0); // Yellow
                float reloadWidth = ((reloadTime - reloadCounter) / (float)reloadTime) * diameter;
                parent.rect(-diameter/2, -diameter/2 - 5, reloadWidth, 3);
            }
        }

        // Display tank info - maintaining original code
        parent.strokeWeight(1);
        parent.fill(230, 50f);
        parent.rect(0 + 25, 0 - 25, 100, 60);
        parent.fill(30);
        parent.textSize(15);
        parent.text(navState + "\n" + this.name + "\n( " + (int)this.position.x + ", " + (int)this.position.y + " )", 25 + 5, -5 - 5);

        parent.popMatrix();

        // Display projectile
        projectile.display();
    }
    /**
     * Displays the field of view (FOV) around the tank.
     * Represents the area that the tank can see or detect.
     */
    void displayFOV() {
        parent.noFill();
        parent.stroke(col,100);
        parent.strokeWeight(2);
        parent.ellipse(0, 0, fieldOfView, fieldOfView);

        parent.fill(col,20);
        parent.ellipse(0, 0, fieldOfView, fieldOfView);
    }

    /**
     * Helper method for smoother acceleration.
     * Gradually changes the current value toward the target value.
     *
     * @param current Current value
     * @param target Target value to accelerate toward
     * @return New value after acceleration
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