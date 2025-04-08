import processing.core.*;

class Tank extends PApplet {

    PVector acceleration;
    PVector velocity;
    PVector position;

    PVector startpos;
    String name;
    PImage img;
    int col; // Change from 'color', works still
    float diameter;

    float speed;
    float maxspeed;

    int state;
    boolean isInTransition;

    // Constructor
    Tank(String _name, PVector _startpos, float _size, int _col ) {
        println("*** Tank.Tank()");
        this.name         = _name;
        this.diameter     = _size;
        this.col          = _col;

        this.startpos     = new PVector(_startpos.x, _startpos.y);
        this.position     = new PVector(this.startpos.x, this.startpos.y);
        this.velocity     = new PVector(0, 0);
        this.acceleration = new PVector(0, 0);

        this.state        = 0; // 0(still), 1(moving)
        this.speed        = 0;
        this.maxspeed     = 3;
        this.isInTransition = false;
    }

    //======================================
    void checkEnvironment() {
        println("*** Tank.checkEnvironment()");
        borders();
    }

    void checkForCollisions(Tree sprite) {
        // Implement collision logic
    }

    void checkForCollisions(Tank tank){
        // Implement collision logic
    }

    void checkForCollisions(PVector vec) {
        checkEnvironment();
    }

    // Border collision logic
    void borders() {
        float r = diameter/2;
        if (position.x < -r) position.x = width+r;
        if (position.y < -r) position.y = height+r;
        if (position.x > width+r) position.x = -r;
        if (position.y > height+r) position.y = -r;
    }

    // Movement functions
    void moveForward() {
        println("*** Tank.moveForward()");

        if (this.velocity.x < this.maxspeed) {
            this.velocity.x += 0.01;
        } else {
            this.velocity.x = this.maxspeed;
        }
    }

    void moveBackward() {
        println("*** Tank.moveBackward()");

        if (this.velocity.x > -this.maxspeed) {
            this.velocity.x -= 0.01;
        } else {
            this.velocity.x = -this.maxspeed;
        }
    }

    void stopMoving() {
        println("*** Tank.stopMoving()");
        this.velocity.x = 0;
    }

    // Handle actions like move, stop, etc.
    void action(String _action) {
        println("*** Tank.action()");
        switch (_action) {
            case "move":
                moveForward();
                break;
            case "reverse":
                moveBackward();
                break;
            case "stop":
                stopMoving();
                break;
        }
    }

    // Update tank's movement logic
    void update() {
        println("*** Tank.update()");
        state = 1;
        switch (state) {
            case 0:
                // idle
                action("stop");
                break;
            case 1:
                action("move");
                break;
            case 2:
                action("reverse");
                break;
        }
        this.position.add(velocity);
    }

    // Draw the tank's visual representation
    void drawTank(float x, float y) {
        // Use fill() to set the color, which is fine here
        fill(this.col, 50);

        ellipse(x, y, 50, 50);
        strokeWeight(1);
        line(x, y, x+25, y);

        // Cannon turret
        ellipse(0, 0, 25, 25);
        strokeWeight(3);
        float cannon_length = this.diameter/2;
        line(0, 0, cannon_length, 0);
    }

    // Display tank on screen
    void display() {
        fill(this.col);  // Use fill() for colors
        strokeWeight(1);

        pushMatrix();  // Transformations (translate, rotate, etc.)

        translate(this.position.x, this.position.y);

        imageMode(CENTER);
        drawTank(0, 0);  // Draw the tank itself
        imageMode(CORNER);

        strokeWeight(1);
        fill(230);
        rect(0 + 25, 0 - 25, 100, 40);  // Displaying info above the tank
        fill(30);
        textSize(15);
        text(this.name + "\n( " + this.position.x + ", " + this.position.y + " )", 25 + 5, -5 - 5);

        popMatrix();  // Revert transformations
    }
}
