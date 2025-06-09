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
    Object object;
    /**
     * Constructor for sensor detection data.
     * @param position Position where the object was detected
     * @param type Type of object detected
     * @param object Reference to the actual detected object
     */
    SensorDetection(PVector position, ObjectType type, Object object) {
        this.position = position;
        this.type = type;
        this.object = object;
    }
}

/**
 * Handles Line of Sight (LOS) detection for tanks.
 * Detects objects in the tank's line of sight and returns information about them.
 * In combat mode, sensor spins independently to scan for enemies.
 */
class Sensor {
    PApplet parent;
    Tank tank;
    float maxViewDistance;
    float radianViewAngle;
    Tank lockedTarget = null;
    boolean isLockedOn = false;

    float rotationAngle = 0;
    float rotationSpeed = PApplet.radians(3);
    boolean isSpinning = false;
    boolean combatMode = false;
    /**
     * Constructor for tank sensor system.
     * @param parent The Processing PApplet instance
     * @param tank The tank this sensor belongs to
     * @param maxViewDistance Maximum detection range
     * @param viewAngle Field of view angle in radians
     */
    Sensor(PApplet parent, Tank tank, float maxViewDistance, float viewAngle) {
        this.parent = parent;
        this.tank = tank;
        this.maxViewDistance = maxViewDistance;
        this.radianViewAngle = viewAngle;
    }

    /**
     * Performs line of sight scan and detects objects in sensor range.
     * Handles both normal scanning and combat mode spinning behavior.
     * @param allTanks Array of tanks to scan for
     * @param allTrees Array of trees to scan for
     * @return List of detected objects with positions and types
     */
    ArrayList<SensorDetection> scan(Tank[] allTanks, Tree[] allTrees) {
        validateLockedTarget();
        tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;

        ArrayList<SensorDetection> detections = new ArrayList<>();

        updateCombatMode();

        if (combatMode && !isLockedOn) {
            isSpinning = true;
            rotationAngle += rotationSpeed;
            if (rotationAngle >= PApplet.TWO_PI) {
                rotationAngle -= PApplet.TWO_PI;
            }
        } else {
            isSpinning = false;
        }

        PVector direction = getSensorDirection();

        PVector start = tank.position.copy();
        PVector end = PVector.add(start, PVector.mult(direction, maxViewDistance));

        if (combatMode && isSpinning) {
            scanForEnemies(allTanks, start, end, detections);
        } else {
            performFullScan(allTanks, allTrees, start, end, detections);
        }

        visualize(detections);
        return detections;
    }

    /**
     * Updates combat mode status based on current game state.
     */
    private void updateCombatMode() {
        if (parent instanceof tanks_bas_v1_0) {
            tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;
            combatMode = game.team0.explorationManager.combatMode;
        }
    }

    /**
     * Gets the current direction vector of the sensor.
     * @return Normalized direction vector for sensor scanning
     */
    PVector getSensorDirection() {
        if (combatMode && isSpinning) {
            PVector direction = new PVector(PApplet.cos(rotationAngle), PApplet.sin(rotationAngle));
            direction.normalize();
            return direction;
        } else {
            return getTankDirection();
        }
    }

    /**
     * Scans specifically for enemy tanks during combat mode spinning.
     * @param allTanks Array of tanks to check
     * @param start Sensor start position
     * @param end Sensor end position
     * @param detections List to add detections to
     */
    private void scanForEnemies(Tank[] allTanks, PVector start, PVector end, ArrayList<SensorDetection> detections) {
        if (isLockedOn && lockedTarget != null && !lockedTarget.isDestroyed) {
            return;
        }

        for (Tank otherTank : allTanks) {
            if (otherTank != null && otherTank != tank && otherTank.col != tank.col) {
                PVector intersection = lineCircleIntersection(start, end, otherTank.position, otherTank.diameter / 2);
                if (intersection != null) {
                    setLockedTarget(otherTank);
                    setIsLockedOn(true);
                    isSpinning = false;

                    detections.add(new SensorDetection(intersection.copy(), SensorDetection.ObjectType.ENEMY, otherTank));

                    System.out.println("Sensor: " + tank.name + " found enemy tank " + otherTank.name + " while spinning");
                    break;
                }
            }
        }
    }

    /**
     * Performs comprehensive scan for all object types.
     * @param allTanks Array of tanks to scan
     * @param allTrees Array of trees to scan
     * @param start Sensor start position
     * @param end Sensor end position
     * @param detections List to add detections to
     */
    private void performFullScan(Tank[] allTanks, Tree[] allTrees, PVector start, PVector end, ArrayList<SensorDetection> detections) {
        tanks_bas_v1_0 game = (tanks_bas_v1_0) parent;

        PVector borderIntersection = checkBorderIntersection(start, end);
        if (borderIntersection != null) {
            end = borderIntersection;
            detections.add(new SensorDetection(end.copy(), SensorDetection.ObjectType.BORDER, null));
        }
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

        for (Tank otherTank : allTanks) {
            if (otherTank != null && otherTank != tank && !otherTank.isDestroyed) {
                PVector intersection = lineCircleIntersection(start, end, otherTank.position, otherTank.diameter / 2);
                if (intersection != null) {
                    SensorDetection.ObjectType type;

                    if (otherTank.col == tank.col) {
                        type = SensorDetection.ObjectType.FRIEND;
                    } else {
                        type = SensorDetection.ObjectType.ENEMY;
                        setLockedTarget(otherTank);
                        if (combatMode) {
                            setIsLockedOn(true);
                        }
                    }

                    detections.add(new SensorDetection(intersection.copy(), type, otherTank));
                }
            }
        }
        if (lineRectIntersection(start, end, game.team0.basePosition, game.team0.baseSize)) {
            SensorDetection.ObjectType type = (tank.col == game.team0.teamColor) ?
                    SensorDetection.ObjectType.FRIEND :
                    SensorDetection.ObjectType.BASE;

            PVector detectionPos = (type == SensorDetection.ObjectType.BASE) ?
                    tank.position.copy() :
                    PVector.add(game.team0.basePosition, PVector.mult(game.team0.baseSize, 0.5f));

            detections.add(new SensorDetection(detectionPos, type, game.team0));
        }
        if (lineRectIntersection(start, end, game.team1.basePosition, game.team1.baseSize) && !game.team0.getEnemyBaseDetected()) {
            SensorDetection.ObjectType type = (tank.col == game.team1.teamColor) ?
                    SensorDetection.ObjectType.FRIEND :
                    SensorDetection.ObjectType.BASE;

            PVector detectionPos = (type == SensorDetection.ObjectType.BASE) ?
                    end :
                    PVector.add(game.team1.basePosition, PVector.mult(game.team1.baseSize, 0.5f));

            detections.add(new SensorDetection(detectionPos, type, game.team1));
        }
    }
    /**
     * Sets the currently locked target for this sensor.
     * @param tank The tank to lock onto
     */
    public void setLockedTarget(Tank tank) {
        lockedTarget = tank;
    }

    /**
     * Gets the tank's current facing direction based on movement state.
     * @return Normalized direction vector
     */
    PVector getTankDirection() {
        PVector direction = new PVector(0, 0);

        switch (tank.state) {
            case 0: // Stationary
                direction.x = 1;
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
     * Calculates intersection point between a line and circle.
     * @param lineStart Start point of the line
     * @param lineEnd End point of the line
     * @param circleCenter Center of the circle
     * @param circleRadius Radius of the circle
     * @return Intersection point or null if no intersection
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

        float t1 = (-b - discriminant) / (2 * a);
        float t2 = (-b + discriminant) / (2 * a);

        if (t1 >= 0 && t1 <= 1) {
            return PVector.add(lineStart, PVector.mult(d, t1));
        }

        if (t2 >= 0 && t2 <= 1) {
            return PVector.add(lineStart, PVector.mult(d, t2));
        }

        return null;
    }

    /**
     * Checks if sensor line intersects with game boundaries.
     * @param start Start point of sensor line
     * @param end End point of sensor line
     * @return Border intersection point or null
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
     * Checks if a line intersects with a rectangular area.
     * @param lineStart Start point of the line
     * @param lineEnd End point of the line
     * @param rectPos Position of rectangle (top-left)
     * @param rectSize Size of rectangle (width, height)
     * @return true if line intersects rectangle
     */
    private boolean lineRectIntersection(PVector lineStart, PVector lineEnd, PVector rectPos, PVector rectSize) {
        float x1 = rectPos.x;
        float y1 = rectPos.y;
        float x2 = rectPos.x + rectSize.x;
        float y2 = rectPos.y + rectSize.y;

        if ((lineStart.x >= x1 && lineStart.x <= x2 && lineStart.y >= y1 && lineStart.y <= y2) ||
                (lineEnd.x >= x1 && lineEnd.x <= x2 && lineEnd.y >= y1 && lineEnd.y <= y2)) {
            return true;
        }

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
     * Checks intersection between two line segments.
     * @param p1 Start of first line segment
     * @param p2 End of first line segment
     * @param p3 Start of second line segment
     * @param p4 End of second line segment
     * @return true if segments intersect
     */
    private boolean lineSegmentIntersection(PVector p1, PVector p2, PVector p3, PVector p4) {
        float d1x = p2.x - p1.x;
        float d1y = p2.y - p1.y;
        float d2x = p4.x - p3.x;
        float d2y = p4.y - p3.y;

        float denominator = d1y * d2x - d1x * d2y;
        if (denominator == 0) {
            return false;
        }

        float s = ((p1.x - p3.x) * d1y - (p1.y - p3.y) * d1x) / denominator;
        float t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / -denominator;

        return s >= 0 && s <= 1 && t >= 0 && t <= 1;
    }

    /**
     * Visualizes the sensor's line of sight and detected objects.
     * Shows different visual styles for different sensor modes.
     * @param detections List of detected objects to visualize
     */
    void visualize(ArrayList<SensorDetection> detections) {
        PVector direction = getSensorDirection();
        PVector start = tank.position.copy();

        if (getIsLockedOn() && lockedTarget != null) {
            parent.pushMatrix();

            parent.stroke(255, 0, 0, 200);
            parent.strokeWeight(3);
            parent.line(start.x, start.y, lockedTarget.position.x, lockedTarget.position.y);

            parent.noFill();
            parent.ellipse(lockedTarget.position.x, lockedTarget.position.y, 30, 30);
            parent.line(lockedTarget.position.x - 15, lockedTarget.position.y, lockedTarget.position.x + 15, lockedTarget.position.y);
            parent.line(lockedTarget.position.x, lockedTarget.position.y - 15, lockedTarget.position.x, lockedTarget.position.y + 15);

            parent.fill(0);
            parent.textSize(12);
            parent.text("LOCKED: " + lockedTarget.name, lockedTarget.position.x + 15, lockedTarget.position.y);

            parent.popMatrix();
            return;
        }

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

        if (getIsLockedOn()) {
            parent.stroke(255, 0, 0, 200);
            parent.strokeWeight(3);
        } else if (isSpinning && combatMode) {
            parent.stroke(255, 100, 0, 180);
            parent.strokeWeight(2);
        } else {
            parent.stroke(255, 255, 0, 150);
            parent.strokeWeight(2);
        }

        if (detections.isEmpty()) {
            PVector end = PVector.add(start, PVector.mult(direction, maxViewDistance));
            parent.line(start.x, start.y, end.x, end.y);
        } else {
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
                parent.noFill();

                if (getIsLockedOn() && closest.type == SensorDetection.ObjectType.ENEMY &&
                        closest.object == lockedTarget) {
                    parent.stroke(255, 0, 0, 255);
                    parent.strokeWeight(4);
                    parent.ellipse(closest.position.x, closest.position.y, 30, 30);
                    parent.line(closest.position.x - 15, closest.position.y, closest.position.x + 15, closest.position.y);
                    parent.line(closest.position.x, closest.position.y - 15, closest.position.x, closest.position.y + 15);
                } else {
                    switch (closest.type) {
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

                parent.fill(0);
                parent.textSize(12);
                String label = closest.type.toString();
                if (isSpinning && combatMode) {
                    label = "SCANNING: " + closest.type.toString();
                } else if (getIsLockedOn() && closest.type == SensorDetection.ObjectType.ENEMY &&
                        closest.object == lockedTarget) {
                    label = "LOCKED: " + ((Tank) closest.object).name;
                }
                parent.text(label, closest.position.x + 15, closest.position.y);
            }
        }

        parent.popMatrix();
    }
    /**
     * Gets the current lock-on status of the sensor.
     * @return true if sensor is locked onto a target
     */
    public boolean getIsLockedOn() {
        return isLockedOn;
    }
    /**
     * Sets the lock-on status of the sensor.
     * @param isLockedOn true to enable lock-on mode
     */
    public void setIsLockedOn(boolean isLockedOn) {
        this.isLockedOn = isLockedOn;
    }
    /**
     * Checks if the sensor is currently in spinning scan mode.
     * @return true if sensor is spinning to scan for targets
     */
    public boolean isSpinning() {
        return isSpinning;
    }
    /**
     * Checks if the sensor is in combat mode.
     * @return true if sensor is in combat scanning mode
     */
    public boolean isCombatMode() {
        return combatMode;
    }
    /**
     * Validates that the locked target is still alive and removes it if destroyed.
     */
    void validateLockedTarget() {
        if (lockedTarget != null && lockedTarget.isDestroyed) {
            lockedTarget = null;
            isLockedOn = false;
        }
    }
}