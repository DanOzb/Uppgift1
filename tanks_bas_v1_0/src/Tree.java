import processing.core.*;
/**
 * Represents a tree obstacle in the game world.
 * Trees obstruct movement and visibility.
 */
class Tree {

    PApplet parent;

    PVector position;
    String  name;
    PImage  img;
    float   diameter;
    float radius;

    /**
     * Creates a new Tree at the specified position using the provided image.
     *
     * @param parent Reference to the Processing applet
     * @param _image Image to use for the tree visualization
     * @param _posx X-coordinate for the tree position
     * @param _posy Y-coordinate for the tree position
     */
    Tree(PApplet parent, PImage _image, float _posx, float _posy) {
        this.parent = parent;
        this.img       = _image;
        this.diameter  = this.img.width*0.7f;
        this.name      = "tree";
        this.position  = new PVector(_posx, _posy);

        this.radius = diameter/2;
    }

    /**
     * Displays the tree on the screen using its image.
     * Centers the image at the tree's position.
     */
    void display() {
        parent.imageMode(parent.CENTER);
        parent.image(img, position.x, position.y);
        parent.imageMode(parent.CORNER);
    }
}
