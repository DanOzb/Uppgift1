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
    HashMap<Tank, Node> currentNodes;
    HashMap<Tank, Node> targetNodes;
    HashMap<Tank, Node> baseNodes;
    HashMap<Tank, Node> enemyBaseNodes;
    HashMap<Tank, ArrayList<PVector>> paths;
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
    HashMap<Tank, Long> homeArrivalTime;
    Long allTanksHomeTime;

    int clearedPixels;
    int totalPixels;
    float exploredPercent;
    HashMap<Tank, Integer> startPositionCounters;

    boolean testDijkstra;
    boolean enemyDetected = false;
    PVector detectedEnemyBase = null;
    boolean attacking = false;
    boolean combatMode = false;


    enum NavigationState {
        EXPLORING,
        MOVING_TO_TARGET,
        RETURNING_HOME,
        WAITING_AT_HOME,
        WAITING_OUTSIDE_ENEMY_BASE,
        ATTACK_MODE,
        POSITION_AROUND_ENEMY_BASE
    }

    /**
     * Constructor for the ExplorationManager.
     * Initializes fog of war, navigation systems, and background thread for coordinated attacks.
     *
     * @param parent           The Processing PApplet instance
     * @param visibilityRadius The radius around tanks that becomes visible/explored
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
        this.baseNodes = new HashMap<Tank, Node>();
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
        enemyBaseNodes = new HashMap<>();

        this.testDijkstra = false;

        threadInstane = new Thread(() -> {

            while (!Thread.currentThread().isInterrupted()) {
                if (areAllTanksHome()) {
                    try {
                        Thread.sleep(3000);

                        for (Tank tank : tanks) {
                            navStates.put(tank, NavigationState.ATTACK_MODE);
                        }
                        if (!isAutoExploreActive()) {
                            toggleAutoExplore();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });

        initializeFog();
        for (Tank tank : tanks) {
            navStates.put(tank, NavigationState.EXPLORING);
        }
    }

    /**
     * Adds a tank to be managed by this exploration system.
     * Creates navigation state, base nodes, and initializes all tracking data.
     *
     * @param tank The tank to add to the exploration system
     */
    void addTank(Tank tank) {
        if (tank == null || tanks.contains(tank)) {
            return;
        }

        tanks.add(tank);

        previousDirections.put(tank, new PVector(0, 0));
        stuckCounters.put(tank, 0);
        lastPositions.put(tank, tank.position.copy());
        samePositionCounters.put(tank, 0);
        navStates.put(tank, NavigationState.EXPLORING);
        startPositionCounters.put(tank, 0);
        paths.put(tank, new ArrayList<PVector>());
        homeArrivalTime.put(tank, 0L);

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

        currentNodes.put(tank, tankBaseNode);
        baseNodes.put(tank, tankBaseNode);

        visitedPositions.add(tank.position.copy());
    }

    /**
     * Removes a tank from the exploration management system.
     * Cleans up all associated navigation data and references.
     *
     * @param tank The tank to remove from management
     */
    void removeTank(Tank tank) {
        if (!tanks.contains(tank)) {
            return;
        }

        tanks.remove(tank);
        currentNodes.remove(tank);
        targetNodes.remove(tank);
        baseNodes.remove(tank);
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
     * Initializes the fog of war graphics layer.
     * Creates the fog overlay and sets up pixel tracking for exploration percentage.
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
     * Updates the positions of all managed tanks in the exploration graph.
     * Handles stuck detection, node creation, and visited position tracking.
     */
    void updateTankPositions() {
        for (Tank tank : tanks) {
            updateTankPosition(tank);
        }
    }

    /**
     * Updates a specific tank's position and navigation state.
     * Handles stuck detection, creates new nodes in unexplored areas.
     *
     * @param tank The tank to update
     */
    private void updateTankPosition(Tank tank) {
        if (tank == null) return;

        PVector lastPosition = lastPositions.get(tank);
        int samePositionCounter = samePositionCounters.get(tank);

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
                PVector.dist(findClosestNode(tank.position).position, tank.position) > maxNodeDistance / 2) {
            addNode(tank.position.x, tank.position.y);
        }
    }

    /**
     * Handles a tank that appears to be stuck by changing its direction randomly.
     * Attempts to help the tank escape from stuck positions.
     *
     * @param tank The tank that appears to be stuck
     */
    void handleStuckTank(Tank tank) {

        float randomAngle = random.nextFloat() * PApplet.TWO_PI;
        PVector escapeDirection = new PVector(PApplet.cos(randomAngle), PApplet.sin(randomAngle));
        if (navStates.get(tank) != NavigationState.POSITION_AROUND_ENEMY_BASE || navStates.get(tank) != NavigationState.ATTACK_MODE) {
            if (Math.abs(escapeDirection.x) > Math.abs(escapeDirection.y)) {
                tank.state = escapeDirection.x > 0 ? 1 : 2;
                parent.println("1");
            } else {
                tank.state = escapeDirection.y > 0 ? 3 : 4;
                parent.println("2");
            }
        }

        if (targetNodes.containsKey(tank) && targetNodes.get(tank) != null && navStates.get(tank) != NavigationState.RETURNING_HOME && navStates.get(tank) != NavigationState.POSITION_AROUND_ENEMY_BASE) {
            navStates.put(tank, NavigationState.EXPLORING);
            targetNodes.put(tank, null);
        }
    }

    /**
     * Finds the closest navigation node to a given position.
     *
     * @param position The position to find the closest node to
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
     * Creates a new navigation node at the specified coordinates.
     * Attempts to connect the new node to nearby visible nodes.
     *
     * @param x X-coordinate for the new node
     * @param y Y-coordinate for the new node
     * @return The newly created Node
     */
    Node addNode(float x, float y) {
        if (!enemyDetected) {
            for (Node existingNode : nodes) {
                if (PVector.dist(existingNode.position, new PVector(x, y)) < minNodeDistance) {
                    return existingNode;
                }
            }
        }

        Node newNode = new Node(parent, x, y);
        nodes.add(newNode);
        connectToVisibleNodes(newNode);

        for (Tank tank : tanks) {
            if (PVector.dist(newNode.position, tank.position) < 30) {
                currentNodes.put(tank, newNode);
            }
        }

        return newNode;
    }

    /**
     * Connects a node to all visible nearby nodes within connection range.
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
     * Checks line of sight between two positions, considering tree obstructions.
     *
     * @param from Starting position
     * @param to   Ending position
     * @return true if there is clear line of sight, false if obstructed
     */
    boolean canSee(PVector from, PVector to) {
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            Tree[] treesToCheck = game.allTrees;
            if (treesToCheck != null) {
                for (Tree tree : treesToCheck) {
                    if (tree != null) {
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
     * Creates a bidirectional connection between two nodes.
     *
     * @param node1  First node to connect
     * @param node2  Second node to connect
     * @param weight The cost/weight of the connection
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
     * @return true if auto-exploration is enabled, false otherwise
     */
    public boolean isAutoExploreActive() {
        return autoExplore;
    }

    /**
     * Toggles auto-exploration mode for all managed tanks.
     * When enabled, tanks autonomously explore the environment.
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
     * Main navigation control method for all tanks.
     * Handles different navigation states and coordinates group behaviors.
     */
    void navigation() {
        if (!autoExplore) return;

        if (areAllTanksHome()) {
            if (enemyDetected && !attacking) {
                startCoordinatedAttack(detectedEnemyBase);

            }
        } else if (areAllTanksOutsideEnemyBase()) {
            for (Tank tank : tanks) {
                navStates.put(tank, NavigationState.ATTACK_MODE);
                navigateTank(tank);
            }
        } else {
            for (Tank tank : tanks) {
                navigateTank(tank);
            }
        }
    }

    /**
     * Handles navigation logic for an individual tank based on its current state.
     *
     * @param tank The tank to navigate
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

                Node tankBaseNode = baseNodes.get(tank);
                if (tankBaseNode != null &&
                        Math.abs(tankBaseNode.position.x - tank.position.x) < 5 &&
                        Math.abs(tankBaseNode.position.y - tank.position.y) < 5) {

                    navStates.put(tank, NavigationState.WAITING_AT_HOME);
                    targetNodes.put(tank, null);
                    tank.state = 0; // Stop moving
                    tank.navState = "Waiting at Home";

                    // Record arrival time
                    if (!homeArrivalTime.containsKey(tank) || homeArrivalTime.get(tank) == 0L) {
                        homeArrivalTime.put(tank, System.currentTimeMillis());
                    }
                    return;
                }

                if (targetNode != null && PVector.dist(tank.position, targetNode.position) < 20) {
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
                    if (navStates.get(tank) == NavigationState.RETURNING_HOME || navStates.get(tank) == NavigationState.POSITION_AROUND_ENEMY_BASE) {
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
                tank.navState = "Positioning for Attack";
                targetNode = targetNodes.get(tank);
                path = paths.get(tank);
                startPositionCounter = startPositionCounters.get(tank);

                Node attackNode = enemyBaseNodes.get(tank);

                if (attackNode != null &&
                        Math.abs(attackNode.position.x - tank.position.x) < 10 &&
                        Math.abs(attackNode.position.y - tank.position.y) < 10) {

                    navStates.put(tank, NavigationState.WAITING_OUTSIDE_ENEMY_BASE);
                    targetNodes.put(tank, null);
                    tank.state = 0;
                    tank.navState = "Waiting outside enemy base";

                    if (!homeArrivalTime.containsKey(tank) || homeArrivalTime.get(tank) == 0L) {
                        homeArrivalTime.put(tank, System.currentTimeMillis());
                    }
                    return;
                }

                if (targetNode != null && PVector.dist(tank.position, targetNode.position) < 20) {
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

            case WAITING_AT_HOME:
                tank.navState = "Waiting at Home";
                tank.state = 0;
                break;

            case WAITING_OUTSIDE_ENEMY_BASE:
                tank.navState = "WAITING OUTSIDE BASE";
                tank.state = 0;

                break;

            case ATTACK_MODE:
                tank.navState = "Attacking";
                combatMode = true;
                if (PVector.dist(tank.position, enemyBaseNodes.get(tank).position) > 30) {
                    targetNodes.put(tank, enemyBaseNodes.get(tank));
                    moveTowardTarget(tank);
                } else if (PVector.dist(tank.position, enemyBaseNodes.get(tank).position) > 15 && PVector.dist(tank.position, enemyBaseNodes.get(tank).position) < 30) {
                    tank.state = random.nextInt(1, 6);
                }
                break;
        }
    }

    /**
     * Recalculates the path for a tank from its current position to its target.
     * Used when tanks get stuck or need to find new routes.
     *
     * @param tank The tank to recalculate the path for
     */
    void recalculatePathFromCurrentPosition(Tank tank) {
        Node currentNode = findClosestNode(tank.position);
        if (currentNode == null || PVector.dist(currentNode.position, tank.position) > minNodeDistance) {
            currentNode = addNode(tank.position.x, tank.position.y);
        }
        currentNodes.put(tank, currentNode);
        if (navStates.get(tank) == NavigationState.POSITION_AROUND_ENEMY_BASE) {
            Node enemyBase = enemyBaseNodes.get(tank);
            if (enemyBase == null) {
                parent.println("Warning: No base node found for tank " + tank.name);
                return;
            }

            ArrayList<Node> pathingToEnemyBase;
            if (testDijkstra) {
                pathingToEnemyBase = dijkstra(currentNode, enemyBase);
            } else {
                pathingToEnemyBase = aStar(currentNode, enemyBase);
            }

            ArrayList<PVector> path = new ArrayList<>();
            if (!pathingToEnemyBase.isEmpty()) {
                for (Node node : pathingToEnemyBase) {
                    path.add(node.position.copy());
                }
                paths.put(tank, path);

                if (pathingToEnemyBase.size() > 1) {
                    targetNodes.put(tank, pathingToEnemyBase.get(0));
                } else {
                    targetNodes.put(tank, enemyBase);
                }
            }
        }
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
     * Moves a tank toward its current target node by setting appropriate direction.
     *
     * @param tank The tank to move toward its target
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
     * Selects the next exploration target for a tank using sensor data.
     * Prioritizes unvisited nodes and avoids obstacles and other tanks.
     *
     * @param tank The tank to select a target for
     * @return The selected Node for exploration, or null if none found
     */
    Node selectExplorationTarget(Tank tank) {
        ArrayList<Node> candidates = new ArrayList<Node>();
        ArrayList<SensorDetection> sensorData = tank.scan(
                ((tanks_bas_v1_0) parent).allTanks,
                ((tanks_bas_v1_0) parent).allTrees
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
     * Checks if a position is too close to other tanks to avoid path conflicts.
     *
     * @param position    The position to check
     * @param excludeTank The tank to exclude from the proximity check
     * @return true if the position is too close to another tank
     */
    boolean isNearOtherTank(PVector position, Tank excludeTank) {
        float minDistance = 75.0f;

        for (Tank otherTank : tanks) {
            if (otherTank != excludeTank) {
                if (PVector.dist(position, otherTank.position) < minDistance) {
                    return true;
                }

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
     * Expands the exploration graph using Rapidly-exploring Random Tree (RRT).
     * Creates new nodes in unexplored areas when no valid targets exist.
     *
     * @param tank The tank requesting graph expansion
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
            if (nearest == null) continue;

            PVector direction = PVector.sub(randomPoint, nearest.position);
            direction.normalize();
            direction.mult(maxNodeDistance);

            PVector newPos = PVector.add(nearest.position, direction);

            newPos.x = PApplet.constrain(newPos.x, 20, parent.width - 20);
            newPos.y = PApplet.constrain(newPos.y, 20, parent.height - 20);

            if (isValidNodePosition(newPos, tank) && navStates.get(tank) != NavigationState.RETURNING_HOME && navStates.get(tank) != NavigationState.POSITION_AROUND_ENEMY_BASE) {
                Node newNode = addNode(newPos.x, newPos.y);
                targetNodes.put(tank, newNode);
                navStates.put(tank, NavigationState.MOVING_TO_TARGET);
                return;
            }
        }
    }

    /**
     * Validates if a position is suitable for creating a new navigation node.
     *
     * @param pos         The position to validate
     * @param excludeTank Tank to exclude from proximity checks
     * @return true if the position is valid for a new node
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
     * Checks if a line intersects with a circular tree (local implementation).
     *
     * @param start  Starting point of the line
     * @param end    Ending point of the line
     * @param center Center of the tree
     * @param radius Radius of the tree
     * @return true if the line intersects the tree
     */
    boolean lineIntersectsTree(PVector start, PVector end, PVector center, float radius) {
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

    /**
     * Checks if all tanks are currently at their home base positions.
     * Only returns true if enemy has been detected (ready for attack phase).
     *
     * @return true if all tanks are home and enemy detected
     */
    boolean areAllTanksHome() {
        int tanksAtHome = 0;
        for (Tank tank : tanks) {
            Node baseNode = baseNodes.get(tank);
            if (baseNode != null &&
                    Math.abs(baseNode.position.x - tank.position.x) < 5 &&
                    Math.abs(baseNode.position.y - tank.position.y) < 5) {
                tanksAtHome++;
            }
        }

        return (tanksAtHome == tanks.size()) && enemyDetected;
    }

    /**
     * Checks if all tanks have reached their assigned attack positions near enemy base.
     *
     * @return true if all tanks are positioned for attack
     */
    boolean areAllTanksOutsideEnemyBase() {
        int tanksAtHome = 0;
        for (Tank tank : tanks) {
            Node enemyBaseNodes = this.enemyBaseNodes.get(tank);
            if (enemyBaseNodes != null &&
                    Math.abs(enemyBaseNodes.position.x - tank.position.x) < 10 &&
                    Math.abs(enemyBaseNodes.position.y - tank.position.y) < 10) {
                tanksAtHome++;
            }
        }

        return (tanksAtHome == tanks.size()) && enemyDetected;
    }

    /**
     * Determines if a position is within any home base boundary.
     *
     * @param position The position to check
     * @return true if position is in a home base
     */
    boolean isInHomeBase(PVector position) {
        if (position.x >= 0 && position.x <= 150 &&
                position.y >= 0 && position.y <= 350) {
            return true;
        }
        return position.x >= parent.width - 100 && position.x <= parent.width &&
                position.y >= parent.height - 400 && position.y <= parent.height;
    }

    /**
     * Updates the fog of war based on all visited positions.
     * Calculates and updates the exploration percentage.
     */
    void updateFog() {
        if (!initialized) return;

        fogLayer.beginDraw();
        fogLayer.background(fogColor, fogAlpha);

        fogLayer.blendMode(PApplet.REPLACE);
        fogLayer.noStroke();

        for (PVector pos : visitedPositions) {
            float diameter = 100.0f; // Default FOV
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
     * Renders the exploration visualization including nodes, edges, and fog of war.
     * Shows current exploration status and navigation graph.
     */
    void display() {
        for (Node node : nodes) {
            boolean isCurrentNode = false;
            boolean isTargetNode = false;
            boolean isBaseNode = false;

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
                parent.fill(0, 0, 255);
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

        if (initialized) {
            parent.image(fogLayer, 0, 0);
        }

        parent.fill(0);
        parent.text(parent.nf(exploredPercent, 1, 2) + "% explored", 20, 20);
    }

    /**
     * Initiates the return home sequence for a specific tank.
     * Calculates optimal path back to the tank's individual base node.
     *
     * @param tank The tank to send home
     */
    void returnHome(Tank tank) {
        navStates.put(tank, NavigationState.RETURNING_HOME);

        Node closestNode = findClosestNode(tank.position);

        if (closestNode == null || PVector.dist(closestNode.position, tank.position) > minNodeDistance) {
            closestNode = addNode(tank.position.x, tank.position.y);
        }
        currentNodes.put(tank, closestNode);

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
     * Initiates return home sequence for all managed tanks simultaneously.
     */
    void returnAllHome() {
        allTanksHomeTime = null;

        for (Tank tank : tanks) {
            returnHome(tank);
        }
    }

    /**
     * Implements Dijkstra's shortest path algorithm between two nodes.
     *
     * @param start The starting node
     * @param goal  The destination node
     * @return List of nodes representing the shortest path
     */
    ArrayList<Node> dijkstra(Node start, Node goal) {
        Set<Node> visitedNodes = new HashSet<>();

        PriorityQueue<Node> openSet = new PriorityQueue<>((a, b) ->
                Float.compare(a.fScore, b.fScore));

        for (Node node : nodes) {
            node.gScore = Float.MAX_VALUE;
        }

        start.gScore = 0f;

        HashMap<Node, Node> traveledFrom = new HashMap<>();

        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.equals(goal)) {
                return reconstructPath(traveledFrom, current);
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
     * Implements A* pathfinding algorithm between two nodes.
     *
     * @param start The starting node
     * @param goal  The destination node
     * @return List of nodes representing the optimal path
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

        HashMap<Node, Node> traveledFrom = new HashMap<>();

        openSet.add(start);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.equals(goal)) {
                return reconstructPath(traveledFrom, current);
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
     * Reconstructs the path from A* or Dijkstra search results.
     *
     * @param traveledFrom Map of nodes to their predecessors
     * @param current      The destination node to trace back from
     * @return List of nodes representing the complete path
     */
    private ArrayList<Node> reconstructPath(HashMap<Node, Node> traveledFrom, Node current) {
        ArrayList<Node> path = new ArrayList<>();

        path.add(current);

        while (traveledFrom.containsKey(current)) {
            current = traveledFrom.get(current);
            path.add(0, current);
        }
        return path;
    }

    /**
     * Calculates heuristic cost estimate between two nodes for A* algorithm.
     *
     * @param a First node
     * @param b Second node
     * @return Estimated cost between the nodes
     */
    private Float heuristicCost(Node a, Node b) {
        return PVector.dist(a.position, b.position);
    }

    /**
     * Checks if a specific tank is in the returning home navigation state.
     *
     * @param tank The tank to check
     * @return true if the tank is returning home or waiting at home
     */
    public boolean isReturningHome(Tank tank) {
        NavigationState state = navStates.get(tank);
        return state == NavigationState.RETURNING_HOME || state == NavigationState.WAITING_AT_HOME || state == NavigationState.POSITION_AROUND_ENEMY_BASE;
    }

    /**
     * Initiates coordinated attack sequence when all tanks are home and enemy detected.
     * Assigns attack positions and begins assault phase.
     *
     * @param enemyBasePos The position of the detected enemy base
     */
    void startCoordinatedAttack(PVector enemyBasePos) {
        attacking = true;
        parent.println("=== COORDINATED ATTACK INITIATED ===");
        parent.println("Target: " + enemyBasePos);

        for (int i = 0; i < tanks.size() && i < 3; i++) {
            Tank tank = tanks.get(i);
            PVector newPos = PVector.add(enemyBasePos, new PVector(-45, 100 * i));
            if (newPos.y > 800) {
                newPos = PVector.add(enemyBasePos, new PVector(-45, -100 * i));
            }
            if (isInHomeBase(newPos)) {
                newPos = new PVector(newPos.x - 30, newPos.y);
            }
            Node attackNode = addNode(newPos.x, newPos.y);
            enemyBaseNodes.put(tank, attackNode);
            System.out.println("Start coordinated attack at position: " + attackNode.position);
            attackEnemyBase(tank);
        }
    }

    /**
     * Sets up attack sequence for a specific tank to assault the enemy base.
     *
     * @param tank The tank to send to attack position
     */
    void attackEnemyBase(Tank tank) {
        navStates.put(tank, NavigationState.POSITION_AROUND_ENEMY_BASE);

        Node closestNode = findClosestNode(tank.position);

        if (closestNode == null || PVector.dist(closestNode.position, tank.position) > minNodeDistance) {
            closestNode = addNode(tank.position.x, tank.position.y);
        }
        currentNodes.put(tank, closestNode);

        Node attackNode = enemyBaseNodes.get(tank);
        if (attackNode == null) {
            parent.println("Warning: No base node found for tank " + tank.name);
            return;
        }

        ArrayList<Node> pathingHome;

        if (testDijkstra)
            pathingHome = dijkstra(closestNode, attackNode);
        else
            pathingHome = aStar(closestNode, attackNode);

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
                targetNodes.put(tank, attackNode);
            }
        }
    }

    /**
     * Debug method to print current navigation states of all tanks.
     */
    public void getNavigationState() {
        for (Tank tank : tanks) {
            System.out.println(tank + " state: " + navStates.get(tank));
        }
    }
}