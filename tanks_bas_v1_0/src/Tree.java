import processing.core.*;

class Tree {

    private PApplet parent;

    PVector position;
    String  name;
    PImage  img;
    float   diameter;

    //**************************************************
    Tree(PApplet parent, PImage _image, int _posx, int _posy) {
        this.parent = parent;
        this.img       = _image;
        this.diameter  = this.img.width/2;
        this.name      = "tree";
        this.position  = new PVector(_posx, _posy);

    }

    //**************************************************

    void checkCollision() {

    }

    //**************************************************
    void display() {

    }
}
