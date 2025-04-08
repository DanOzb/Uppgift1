// Create a new Fog.java file
import processing.core.*;
import java.util.ArrayList;

class Fog {
    private PApplet parent;
    PGraphics fogLayer;
    int fogColor;
    int fogAlpha;
    boolean initialized;

    // Track cleared areas
    ArrayList<PVector> clearedAreas;
    float clearRadius;

    Fog(PApplet parent) {
        this.parent = parent;
        this.fogColor = parent.color(50, 50, 50);  // Dark gray fog
        this.fogAlpha = 100;  // More transparent fog
        this.initialized = false;
        this.clearedAreas = new ArrayList<PVector>();
        this.clearRadius = 100;  // Radius of cleared area around each point
    }

    void initialize() {
        if (parent.width > 0 && parent.height > 0) {
            fogLayer = parent.createGraphics(parent.width, parent.height);
            resetFog();
            initialized = true;
        }
    }

    void resetFog() {
        if (!initialized) return;

        fogLayer.beginDraw();
        fogLayer.background(fogColor, fogAlpha);
        fogLayer.endDraw();

        // Redraw all cleared areas
        for (PVector pos : clearedAreas) {
            clearArea(pos.x, pos.y);
        }
    }

    void clearAroundTank(Tank tank) {
        if (!initialized) return;

        // Add current tank position to cleared areas if not already there
        boolean positionExists = false;
        for (PVector pos : clearedAreas) {
            if (pos.dist(tank.position) < clearRadius/2) {
                positionExists = true;
                break;
            }
        }

        if (!positionExists) {
            clearedAreas.add(new PVector(tank.position.x, tank.position.y));
        }

        // Clear area around tank
        clearArea(tank.position.x, tank.position.y);
    }

    void clearArea(float x, float y) {
        fogLayer.beginDraw();
        // Use REPLACE mode to completely remove fog
        fogLayer.blendMode(PApplet.REPLACE);

        // Create gradient for fog removal
        for (int i = 0; i < 15; i++) {
            float radius = clearRadius + (i * 4);  // Wider spacing between rings
            float alpha = parent.map(i, 0, 15, 0, fogAlpha);  // Gradient from transparent to fog

            fogLayer.noStroke();
            fogLayer.fill(fogColor, alpha);
            fogLayer.ellipse(x, y, radius * 2, radius * 2);
        }

        // Clear the inner area completely
        fogLayer.fill(fogColor, 0);  // Completely transparent
        fogLayer.ellipse(x, y, clearRadius * 2, clearRadius * 2);

        // Reset blend mode
        fogLayer.blendMode(PApplet.BLEND);
        fogLayer.endDraw();
    }

    void display() {
        if (!initialized) return;
        parent.image(fogLayer, 0, 0);
    }
}