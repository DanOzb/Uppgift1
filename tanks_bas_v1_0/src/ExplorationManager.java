import processing.core.*;
import java.util.*;

/**
 * Manages the exploration behavior and fog of war for multiple tanks.
 * This class handles automatic exploration, path finding, node management,
 * and fog of war visualization.
 */
class ExplorationManager {
    PApplet parent;
    Thread threadInstane;


    PGraphics fogLayer;
    int fogColor;
    int fogAlpha;
    boolean initialized;
    ArrayList<PVector> visitedPositions;

    ArrayList<Node> nodes;
    ArrayList<Edge> edges;
    HashMap<Tank, Node> currentNodes; // Track current node for each tank
    HashMap<Tank, Node> targetNodes;  // Track target node for each tank
    HashMap<Tank, Node> baseNodes;    // Each tank has its own base node
    HashMap<Tank, ArrayList<PVector>> paths; // Track path for each tank
    Random random;

    List<Tank> tanks;
    boolean autoExplore;
    float visibilityRadius;
    float minNodeDistance;
    float maxNodeDistance;
    HashMap<Tank, PVector> previousDirections;
    HashMap<Tank, Integer> stuckCounters;
    HashMap<Tank, PVector> lastPositions;
    HashMap<Tank, Integer> samePositionCounters;
    HashMap<Tank, NavigationState> navStates;
    HashMap<Tank, Long> lastTargetUpdateTime;
    HashMap<Tank, Long> homeArrivalTime; // Track when each tank arrived home
    Long allTanksHomeTime;
    int tankHomeCounter;

    int clearedPixels;
    int totalPixels;
    float exploredPercent;
    HashMap<Tank, Integer> startPositionCounters;

    boolean testDijkstra;
    boolean enemyDetected = false;
    PVector detectedEnemyBase = null;

    ArrayList<PVector> pathToEnemyBase;

    enum NavigationState {
        EXPLORING,
        MOVING_TO_TARGET,
        RETURNING_HOME,
        WAITING_AT_HOME,
        POSITION_AROUND_ENEMY_BASE
    }

    /**
     * Creates a new ExplorationManager with the specified visibility radius.
     *
     * @param parent The Processing PApplet this manager belongs to
     * @param visibilityRadius The radius around the tank that is visible/explored
     */
    ExplorationManager(PApplet parent, float visibilityRadius) {
        this.parent = parent;
        this.visibilityRadius = visibilityRadius;

        this.fogColor = parent.color(50, 50, 50);
        this.fogAlpha = 100;
        this.initialized = false;
        this.visitedPositions = new ArrayList<PVector>();

        this.nodes = new ArrayList<Node>();
        this.edges = new ArrayList<Edge>();
        this.tanks = new ArrayList<Tank>();
        this.currentNodes = new HashMap<Tank, Node>();
        this.targetNodes = new HashMap<Tank, Node>();
        this.baseNodes = new HashMap<Tank, Node>();  // New: individual base nodes
        this.paths = new HashMap<Tank, ArrayList<PVector>>();
        this.random = new Random();
        this.autoExplore = false;
        this.minNodeDistance = 50;
        this.maxNodeDistance = 150;

        this.previousDirections = new HashMap<Tank, PVector>();
        this.stuckCounters = new HashMap<Tank, Integer>();
        this.lastPositions = new HashMap<Tank, PVector>();
        this.samePositionCounters = new HashMap<Tank, Integer>();
        this.navStates = new HashMap<Tank, NavigationState>();
        this.startPositionCounters = new HashMap<Tank, Integer>();
        this.lastTargetUpdateTime = new HashMap<Tank, Long>();
        this.homeArrivalTime = new HashMap<Tank, Long>();
        this.allTanksHomeTime = null;

        this.testDijkstra = false;

        threadInstane = new Thread(() -> {
            while(true) {
                if(areAllTanksHome()){
                    try {
                        System.out.println("passed");
                        Thread.sleep(3000);

                    }catch (InterruptedException e){
                        e.printStackTrace();
                    } finally {
                        for (Tank tank : tanks) {
                            navStates.put(tank, NavigationState.POSITION_AROUND_ENEMY_BASE);
                        }
                        if(!isAutoExploreActive()) {
                            toggleAutoExplore();
                        }
                    }
                }
            }
        });
        initializeFog();

        for(Tank tank : tanks) {
            navStates.put(tank, NavigationState.EXPLORING);
        }
    }

    /**
     * Adds a tank to be managed by this exploration manager.
     * Creates a starting node at the tank's position if needed and assigns it as the tank's base node.
     *
     * @param tank The tank to be added to this manager
     */
    void addTank(Tank tank) {
        if (tank == null || tanks.contains(tank)) {
            return;
        }

        tanks.add(tank);

        // Initialize all the maps for this tank
        previousDirections.put(tank, new PVector(0, 0));
        stuckCounters.put(tank, 0);
        lastPositions.put(tank, tank.position.copy());
        samePositionCounters.put(tank, 0);
        navStates.put(tank, NavigationState.EXPLORING);
        startPositionCounters.put(tank, 0);
        paths.put(tank, new ArrayList<PVector>());
        homeArrivalTime.put(tank, 0L);

        // Create or find a base node for this tank
        Node tankBaseNode = null;
        for (Node existingNode : nodes) {
            if (PVector.dist(existingNode.position, tank.position) < minNodeDistance) {
                tankBaseNode = existingNode;
                break;
            }
        }

        if (tankBaseNode == null) {
            tankBaseNode = new Node(parent, tank.position.x, tank.position.y);
            nodes.add(tankBaseNode);
        }

        // Assign this node as both current and base node for the tank
        currentNodes.put(tank, tankBaseNode);
        baseNodes.put(tank, tankBaseNode);  // Each tank gets its own base node

        // Add to visited positions
        visitedPositions.add(tank.position.copy());
    }

    /**
     * Removes a tank from being managed by this exploration manager.
     *
     * @param tank The tank to remove
     */
    void removeTank(Tank tank) {
        if (!tanks.contains(tank)) {
            return;
        }

        tanks.remove(tank);
        currentNodes.remove(tank);
        targetNodes.remove(tank);
        baseNodes.remove(tank);  // Remove the tank's base node reference
        previousDirections.remove(tank);
        stuckCounters.remove(tank);
        lastPositions.remove(tank);
        samePositionCounters.remove(tank);
        navStates.remove(tank);
        startPositionCounters.remove(tank);
        paths.remove(tank);
        homeArrivalTime.remove(tank);
    }

    /**
     * Initializes the fog of war layer.
     * Creates a graphic layer for the fog and sets it to fully opaque.
     */
    void initializeFog() {
        if (parent.width > 0 && parent.height > 0) {
            fogLayer = parent.createGraphics(parent.width, parent.height);
            fogLayer.beginDraw();
            fogLayer.background(fogColor, fogAlpha);
            fogLayer.endDraw();
            initialized = true;
            fogLayer.loadPixels();
            totalPixels = fogLayer.width * fogLayer.height;
            clearedPixels = 0;
        }
    }

    /**
     * Updates the positions of all tanks in the exploration graph.
     * Detects if tanks are stuck, adds visited positions for fog clearing,
     * and creates new nodes when in unexplored territory.
     */
    void updateTankPositions() {
        for (Tank tank : tanks) {
            updateTankPosition(tank);
        }
    }

    /**
     * Updates a specific tank's position in the exploration graph.
     * Detects if the tank is stuck, adds visited positions for fog clearing,
     * and creates new nodes when in unexplored territory.
     */
    private void updateTankPosition(Tank tank) {
        if (tank == null) return;

        PVector lastPosition = lastPositions.get(tank);
        int samePositionCounter = samePositionCounters.get(tank);

        // Only check for stuck tanks if auto-exploration is active
        if (autoExplore && PVector.dist(tank.position, lastPosition) < 1.0f) {
            samePositionCounter++;
            if (samePositionCounter > 60) {
                handleStuckTank(tank);
                samePositionCounter = 0;
            }
        } else {
            samePositionCounter = 0;
            lastPositions.put(tank, tank.position.copy());
        }

        samePositionCounters.put(tank, samePositionCounter);

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

        Node closestNode = findClosestNode(tank.position);
        if (closestNode != null && PVector.dist(closestNode.position, tank.position) < 20) {
            currentNodes.put(tank, closestNode);
        }

        if (findClosestNode(tank.position) == null ||
                PVector.dist(findClosestNode(tank.position).position, tank.position) > maxNodeDistance/2) {
            addNode(tank.position.x, tank.position.y);
        }
    }

    /**
     * Handles a tank that appears to be stuck in one position.
     * Changes direction randomly to attempt to escape the stuck position.
     */
    void handleStuckTank(Tank tank) {
        //parent.println(tank.name + " appears stuck - changing direction");

        float randomAngle = random.nextFloat() * PApplet.TWO_PI;
        PVector escapeDirection = new PVector(PApplet.cos(randomAngle), PApplet.sin(randomAngle));

        if (Math.abs(escapeDirection.x) > Math.abs(escapeDirection.y)) {
            tank.state = escapeDirection.x > 0 ? 1 : 2;
            parent.println("1");
        } else {
            tank.state = escapeDirection.y > 0 ? 3 : 4;
            parent.println("2");
        }

        if (targetNodes.containsKey(tank) && targetNodes.get(tank) != null && navStates.get(tank) != NavigationState.RETURNING_HOME) {
            parent.println("IT SHOULDN'T GET HERE");
            navStates.put(tank, NavigationState.EXPLORING);
            targetNodes.put(tank, null);
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
    Node addNode(float x, float y) {
        // Check if a node already exists nearby
        if (!enemyDetected){
            for (Node existingNode : nodes) {
                if (PVector.dist(existingNode.position, new PVector(x, y)) < minNodeDistance) {
                    return existingNode;
                }
            }
        }

        Node newNode = new Node(parent, x, y);
        nodes.add(newNode);
        connectToVisibleNodes(newNode);

        // Update current nodes for tanks that are close to this new node
        for (Tank tank : tanks) {
            if (PVector.dist(newNode.position, tank.position) < 30) {
                currentNodes.put(tank, newNode);
            }
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
    boolean canSee(PVector from, PVector to) {
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            // Get trees directly from the game instead of calling collisions
            Tree[] treesToCheck = game.allTrees;
            if (treesToCheck != null) {
                for (Tree tree : treesToCheck) {
                    if (tree != null) {
                        // Use local lineIntersectsTree method instead of calling collisions
                        if (lineIntersectsTree(from, to, tree.position, tree.radius + 10)) {
                            return false;
                        }
                    }
                }
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
        node2.addEdge(node1, weight);
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
     * Toggles the auto-exploration mode on or off for all tanks.
     * When enabled, tanks will automatically explore the environment.
     * When disabled, tanks will stop moving.
     */
    void toggleAutoExplore() {
        autoExplore = !autoExplore;



        if (autoExplore) {
            parent.println("Auto-exploration enabled");
            for (Tank tank : tanks) {
                navStates.put(tank, NavigationState.EXPLORING);
            }
        } else {
            parent.println("Auto-exploration disabled");
            for (Tank tank : tanks) {
                tank.state = 0;
            }
        }
    }

    /**
     * Main navigation method that controls all tanks' movement.
     * Handles different navigation states for each tank: exploring, moving to target,
     * backtracking, and returning home.
     */
    void navigation() {
        if (!autoExplore) return;

        if(areAllTanksHome()){
            if (enemyDetected) {
                startCoordinatedAttack(detectedEnemyBase);
            }
        }else {
            for (Tank tank : tanks) {
                navigateTank(tank);
            }
        }
    }

    /**
     * Handles navigation for a specific tank.
     */
    private void navigateTank(Tank tank) {

        if (tank == null) return;

        NavigationState navState = navStates.get(tank);

        switch (navState) {
            case RETURNING_HOME:
                tank.navState = "ReturningHome";
                Node targetNode = targetNodes.get(tank);
                ArrayList<PVector> path = paths.get(tank);
                int startPositionCounter = startPositionCounters.get(tank);

                if (targetNode != null && PVector.dist(tank.position, targetNode.position) < 20) {
                    //targetnode har inte uppdaterats på 3s, findclosestnode -> a*
                    if (!path.isEmpty()) {
                        path.remove(0);
                    }
                    if (!path.isEmpty()) {
                        PVector nextPos = path.get(0);
                        Node nextNode = findClosestNode(nextPos);
                        if (nextNode != null) {
                            targetNodes.put(tank, nextNode);
                        }
                    }
                }

                if (targetNode != null) {
                    moveTowardTarget(tank);
                }
                startPositionCounters.put(tank, startPositionCounter);
                break;

            case EXPLORING:
                tank.navState = "Exploring";
                targetNode = targetNodes.get(tank);

                if (targetNode == null || PVector.dist(tank.position, targetNode.position) < 20) {
                    if(navStates.get(tank) == NavigationState.RETURNING_HOME){
                        return;
                    }
                    targetNode = selectExplorationTarget(tank);
                    if (targetNode != null) {
                        navStates.put(tank, NavigationState.MOVING_TO_TARGET);
                        targetNodes.put(tank, targetNode);
                    } else {
                        expandRRT(tank);
                    }
                }
                break;

            case MOVING_TO_TARGET:
                tank.navState = "MovingToTarget";
                moveTowardTarget(tank);
                targetNode = targetNodes.get(tank);

                if (targetNode != null && PVector.dist(tank.position, targetNode.position) < 50) {
                    if (navStates.get(tank) == NavigationState.RETURNING_HOME) {
                        return;
                    }
                    targetNode.markVisited();
                    navStates.put(tank, NavigationState.EXPLORING);
                    targetNodes.put(tank, null);
                }
                break;

            case POSITION_AROUND_ENEMY_BASE:
                threadInstane.interrupt();
                if(!isAutoExploreActive()) {
                    toggleAutoExplore();
                }
                tank.navState = "Positioning for Attack";
                targetNode = targetNodes.get(tank);
                path = paths.get(tank);
                startPositionCounter = startPositionCounters.get(tank);

                if (targetNode != null && PVector.dist(tank.position, targetNode.position) < 20) {
                    //targetnode har inte uppdaterats på 3s, findclosestnode -> a*
                    if (!path.isEmpty()) {
                        path.remove(0);
                    }
                    if (!path.isEmpty()) {
                        PVector nextPos = path.get(0);
                        Node nextNode = findClosestNode(nextPos);
                        if (nextNode != null) {
                            targetNodes.put(tank, nextNode);
                        }
                    }
                }
                if (targetNode != null) {
                    System.out.println("is it here?");
                    moveTowardTarget(tank);
                }
                startPositionCounters.put(tank, startPositionCounter);
                break;
        }
    }

    void recalculatePathFromCurrentPosition(Tank tank) {
        Node currentNode = findClosestNode(tank.position);
        if (currentNode == null || PVector.dist(currentNode.position, tank.position) > minNodeDistance) {
            currentNode = addNode(tank.position.x, tank.position.y);
        }
        currentNodes.put(tank, currentNode);

        // Use the tank's individual base node instead of a shared baseNode
        Node tankBaseNode = baseNodes.get(tank);
        if (tankBaseNode == null) {
            parent.println("Warning: No base node found for tank " + tank.name);
            return;
        }

        ArrayList<Node> pathingHome;
        if (testDijkstra) {
            pathingHome = dijkstra(currentNode, tankBaseNode);
        } else {
            pathingHome = aStar(currentNode, tankBaseNode);
        }

        ArrayList<PVector> path = new ArrayList<>();
        if (!pathingHome.isEmpty()) {
            for (Node node : pathingHome) {
                path.add(node.position.copy());
            }
            paths.put(tank, path);

            if (pathingHome.size() > 1) {
                targetNodes.put(tank, pathingHome.get(0));
            } else {
                targetNodes.put(tank, tankBaseNode);
            }
        }
    }

    /**
     * Moves a tank toward its current target node.
     * Sets the tank's state (direction) based on the direction to the target.
     */
    void moveTowardTarget(Tank tank) {
        Node targetNode = targetNodes.get(tank);
        if (targetNode == null) return;

        PVector direction = PVector.sub(targetNode.position, tank.position);
        direction.normalize();

        float dx = direction.x;
        float dy = direction.y;

        if (Math.abs(dx) > 0.3f && Math.abs(dy) > 0.3f) {
            if (dx > 0 && dy > 0) {
                tank.state = 5;
            } else if (dx > 0 && dy < 0) {
                tank.state = 6;
            } else if (dx < 0 && dy > 0) {
                tank.state = 7;
            } else {
                tank.state = 8;
            }
        } else {
            if (Math.abs(dx) > Math.abs(dy)) {
                tank.state = dx > 0 ? 1 : 2;
            } else {
                tank.state = dy > 0 ? 3 : 4;
            }
        }

        previousDirections.put(tank, direction.copy());
    }

    /**
     * Selects the next target node for a tank's exploration.
     * Prioritizes unvisited nodes that are visible and not in the home base.
     * Also avoids nodes near other tanks to prevent tanks from crossing paths.
     *
     * @param tank The tank to select an exploration target for
     * @return The selected node for exploration, or null if no suitable node was found
     */
    /**
     * Selects the next target node for a tank's exploration.
     * Now uses the tank's sensor data to make better decisions.
     */
    Node selectExplorationTarget(Tank tank) {
        ArrayList<Node> candidates = new ArrayList<Node>();
        ArrayList<SensorDetection> sensorData = tank.scan(
                ((tanks_bas_v1_0)parent).allTanks,
                ((tanks_bas_v1_0)parent).allTrees
        );

        // Check if there are any obstacles in our immediate path
        boolean obstacleAhead = false;
        for (SensorDetection detection : sensorData) {
            if ((detection.type == SensorDetection.ObjectType.TREE ||
                    detection.type == SensorDetection.ObjectType.BORDER) &&
                    PVector.dist(tank.position, detection.position) < 100) {
                obstacleAhead = true;
                break;
            }
        }

        for (Node node : nodes) {
            if (!node.visited &&
                    PVector.dist(node.position, tank.position) < maxNodeDistance * 3 &&
                    (obstacleAhead || canSee(tank.position, node.position)) &&
                    !isInHomeBase(node.position) &&
                    !isNearOtherTank(node.position, tank)) {
                candidates.add(node);
            }
        }

        if (!candidates.isEmpty()) {
            // Find the closest candidate
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

        // If no good candidates, try to identify direction with clearest path
        if (obstacleAhead) {
            float clearest = 0;
            float clearestAngle = 0;

            for (int angle = 0; angle < 360; angle += 45) {
                float rad = PApplet.radians(angle);
                PVector direction = new PVector(PApplet.cos(rad), PApplet.sin(rad));
                PVector testPoint = PVector.add(tank.position, PVector.mult(direction, 150));

                if (isValidNodePosition(testPoint, tank)) {
                    float clearPath = 1.0f;
                    for (SensorDetection detection : sensorData) {
                        if (detection.type == SensorDetection.ObjectType.TREE ||
                                detection.type == SensorDetection.ObjectType.BORDER) {
                            PVector toDetection = PVector.sub(detection.position, tank.position);
                            toDetection.normalize();
                            float dotProduct = direction.dot(toDetection);
                            if (dotProduct > 0.7) { // Within ~45 degrees
                                clearPath -= (1.0f - PVector.dist(tank.position, detection.position) / 300.0f);
                            }
                        }
                    }

                    if (clearPath > clearest) {
                        clearest = clearPath;
                        clearestAngle = angle;
                    }
                }
            }

            if (clearest > 0 && isValidNodePosition(tank.position, tank)) {
                float rad = PApplet.radians(clearestAngle);
                PVector direction = new PVector(PApplet.cos(rad), PApplet.sin(rad));
                PVector newPos = PVector.add(tank.position, PVector.mult(direction, 100));
                return addNode(newPos.x, newPos.y);
            }
        }

        return null;
    }

    /**
     * Checks if a position is too close to other tanks.
     * Used to prevent tanks from selecting targets that would cause them to cross paths.
     *
     * @param position Position to check
     * @param excludeTank Tank to exclude from the check
     * @return true if the position is too close to another tank
     */
    boolean isNearOtherTank(PVector position, Tank excludeTank) {
        float minDistance = 75.0f; // Minimum distance to keep between tanks

        for (Tank otherTank : tanks) {
            if (otherTank != excludeTank) {
                // Check distance to other tank
                if (PVector.dist(position, otherTank.position) < minDistance) {
                    return true;
                }

                // Also check if position is close to other tank's target
                Node otherTargetNode = targetNodes.get(otherTank);
                if (otherTargetNode != null &&
                        PVector.dist(position, otherTargetNode.position) < minDistance) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Expands the exploration graph using Rapidly-exploring Random Tree (RRT) algorithm.
     * Creates new nodes in unexplored areas to guide exploration when no valid targets exist.
     */
    void expandRRT(Tank tank) {
        for (int attempts = 0; attempts < 10; attempts++) {
            PVector randomPoint = new PVector(
                    random.nextFloat() * parent.width,
                    random.nextFloat() * parent.height
            );

            if (isInHomeBase(randomPoint) || isNearOtherTank(randomPoint, tank)) {
                continue;
            }

            Node nearest = findClosestNode(tank.position);
            if(nearest == null) continue;

            PVector direction = PVector.sub(randomPoint, nearest.position);
            direction.normalize();
            direction.mult(maxNodeDistance);

            PVector newPos = PVector.add(nearest.position, direction);

            newPos.x = PApplet.constrain(newPos.x, 20, parent.width - 20);
            newPos.y = PApplet.constrain(newPos.y, 20, parent.height - 20);

            if (isValidNodePosition(newPos, tank) && navStates.get(tank) != NavigationState.RETURNING_HOME) {
                Node newNode = addNode(newPos.x, newPos.y);
                targetNodes.put(tank, newNode);
                navStates.put(tank, NavigationState.MOVING_TO_TARGET);
                return;
            }
        }
    }

    /**
     * Checks if a position is valid for creating a new node.
     * Validates that the position is not in a home base, not too close to existing nodes,
     * not too close to other tanks, and not colliding with obstacles.
     *
     * @param pos Position to validate
     * @param excludeTank Tank to exclude from proximity checks
     * @return true if the position is valid for a new node, false otherwise
     */
    boolean isValidNodePosition(PVector pos, Tank excludeTank) {
        if (isInHomeBase(pos) || isNearOtherTank(pos, excludeTank)) {
            return false;
        }

        for (Node node : nodes) {
            if (PVector.dist(node.position, pos) < minNodeDistance) {
                return false;
            }
        }

        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.allTrees != null) {
                for (Tree tree : game.allTrees) {
                    if (tree != null) {
                        if (lineIntersectsTree(excludeTank.position, pos, tree.position, tree.radius)) {
                            return false;
                        }
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
     * Used for line-of-sight and path planning calculations.
     */
    boolean lineIntersectsTree(PVector start, PVector end, PVector center, float radius) {
        // Direct implementation instead of calling collisions
        PVector d = PVector.sub(end, start);
        PVector f = PVector.sub(start, center);

        float a = d.dot(d);
        float b = 2 * f.dot(d);
        float c = f.dot(f) - radius * radius;

        float discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return false;
        } else {
            discriminant = (float) Math.sqrt(discriminant);
            float t1 = (-b - discriminant) / (2 * a);
            float t2 = (-b + discriminant) / (2 * a);
            return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
        }
    }

    boolean areAllTanksHome(){
        if(enemyDetected){
            System.out.println(enemyDetected);
            int count = 0;
            for(Tank tank : tanks){
                if(Math.abs(baseNodes.get(tank).position.x - tank.position.x) < 5 && Math.abs(baseNodes.get(tank).position.y- tank.position.y) < 5) {
                    count++;
                }
            }
            return count == tanks.size();
        }

        for(Tank tank : tanks) {
            if(navStates.get(tank) != NavigationState.RETURNING_HOME) {
                return false;
            }

        }
        return false;
    }

    /**
     * Checks if a position is inside a home base.
     *
     * @param position Position to check
     * @return true if the position is in a home base, false otherwise
     */
    boolean isInHomeBase(PVector position) {
        if (position.x >= 0 && position.x <= 150 &&
                position.y >= 0 && position.y <= 350) {
            return true; // Team 0 (red) base
        }

        if (position.x >= parent.width - 151 && position.x <= parent.width &&
                position.y >= parent.height - 351 && position.y <= parent.height) {
            return true; // Team 1 (blue) base
        }

        return false;
    }

    /**
     * Updates the fog of war based on visited positions.
     * Calculates the percentage of the map that has been explored.
     */
    void updateFog() {
        if (!initialized) return;

        fogLayer.beginDraw();
        fogLayer.background(fogColor, fogAlpha);

        fogLayer.blendMode(PApplet.REPLACE);
        fogLayer.noStroke();

        for (PVector pos : visitedPositions) {
            float diameter = 100.0f; // Default FOV
            // Find if any tank is near this position for FOV
            for (Tank tank : tanks) {
                if (PVector.dist(tank.position, pos) < 30) {
                    diameter = tank.fieldOfView;
                    break;
                }
            }

            fogLayer.fill(fogColor, 0);
            fogLayer.ellipse(pos.x, pos.y, diameter, diameter);
        }

        fogLayer.endDraw();

        // Count cleared pixels
        fogLayer.loadPixels();
        clearedPixels = 0;
        for (int i = 0; i < totalPixels; i++) {
            int c = fogLayer.pixels[i];
            float alpha = parent.alpha(c);
            if (alpha == 0) {
                clearedPixels++;
            }
        }

        exploredPercent = (clearedPixels / (float) totalPixels) * 100;
    }

    /**
     * Displays the exploration graph and fog of war.
     * Shows nodes, connections, and the exploration percentage.
     */
    void display() {
        // Display nodes
        for (Node node : nodes) {
            boolean isCurrentNode = false;
            boolean isTargetNode = false;
            boolean isBaseNode = false;

            // Check if this node is a current, target, or base node for any tank
            for (Tank tank : tanks) {
                if (currentNodes.containsKey(tank) && node == currentNodes.get(tank)) {
                    isCurrentNode = true;
                }
                if (targetNodes.containsKey(tank) && node == targetNodes.get(tank)) {
                    isTargetNode = true;
                }
                if (baseNodes.containsKey(tank) && node == baseNodes.get(tank)) {
                    isBaseNode = true;
                }
            }

            if (isBaseNode) {
                parent.fill(0, 0, 255); // Blue for base nodes
            } else if (isCurrentNode) {
                parent.fill(0, 200, 0);
            } else if (isTargetNode) {
                parent.fill(200, 200, 0);
            } else if (node.visited) {
                parent.fill(150, 150, 200, 150);
            } else {
                parent.fill(200, 150, 150, 150);
            }

            parent.noStroke();
            parent.ellipse(node.position.x, node.position.y, 15, 15);
        }

        // Display edges
        parent.stroke(100, 100, 200, 100);
        parent.strokeWeight(1);
        for (Edge edge : edges) {
            parent.line(
                    edge.source.position.x, edge.source.position.y,
                    edge.destination.position.x, edge.destination.position.y
            );
        }
        parent.strokeWeight(1);

        // Display fog of war
        if (initialized) {
            parent.image(fogLayer, 0, 0);
        }

        // Display exploration percentage
        parent.fill(0);
        parent.text(parent.nf(exploredPercent, 1, 2) + "% explored", 20, 20);
    }

    /**
     * Initiates the return home sequence for a tank.
     * Calculates a path back to the tank's individual home base node using A* pathfinding.
     */
    void returnHome(Tank tank) {
        navStates.put(tank, NavigationState.RETURNING_HOME);

        Node closestNode = findClosestNode(tank.position);

        if (closestNode == null || PVector.dist(closestNode.position, tank.position) > minNodeDistance) {
            closestNode = addNode(tank.position.x, tank.position.y);
        }
        currentNodes.put(tank, closestNode);

        // Use the tank's individual base node instead of a shared baseNode
        Node tankBaseNode = baseNodes.get(tank);
        if (tankBaseNode == null) {
            parent.println("Warning: No base node found for tank " + tank.name);
            return;
        }

        ArrayList<Node> pathingHome;

        if (testDijkstra)
            pathingHome = dijkstra(closestNode, tankBaseNode);
        else
            pathingHome = aStar(closestNode, tankBaseNode);

        ArrayList<PVector> path = new ArrayList<>();
        if (!pathingHome.isEmpty()) {
            path.clear();

            for (Node node : pathingHome) {
                path.add(node.position.copy());
            }

            paths.put(tank, path);

            if (pathingHome.size() > 1) {
                targetNodes.put(tank, pathingHome.get(0));
            } else {
                targetNodes.put(tank, tankBaseNode);
            }
        }
    }

    /**
     * Initiates the return home sequence for all tanks.
     */
    void returnAllHome() {
        // Reset the all-tanks-home timer when starting return sequence
        allTanksHomeTime = null;

        for (Tank tank : tanks) {
            returnHome(tank);
        }
    }

    /**
     * Implements Dijkstra's algorithm to find the shortest path between start and goal nodes.
     *
     * @param start The starting node for the path finding algorithm
     * @param goal The destination node to find a path to
     * @return An ArrayList of Nodes representing the shortest path from start to goal
     */
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
                return reconstructPath(traveledFrom,current);
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
                return reconstructPath(traveledFrom,current);
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
    private ArrayList<Node> reconstructPath(HashMap<Node,Node> traveledFrom, Node current) {
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
     * Checks if a specific tank is currently in the process of returning home.
     *
     * @param tank The tank to check
     * @return true if the tank is returning home, false otherwise
     */
    public boolean isReturningHome(Tank tank) {
        NavigationState state = navStates.get(tank);
        return state == NavigationState.RETURNING_HOME || state == NavigationState.WAITING_AT_HOME;
    }

    void startCoordinatedAttack(PVector enemyBasePos) {
        parent.println("=== COORDINATED ATTACK INITIATED ===");
        parent.println("Target: " + enemyBasePos);

        // Assign roles and positions to each tank
        for (int i = 0; i < tanks.size() && i < 3; i++) {
                Tank tank = tanks.get(i);
                PVector attackPosition = calculateAttackPosition(enemyBasePos, i);

                // Create attack node and set as target
                Node attackNode = addNode(attackPosition.x, attackPosition.y);
                targetNodes.put(tank, attackNode);
                navStates.put(tank, NavigationState.POSITION_AROUND_ENEMY_BASE);
                ArrayList<PVector> path = new ArrayList<>();
                for(Node node : aStar(baseNodes.get(tank), attackNode)){
                    path.add(node.position.copy());
                }
                paths.put(tank, path);
                parent.println(tank.name + " assigned to position: " + attackPosition);
        }
    }

    PVector calculateAttackPosition(PVector enemyBase, int tankIndex) {
        float safeDistance = 100;

        // Get the detection position for more varied attack angles
        PVector detectionPos = null;
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            detectionPos = game.team0.detectionPosition;
        }

        if (detectionPos != null) {
            // Calculate angles based on the direction from detection position to enemy base
            PVector detectionToEnemy = PVector.sub(enemyBase, detectionPos);
            float baseAngle = PApplet.atan2(detectionToEnemy.y, detectionToEnemy.x);

            // Spread tanks around the detection angle
            float[] angleOffsets = {0, PApplet.radians(-45), PApplet.radians(45)}; // Center, left, right
            float angle = baseAngle + angleOffsets[tankIndex % 3];

            float x = enemyBase.x + safeDistance * PApplet.cos(angle);
            float y = enemyBase.y + safeDistance * PApplet.sin(angle);

            x = PApplet.constrain(x, 50, parent.width - 50);
            y = PApplet.constrain(y, 50, parent.height - 50);

            return new PVector(x, y);
        } else {
            // Fallback to original method
            float[] angles = {180, 135, 225};
            float angle = PApplet.radians(angles[tankIndex % 3]);
            float x = enemyBase.x + safeDistance * PApplet.cos(angle);
            float y = enemyBase.y + safeDistance * PApplet.sin(angle);

            x = PApplet.constrain(x, 50, parent.width - 50);
            y = PApplet.constrain(y, 50, parent.height - 50);

            return new PVector(x, y);
        }
    }
}