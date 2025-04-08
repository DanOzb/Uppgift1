import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;

class Node {
    private PApplet parent;
    PVector position;
    ArrayList<Edge> edges;
    boolean visited;
    int visitCount;

    Node(PApplet parent, float x, float y){
        this.parent = parent;
        position = new PVector(x, y);
        edges = new ArrayList<>();
        visited = false;
        visitCount = 0;
    }

    void addEdge(Node destination, float weight){
        edges.add(new Edge(this, destination, weight));
        destination.edges.add(new Edge(this, destination, weight));
    }

    void markVisited(){
        visited = true;
        visitCount++;
    }

    //Så att vi kan se vilka noder som har blivit upptäckta
    void display(){
        parent.fill(visited ? parent.color(150, 200, 150) : parent.color(200, 150, 150));
        parent.ellipse(position.x, position.y, 20, 20);

        for(Edge edge : edges){
            parent.stroke(100, 100, 200);
            parent.line(position.x, position.y, edge.destination.position.x, edge.destination.position.y);
        }
    }



}
