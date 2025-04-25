import processing.core.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;
import java.util.Random;

class ExplorationManager {
    private PApplet parent;

    // Fog-related properties
    private PGraphics fogLayer;
    private int fogColor;
    private int fogAlpha;
    private boolean initialized;
    private ArrayList<PVector> visitedPositions;

    // Explorer-related properties
    private ArrayList<Node> nodes;
    private ArrayList<Edge> edges;
    private Node currentNode;
    private Node targetNode;
    private ArrayList<PVector> path;
    private Random random;

    // Shared properties
    private Tank tank;
    private boolean autoExplore;
    private float visibilityRadius;
    private float minNodeDistance;
    private float maxNodeDistance;
    private PVector previousDirection;
    private int stuckCounter;
    private PVector lastPosition;
    private int samePositionCounter;

    int clearedPixels;
    int totalPixels;
    float exploredPercent;

    // Navigation states
    private enum NavigationState {
        EXPLORING,
        MOVING_TO_TARGET,
        BACKTRACKING
    }

    private NavigationState navState;

    ExplorationManager(PApplet parent, float visibilityRadius) {
        this.parent = parent;
        this.visibilityRadius = visibilityRadius;

        // Fog initialization
        this.fogColor = parent.color(50, 50, 50);
        this.fogAlpha = 100;
        this.initialized = false;
        this.visitedPositions = new ArrayList<PVector>();

        // Explorer initialization
        this.nodes = new ArrayList<Node>();
        this.edges = new ArrayList<Edge>();
        this.path = new ArrayList<PVector>();
        this.random = new Random();
        this.autoExplore = false;
        this.minNodeDistance = 50;
        this.maxNodeDistance = 150;
        this.previousDirection = new PVector(0, 0);
        this.stuckCounter = 0;
        this.samePositionCounter = 0;
        this.navState = NavigationState.EXPLORING;
    }

    void setTank(Tank tank) {
        this.tank = tank;

        // Create starting node at tank's position
        if (tank != null) {
            Node startNode = new Node(parent, tank.position.x, tank.position.y);
            nodes.add(startNode);
            currentNode = startNode;
            lastPosition = tank.position.copy();

            // Add this position to visited positions for fog clearing
            visitedPositions.add(tank.position.copy());
        }
    }

    void initialize() {
        if (parent.width > 0 && parent.height > 0) {
            fogLayer = parent.createGraphics(parent.width, parent.height);
            fogLayer.beginDraw();
            fogLayer.background(fogColor, fogAlpha); // Start with full fog
            fogLayer.endDraw();
            initialized = true;
            fogLayer.loadPixels();
            totalPixels = fogLayer.width * fogLayer.height;
            clearedPixels = 0;
        }
    }

    void updateTankPosition() {
        if (tank == null) return;

        // Check if tank has moved since last update
        if (PVector.dist(tank.position, lastPosition) < 1.0f) {
            samePositionCounter++;
            if (samePositionCounter > 60) {  // If stuck for about 1 second (60 frames)
                handleStuckTank();
                samePositionCounter = 0;
            }
        } else {
            samePositionCounter = 0;
            lastPosition = tank.position.copy();
        }

        // Record visited position for fog clearing
        PVector currentPos = tank.position.copy();
        boolean alreadyVisited = false;

        for (PVector pos : visitedPositions) {
            if (PVector.dist(pos, currentPos) < 30) {
                alreadyVisited = true;
                break;
            }
        }

        if (!alreadyVisited) {
            visitedPositions.add(currentPos);
            updateFog();
        }

        // Update current node to closest node if very close
        Node closestNode = findClosestNode(tank.position);
        if (closestNode != null && PVector.dist(closestNode.position, tank.position) < 20) {
            currentNode = closestNode;
        }

        // If we're in unexplored territory, create a new node
        if (findClosestNode(tank.position) == null ||
                PVector.dist(findClosestNode(tank.position).position, tank.position) > maxNodeDistance/2) {
            addNode(tank.position.x, tank.position.y);
        }
    }

    void handleStuckTank() {
        parent.println("Tank appears stuck - changing direction");

        // Generate a random direction to attempt to escape
        float randomAngle = random.nextFloat() * PApplet.TWO_PI;
        PVector escapeDirection = new PVector(PApplet.cos(randomAngle), PApplet.sin(randomAngle));

        // Set tank state based on escape direction
        if (Math.abs(escapeDirection.x) > Math.abs(escapeDirection.y)) {
            tank.state = escapeDirection.x > 0 ? 1 : 2; // Right or Left
        } else {
            tank.state = escapeDirection.y > 0 ? 3 : 4; // Down or Up
        }

        // Mark the current target as potentially unreachable
        if (targetNode != null) {
            navState = NavigationState.EXPLORING; // Reset to exploration mode
            targetNode = null;
        }
    }

    Node findClosestNode(PVector position) {
        if (nodes.isEmpty()) return null;

        Node closest = null;
        float minDist = Float.MAX_VALUE;

        for (Node node : nodes) {
            float dist = PVector.dist(node.position, position);
            if (dist < minDist) {
                minDist = dist;
                closest = node;
            }
        }

        return closest;
    }

    Node addNode(float x, float y) {
        Node newNode = new Node(parent, x, y);
        nodes.add(newNode);

        // Try to connect to nearby nodes
        connectToVisibleNodes(newNode);

        // If this is our current position, update current node
        if (PVector.dist(newNode.position, tank.position) < 30) {
            currentNode = newNode;
        }

        return newNode;
    }

    void connectToVisibleNodes(Node node) {
        for (Node other : nodes) {
            if (node == other) continue;

            float distance = PVector.dist(node.position, other.position);
            if (distance <= maxNodeDistance && canSee(node.position, other.position)) {
                connectNodes(node, other, distance);
            }
        }
    }

    boolean canSee(PVector from, PVector to) {
        // Check if line between points intersects with any obstacles
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.allTrees != null) {
                for (Tree tree : game.allTrees) {
                    if (tree != null) {
                        // Line-circle intersection test
                        PVector line = PVector.sub(to, from);
                        PVector treeToFrom = PVector.sub(from, tree.position);

                        float a = line.dot(line);
                        float b = 2 * treeToFrom.dot(line);
                        float c = treeToFrom.dot(treeToFrom) - (tree.radius + 10) * (tree.radius + 10);

                        float discriminant = b * b - 4 * a * c;

                        if (discriminant >= 0) {
                            // Line intersects circle
                            float t = (-b - PApplet.sqrt(discriminant)) / (2 * a);
                            if (t >= 0 && t <= 1) {
                                return false; // Intersection within segment
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    void connectNodes(Node node1, Node node2, float weight) {
        Edge edge = new Edge(node1, node2, weight);
        edges.add(edge);
        node1.addEdge(node2, weight);
        node2.addEdge(node1, weight); // Bidirectional connection
    }

    public boolean isAutoExploreActive() {
        return autoExplore;
    }

    void toggleAutoExplore() {
        autoExplore = !autoExplore;
        if (autoExplore) {
            parent.println("Auto-exploration enabled");
            navState = NavigationState.EXPLORING;
        } else {
            parent.println("Auto-exploration disabled");
            tank.state = 0; // Stop the tank
        }
    }

    void exploreDFS() {
        if (!autoExplore || tank == null) return;

        switch (navState) {
            case EXPLORING:
                if (targetNode == null || PVector.dist(tank.position, targetNode.position) < 20) {
                    // Find new exploration target
                    targetNode = selectExplorationTarget();
                    if (targetNode != null) {
                        navState = NavigationState.MOVING_TO_TARGET;
                    } else {
                        // No good targets, try RRT expansion
                        expandRRT();
                    }
                }
                break;

            case MOVING_TO_TARGET:
                // Move toward target node
                moveTowardTarget();
                break;

            case BACKTRACKING:
                // We're stuck, backtrack to known territory
                if (currentNode != null && targetNode != null) {
                    moveTowardTarget();
                } else {
                    navState = NavigationState.EXPLORING;
                }
                break;
        }
    }

    void moveTowardTarget() {
        if (targetNode == null) return;

        // Calculate direction to target
        PVector direction = PVector.sub(targetNode.position, tank.position);
        direction.normalize();

        // Determine the tank's state based on direction
        float dx = direction.x;
        float dy = direction.y;

        // Check for diagonal movement
        if (Math.abs(dx) > 0.3f && Math.abs(dy) > 0.3f) {
            // Diagonal movement
            if (dx > 0 && dy > 0) {
                tank.state = 5; // Right + Down
            } else if (dx > 0 && dy < 0) {
                tank.state = 6; // Right + Up
            } else if (dx < 0 && dy > 0) {
                tank.state = 7; // Left + Down
            } else {
                tank.state = 8; // Left + Up
            }
        } else {
            // Cardinal movement
            if (Math.abs(dx) > Math.abs(dy)) {
                tank.state = dx > 0 ? 1 : 2; // Right or Left
            } else {
                tank.state = dy > 0 ? 3 : 4; // Down or Up
            }
        }

        // Store the direction for "stuck" detection
        previousDirection = direction.copy();

        // Check if we've reached the target
        if (PVector.dist(tank.position, targetNode.position) < 50) {
            targetNode.markVisited();
            navState = NavigationState.EXPLORING;
            targetNode = null;
        }
    }

    Node selectExplorationTarget() {
        // First, try to find an unvisited node nearby
        ArrayList<Node> candidates = new ArrayList<Node>();

        for (Node node : nodes) {
            if (!node.visited &&
                    PVector.dist(node.position, tank.position) < maxNodeDistance * 3 &&
                    canSee(tank.position, node.position) &&
                    !isInHomeBase(node.position)) {  // Add this check
                candidates.add(node);
            }
        }

        // If we have candidates, pick the closest one
        if (!candidates.isEmpty()) {
            Node closest = null;
            float minDist = Float.MAX_VALUE;

            for (Node node : candidates) {
                float dist = PVector.dist(node.position, tank.position);
                if (dist < minDist) {
                    minDist = dist;
                    closest = node;
                }
            }

            return closest;
        }

        // If no unvisited nodes nearby, try generating a new target in unexplored space
        return null;
    }

    void expandRRT() {
        // Try several times to find a valid position
        for (int attempts = 0; attempts < 10; attempts++) {
            // Generate a random point in the map
            PVector randomPoint = new PVector(
                    random.nextFloat() * parent.width,
                    random.nextFloat() * parent.height
            );

            // Skip if in home base
            if (isInHomeBase(randomPoint)) {
                continue;
            }

            // Find the closest existing node to this random point
            Node nearest = findClosestNode(randomPoint);
            if (nearest == null) continue;

            // Create a new point in the direction of the random point
            PVector direction = PVector.sub(randomPoint, nearest.position);
            direction.normalize();
            direction.mult(maxNodeDistance);

            PVector newPos = PVector.add(nearest.position, direction);

            // Ensure new point is within bounds
            newPos.x = PApplet.constrain(newPos.x, 20, parent.width - 20);
            newPos.y = PApplet.constrain(newPos.y, 20, parent.height - 20);

            // Check if the new position is valid
            if (isValidNodePosition(newPos)) {
                Node newNode = addNode(newPos.x, newPos.y);
                targetNode = newNode;
                navState = NavigationState.MOVING_TO_TARGET;
                return;
            }
        }
    }

    boolean isValidNodePosition(PVector pos) {
        // Check if in home base - skip these areas
        if (isInHomeBase(pos)) {
            return false;
        }

        // Check distance from existing nodes
        for (Node node : nodes) {
            if (PVector.dist(node.position, pos) < minNodeDistance) {
                return false;
            }
        }

        // Check for obstacle collision
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.allTrees != null) {
                for (Tree tree : game.allTrees) {
                    if (tree != null) {
                        float dist = PVector.dist(pos, tree.position);
                        if (dist < tree.radius + 30) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    boolean isInHomeBase(PVector position) {
        // Check if position is in Team 0 base (red)
        if (position.x <= 150 && position.y <= 350) {
            return true;
        }

        // Check if position is in Team 1 base (blue)
        if (position.x >= parent.width - 151 && position.y >= parent.height - 351) {
            return true;
        }

        return false;
    }

    void updateFog() {
        if (!initialized) return;

        // Redraw the fog layer
        fogLayer.beginDraw();
        fogLayer.background(fogColor, fogAlpha); // Start fresh

        // Cut out all visited areas
        fogLayer.blendMode(PApplet.REPLACE);
        fogLayer.noStroke();

        // Draw cleared circles at each visited position
        for (PVector pos : visitedPositions) {
            float diameter = tank != null ? tank.fieldOfView : 100.0f;
            fogLayer.fill(fogColor, 0); // Fully transparent
            fogLayer.ellipse(pos.x, pos.y, diameter, diameter);
        }
        clearedPixels = 0;

        for (int i = 0; i < totalPixels; i++) {
            int c = fogLayer.pixels[i];
            float alpha = parent.alpha(c);
            if (alpha == 0) {  // threshold for "cleared"
                clearedPixels++;
            }
        }

        // Return to normal blend mode
        fogLayer.blendMode(PApplet.BLEND);
        fogLayer.endDraw();
        exploredPercent = (clearedPixels / (float) totalPixels) * 100;
    }

    void display() {
        // Display all nodes and connections
        for (Node node : nodes) {
            // Display based on visit status
            if (node == currentNode) {
                parent.fill(0, 200, 0); // Current node in green
            } else if (node == targetNode) {
                parent.fill(200, 200, 0); // Target node in yellow
            } else if (node.visited) {
                parent.fill(150, 150, 200, 150); // Visited nodes in blue, semi-transparent
            } else {
                parent.fill(200, 150, 150, 150); // Unvisited nodes in red, semi-transparent
            }

            parent.noStroke();
            parent.ellipse(node.position.x, node.position.y, 15, 15);
        }

        // Display connections between nodes
        parent.stroke(100, 100, 200, 100);
        parent.strokeWeight(1);
        for (Edge edge : edges) {
            parent.line(
                    edge.source.position.x, edge.source.position.y,
                    edge.destination.position.x, edge.destination.position.y
            );
        }

        // Reset stroke
        parent.strokeWeight(1);

        // Display fog layer
        if (initialized) {
            parent.image(fogLayer, 0, 0);
        }
        parent.fill(0);
        parent.text(parent.nf(exploredPercent, 1, 2) + "% explored", 20, 20);
    }

    void handleBorderCollision() {
        parent.println("Border collision detected - adjusting navigation");

        // If we were moving to a target, it might be unreachable
        if (navState == NavigationState.MOVING_TO_TARGET) {
            // Mark current target as potentially unreachable or problematic
            if (targetNode != null) {
                // Increment a counter or mark in some way - we'll use a direct field for simplicity
                stuckCounter++;

                // If we've hit too many borders while trying to reach this target, pick a new one
                if (stuckCounter > 3) {
                    parent.println("Giving up on current target after multiple collisions");
                    navState = NavigationState.EXPLORING;
                    targetNode = null;
                    stuckCounter = 0;
                } else {
                    // Try to find a different path or approach
                    // Generate a random direction to escape the border
                    float randomAngle = random.nextFloat() * PApplet.TWO_PI;
                    PVector escapeDirection = new PVector(PApplet.cos(randomAngle), PApplet.sin(randomAngle));

                    // Set tank state based on escape direction (away from border)
                    if (Math.abs(escapeDirection.x) > Math.abs(escapeDirection.y)) {
                        tank.state = escapeDirection.x > 0 ? 1 : 2; // Right or Left
                    } else {
                        tank.state = escapeDirection.y > 0 ? 3 : 4; // Down or Up
                    }
                }
            }
        } else {
            // If we're just exploring, change direction to move away from border
            // Determine which border was hit by looking at tank position
            PVector center = new PVector(parent.width/2, parent.height/2);
            PVector directionToCenter = PVector.sub(center, tank.position);
            directionToCenter.normalize();

            // Set tank state to move toward center of map
            if (Math.abs(directionToCenter.x) > Math.abs(directionToCenter.y)) {
                tank.state = directionToCenter.x > 0 ? 1 : 2; // Right or Left
            } else {
                tank.state = directionToCenter.y > 0 ? 3 : 4; // Down or Up
            }
        }

        // Add the current position to nodes if it doesn't exist
        // This helps build a map of the boundary
        float padding = 30; // Stay a bit away from the actual border
        float boundaryX = PApplet.constrain(tank.position.x, padding, parent.width - padding);
        float boundaryY = PApplet.constrain(tank.position.y, padding, parent.height - padding);

        Node boundaryNode = new Node(parent, boundaryX, boundaryY);

        // Only add if not too close to existing nodes
        boolean tooClose = false;
        for (Node node : nodes) {
            if (PVector.dist(node.position, boundaryNode.position) < minNodeDistance) {
                tooClose = true;
                break;
            }
        }

        if (!tooClose) {
            nodes.add(boundaryNode);
            connectToVisibleNodes(boundaryNode);
        }
    }
}