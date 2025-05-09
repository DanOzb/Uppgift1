import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;

class Node {
    private PApplet parent;
    PVector position;
    ArrayList<Edge> edges;
    boolean visited;
    int visitCount;

    // Add exploration metrics
    float explorationValue;
    float lastVisitTime;

    //A*
    float fScore;
    float gScore;

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

    void addEdge(Node destination, float weight){
        // Check if edge already exists
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

    void markVisited(){
        visited = true;
        visitCount++;
        lastVisitTime = parent.millis();
        explorationValue = 0; // Reset exploration value upon visit
    }

    void display(){ //TODO: kolla i exploration manager, den här kanske ska ligga här
        // This is now handled in ExplorationManager to centralize visualization
        parent.fill(visited ? parent.color(150, 200, 150) : parent.color(200, 150, 150), 150);
        parent.noStroke();
        parent.ellipse(position.x, position.y, 12, 12);
    }

    boolean isNeighbor(Node other) { //TODO: kanske behövs återkom efter explorationmanager
        for (Edge edge : edges) {
            if (edge.destination == other) {
                return true;
            }
        }
        return false;
    }
}