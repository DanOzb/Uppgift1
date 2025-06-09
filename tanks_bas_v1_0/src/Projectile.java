import processing.core.*;
import java.util.*;

/**
 * Represents a projectile fired by a tank.
 * Handles movement, collision detection, and explosion effects.
 */
class Projectile {
    PApplet parent;
    PVector position;
    PVector velocity;
    float speed = 5.0f;
    float radius = 8.0f;

    Tank owner;
    boolean active = false;
    boolean exploding = false;

    int explosionDuration = 30; // frames
    int explosionTimer = 0;
    ArrayList<ExplosionParticle> particles;

    /**
     * Constructor for creating a projectile.
     * @param parent The Processing PApplet instance
     * @param owner The tank that will fire this projectile
     */
    Projectile(PApplet parent, Tank owner) {
        this.parent = parent;
        this.owner = owner;
        this.position = new PVector();
        this.velocity = new PVector();
        this.particles = new ArrayList<ExplosionParticle>();
    }

    /**
     * Fires the projectile from a starting position in a specified direction.
     * @param startPos The initial position of the projectile
     * @param direction The direction vector for projectile movement
     */
    void fire(PVector startPos, PVector direction) {
        this.position = startPos.copy();
        this.velocity = direction.copy().normalize().mult(speed);
        this.active = true;
        this.exploding = false;
    }

    /**
     * Updates projectile position and handles explosion animation.
     * Moves the projectile and manages its lifecycle.
     */
    void update() {
        if (!active) return;

        if (exploding) {
            // Update explosion animation
            explosionTimer++;

            // Update particles
            for (int i = particles.size() - 1; i >= 0; i--) {
                ExplosionParticle p = particles.get(i);
                p.update();
                if (p.isDead()) {
                    particles.remove(i);
                }
            }

            // End explosion when timer expires
            if (explosionTimer >= explosionDuration && particles.isEmpty()) {
                active = false;
                exploding = false;
            }
        } else {
            // Move the projectile
            position.add(velocity);

            // Check if out of bounds
            if (position.x < 0 || position.x > parent.width ||
                    position.y < 0 || position.y > parent.height) {
                explode();
            }
        }
    }

    /**
     * Initiates explosion animation and creates particle effects.
     * Deactivates the projectile and starts visual explosion.
     */
    void explode() {
        exploding = true;
        explosionTimer = 0;

        // Create explosion particles
        for (int i = 0; i < 15; i++) {
            particles.add(new ExplosionParticle(parent, position.copy()));
        }
    }

    /**
     * Checks for collision between this projectile and a tank.
     * @param tank The tank to check collision against
     */
    void checkTankCollision(Tank tank) {
        if (!active || exploding || tank == owner || tank.isDestroyed) return;

        float distance = PVector.dist(position, tank.position);
        if (distance < (radius + tank.diameter/2)) {
            explode();
            tank.handleHit();
            owner.registerHit();
        }
    }
    /**
     * Checks for collision between this projectile and a tree.
     *
     * @param tree The tree to check collision against
     */
    void checkTreeCollision(Tree tree) {
        if (!active || exploding) return;

        float distance = PVector.dist(position, tree.position);
        if (distance < (radius + tree.radius)) {
            explode();
        }
    }
    /**
     * Renders the projectile or explosion animation.
     */
    void display() {
        if (!active) return;

        if (exploding) {
            // Draw explosion particles
            for (ExplosionParticle p : particles) {
                p.display();
            }
        } else {
            // Draw projectile
            parent.pushMatrix();
            parent.translate(position.x, position.y);
            parent.fill(owner.col);
            parent.noStroke();
            parent.ellipse(0, 0, radius * 2, radius * 2);
            parent.popMatrix();
        }
    }
}

/**
 * Particle for explosion effects.
 */
class ExplosionParticle {
    PApplet parent;
    PVector position;
    PVector velocity;
    float size;
    int opacity = 255;
    int fadeRate = 10;
    /**
     * Constructor for explosion particle effect.
     * @param parent The Processing PApplet instance
     * @param pos Initial position of the particle
     */
    ExplosionParticle(PApplet parent, PVector pos) {
        this.parent = parent;
        this.position = pos.copy();

        // Random velocity in all directions
        float angle = parent.random(PApplet.TWO_PI);
        float magnitude = parent.random(0.5f, 2.5f);
        this.velocity = new PVector(PApplet.cos(angle) * magnitude, PApplet.sin(angle) * magnitude);

        this.size = parent.random(5, 15);
    }
    /**
     * Updates particle position, velocity, and opacity over time.
     */
    void update() {
        position.add(velocity);

        // Slow down over time
        velocity.mult(0.95f);

        // Fade out
        opacity -= fadeRate;
    }
    /**
     * Renders the particle with current position and opacity.
     */
    void display() {
        parent.noStroke();
        parent.fill(255, 200, 0, opacity); // Orange/yellow explosion
        parent.ellipse(position.x, position.y, size, size);
    }
    /**
     * Checks if the particle has faded out completely.
     * @return true if the particle should be removed
     */
    boolean isDead() {
        return opacity <= 0;
    }
}