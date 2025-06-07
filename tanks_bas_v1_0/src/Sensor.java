import processing.core.*;
import java.util.*;

/**
 * Represents a detected object from the sensor.
 * Stores the type of object and its position.
 */
class SensorDetection {
    public enum ObjectType {
        FRIEND,
        ENEMY,
        TREE,
        BORDER,
        BASE
    }

    PVector position;
    ObjectType type;
    Object object; // Reference to the detected object

    SensorDetection(PVector position, ObjectType type, Object object) {
        this.position = position;
        this.type = type;
        this.object = object;
    }
    public ObjectType getType() {
        return type;
    }
}

/**
 * Handles Line of Sight (LOS) detection for tanks.
 * Detects objects in the tank's line of sight and returns information about them.
 */
class Sensor {
    PApplet parent;
    Tank tank;
    float maxViewDistance;
    float radianViewAngle; // Field of view angle in radians
    Tank lockedTarget = null;
    boolean isLockedOn = false;

    SensorDetection detect;

    Sensor(PApplet parent, Tank tank, float maxViewDistance, float viewAngle) {
        this.parent = parent;
        this.tank = tank;
        this.maxViewDistance = maxViewDistance;
        this.radianViewAngle = viewAngle;
    }

    /**
     * Performs a line of sight scan in the tank's current direction.
     *
     * @param allTanks Array of all tanks to check against
     * @param allTrees Array of all trees to check against
     * @return List of detected objects with their positions and types
     */
    ArrayList<SensorDetection> scan(Tank[] allTanks, Tree[] allTrees) {
        ArrayList<SensorDetection> detections = new ArrayList<>();
        if(isLockedOn) return detections;

        // Determine the tank's facing direction based on its state
        PVector direction = getTankDirection();

        // Calculate the start and end points for the ray
        PVector start = tank.position.copy();
        PVector end = PVector.add(start, PVector.mult(direction, maxViewDistance));

        // Check for border intersections and adjust end point if needed
        PVector borderIntersection = checkBorderIntersection(start, end);
        if (borderIntersection != null) {
            end = borderIntersection;
            detections.add(new SensorDetection(end.copy(), SensorDetection.ObjectType.BORDER, null));
        }

        // Check for tree intersections
        PVector closestTreeIntersection = null;
        Tree closestTree = null;
        float closestTreeDistance = Float.MAX_VALUE;

        for (Tree tree : allTrees) {
            if (tree != null) {
                PVector intersection = lineCircleIntersection(start, end, tree.position, tree.radius);
                if (intersection != null) {
                    float distance = PVector.dist(start, intersection);
                    if (distance < closestTreeDistance) {
                        closestTreeDistance = distance;
                        closestTreeIntersection = intersection;
                        closestTree = tree;
                    }
                }
            }
        }

        if (closestTreeIntersection != null) {
            end = closestTreeIntersection;
            detections.add(new SensorDetection(end.copy(), SensorDetection.ObjectType.TREE, closestTree));
        }

        // Check for tank intersections that are before the end point
        for (Tank otherTank : allTanks) {
            if (otherTank != null && otherTank != tank) {
                PVector intersection = lineCircleIntersection(start, end, otherTank.position, otherTank.diameter/2);
                if (intersection != null) {
                    SensorDetection.ObjectType type;

                    // Identify friend or enemy based on color
                    if (otherTank.col == tank.col) {
                        type = SensorDetection.ObjectType.FRIEND;
                    } else {
                        type = SensorDetection.ObjectType.ENEMY;
                        setIsLockedOn(true);
                        setLockedTarget(otherTank);
                        System.out.println("Sensor: " + tank.name + " found enemy tank " + otherTank.name);
                    }

                    // Add detection with a reference to the actual tank
                    detections.add(new SensorDetection(intersection.copy(), type, otherTank));
                }
            }
        }

        // Check for base detections
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;

            // Detect team0 base
            if (lineRectIntersection(start, end, game.team0.basePosition, game.team0.baseSize)) {
                SensorDetection.ObjectType type = (tank.col == game.team0.teamColor) ?
                        SensorDetection.ObjectType.FRIEND :
                        SensorDetection.ObjectType.BASE;

                // Use tank's position for enemy bases, base center for friendly bases
                PVector detectionPos = (type == SensorDetection.ObjectType.BASE) ?
                        tank.position.copy() :
                        PVector.add(game.team0.basePosition, PVector.mult(game.team0.baseSize, 0.5f));

                detections.add(new SensorDetection(detectionPos, type, game.team0));
            }
            // Detect team1 base
            if (lineRectIntersection(start, end, game.team1.basePosition, game.team1.baseSize) && !game.team0.getEnemyBaseDetected()) {
                System.out.println("Sensor: base detected");
                    SensorDetection.ObjectType type = (tank.col == game.team1.teamColor) ?
                            SensorDetection.ObjectType.FRIEND :
                            SensorDetection.ObjectType.BASE;


                // Use tank's position for enemy bases, base center for friendly bases
                PVector detectionPos = (type == SensorDetection.ObjectType.BASE) ?
                        end :
                        PVector.add(game.team1.basePosition, PVector.mult(game.team1.baseSize, 0.5f));

                detections.add(new SensorDetection(detectionPos, type, game.team1));
            }
        }
        visualize(detections);
        return detections;
    }

    public void setLockedTarget(Tank tank) {
        System.out.println("Sensor: tank " + tank.name+ " is 0");
        tank.state = 0;
        lockedTarget = tank;
    }

    /**
     * Gets the tank's direction vector based on its state.
     *
     * @return Normalized direction vector
     */
    PVector getTankDirection() {
        PVector direction = new PVector(0, 0);

        switch (tank.state) {
            case 0: // Stationary
                direction.x = 1; // Default to facing right when stationary
                break;
            case 1: // Right
                direction.x = 1;
                break;
            case 2: // Left
                direction.x = -1;
                break;
            case 3: // Down
                direction.y = 1;
                break;
            case 4: // Up
                direction.y = -1;
                break;
            case 5: // Right+Down
                direction.x = 1;
                direction.y = 1;
                break;
            case 6: // Right+Up
                direction.x = 1;
                direction.y = -1;
                break;
            case 7: // Left+Down
                direction.x = -1;
                direction.y = 1;
                break;
            case 8: // Left+Up
                direction.x = -1;
                direction.y = -1;
                break;
        }

        direction.normalize();
        return direction;
    }

    /**
     * Checks if a line intersects with a circle.
     *
     * @param lineStart Start point of the line
     * @param lineEnd End point of the line
     * @param circleCenter Center of the circle
     * @param circleRadius Radius of the circle
     * @return Point of intersection, or null if no intersection
     */
    private PVector lineCircleIntersection(PVector lineStart, PVector lineEnd, PVector circleCenter, float circleRadius) {
        PVector d = PVector.sub(lineEnd, lineStart);
        PVector f = PVector.sub(lineStart, circleCenter);

        float a = d.dot(d);
        float b = 2 * f.dot(d);
        float c = f.dot(f) - circleRadius * circleRadius;

        float discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return null;
        }

        discriminant = (float) Math.sqrt(discriminant);

        // Two possible t values for intersection
        float t1 = (-b - discriminant) / (2 * a);
        float t2 = (-b + discriminant) / (2 * a);

        // Check if either intersection point is within the line segment
        if (t1 >= 0 && t1 <= 1) {
            return PVector.add(lineStart, PVector.mult(d, t1));
        }

        if (t2 >= 0 && t2 <= 1) {
            return PVector.add(lineStart, PVector.mult(d, t2));
        }

        return null;
    }

    /**
     * Checks if a line intersects with the game borders.
     *
     * @param start Start point of the line
     * @param end End point of the line
     * @return Point of intersection with the border, or null if no intersection
     */
    private PVector checkBorderIntersection(PVector start, PVector end) {
        float width = parent.width;
        float height = parent.height;

        // Line equation parameters
        float x1 = start.x;
        float y1 = start.y;
        float x2 = end.x;
        float y2 = end.y;

        // Check right border (x = width)
        if (x2 > width) {
            float t = (width - x1) / (x2 - x1);
            float y = y1 + t * (y2 - y1);
            if (y >= 0 && y <= height && t >= 0 && t <= 1) {
                return new PVector(width, y);
            }
        }

        // Check bottom border (y = height)
        if (y2 > height) {
            float t = (height - y1) / (y2 - y1);
            float x = x1 + t * (x2 - x1);
            if (x >= 0 && x <= width && t >= 0 && t <= 1) {
                return new PVector(x, height);
            }
        }

        // Check left border (x = 0)
        if (x2 < 0) {
            float t = (0 - x1) / (x2 - x1);
            float y = y1 + t * (y2 - y1);
            if (y >= 0 && y <= height && t >= 0 && t <= 1) {
                return new PVector(0, y);
            }
        }

        // Check top border (y = 0)
        if (y2 < 0) {
            float t = (0 - y1) / (y2 - y1);
            float x = x1 + t * (x2 - x1);
            if (x >= 0 && x <= width && t >= 0 && t <= 1) {
                return new PVector(x, 0);
            }
        }

        return null;
    }

    /**
     * Checks if a line intersects with a rectangle.
     *
     * @param lineStart Start point of the line
     * @param lineEnd End point of the line
     * @param rectPos Position of the rectangle (top-left corner)
     * @param rectSize Size of the rectangle (width, height)
     * @return True if the line intersects with the rectangle
     */
    private boolean lineRectIntersection(PVector lineStart, PVector lineEnd, PVector rectPos, PVector rectSize) {
        float x1 = rectPos.x;
        float y1 = rectPos.y;
        float x2 = rectPos.x + rectSize.x;
        float y2 = rectPos.y + rectSize.y;

        // Check if either endpoint is inside the rectangle
        if ((lineStart.x >= x1 && lineStart.x <= x2 && lineStart.y >= y1 && lineStart.y <= y2) ||
                (lineEnd.x >= x1 && lineEnd.x <= x2 && lineEnd.y >= y1 && lineEnd.y <= y2)) {
            return true;
        }

        // Check if the line intersects any of the rectangle's edges
        PVector topLeft = new PVector(x1, y1);
        PVector topRight = new PVector(x2, y1);
        PVector bottomLeft = new PVector(x1, y2);
        PVector bottomRight = new PVector(x2, y2);

        return lineSegmentIntersection(lineStart, lineEnd, topLeft, topRight) ||
                lineSegmentIntersection(lineStart, lineEnd, topRight, bottomRight) ||
                lineSegmentIntersection(lineStart, lineEnd, bottomRight, bottomLeft) ||
                lineSegmentIntersection(lineStart, lineEnd, bottomLeft, topLeft);
    }

    /**
     * Checks if two line segments intersect.
     *
     * @param p1 Start point of first line segment
     * @param p2 End point of first line segment
     * @param p3 Start point of second line segment
     * @param p4 End point of second line segment
     * @return True if the line segments intersect
     */
    private boolean lineSegmentIntersection(PVector p1, PVector p2, PVector p3, PVector p4) {
        float d1x = p2.x - p1.x;
        float d1y = p2.y - p1.y;
        float d2x = p4.x - p3.x;
        float d2y = p4.y - p3.y;

        float denominator = d1y * d2x - d1x * d2y;
        if (denominator == 0) {
            return false; // Lines are parallel
        }

        float s = ((p1.x - p3.x) * d1y - (p1.y - p3.y) * d1x) / denominator;
        float t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / -denominator;

        return s >= 0 && s <= 1 && t >= 0 && t <= 1;
    }

    /**
     * Draws the sensor's line of sight for visualization.
     * Shows red visualization when tank is locked onto a target.
     */
    void visualize(ArrayList<SensorDetection> detections) {
        PVector direction = getTankDirection();
        PVector start = tank.position.copy();

        // Check if tank is locked on by accessing the game and finding the tank agent
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            for (TankAgent agent : game.team0.agents) {
                if (agent.tank == this.tank && agent.isLockedOn()) {
                    lockedTarget = agent.getLockedTarget();
                    break;
                }
            }
        }

        parent.pushMatrix();

        // Set stroke color based on lock-on status
        if (getIsLockedOn()) {
            parent.stroke(255, 0, 0, 200); // Bright red for locked on
            parent.strokeWeight(3);
        } else {
            parent.stroke(255, 255, 0, 150); // Yellow for normal
            parent.strokeWeight(2);
        }

        if (detections.isEmpty()) {
            // No detections, draw full ray
            PVector end = PVector.add(start, PVector.mult(direction, maxViewDistance));
            parent.line(start.x, start.y, end.x, end.y);
        } else {
            // Find the closest detection
            SensorDetection closest = null;
            float minDist = Float.MAX_VALUE;

            for (SensorDetection detection : detections) {
                float dist = PVector.dist(start, detection.position);
                if (dist < minDist) {
                    minDist = dist;
                    closest = detection;
                }
            }

            if (closest != null) {
                parent.line(start.x, start.y, closest.position.x, closest.position.y);
                System.out.println(closest.type);
                parent.noFill();

                if (getIsLockedOn() && closest.type == SensorDetection.ObjectType.ENEMY &&
                        closest.object == lockedTarget) {
                    parent.stroke(255, 0, 0, 255);
                    parent.strokeWeight(4);
                    // Draw crosshairs on the locked target
                    parent.ellipse(closest.position.x, closest.position.y, 30, 30);
                    parent.line(closest.position.x - 15, closest.position.y, closest.position.x + 15, closest.position.y);
                    parent.line(closest.position.x, closest.position.y - 15, closest.position.x, closest.position.y + 15);
                } else {
                    switch(closest.type) {
                        case FRIEND:
                            parent.stroke(0, 255, 0, 200);
                            break;
                        case ENEMY:
                            parent.stroke(255, 0, 0, 200);
                            break;
                        case TREE:
                            parent.stroke(0, 150, 0, 200);
                            break;
                        case BORDER:
                            parent.stroke(150, 150, 150, 200);
                            break;
                        case BASE:
                            parent.stroke(0, 0, 255, 200);
                            break;
                    }
                    parent.ellipse(closest.position.x, closest.position.y, 20, 20);
                }

                // Draw a label for the detected object
                parent.fill(0);
                parent.textSize(12);
                String label = closest.type.toString();
                if (getIsLockedOn() && closest.type == SensorDetection.ObjectType.ENEMY &&
                        closest.object == lockedTarget) {
                    label = "LOCKED: " + ((Tank)closest.object).name;
                }
                parent.text(label, closest.position.x + 15, closest.position.y);
            }
        }

        parent.popMatrix();
    }

    public boolean getIsLockedOn() {
        return isLockedOn;
    }
    public void setIsLockedOn(boolean isLockedOn) {
        this.isLockedOn = isLockedOn;
    }
}