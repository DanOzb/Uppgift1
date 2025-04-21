import processing.core.*;

class Tank {
    private PApplet parent;

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


    float fieldOfView;
    int state;
    boolean isInTransition;

    Environment environment;

    // Constructor
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

        this.state        = 0; // 0(still), 1(moving)
        this.speed        = 1;
        this.maxspeed     = 3;
        this.isInTransition = false;

        this.fieldOfView = 100.0f;

        environment = new Environment(parent);
        environment.setPosition(environment.createNode(this.position.x, this.position.y));
    }

    //======================================
    void checkEnvironment() {
        parent.println("*** Tank.checkEnvironment()");
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
        if (position.x + r > parent.width)
            position.x = parent.width-r;
        if (position.y + r > parent.height)
            position.y = parent.height-r;
        if (position.x - r < 0)
            position.x = r;
        if (position.y - r < 0)
            position.y = r;
    }

    // Movement functions
    void moveForward() {
        parent.println("*** Tank.moveForward()");

        if (this.velocity.x < this.maxspeed) {
            this.velocity.x += 0.01;
        } else {
            this.velocity.x = this.maxspeed;
        }
    }

    void moveBackward() {
        parent.println("*** Tank.moveBackward()");

        if (this.velocity.x > -this.maxspeed) {
            this.velocity.x -= 0.01;
        } else {
            this.velocity.x = -this.maxspeed;
        }
    }

    void stopMoving() {
        parent.println("*** Tank.stopMoving()");
        this.velocity.x = 0;
    }

    // Handle actions like move, stop, etc.
    void action(String _action) {
        parent.println("*** Tank.action()");
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
        parent.println("*** Tank.update()");
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

    // Display tank on screen
    void display() {
        parent.fill(this.col);  // Use fill() for colors
        parent.strokeWeight(1);

        parent.pushMatrix();  // Transformations (translate, rotate, etc.)

        parent.translate(this.position.x, this.position.y);

        displayFOV();

        parent.imageMode(parent.CENTER);
        drawTank(0, 0);  // Draw the tank itself
        parent.imageMode(parent.CORNER);

        parent.strokeWeight(1);
        parent.fill(230);
        parent.rect(0 + 25, 0 - 25, 100, 40);  // Displaying info above the tank
        parent.fill(30);
        parent.textSize(15);
        parent.text(this.name + "\n( " + this.position.x + ", " + this.position.y + " )", 25 + 5, -5 - 5);

        parent.popMatrix();  // Revert transformations
    }

    void displayFOV() {
        parent.noFill();
        parent.stroke(col,100);
        parent.strokeWeight(2);
        parent.ellipse(0, 0, fieldOfView, fieldOfView);

        parent.fill(col,20);
        parent.ellipse(0, 0, fieldOfView, fieldOfView);
    }
}
