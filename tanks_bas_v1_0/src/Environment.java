import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class Environment {
    PApplet parent;
    ArrayList<Node> nodes;
    Node position;
    Stack<Node> paths;

    Environment(PApplet parent) {
        this.parent = parent;
        nodes = new ArrayList<>(64);
    }

    void setPosition(Node node) {
        nodes.add(node);
    }

    Node createNode(float posX, float posY){
        Node node = new Node(parent, posX, posY);
        nodes.add(node);
        return node;
    }

    Node moveToNode(Node destination){
        if(position != null){
            boolean connected = false;
            for(Edge edge : position.edges){
                if(edge.destination == destination && edge.traversable){
                    connected = true;
                    break;
                }
            }
            if (!connected) {
                parent.println("Cannot move: no traversable edge to destination");
                return position;
            }
        }
        position = destination;
        position.markVisited();
        paths.push(position);
        return position;
    }

    Node backtrack() {
        if (paths.size() > 1) {
            paths.pop(); // Remove current position
            position = paths.peek(); // Go back to previous position
            return position;
        } else {
            parent.println("Cannot backtrack: at starting point");
            return position;
        }
    }

    ArrayList<Node> getUnvisitedNeighbors() {
        ArrayList<Node> unvisited = new ArrayList<Node>();
        if (position != null) {
            for (Edge edge : position.edges) {
                if (!edge.destination.visited && edge.traversable) {
                    unvisited.add(edge.destination);
                }
            }
        }
        return unvisited;
    }

    ArrayList<Node> dfs(Node start, Node target) {
        ArrayList<Node> path = new ArrayList<Node>();
        HashMap<Node, Node> parentMap = new HashMap<Node, Node>();
        Stack<Node> stack = new Stack<Node>();
        HashSet<Node> visited = new HashSet<Node>();

        stack.push(start);
        visited.add(start);

        while (!stack.empty()) {
            Node current = stack.pop();

            if (current == target) {
                // Reconstruct path
                Node step = current;
                while (step != null) {
                    path.add(0, step);
                    step = parentMap.get(step);
                }
                return path;
            }

            for (Edge edge : current.edges) {
                if (!visited.contains(edge.destination) && edge.traversable) {
                    visited.add(edge.destination);
                    parentMap.put(edge.destination, current);
                    stack.push(edge.destination);
                }
            }
        }

        return path; // Empty if no path found
    }

}
