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
     * Constructor for creating a navigation node.
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
     * Adds a directed edge from this node to another node.
     * Only creates the edge if it doesn't already exist.
     * @param destination The target node to connect to
     * @param weight The traversal cost of the connection
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
     * Marks this node as visited and updates exploration metrics.
     * Resets exploration value and updates visit tracking.
     */
    void markVisited(){
        visited = true;
        visitCount++;
        lastVisitTime = parent.millis();
        explorationValue = 0;
    }
}