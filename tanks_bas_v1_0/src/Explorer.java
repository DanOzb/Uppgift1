import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;

public class Explorer {
    private PApplet parent;
    PVector position;
    ArrayList<Node> nodes;
    ArrayList<PVector> path;
    ArrayList<Node> stack;  // Stack for DFS
    Tree[] trees;
    float stepSize;
    Environment environment;

    Explorer(PApplet parent,float x, float y, float step) {
        position = new PVector(x, y);
        nodes = new ArrayList<Node>();
        path = new ArrayList<PVector>();
        stack = new ArrayList<Node>();
        stepSize = step;
        //trees = obs;
        this.parent = parent;
        this.environment = new Environment(parent);

        // Create the starting node
        Node startNode = new Node(parent, x, y);
        nodes.add(startNode);
        path.add(position.copy());

        // Add the starting node to DFS stack
        stack.add(startNode);
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

    PVector[] getPossibleMoves() {
        // Four possible directions: up, right, down, left
        PVector[] moves = new PVector[4];
        moves[0] = new PVector(position.x, position.y - stepSize);  // Up
        moves[1] = new PVector(position.x + stepSize, position.y);  // Right
        moves[2] = new PVector(position.x, position.y + stepSize);  // Down
        moves[3] = new PVector(position.x - stepSize, position.y);  // Left
        return moves;
    }

    void exploreDFS() {
        if (stack.isEmpty()) {
            return;  // Exploration complete
        }

        // Get the current node from the top of the stack
        Node current = stack.get(stack.size() - 1);
        current.visited = true;

        // Move the explorer to the current node's position
        position = current.position.copy();

        // Get possible moves from current position
        PVector[] possibleMoves = getPossibleMoves();
        boolean foundNewNode = false;

        for (PVector newPos : possibleMoves) {
            // Skip if out of bounds
            if (newPos.x < 0 || newPos.x >= 800 || newPos.y < 0 || newPos.y >= 800) {
                continue;
            }

            // Skip if position is an obstacle
//            if (isTree(newPos)) {
//                continue;
//            }

            // Check if there's already a node at this position
            Node existingNode = getNodeAt(newPos);

            if (existingNode == null) {
                // Create a new node at this position
                Node newNode = environment.createNode(newPos.x, newPos.y);
                environment.setPosition(newNode);
                newNode.display();
                System.out.println("node placed");
                nodes.add(newNode);

                // Connect the nodes
                current.addNeighbor(newNode);
                newNode.addNeighbor(current);

                // Add to DFS stack and move there
                stack.add(newNode);
                path.add(newPos.copy());
                foundNewNode = true;
                break;
            } else if (!existingNode.visited) {
                // Connect existing unvisited node
                current.addNeighbor(existingNode);
                existingNode.addNeighbor(current);

                // Add to DFS stack and move there
                stack.add(existingNode);
                path.add(newPos.copy());
                foundNewNode = true;
                break;
            }
        }

        if (!foundNewNode) {
            // Backtrack: remove the current node from the stack
            stack.remove(stack.size() - 1);

            // Move back to previous node if available
            if (!stack.isEmpty()) {
                Node prev = stack.get(stack.size() - 1);
                position = prev.position.copy();
                path.add(position.copy());
            }
        }
    }
    void display() {

    }



}
