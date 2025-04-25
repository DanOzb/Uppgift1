import processing.core.*;

class Tree {

    private PApplet parent;

    PVector position;
    String  name;
    PImage  img;
    float   diameter;
    float radius;

    //**************************************************
    Tree(PApplet parent, PImage _image, float _posx, float _posy) {
        this.parent = parent;
        this.img       = _image;
        this.diameter  = this.img.width/2;
        this.name      = "tree";
        this.position  = new PVector(_posx, _posy);

        this.radius = diameter/2;
    }



    //**************************************************

    boolean checkCollision(Tank tank) {
        PVector distanceVect = PVector.sub(tank.position, position);
        float distanceVecMag = distanceVect.mag();
        float minDistance = radius + tank.diameter/2;

        if (distanceVecMag < minDistance) {
            // Calculate collision response
            parent.println("Collision Detected");

            // Calculate overlap and push direction
            float overlap = minDistance - distanceVecMag;

            // If distanceVecMag is too small (zero or near zero), use a default direction
            if (distanceVecMag < 0.1f) {
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

    //**************************************************
    void display() {

    }
}
