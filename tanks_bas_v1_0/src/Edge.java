import processing.core.PApplet;
import processing.core.PVector;

public class Edge {
    Node source;
    Node destination;
    float weight;
    boolean traversable;
    int useCount;

    Edge(Node source, Node destination, float weight){
        this.source = source;
        this.destination = destination;
        this.weight = weight;
        this.traversable = true;
        this.useCount = 0;
    }

    void markUsed() {
        useCount++;
    }

    PVector getDirection() {
        return PVector.sub(destination.position, source.position).normalize();
    }

    void setTraversable(boolean traversable) {
        this.traversable = traversable;
    }

    boolean isTraversable() {
        return traversable;
    }

    float getLength() {
        return weight;
    }
}