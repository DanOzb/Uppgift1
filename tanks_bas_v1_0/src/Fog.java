import processing.core.*;
import java.util.ArrayList;

class Fog {
    private PApplet parent;
    PGraphics fogLayer;
    int fogColor;
    int fogAlpha;
    boolean initialized;
    Environment environment;

    // Store visited positions
    ArrayList<PVector> visitedPositions;

    Fog(PApplet parent) {
        this.parent = parent;
        this.fogColor = parent.color(50, 50, 50);
        this.fogAlpha = 100;
        this.initialized = false;
        this.visitedPositions = new ArrayList<PVector>();
        this.environment = new Environment(parent);
    }

    void initialize() {
        if (parent.width > 0 && parent.height > 0) {
            fogLayer = parent.createGraphics(parent.width, parent.height);
            fogLayer.beginDraw();
            fogLayer.background(fogColor, fogAlpha); // Start with full fog
            fogLayer.endDraw();
            initialized = true;
        }
    }

    void clearAroundTank(Tank tank) {
        if (!initialized) return;

        // Add current position to visited positions
        PVector currentPos = new PVector(tank.position.x, tank.position.y);
        boolean alreadyVisited = false;

        // Only add if not too close to existing points
        for (PVector pos : visitedPositions) {
            if (PVector.dist(pos, currentPos) < 50) {
                alreadyVisited = true;
                Node node = environment.createNode(pos.x, pos.y); //TODO: bandaid
                environment.setPosition(node);
                node.display();
                break;
            }
        }

        if (!alreadyVisited) {
            visitedPositions.add(currentPos);
        }

        // Redraw the fog layer
        fogLayer.beginDraw();
        fogLayer.background(fogColor, fogAlpha); // Start fresh

        // Cut out all visited areas
        fogLayer.blendMode(PApplet.REPLACE);
        fogLayer.noStroke();

        // Draw cleared circles at each visited position - USE THE FULL FOV VALUE
        for (PVector pos : visitedPositions) {
            // Use the full fieldOfView value (not divided by 2)
            float diameter = tank.fieldOfView;
            fogLayer.fill(fogColor, 0); // Fully transparent
            fogLayer.ellipse(pos.x, pos.y, diameter, diameter);
        }

        // Return to normal blend mode
        fogLayer.blendMode(PApplet.BLEND);
        fogLayer.endDraw();
    }

    void resetFog() {
        // This method intentionally does nothing now
    }

    void display() {
        if (!initialized) return;
        parent.image(fogLayer, 0, 0);
    }
}