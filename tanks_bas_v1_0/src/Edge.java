import processing.core.PApplet;

public class Edge extends PApplet {
    Node source;
    Node destination;
    float weight;

    Edge(Node source, Node destination, float weight){
        this.source = source;
        this.destination = destination;
        this.weight = weight;
    }
}
