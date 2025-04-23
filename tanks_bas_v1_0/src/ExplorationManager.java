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
        Node existingNode = null;

        // Look for existing node at this grid position
        for (Node node : nodes) {
            if (PVector.dist(node.position, gridPos) < stepSize/2) {
                existingNode = node;
                break;
            }
        }

        if (existingNode == null) {
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

            // If we're in auto-explore mode, add to DFS stack if not already present
            if (autoExplore && !dfsStackContains(currentNode)) {
                dfsStack.push(currentNode);
                System.out.println("Added new node to DFS stack, size now: " + dfsStack.size());
            }

            // Add to visited positions for fog clearing
            visitedPositions.add(gridPos.copy());
            updateFog();
        }
        else if (existingNode != currentNode) {
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

            // If we're in auto-explore mode and this node isn't in the stack, add it
            if (autoExplore && !dfsStackContains(currentNode)) {
                dfsStack.push(currentNode);
                System.out.println("Added existing node to DFS stack, size now: " + dfsStack.size());
            }
        }
    }
    boolean dfsStackContains(Node node) {
        for (Node stackNode : dfsStack) {
            if (stackNode == node) {
                return true;
            }
        }
        return false;
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
    boolean borderCollisionOccurred = false;

    void handleBorderCollision() {
        borderCollisionOccurred = true;
        System.out.println("Border collision detected, will recalculate path");

        // Force the current node to be marked as fully explored
        if (currentNode != null) {
            // Mark all directions from this node as "visited" to force backtracking
            currentNode.markVisited();

            // Force a path recalculation by removing current node from stack
            if (!dfsStack.isEmpty() && dfsStack.peek() == currentNode) {
                dfsStack.pop();
            }
        }
    }

    void exploreDFS() {
        if (!autoExplore || tank == null) {
            return; // Nothing to explore
        }

        System.out.println("DFS exploration active with stack size: " + dfsStack.size());

        // Handle border collision recovery
        if (borderCollisionOccurred) {
            borderCollisionOccurred = false;

            // Try to find a new path after collision
            if (!dfsStack.isEmpty()) {
                // Get the next node to move to (backtracking)
                Node nextNode = dfsStack.peek();
                PVector nextPos = nextNode.position;

                // Calculate direction based on the backtracking node
                float dx = nextPos.x - tank.position.x;
                float dy = nextPos.y - tank.position.y;

                // Set direction based on the largest component
                if (Math.abs(dx) > Math.abs(dy)) {
                    tank.state = dx > 0 ? 1 : 2; // Right or Left
                } else {
                    tank.state = dy > 0 ? 3 : 4; // Down or Up
                }

                System.out.println("Recovered from border collision, moving to: " + nextPos.x + ", " + nextPos.y);
                return;
            }
        }

        // Regular DFS exploration logic
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
                if (Math.abs(dx) > Math.abs(dy)) {
                    // Horizontal movement predominant
                    if (dx > 1) {
                        tank.state = 1; // Move right
                        System.out.println("Auto-moving RIGHT");
                    } else if (dx < -1) {
                        tank.state = 2; // Move left
                        System.out.println("Auto-moving LEFT");
                    } else {
                        tank.state = 0; // Stop if we're very close
                    }
                } else {
                    // Vertical movement predominant
                    if (dy > 1) {
                        tank.state = 3; // Move down
                        System.out.println("Auto-moving DOWN");
                    } else if (dy < -1) {
                        tank.state = 4; // Move up
                        System.out.println("Auto-moving UP");
                    } else {
                        tank.state = 0; // Stop if we're very close
                    }
                }
            } else {
                // No valid next position, stop the tank
                tank.state = 0;
                System.out.println("No valid next position, stopping tank");
                // Optionally disable auto-explore if exploration is complete
                if (dfsStack.isEmpty()) {
                    autoExplore = false;
                    System.out.println("Exploration complete, auto-explore disabled");
                }
            }
        }
    }

    PVector getNextDFSPosition() {
        if (dfsStack.isEmpty()) {
            System.out.println("DFS stack empty");
            return null;
        }

        if (currentNode == null) {
            System.out.println("Current node is null");
            return null;
        }

        System.out.println("Current node position: " + currentNode.position.x + ", " + currentNode.position.y);

        // Mark the current node as visited since we're processing it
        currentNode.markVisited();

        // Define the four possible moves in a specific order (right, down, left, up)
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
            boolean isObstacle = checkForObstacle(newPos);
            if (isObstacle) {
                continue;
            }

            // Check if this position has already been visited
            boolean hasVisitedNode = false;
            Node existingNode = null;

            for (Node node : nodes) {
                if (PVector.dist(node.position, newPos) < stepSize/2) {
                    existingNode = node;
                    if (node.visited) {
                        hasVisitedNode = true;
                    }
                    break;
                }
            }

            // If there's no node or the node exists but hasn't been visited
            if (existingNode == null || !hasVisitedNode) {
                System.out.println("Found direction to explore at: " + newPos.x + ", " + newPos.y);
                return newPos;
            }
        }

        // If we get here, all adjacent positions are either obstacles, out of bounds, or already visited
        // We need to backtrack
        System.out.println("No unexplored positions found, need to backtrack");

        // Remove current node from stack to backtrack
        if (!dfsStack.isEmpty()) {
            dfsStack.pop(); // Remove current node

            // If there are more nodes to explore
            if (!dfsStack.isEmpty()) {
                Node backtrackNode = dfsStack.peek();
                System.out.println("Backtracking to: " + backtrackNode.position.x + ", " + backtrackNode.position.y);
                return backtrackNode.position;
            }
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
    // Add this helper method to check for obstacles
    boolean checkForObstacle(PVector pos) {
        // This will check if there's a tree or other obstacle at the given position
        // For now, a simple implementation that assumes Tank.checkForCollisions() handles this
        // You can enhance this with actual collision detection with obstacles

        // Example obstacle check (you need to implement based on your game's objects)
        // For trees, you could do something like:

        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            if (game.allTrees != null) {
                for (Tree tree : game.allTrees) {
                    if (tree != null) {
                        float dist = PVector.dist(pos, tree.position);
                        if (dist < tree.radius + stepSize/2) {
                            return true; // There's an obstacle
                        }
                    }
                }
            }
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