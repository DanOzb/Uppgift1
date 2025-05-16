public interface CollisionHandler {
    void handleBorderCollision(Tank tank);
    void handleTreeCollision(Tank tank, Tree tree);
    boolean isReturningHome(Tank tank);
    void handleEnemyBaseCollision(Tank tank);
}
