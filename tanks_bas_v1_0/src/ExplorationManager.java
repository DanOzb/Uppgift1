import processing.core.*;
import java.util.ArrayList;
import java.util.Stack;

class ExplorationManager {
    private PApplet parent;

    // Fog-related properties
    private PGraphics fogLayer;
    private int fogColor;
    private int fogAlpha;
    private boolean initialized;
    private ArrayList<PVector> visitedPositions;

    // Explorer-related properties
    private float stepSize;
    private ArrayList<Node> nodes;
    private Stack<Node> dfsStack;
    private ArrayList<PVector> path;

    // Shared properties
    private Tank tank;
    private Node currentNode;
    private boolean autoExplore;

    ExplorationManager(PApplet parent, float stepSize) {
        this.parent = parent;
        this.stepSize = stepSize;

        // Fog initialization
        this.fogColor = parent.color(50, 50, 50);
        this.fogAlpha = 100;
        this.initialized = false;
        this.visitedPositions = new ArrayList<PVector>();

        // Explorer initialization
        this.nodes = new ArrayList<Node>();
        this.dfsStack = new Stack<Node>();
        this.path = new ArrayList<PVector>();
        this.autoExplore = false;
    }

    void setTank(Tank tank) {
        this.tank = tank;

        // Create starting node at tank's position
        if (tank != null) {
            float gridX = Math.round(tank.position.x / stepSize) * stepSize;
            float gridY = Math.round(tank.position.y / stepSize) * stepSize;

            Node startNode = new Node(parent, gridX, gridY);
            nodes.add(startNode);
            dfsStack.push(startNode);
            currentNode = startNode;

            // Add this position to visited positions for fog clearing
            visitedPositions.add(new PVector(gridX, gridY));
        }
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

    Node getNodeAt(PVector pos) {
        for (Node node : nodes) {
            if (PVector.dist(node.position, pos) < 1) {
                return node;
            }
        }
        return null;
    }

    boolean hasNodeAt(PVector pos) {
        return getNodeAt(pos) != null;
    }

    void updateTankPosition() {
        if (tank == null) return;

        // Get the grid-aligned position of the tank
        float gridX = Math.round(tank.position.x / stepSize) * stepSize;
        float gridY = Math.round(tank.position.y / stepSize) * stepSize;
        PVector gridPos = new PVector(gridX, gridY);

        // Debug information
        System.out.println("Tank actual position: " + tank.position.x + ", " + tank.position.y);
        System.out.println("Tank grid position: " + gridX + ", " + gridY);

        // Check if this is a new grid position
        boolean isNewPosition = true;
        Node existingNode = null;

        // Look for existing node at this grid position with a more forgiving distance check
        for (Node node : nodes) {
            if (PVector.dist(node.position, gridPos) < stepSize/2) {
                isNewPosition = false;
                existingNode = node;
                break;
            }
        }

        if (isNewPosition) {
            // Create a new node at this position
            System.out.println("Creating new node at: " + gridX + ", " + gridY);
            Node newNode = new Node(parent, gridX, gridY);
            nodes.add(newNode);

            // Connect with previous node if available
            if (currentNode != null) {
                System.out.println("Connecting to previous node at: " +
                        currentNode.position.x + ", " + currentNode.position.y);
                connectNodes(currentNode, newNode);
            }

            // Update current node
            currentNode = newNode;
            currentNode.markVisited();

            // If we're in auto-explore mode, add to DFS stack
            if (autoExplore) {
                dfsStack.push(currentNode);
                System.out.println("Added new node to DFS stack, size now: " + dfsStack.size());
            }

            // Add to visited positions for fog clearing
            visitedPositions.add(gridPos.copy());
            updateFog();
        }
        else if (existingNode != null && existingNode != currentNode) {
            // We've moved to an existing node
            System.out.println("Moved to existing node at: " + gridX + ", " + gridY);

            // Connect with previous node if available and not already connected
            if (currentNode != null) {
                boolean alreadyConnected = false;
                for (Edge edge : currentNode.edges) {
                    if (edge.destination == existingNode) {
                        alreadyConnected = true;
                        break;
                    }
                }

                if (!alreadyConnected) {
                    System.out.println("Connecting existing nodes");
                    connectNodes(currentNode, existingNode);
                }
            }

            // Update current node
            currentNode = existingNode;
            currentNode.markVisited();

            // If we're in auto-explore mode and this node isn't in the stack, add it
            if (autoExplore && !dfsStack.contains(currentNode)) {
                dfsStack.push(currentNode);
                System.out.println("Added existing node to DFS stack, size now: " + dfsStack.size());
            }
        }
    }

    void connectNodes(Node node1, Node node2) {
        // Check if connection already exists
        boolean edgeExists = false;
        for (Edge edge : node1.edges) {
            if (edge.destination == node2) {
                edgeExists = true;
                break;
            }
        }

        if (!edgeExists) {
            // Calculate distance for weight
            float weight = PVector.dist(node1.position, node2.position);
            node1.addEdge(node2, weight);

            // Add to path for visualization
            path.add(node2.position.copy());
        }
    }
    public boolean isAutoExploreActive() {
        return autoExplore;
    }

    void exploreDFS() {
        if (!autoExplore || dfsStack.isEmpty() || tank == null) {
            return; // Nothing to explore
        }

        System.out.println("DFS exploration active with stack size: " + dfsStack.size());

        // Check if tank has reached the current node's position (within a threshold)
        float distToCurrentNode = PVector.dist(tank.position, currentNode.position);
        System.out.println("Distance to current node: " + distToCurrentNode);

        if (distToCurrentNode < stepSize/4) {
            // Tank has reached the current node, find the next position to explore
            PVector nextPos = getNextDFSPosition();

            if (nextPos != null) {
                // Calculate direction vector from current position to next position
                float dx = nextPos.x - tank.position.x;
                float dy = nextPos.y - tank.position.y;

                System.out.println("Direction vector: dx=" + dx + ", dy=" + dy);

                // Determine predominant direction and set tank state accordingly
                // Use a small threshold to avoid floating point comparison issues
                if (Math.abs(dx) > Math.abs(dy)) {
                    // Horizontal movement predominant
                    if (dx > 1) {
                        tank.state = 1; // Move right
                        System.out.println("Auto-moving RIGHT");
                    } else if (dx < -1) {
                        tank.state = 2; // Move left
                        System.out.println("Auto-moving LEFT");
                    }
                } else {
                    // Vertical movement predominant
                    if (dy > 1) {
                        tank.state = 3; // Move down
                        System.out.println("Auto-moving DOWN");
                    } else if (dy < -1) {
                        tank.state = 4; // Move up
                        System.out.println("Auto-moving UP");
                    }
                }
            } else {
                // No valid next position, stop the tank
                tank.state = 0;
                System.out.println("No valid next position, stopping tank");
            }
        } else {
            // Tank is still moving toward the current node, continue current direction
            System.out.println("Still moving toward current node");
        }
    }

    // Get next position to explore based on DFS
    PVector getNextDFSPosition() {
        if (dfsStack.isEmpty() || currentNode == null) {
            System.out.println("DFS stack empty or current node null");
            return null;
        }

        System.out.println("Current node position: " + currentNode.position.x + ", " + currentNode.position.y);

        // Define the four possible moves in a specific order (right, down, left, up)
        // This order matters for DFS behavior
        PVector[] moves = new PVector[4];
        moves[0] = new PVector(currentNode.position.x + stepSize, currentNode.position.y); // Right
        moves[1] = new PVector(currentNode.position.x, currentNode.position.y + stepSize); // Down
        moves[2] = new PVector(currentNode.position.x - stepSize, currentNode.position.y); // Left
        moves[3] = new PVector(currentNode.position.x, currentNode.position.y - stepSize); // Up

        // Check each possible move in the defined order
        for (PVector newPos : moves) {
            // Skip if out of bounds
            if (newPos.x < 0 || newPos.x >= parent.width || newPos.y < 0 || newPos.y >= parent.height) {
                continue;
            }

            // Check for obstacle collisions (trees)
            boolean isObstacle = false;
            // Add code here to check for tree collisions if needed

            if (isObstacle) {
                continue;
            }

            // Check if this position has already been visited using a more forgiving distance check
            boolean hasNode = false;
            for (Node node : nodes) {
                if (PVector.dist(node.position, newPos) < stepSize/2) {
                    hasNode = true;
                    // If there's a node but it's not visited, we can still go there
                    if (!node.visited) {
                        System.out.println("Found unvisited existing node at: " + newPos.x + ", " + newPos.y);
                        return newPos;
                    }
                    break;
                }
            }

            // If no node exists at this position, it's a valid unexplored position
            if (!hasNode) {
                System.out.println("Found unexplored position at: " + newPos.x + ", " + newPos.y);
                return newPos;
            }
        }

        // If we get here, all adjacent positions are either obstacles, out of bounds, or already visited
        // We need to backtrack
        System.out.println("No unexplored positions found, need to backtrack");

        // Remove current node from stack to backtrack
        if (dfsStack.size() > 1) {
            dfsStack.pop();
            Node backtrackNode = dfsStack.peek();
            System.out.println("Backtracking to: " + backtrackNode.position.x + ", " + backtrackNode.position.y);
            return backtrackNode.position;
        }

        // If we can't backtrack further, exploration is complete
        System.out.println("Exploration complete!");
        return null;
    }

    void toggleAutoExplore() {
        autoExplore = !autoExplore;
        if (autoExplore) {
            parent.println("Auto-exploration enabled");
        } else {
            parent.println("Auto-exploration disabled");
        }
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

        // Return to normal blend mode
        fogLayer.blendMode(PApplet.BLEND);
        fogLayer.endDraw();
    }

    void display() {
        // Display all nodes and connections
        for (Node node : nodes) {
            node.display();
        }

        // Display fog layer
        if (initialized) {
            parent.image(fogLayer, 0, 0);
        }
    }
}