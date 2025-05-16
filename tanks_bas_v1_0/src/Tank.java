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

        this.losSensor = new Sensor(parent, this, 120.0f, PApplet.radians(45));

    }

    ArrayList<SensorDetection> scan(Tank[] allTanks, Tree[] allTrees) {
        return losSensor.scan(allTanks, allTrees);
    }


    /**
     * Stops the tank's movement by setting velocity to zero.
     */
    void stopMoving() {
        this.velocity = velocity.mult(0);
        this.acceleration = acceleration.mult(0);
    }
    /**
     * Updates the tank's position and velocity based on its current state.
     * Handles different movement directions including diagonals.
     */
    void update() {
        // Update movement based on current state
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
        parent.strokeWeight(1);
        parent.line(x, y, x+25, y);

        // Cannon turret
        parent.ellipse(0, 0, 25, 25);
        parent.strokeWeight(3);
        float cannon_length = this.diameter/2;
        parent.line(0, 0, cannon_length, 0);
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

        displayFOV();

        parent.imageMode(parent.CENTER);
        drawTank(0, 0);
        parent.imageMode(parent.CORNER);

        parent.strokeWeight(1);
        parent.fill(230, 50f);
        parent.rect(0 + 25, 0 - 25, 100, 60);
        parent.fill(30);
        parent.textSize(15);
        parent.text(navState + "\n" + this.name + "\n( " + (int)this.position.x + ", " + (int)this.position.y + " )", 25 + 5, -5 - 5);

        parent.popMatrix();

        // If we have detections, visualize them
        if (parent instanceof tanks_bas_v1_0) { //TODO: inte instanceof
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            ArrayList<SensorDetection> detections = scan(game.allTanks, game.allTrees);
            losSensor.visualize(detections);
        }
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
        if (current < target) {
            return Math.min(current + acceleration, target);
        } else if (current > target) {
            return Math.max(current - acceleration, target);
        }
        return current;
    }
}