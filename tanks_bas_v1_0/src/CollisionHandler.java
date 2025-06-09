public interface CollisionHandler {
    /**
     * Handles collision between a tank and the screen border.
     * @param tank The tank that collided with the border
     */
    void handleBorderCollision(Tank tank);
    /**
     * Handles collision between a tank and a tree.
     * @param tank The tank involved in the collision
     * @param tree The tree involved in the collision (null for persistent collisions)
     */
    void handleTreeCollision(Tank tank, Tree tree);
    /**
     * Checks if a tank is currently returning to its home base.
     * @param tank The tank to check
     * @return true if the tank is returning home, false otherwise
     */
    boolean isReturningHome(Tank tank);
    /**
     * Handles collision between a tank and an enemy base.
     * @param tank The tank that collided with the enemy base
     */
    void handleEnemyBaseCollision(Tank tank);
    /**
     * Handles collision between two tanks.
     * @param tank The first tank in the collision
     * @param tank2 The second tank in the collision
     */
    void handleTankCollision(Tank tank, Tank tank2);

}
