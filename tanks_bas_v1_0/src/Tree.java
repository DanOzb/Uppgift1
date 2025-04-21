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

    boolean checkCollision(Tank other) {
    PVector distanceVect = PVector.sub(other.position, position);
    float distanceVecMag = distanceVect.mag();
    float minDistance = radius + other.diameter/2;

    if (distanceVecMag < minDistance) {
        parent.println("Collision Detected");
        return true;
    }
        return false;
    }

    //**************************************************
    void display() {

    }
}
