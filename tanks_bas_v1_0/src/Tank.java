import processing.core.*;

class Tank {
    private PApplet parent;

    PVector acceleration;
    PVector velocity;
    PVector position;

    PVector startpos;
    String name;

    String navState = "Idle";
    PImage img;
    int col; // Change from 'color', works still
    float diameter;
    float speed;
    float maxspeed;

    float fieldOfView;
    int state;
    boolean isInTransition;
    boolean collisionDetected;

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
        this.maxspeed     = 2;  // Reduced maxspeed to make DFS more visible
        this.isInTransition = false;
        this.collisionDetected = false;

        this.fieldOfView = 100.0f;

    }

    //======================================
    void checkEnvironment() {
        //parent.println("*** Tank.checkEnvironment()");
        borders();
    }

    boolean checkForCollisions(Tree... trees) {
        collisionDetected = false;

        for (Tree tree : trees) {
            if (tree != null && tree.checkCollision(this)) {
                collisionDetected = true;

                // If auto-explore is active, let the exploration manager know
                if (parent instanceof tanks_bas_v1_0) {
                    tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
                    if (game.explorationManager != null && game.explorationManager.isAutoExploreActive()) {
                        if (!game.explorationManager.isReturningHome()) {
                            game.explorationManager.handleBorderCollision();
                        } else {
                            velocity.mult(0.5f);
                        }
                    }
                }
            }
        }

        // If a collision was detected, temporarily stop the tank's movement
        if (collisionDetected) {
            velocity.mult(0.5f);  // Significant velocity reduction but not complete stop
        }
        return collisionDetected;
    }

    void checkForCollisions(Tank otherTank) {
        if (otherTank == this) return; // Skip self-collision

        // Check if tanks are from the same team (same color)
        boolean sameTeam = this.col == otherTank.col;

        PVector distanceVect = PVector.sub(position, otherTank.position);
        float distanceVecMag = distanceVect.mag();
        float minDistance = diameter/2 + otherTank.diameter/2;

        if (distanceVecMag < minDistance) {
            // If same team, stop and change direction instead of pushing
            if (sameTeam) {
                // Stop moving
                velocity.mult(0);

                // Change direction - move away from friendly tank
                if (Math.abs(distanceVect.x) > Math.abs(distanceVect.y)) {
                    state = distanceVect.x > 0 ? 1 : 2; // Right or Left
                } else {
                    state = distanceVect.y > 0 ? 3 : 4; // Down or Up
                }

                parent.println(name + " detected friendly " + otherTank.name + " and changed direction");
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
            position.x += distanceVect.x * overlap * 0.5f;
            position.y += distanceVect.y * overlap * 0.5f;
            otherTank.position.x -= distanceVect.x * overlap * 0.5f;
            otherTank.position.y -= distanceVect.y * overlap * 0.5f;

            // Zero out the velocity component in the collision direction
            float dotProduct = velocity.x * distanceVect.x + velocity.y * distanceVect.y;
            if (dotProduct < 0) {
                velocity.x -= dotProduct * distanceVect.x;
                velocity.y -= dotProduct * distanceVect.y;
            }

            parent.println("Tank collision detected between " + name + " and " + otherTank.name);
        }
    }

    void checkForCollisions(PVector vec) {
        checkEnvironment();
    }

    boolean isInEnemyBase() {
        // Team 0 tanks (red) can't enter Team 1 base (blue)
        if (col == parent.color(204, 50, 50)) { // Team 0 color
            // Check if in Team 1 base area
            return (position.x >= parent.width - 151 && position.y >= parent.height - 351);
        }
        // Team 1 tanks (blue) can't enter Team 0 base (red)
        else if (col == parent.color(0, 150, 200)) { // Team 1 color
            // Check if in Team 0 base area
            return (position.x <= 150 && position.y <= 350);
        }
        return false;
    }

    boolean willHitEnemyBase(PVector futurePosition) {
        // Team 0 tanks (red) can't enter Team 1 base (blue)
        if (col == parent.color(204, 50, 50)) { // Team 0 color
            // Check if future position would be in Team 1 base area
            return (futurePosition.x >= parent.width - 151 && futurePosition.y >= parent.height - 351);
        }
        // Team 1 tanks (blue) can't enter Team 0 base (red)
        else if (col == parent.color(0, 150, 200)) { // Team 1 color
            // Check if future position would be in Team 0 base area
            return (futurePosition.x <= 150 && futurePosition.y <= 350);
        }
        return false;
    }

    void checkBaseCollisions() {
        // First check if we're already in an enemy base (shouldn't happen, but just in case)
        if (isInEnemyBase()) {
            // Calculate nearest valid position outside the enemy base
            PVector safePosition = new PVector(position.x, position.y);

            // For Team 0 tanks (red) in Team 1 base (blue)
            if (col == parent.color(204, 50, 50)) {
                // Check which boundary is closest and move there
                float distToLeftBoundary = position.x - (parent.width - 151);
                float distToTopBoundary = position.y - (parent.height - 351);

                if (Math.abs(distToLeftBoundary) < Math.abs(distToTopBoundary)) {
                    // Closer to left boundary
                    safePosition.x = parent.width - 151 - 5; // Small extra margin
                } else {
                    // Closer to top boundary
                    safePosition.y = parent.height - 351 - 5; // Small extra margin
                }
            }
            // For Team 1 tanks (blue) in Team 0 base (red)
            else if (col == parent.color(0, 150, 200)) {
                // Check which boundary is closest and move there
                float distToRightBoundary = 150 - position.x;
                float distToBottomBoundary = 350 - position.y;

                if (Math.abs(distToRightBoundary) < Math.abs(distToBottomBoundary)) {
                    // Closer to right boundary
                    safePosition.x = 150 + 5; // Small extra margin
                } else {
                    // Closer to bottom boundary
                    safePosition.y = 350 + 5; // Small extra margin
                }
            }

            // Apply the safe position
            position = safePosition;

            // Stop the tank's momentum
            velocity.mult(0);
            parent.println(name + " was in enemy base and moved to safety");
            return;
        }

        // Now check for potential collisions with base boundaries
        PVector futurePosition = PVector.add(position, velocity);
        boolean willCollide = false;

        // Team 0 tanks (red) hitting Team 1 base (blue)
        if (col == parent.color(204, 50, 50)) {
            if (futurePosition.x >= parent.width - 151 && position.y >= parent.height - 351) {
                // Will hit left boundary of blue base
                velocity.x = 0;
                position.x = parent.width - 151 - 1;
                willCollide = true;
            }
            if (futurePosition.y >= parent.height - 351 && position.x >= parent.width - 151) {
                // Will hit top boundary of blue base
                velocity.y = 0;
                position.y = parent.height - 351 - 1;
                willCollide = true;
            }
            // Handle corner approach
            if (futurePosition.x >= parent.width - 151 && futurePosition.y >= parent.height - 351 &&
                    position.x < parent.width - 151 && position.y < parent.height - 351) {
                // Approaching the corner - stop both directions
                velocity.mult(0);
                willCollide = true;
            }
        }
        // Team 1 tanks (blue) hitting Team 0 base (red)
        else if (col == parent.color(0, 150, 200)) {
            if (futurePosition.x <= 150 && position.y <= 350) {
                // Will hit right boundary of red base
                velocity.x = 0;
                position.x = 150 + 1;
                willCollide = true;
            }
            if (futurePosition.y <= 350 && position.x <= 150) {
                // Will hit bottom boundary of red base
                velocity.y = 0;
                position.y = 350 + 1;
                willCollide = true;
            }
            // Handle corner approach
            if (futurePosition.x <= 150 && futurePosition.y <= 350 &&
                    position.x > 150 && position.y > 350) {
                // Approaching the corner - stop both directions
                velocity.mult(0);
                willCollide = true;
            }
        }

        if (willCollide) {
            // Get reference to the game
            if (parent instanceof tanks_bas_v1_0) {
                tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;

                // If exploration manager exists, tell it to return home
                if (game.explorationManager != null) {
                    System.out.println("Collided");
                    game.explorationManager.returnHome();
                }
            }
            parent.println(name + " detected enemy base - returning home");
        }
    }



    // Movement functions
    void moveForward() {
        //parent.println("*** Tank.moveForward()");

        if (this.velocity.x < this.maxspeed) {
            this.velocity.x += 0.1;
        } else {
            this.velocity.x = this.maxspeed;
        }
    }

    void moveBackward() {
        //parent.println("*** Tank.moveBackward()");

        if (this.velocity.x > -this.maxspeed) {
            this.velocity.x -= 0.1;
        } else {
            this.velocity.x = -this.maxspeed;
        }
    }

    void stopMoving() {
        //parent.println("*** Tank.stopMoving()");
        this.velocity.x = 0;
        this.velocity.y = 0;
    }

    // In Tank.java - update the update() method:
    void update() {
        // Update movement based on current state
        switch (state) {
            case 0:
                // idle
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

        PVector futurePosition = PVector.add(position, velocity);

        // Check if future position would be valid
        boolean validMove = true;

        // Team 0 tanks (red) approaching Team 1 base (blue)
        if (col == parent.color(204, 50, 50)) {
            if (futurePosition.x >= parent.width - 151 && futurePosition.y >= parent.height - 351) {
                validMove = false;
            }
        }
        // Team 1 tanks (blue) approaching Team 0 base (red)
        else if (col == parent.color(0, 150, 200)) {
            if (futurePosition.x <= 150 && futurePosition.y <= 350) {
                validMove = false;
            }
        }

        // Only move if it's a valid position
        if (validMove) {
            position.add(velocity);
        } else {
            // The base collision handling will take care of adjusting position and velocity
            checkBaseCollisions();
        }
    }


    private void moveUpwards() {
        //parent.println("*** Tank.moveDownwards()");
        if (this.velocity.y > -this.maxspeed) {
            this.velocity.y -= 0.1;
        } else {
            this.velocity.y = -this.maxspeed;
        }
    }

    private void moveDownwards() {
        //parent.println("*** Tank.moveUpwards()");

        if (this.velocity.y < this.maxspeed) {
            this.velocity.y += 0.1;
        } else {
            this.velocity.y = this.maxspeed;
        }
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
        parent.rect(0 + 25, 0 - 25, 100, 60);  // Displaying info above the tank
        parent.fill(30);
        parent.textSize(15);
        parent.text(navState + "\n" + this.name + "\n( " + (int)this.position.x + ", " + (int)this.position.y + " )", 25 + 5, -5 - 5);

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

    // Helper method for smoother acceleration
    float accelerateTowards(float current, float target) {
        float acceleration = 0.2f;
        if (current < target) {
            return Math.min(current + acceleration, target);
        } else if (current > target) {
            return Math.max(current - acceleration, target);
        }
        return current;
    }

    // Helper method for smooth deceleration
    float decelerate(float current) {
        float deceleration = 0.3f;
        if (Math.abs(current) < deceleration) {
            return 0;
        } else if (current > 0) {
            return current - deceleration;
        } else {
            return current + deceleration;
        }
    }

    void borders() {
        float r = diameter / 2;
        boolean collision = false;

        if (position.x + r > parent.width) { // Right border
            position.x = parent.width - r;
            velocity.x = -velocity.x * 0.5f; // Bounce with friction
            collision = true;
        }

        if (position.y + r > parent.height) { // Bottom border
            position.y = parent.height - r;
            velocity.y = -velocity.y * 0.5f; // Bounce with friction
            collision = true;
        }

        if (position.x - r < 0) { // Left border
            position.x = r;
            velocity.x = -velocity.x * 0.5f; // Bounce with friction
            collision = true;
        }

        if (position.y - r < 0) { // Top border
            position.y = r;
            velocity.y = -velocity.y * 0.5f; // Bounce with friction
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
}