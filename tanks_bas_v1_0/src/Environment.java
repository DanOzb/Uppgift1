import processing.core.PApplet;

import java.util.ArrayList;

public class Environment {
    PApplet parent;
    ArrayList<Node> nodes;
    Node position;

    Environment(PApplet parent) {
        this.parent = parent;
        nodes = new ArrayList<>(64);
    }

    void setPosition(Node node) {
        nodes.add(node);

    }


}
