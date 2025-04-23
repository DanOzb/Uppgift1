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

        // Get the grid position of the tank
        float gridX = Math.round(tank.position.x / stepSize) * stepSize;
        float gridY = Math.round(tank.position.y / stepSize) * stepSize;
        PVector gridPos = new PVector(gridX, gridY);

        // Check if tank has moved to a new grid position
        Node existingNode = getNodeAt(gridPos);
        if (existingNode != null) {
            // Tank is at an existing node
            currentNode = existingNode;
            currentNode.markVisited();

            // If we're in auto-explore mode, update DFS stack
            if (autoExplore && !dfsStack.contains(currentNode)) {
                // Add connection with previous node if not the first node
                if (!dfsStack.isEmpty()) {
                    Node prevNode = dfsStack.peek();
                    connectNodes(prevNode, currentNode);
                }
                dfsStack.push(currentNode);
            }
        } else {
            // Create a new node at this position
            Node newNode = new Node(parent, gridX, gridY);
            nodes.add(newNode);

            // Connect with previous node if available
            if (currentNode != null) {
                connectNodes(currentNode, newNode);
            }

            // Update current node
            currentNode = newNode;
            currentNode.markVisited();

            // If we're in auto-explore mode, update DFS stack
            if (autoExplore) {
                dfsStack.push(currentNode);
            }
        }

        // Add to visited positions for fog clearing if not too close to existing positions
        boolean alreadyVisited = false;
        for (PVector pos : visitedPositions) {
            if (PVector.dist(pos, gridPos) < stepSize / 2) {
                alreadyVisited = true;
                break;
            }
        }

        if (!alreadyVisited) {
            visitedPositions.add(gridPos.copy());
            updateFog();
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

    void exploreDFS() {
        if (dfsStack.isEmpty() || tank == null) {
            return; // Nothing to explore
        }

        if (autoExplore) {
            // Get next direction for DFS
            PVector nextPos = getNextDFSPosition();

            if (nextPos != null) {
                // Set tank direction based on next position
                float dx = nextPos.x - tank.position.x;
                float dy = nextPos.y - tank.position.y;

                // Determine direction based on largest component
                if (Math.abs(dx) > Math.abs(dy)) {
                    // Move horizontally
                    if (dx > 0) {
                        tank.state = 1; // Right
                    } else {
                        tank.state = 2; // Left
                    }
                } else {
                    // Move vertically
                    if (dy > 0) {
                        tank.state = 3; // Down
                    } else {
                        tank.state = 4; // Up
                    }
                }
            } else {
                // Backtrack if no unvisited neighbors
                if (dfsStack.size() > 1) {
                    dfsStack.pop(); // Remove current
                    Node backtrackNode = dfsStack.peek();

                    // Set tank direction to move toward backtrack node
                    float dx = backtrackNode.position.x - tank.position.x;
                    float dy = backtrackNode.position.y - tank.position.y;

                    // Determine direction based on largest component
                    if (Math.abs(dx) > Math.abs(dy)) {
                        // Move horizontally
                        if (dx > 0) {
                            tank.state = 1; // Right
                        } else {
                            tank.state = 2; // Left
                        }
                    } else {
                        // Move vertically
                        if (dy > 0) {
                            tank.state = 3; // Down
                        } else {
                            tank.state = 4; // Up
                        }
                    }
                } else {
                    // Exploration complete
                    tank.state = 0; // Stop
                    autoExplore = false;
                }
            }
        }
    }

    // Get next position to explore based on DFS
    PVector getNextDFSPosition() {
        if (dfsStack.isEmpty() || currentNode == null) return null;

        // Get possible moves from current position
        ArrayList<PVector> possibleMoves = new ArrayList<PVector>();
        possibleMoves.add(new PVector(currentNode.position.x, currentNode.position.y - stepSize)); // Up
        possibleMoves.add(new PVector(currentNode.position.x + stepSize, currentNode.position.y)); // Right
        possibleMoves.add(new PVector(currentNode.position.x, currentNode.position.y + stepSize)); // Down
        possibleMoves.add(new PVector(currentNode.position.x - stepSize, currentNode.position.y)); // Left

        // Check each possible move
        for (PVector newPos : possibleMoves) {
            // Skip if out of bounds
            if (newPos.x < 0 || newPos.x >= parent.width || newPos.y < 0 || newPos.y >= parent.height) {
                continue;
            }

            // Skip if obstacle (would need to check tree collisions here)
            // For now, we'll just check if the position already has a node
            if (!hasNodeAt(newPos)) {
                return newPos; // Found an unexplored position
            } else {
                // Check if the node at this position is unvisited
                Node node = getNodeAt(newPos);
                if (!node.visited) {
                    return newPos; // Found an unvisited node
                }
            }
        }

        return null; // No valid moves found
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