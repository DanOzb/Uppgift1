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

    Node(PApplet parent, float x, float y){
        this.parent = parent;
        position = new PVector(x, y);
        edges = new ArrayList<>();
        visited = false;
        visitCount = 0;
        explorationValue = 100.0f; // Start with high exploration value
        lastVisitTime = parent.millis();
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

    float getImportance() {
        // Higher value = more important to visit
        // Factors: exploration value, time since last visit, number of edges

        float timeFactor = (parent.millis() - lastVisitTime) / 5000.0f; // Time factor grows over time
        float edgeFactor = 5.0f / (edges.size() + 1.0f); // Fewer edges = higher value

        return explorationValue + timeFactor + edgeFactor;
    }

    void display(){
        // This is now handled in ExplorationManager to centralize visualization
        parent.fill(visited ? parent.color(150, 200, 150) : parent.color(200, 150, 150), 150);
        parent.noStroke();
        parent.ellipse(position.x, position.y, 12, 12);
    }

    boolean isNeighbor(Node other) {
        for (Edge edge : edges) {
            if (edge.destination == other) {
                return true;
            }
        }
        return false;
    }
}