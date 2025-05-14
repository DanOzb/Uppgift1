/**
 * Represents an edge in the navigation graph.
 * Edges connect nodes and store information about the connection such as weight and traversability.
 */

class Edge {
    Node source;
    Node destination;
    float weight;
    boolean traversable;
    int useCount;
    /**
     * Creates a new edge between two nodes with a specified weight.
     *
     * @param source The starting node of the edge
     * @param destination The ending node of the edge
     * @param weight The cost/weight of traversing this edge
     */
    Edge(Node source, Node destination, float weight) {
        this.source = source;
        this.destination = destination;
        this.weight = weight;
        this.traversable = true;
        this.useCount = 0;
    }
}
