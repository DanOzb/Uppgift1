// Create a new Fog.java file
import processing.core.*;

class Fog {
    private PApplet parent;
    PGraphics fogLayer;
    int fogColor;
    int fogAlpha;

    Fog(PApplet parent) {
        this.parent = parent;
        fogLayer = parent.createGraphics(parent.width, parent.height);
        fogColor = parent.color(50, 50, 50);  // Dark gray fog
        fogAlpha = 180;  // Semi-transparent fog

        // Initialize with full fog
        resetFog();
    }

    void resetFog() {
        fogLayer.beginDraw();
        fogLayer.background(fogColor, fogAlpha);
        fogLayer.endDraw();
    }

    void clearAroundTank(Tank tank) {
        float fovRadius = tank.fieldOfView / 2;

        fogLayer.beginDraw();
        // Use REPLACE blending mode to clear fog in FOV area
        fogLayer.blendMode(PApplet.REPLACE);

        // Create gradient for fog dissipation around FOV
        for (int i = 0; i < 20; i++) {
            float radius = fovRadius + i;
            float alpha = i * (fogAlpha / 20);  // Gradually increase alpha

            fogLayer.noStroke();
            fogLayer.fill(fogColor, alpha);
            fogLayer.ellipse(tank.position.x, tank.position.y, radius * 2, radius * 2);
        }

        // Clear the inner FOV area completely
        fogLayer.fill(fogColor, 0);  // Transparent
        fogLayer.ellipse(tank.position.x, tank.position.y, fovRadius * 2, fovRadius * 2);

        // Reset blend mode
        fogLayer.blendMode(PApplet.BLEND);
        fogLayer.endDraw();
    }

    void display() {
        parent.image(fogLayer, 0, 0);
    }
}