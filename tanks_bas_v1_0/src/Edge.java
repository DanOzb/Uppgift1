import processing.core.PVector;

public class Edge {
    Node source;
    Node destination;
    float weight;
    boolean traversable;
    int useCount;

    Edge(Node source, Node destination, float weight) {
        this.source = source;
        this.destination = destination;
        this.weight = weight;
        this.traversable = true;
        this.useCount = 0;
    }
}