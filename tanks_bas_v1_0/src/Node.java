import processing.core.PApplet;
import processing.core.PVector;

import java.util.ArrayList;

class Node extends PApplet {
    PVector position;
    ArrayList<Edge> edges;
    boolean visited;
    int visitCount;

    Node(float x, float y){
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
        fill(visited ? color(150, 200, 150) : color(200, 150, 150));
        ellipse(position.x, position.y, 20, 20);

        for(Edge edge : edges){
            stroke(100, 100, 200);
            line(position.x, position.y, edge.destination.position.x, edge.destination.position.y);
        }
    }



}
