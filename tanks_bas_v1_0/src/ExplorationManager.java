import processing.core.*;

import java.util.*;

/**
 * Manages the exploration behavior and fog of war for a tank.
 * This class handles automatic exploration, path finding, node management,
 * and fog of war visualization.
 */

class ExplorationManager {
    PApplet parent;

    // Fog-related properties
    PGraphics fogLayer;
    int fogColor;
    int fogAlpha;
    boolean initialized;
    ArrayList<PVector> visitedPositions;

    // Explorer-related properties
    ArrayList<Node> nodes;
    ArrayList<Edge> edges;
    Node currentNode;
    Node targetNode;
    Node baseNode; //to return home
    ArrayList<PVector> path;
    Random random;

    // Shared properties
    Tank tank;
    boolean autoExplore;
    float visibilityRadius;
    float minNodeDistance;
    float maxNodeDistance;
    PVector previousDirection;
    int stuckCounter;
    PVector lastPosition;
    int samePositionCounter;

    int clearedPixels;
    int totalPixels;
    float exploredPercent;
    int startPositionCounter;

    boolean testDijkstra;

    // Navigation states
    private enum NavigationState {
        EXPLORING,
        MOVING_TO_TARGET,
        BACKTRACKING,
        RETURNING_HOME
    }

    private NavigationState navState;


    /**
     * Creates a new ExplorationManager with the specified visibility radius.
     *
     * @param parent The Processing PApplet this manager belongs to
     * @param visibilityRadius The radius around the tank that is visible/explored
     */
    ExplorationManager(PApplet parent, float visibilityRadius) {
        this.parent = parent;
        this.visibilityRadius = visibilityRadius;

        // Fog initialization
        this.fogColor = parent.color(50, 50, 50);
        this.fogAlpha = 100;
        this.initialized = false;
        this.visitedPositions = new ArrayList<PVector>();

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

        this.testDijkstra = false;
    }

    /**
     * Sets the tank controlled by this exploration manager.
     * Creates a starting node at the tank's position.
     *
     * @param tank The tank to be controlled by this manager
     */

    void setTank(Tank tank) {
        this.tank = tank;

        // Create starting node at tank's position
        if (tank != null) {
            Node startNode = new Node(parent, tank.position.x, tank.position.y);
            nodes.add(startNode);
            currentNode = startNode;
            baseNode = startNode;
            lastPosition = tank.position.copy();

            // Add this position to visited positions for fog clearing
            visitedPositions.add(tank.position.copy());
        }
    }
    /**
     * Initializes the fog of war layer.
     * Creates a graphic layer for the fog and sets it to fully opaque.
     */
    void initializeFog() {
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
    /**
     * Updates the tank's position in the exploration graph.
     * Detects if the tank is stuck, adds visited positions for fog clearing,
     * and creates new nodes when in unexplored territory.
     */
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

        // Record visited position for fog clearingb
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
    /**
     * Handles a tank that appears to be stuck in one position.
     * Changes direction randomly to attempt to escape the stuck position.
     */
    void handleStuckTank() { //TODO: skapar den här problem?
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
    /**
     * Finds the closest node to a given position.
     *
     * @param position Position to find the closest node to
     * @return The closest Node, or null if no nodes exist
     */
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
    /**
     * Adds a new node at the specified coordinates to the exploration graph.
     * Attempts to connect the new node to nearby nodes if possible.
     *
     * @param x X-coordinate for the new node
     * @param y Y-coordinate for the new node
     * @return The newly created Node
     */
    Node addNode(float x, float y) { //TODO: borde användas överallt, och aldrig direkt till nodklassen
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
    /**
     * Attempts to connect a node to all visible nearby nodes.
     * A connection is made if the nodes are within range and have line of sight.
     *
     * @param node The node to connect to other nodes
     */
    void connectToVisibleNodes(Node node) {
        for (Node other : nodes) {
            if (node == other) continue;
            float distance = PVector.dist(node.position, other.position);
            if (distance <= maxNodeDistance && canSee(node.position, other.position)) {
                connectNodes(node, other, distance);
            }
        }
    }
    /**
     * Checks if there is line of sight between two positions.
     * Uses the collision system to check for obstacles.
     *
     * @param from Starting position
     * @param to Ending position
     * @return true if there is clear line of sight, false if obstructed
     */
    boolean canSee(PVector from, PVector to) { //TODO: varför 2 canSee?
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.collisions != null) {
                return game.collisions.canSee(from, to);
            }
        }
        return true;
    }
    /**
     * Creates a bidirectional connection between two nodes with a specified weight.
     *
     * @param node1 First node to connect
     * @param node2 Second node to connect
     * @param weight Weight/cost of the connection
     */
    void connectNodes(Node node1, Node node2, float weight) {
        Edge edge = new Edge(node1, node2, weight);
        edges.add(edge);
        node1.addEdge(node2, weight);
        node2.addEdge(node1, weight); // Bidirectional connection
    }
    /**
     * Checks if auto-exploration mode is currently active.
     *
     * @return true if auto-exploration is active, false otherwise
     */
    public boolean isAutoExploreActive() {
        return autoExplore;
    }
    /**
     * Toggles the auto-exploration mode on or off.
     * When enabled, the tank will automatically explore the environment.
     * When disabled, the tank will stop moving.
     */
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
    /**
     * Main navigation method that controls the tank's movement.
     * Handles different navigation states: exploring, moving to target,
     * backtracking, and returning home.
     */
    void navigation() {
        if (!autoExplore || tank == null) return;

        switch (navState) {
            case RETURNING_HOME:
                tank.navState = "ReturningHome";
                if (targetNode != null && PVector.dist(tank.position, targetNode.position) < 20) {
                    if (!path.isEmpty()) {
                        path.remove(0);
                    }
                    if (!path.isEmpty()) {
                        PVector nextPos = path.get(0);
                        Node nextNode = findClosestNode(nextPos);
                        if (nextNode != null) {
                            targetNode = nextNode;
                        }
                    }
                }

                if (targetNode != null) {
                    moveTowardTarget();
                }

                if (PVector.dist(tank.position, baseNode.position) < 10) {
                    tank.state = 0;
                    startPositionCounter++;
                    if(startPositionCounter >= 180){
                        System.out.println("should be home here");
                        navState = NavigationState.EXPLORING;
                        startPositionCounter = 0;
                    }
                }
                break;
            case EXPLORING:
                tank.navState = "Exploring";
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
                tank.navState = "MovingToTarget";
                // Move toward target node
                moveTowardTarget();
                break;

            case BACKTRACKING:
                tank.navState = "Backtracking";
                // We're stuck, backtrack to known territory
                if (currentNode != null && targetNode != null) {
                    moveTowardTarget();
                } else {
                    navState = NavigationState.EXPLORING;
                }
                break;
        }
    }
    /**
     * Moves the tank toward the current target node.
     * Sets the tank's state (direction) based on the direction to the target.
     */
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
        if (PVector.dist(tank.position, targetNode.position) < 50 && navState != NavigationState.RETURNING_HOME) {
            targetNode.markVisited();
            navState = NavigationState.EXPLORING;
            targetNode = null;
        }
    }
    /**
     * Selects the next target node for exploration.
     * Prioritizes unvisited nodes that are visible and not in the home base.
     *
     * @return The selected node for exploration, or null if no suitable node was found
     */
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
    /**
     * Expands the exploration graph using Rapidly-exploring Random Tree (RRT) algorithm.
     * Creates new nodes in unexplored areas to guide exploration when no valid targets exist.
     */
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
            //Node nearest = findClosestNode(randomPoint);
            //if (nearest == null) continue;

            //find node closest to tank
            Node nearest = findClosestNode(tank.position);
            if(nearest == null) continue;

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
    /**
     * Checks if a position is valid for creating a new node.
     * Validates that the position is not in a home base, not too close to existing nodes,
     * and not colliding with obstacles.
     *
     * @param pos Position to validate
     * @return true if the position is valid for a new node, false otherwise
     */
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
                    if(lineIntersectsTree(tank.position, pos, tree.position, tree.radius)){
                        return false;
                    }
                    if (tree != null) {
                        float dist = PVector.dist(pos, tree.position);
                        if (dist < tree.radius + 60) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
    /**
     * Checks if a line between two points intersects with a tree.
     * Delegates to the Collisions class if available, otherwise performs the check directly.
     *
     * @param start Starting point of the line
     * @param end Ending point of the line
     * @param center Center of the tree
     * @param radius Radius of the tree
     * @return true if the line intersects with the tree, false otherwise
     */
    boolean lineIntersectsTree(PVector start, PVector end, PVector center, float radius) {
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.collisions != null) {
                return game.collisions.lineIntersectsTree(start, end, center, radius);
            }
        }

        PVector d = PVector.sub(end, start);
        PVector f = PVector.sub(start, center);

        float a = d.dot(d);
        float b = 2 * f.dot(d);
        float c = f.dot(f) - radius * radius;

        float discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return false; // No intersection
        } else {
            discriminant = (float) Math.sqrt(discriminant);
            float t1 = (-b - discriminant) / (2 * a);
            float t2 = (-b + discriminant) / (2 * a);
            return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
        }
    }
    /**
     * Checks if a position is inside a home base.
     * Delegates to the Collisions class if available.
     *
     * @param position Position to check
     * @return true if the position is in a home base, false otherwise
     */
    boolean isInHomeBase(PVector position) {
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.collisions != null) {
                return game.collisions.isInHomeBase(position);
            }
        }
        return false;
    }
    /**
     * Updates the fog of war based on visited positions.
     * Calculates the percentage of the map that has been explored.
     */
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
    /**
     * Displays the exploration graph and fog of war.
     * Shows nodes, connections, and the exploration percentage.
     */
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
    /**
     * Handles a collision with the map border.
     * Adjusts navigation strategy and creates a new node near the border.
     */
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

        if (isValidNodePosition(tank.position)) {
            addNode(boundaryX,boundaryY);
        }
    }
    /**
     * Initiates the return home sequence.
     * Calculates a path back to the home base node using A* pathfinding.
     */
    void returnHome() {
        navState = NavigationState.RETURNING_HOME;

        Node closestNode = findClosestNode(tank.position);

        if (closestNode == null || PVector.dist(closestNode.position, tank.position) > minNodeDistance) {
            closestNode = addNode(tank.position.x, tank.position.y);
        }
        currentNode = closestNode;


        ArrayList<Node> pathingHome;

        if(testDijkstra)
            pathingHome = dijkstra(closestNode, baseNode);
        else
            pathingHome = aStar(closestNode, baseNode);

        if (!pathingHome.isEmpty()) {
            path.clear();
        }

        for (Node node : pathingHome) {
            path.add(node.position.copy());
        }

        if (pathingHome.size() > 1) {
            targetNode = pathingHome.get(0); //TODO: bandaid kanske ändra case till 1?
        } else {
            targetNode = baseNode;
        }
    }

    ArrayList<Node> dijkstra(Node start, Node goal){
        Set<Node> visitedNodes = new HashSet<>();

        PriorityQueue<Node> openSet = new PriorityQueue<>((a, b) ->
                Float.compare(a.fScore, b.fScore));

        for (Node node : nodes) {
            node.gScore = Float.MAX_VALUE;
        }

        start.gScore = 0f;

        HashMap<Node,Node> traveledFrom = new HashMap<>();

        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.equals(goal)) {
                return reconstructPAth(traveledFrom,current);
            }

            visitedNodes.add(current);

            for (Edge edge : current.edges) {
                if (!edge.traversable) continue;

                Node neighbor = edge.destination;
                if (visitedNodes.contains(neighbor)) continue;

                float currentGScore = current.gScore + edge.weight;

                if (currentGScore < neighbor.gScore) {
                    traveledFrom.put(neighbor, current);

                    neighbor.gScore = current.gScore;

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return new ArrayList<>();


    }
    /**
     * Implements the A* pathfinding algorithm to find a path between two nodes.
     *
     * @param start Starting node
     * @param goal Destination node
     * @return List of nodes representing the path from start to goal
     */
    ArrayList<Node> aStar(Node start, Node goal) {

        Set<Node> visitedNodes = new HashSet<>();

        PriorityQueue<Node> openSet = new PriorityQueue<>((a, b) ->
                Float.compare(a.fScore, b.fScore));

        for (Node node : nodes) {
            node.gScore = Float.MAX_VALUE;
            node.fScore = Float.MAX_VALUE;
        }

        start.gScore = 0f;
        start.fScore = heuristicCost(start, goal);

        HashMap<Node,Node> traveledFrom = new HashMap<>();

        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.equals(goal)) {
                return reconstructPAth(traveledFrom,current);
            }

            visitedNodes.add(current);

            for (Edge edge : current.edges) {
                if (!edge.traversable) continue;

                Node neighbor = edge.destination;
                if (visitedNodes.contains(neighbor)) continue;

                float currentGScore = current.gScore + edge.weight;

                if (currentGScore < neighbor.gScore) {
                    traveledFrom.put(neighbor, current);

                    neighbor.gScore = current.gScore;
                    neighbor.fScore = neighbor.gScore + heuristicCost(neighbor, goal);

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return new ArrayList<>();
    }
    /**
     * Reconstructs the path from the A* search results.
     *
     * @param traveledFrom Map of nodes to their predecessor in the path
     * @param current Current node to reconstruct the path from
     * @return List of nodes representing the path
     */
    private ArrayList<Node> reconstructPAth(HashMap<Node,Node> traveledFrom, Node current) {
        ArrayList<Node> path = new ArrayList<>();

        path.add(current);

        while (traveledFrom.containsKey(current)) {
            current = traveledFrom.get(current);
            path.add(0, current);
        }
        return path;
    }
    /**
     * Calculates the heuristic cost between two nodes for A* pathfinding.
     * Uses Euclidean distance as the heuristic.
     *
     * @param a First node
     * @param b Second node
     * @return Estimated cost between nodes
     */
    private Float heuristicCost(Node a, Node b) {
        return PVector.dist(a.position, b.position);
    }
    /**
     * Checks if the tank is currently in the process of returning home.
     *
     * @return true if the tank is returning home, false otherwise
     */
    public boolean isReturningHome() {
        return navState == NavigationState.RETURNING_HOME;
    }
    /**
     * Test method to trigger the return home behavior.
     */
    void testReturnHome() {
        returnHome();
    }
}