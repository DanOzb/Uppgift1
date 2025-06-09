import processing.core.*;

/**
 * Represents a tree obstacle in the game world.
 * Trees obstruct movement and visibility.
 */
class Tree {

    PApplet parent;

    PVector position;
    String name;
    PImage img;
    float diameter;
    float radius;

    /**
     * Constructor for creating a tree obstacle.
     * @param parent Reference to the Processing applet
     * @param _image Image asset to use for tree visualization
     * @param _posx X-coordinate for tree placement
     * @param _posy Y-coordinate for tree placement
     */
    Tree(PApplet parent, PImage _image, float _posx, float _posy) {
        this.parent = parent;
        this.img = _image;
        this.diameter = this.img.width * 0.7f;
        this.name = "tree";
        this.position = new PVector(_posx, _posy);

        this.radius = diameter / 2;
    }

    /**
     * Renders the tree at its position using the assigned image.
     * Centers the image at the tree's position coordinates.
     */
    void display() {
        parent.imageMode(parent.CENTER);
        parent.image(img, position.x, position.y);
        parent.imageMode(parent.CORNER);
    }
}
