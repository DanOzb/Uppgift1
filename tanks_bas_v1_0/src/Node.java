import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;

/**
 * Represents a node in the navigation graph.
 * Nodes store position data, connection information, and exploration data.
 */

class Node {
    PApplet parent;
    PVector position;
    ArrayList<Edge> edges;
    boolean visited;
    int visitCount;

    float explorationValue;
    float lastVisitTime;

    //A*
    float fScore;
    float gScore;
    /**
     * Creates a new Node at the specified coordinates.
     *
     * @param parent Reference to the Processing applet
     * @param x X-coordinate of the node
     * @param y Y-coordinate of the node
     */
    Node(PApplet parent, float x, float y){
        this.parent = parent;
        position = new PVector(x, y);
        edges = new ArrayList<>();
        visited = false;
        visitCount = 0;
        explorationValue = 100.0f; // Start with high exploration value
        lastVisitTime = parent.millis();

        fScore = Float.MAX_VALUE;
        gScore = Float.MAX_VALUE;
    }
    /**
     * Adds an edge from this node to another node with the specified weight.
     * Only adds the edge if it doesn't already exist.
     *
     * @param destination The node to connect to
     * @param weight The weight/cost of the connection
     */
    void addEdge(Node destination, float weight){
        boolean edgeExists = false;
        for (Edge edge : edges) {
            if (edge.destination == destination) {
                edgeExists = true;
                break;
            }
        }

        if (!edgeExists) {
            edges.add(new Edge(this, destination, weight));
        }
    }
    /**
     * Marks this node as visited.
     * Updates visit count, last visit time, and resets exploration value.
     */
    void markVisited(){
        visited = true;
        visitCount++;
        lastVisitTime = parent.millis();
        explorationValue = 0;
    }
    /**
     * Displays the node on screen.
     * Visualizes the node differently based on whether it has been visited.
     */
    void display(){ //TODO: kolla i exploration manager, den här kanske ska ligga här
        parent.fill(visited ? parent.color(150, 200, 150) : parent.color(200, 150, 150), 150);
        parent.noStroke();
        parent.ellipse(position.x, position.y, 12, 12);
    }
    /**
     * Checks if another node is a neighbor of this node.
     *
     * @param other The node to check
     * @return true if the other node is a neighbor, false otherwise
     */
    boolean isNeighbor(Node other) { //TODO: kanske behövs återkom efter explorationmanager
        for (Edge edge : edges) {
            if (edge.destination == other) {
                return true;
            }
        }
        return false;
    }
}