import processing.core.*;

/**
 * Main game class for the tank game.
 * Handles game initialization, updates, rendering, and input processing.
 */
public class tanks_bas_v1_0 extends PApplet{
// Följande kan användas som bas inför uppgiften.
// Syftet är att sammanställa alla varabelvärden i scenariet.
// Variabelnamn har satts för att försöka överensstämma med exempelkoden.
// Klassen Tank är minimal och skickas mer med som koncept(anrop/states/vektorer).

  Collisions collisions;
  boolean left, right, up, down;
  boolean mouse_pressed;

  Team team0;
  Team team1;
  TankAgent tankAgent0;

  PImage tree_img;
  PVector tree1_pos, tree2_pos, tree3_pos;

  Tree[] allTrees   = new Tree[3];
  Tank[] allTanks   = new Tank[6];

  // Team0
  int team0Color;
  PVector team0_tank0_startpos;
  PVector team0_tank1_startpos;
  PVector team0_tank2_startpos;
  Tank tank0, tank1, tank2;

  // Team1
  int team1Color;
  PVector team1_tank0_startpos;
  PVector team1_tank1_startpos;
  PVector team1_tank2_startpos;
  Tank tank3, tank4, tank5;

  int tank_size;

  boolean gameOver;
  boolean pause;

  /**
   * Configures the game window size.
   */
  public void settings()
  {
    size(800, 800);
  }
  /**
   * Initializes the game including tanks, trees, teams, and collision systems.
   * Sets up the complete game state and all game objects.
   */
  public void setup() {
    up             = false;
    down           = false;
    mouse_pressed  = false;

    gameOver       = false;
    pause          = true;

    // Trad
    tree_img = loadImage("tree01_v2.png");
    if(tree_img == null)
      println("Image could not load");
    else{
      println("Image loaded successfully!");
    }
    tree1_pos = new PVector(230, 600);
    tree2_pos = new PVector(280, 230);
    tree3_pos = new PVector(530, 520);

    //trees
    allTrees[0] = new Tree(this, tree_img, tree1_pos.x, tree1_pos.y);
    allTrees[1] = new Tree(this, tree_img, tree2_pos.x, tree2_pos.y);
    allTrees[2] = new Tree(this, tree_img, tree3_pos.x, tree3_pos.y);

    tank_size = 50;

    // Team0
    team0Color  = color(204, 50, 50);// Base Team 0(red)
    team0_tank0_startpos  = new PVector(50, 50);
    team0_tank1_startpos  = new PVector(50, 150);
    team0_tank2_startpos  = new PVector(50, 250);

    // Team1
    team1Color  = color(0, 150, 200);             // Base Team 1(blue)
    team1_tank0_startpos  = new PVector(width-50, height-250);
    team1_tank1_startpos  = new PVector(width-50, height-150);
    team1_tank2_startpos  = new PVector(width-50, height-50);

    //tank0_startpos = new PVector(50, 50);
    tank0 = new Tank(this,"tank0", team0_tank0_startpos,tank_size, team0Color );
    tank1 = new Tank(this,"tank1", team0_tank1_startpos,tank_size, team0Color );
    tank2 = new Tank(this,"tank2", team0_tank2_startpos,tank_size, team0Color );

    tank3 = new Tank(this,"tank3", team1_tank0_startpos,75, team1Color );
    tank4 = new Tank(this,"tank4", team1_tank1_startpos,75, team1Color );
    tank5 = new Tank(this,"tank5", team1_tank2_startpos,75, team1Color );

    allTanks[0] = tank0;
    allTanks[1] = tank1;
    allTanks[2] = tank2;
    allTanks[3] = tank3;
    allTanks[4] = tank4;
    allTanks[5] = tank5;

    collisions = new Collisions(this);
    collisions.setTrees(allTrees);

    // Create teams with shared exploration managers
    team0 = new Team(this, team0Color, new PVector(0, 0), new PVector(150, 350));
    team1 = new Team(this, team1Color, new PVector(width - 151, height - 351), new PVector(150, 350));

    team0.addTank(tank0);
    team0.addTank(tank1);
    team0.addTank(tank2);
    //team0.addTank(tank2);

    tankAgent0 = team0.agents.get(0);

    team0.setupCollisionHandlers(collisions);
  }
  /**
   * Main game loop that handles updates and rendering.
   * Processes input, updates game logic, checks collisions, and renders everything.
   */
  public void draw() {
    frameRate(60);
    background(200);
    checkForInput();

    if (!gameOver && !pause) {
      // UPDATE LOGIC
      updateTanksLogic();
      // CHECK FOR COLLISIONS
      checkForCollisions();
      // Update the team, which now updates the shared exploration manager
      team0.update();
    }
    // UPDATE DISPLAY
    team0.displayHomeBase();
    team1.displayHomeBase();
    displayTrees();
    team0.display();
    displayTanks();
    displayGUI();
  }


  /**
   * Processes keyboard input for manual tank control.
   * Only active when auto-exploration is disabled.
   */
  void checkForInput() {
    if (tankAgent0.isAutoExploreActive()) {
      return;
    }
    if (right && down) {
      tank0.state = 5; // Right + Down
    } else if (right && up) {
      tank0.state = 6; // Right + Up
    } else if (left && down) {
      tank0.state = 7; // Left + Down
    } else if (left && up) {
      tank0.state = 8; // Left + Up
    } else if (right) {
      tank0.state = 1; // Right
    } else if (left) {
      tank0.state = 2; // Left
    } else if (down) {
      tank0.state = 3; // Down
    } else if (up) {
      tank0.state = 4; // Up
    } else {
      tank0.state = 0; // No keys pressed
    }
  }


  /**
   * Updates logic for all tanks including movement and agent decision making.
   * Handles tank updates, destruction checking, and sensor processing.
   */
  void updateTanksLogic() {
    // Update all tanks
    int count = 0;
    for (Tank tank : allTanks) {
      if(tank.isDestroyed){
        count++;
        System.out.println("Tank is destroyed!");
        tank = null;
      }
      if (tank != null) {
        tank.update();
      }
    }
    if(count == 3) gameOver = true;

    // Update tank agents with sensor information
    for (TankAgent agent : team0.agents) {
      agent.updateSensor(allTanks, allTrees);
    }
  }

  /**
   * Performs collision detection for all game entities.
   * Checks tank-tank, tank-tree, tank-border, and projectile collisions.
   */
  void checkForCollisions() {
    if (collisions != null) {
      collisions.checkAllCollisions(allTanks, allTrees);
    }

    for (Tank tank : allTanks) {
      if (tank != null) {
        // Check projectile collision with trees
        for (Tree tree : allTrees) {
          if (tree != null) {
            tank.projectile.checkTreeCollision(tree);
          }
        }

        // Check projectile collision with other tanks
        for (Tank otherTank : allTanks) {
          if (otherTank != null && otherTank != tank) {
            tank.projectile.checkTankCollision(otherTank);
          }
        }
      }
    }
  }

  /**
   * Renders all trees in the game world.
   */
  void displayTrees() {
    for (Tree tree : allTrees) {
      if (tree != null) {
        tree.display();
      }
    }
  }
  /**
   * Renders game UI including pause screen and game over messages.
   */
  void displayTanks() {
    for (Tank tank : allTanks) {
      if (tank != null) {
        tank.display();
      }
    }
  }
  /**
   * Renders game UI including pause screen and game over messages.
   */
  void displayGUI() {
    if (pause) {
      textSize(36);
      fill(30);
      text("...Paused! (\'p\'-continues)\n(arrow keys-change direction)\n(\'a\'-auto explores environment)\n(\'r'\'-returns home with A*)\n(\'d\'-returns home with dijkstra)", (float) (width/2.45), (float) (height/3));
    }

    if (gameOver) {
      textSize(36);
      fill(30);
      text("Game Over!", width/2-100, height/2);
    }
  }

  /**
   * Handles key press events for movement controls.
   * Updates directional flags for arrow key movement.
   */
  public void keyPressed() {
    if (key == CODED) {
      switch(keyCode) {
        case LEFT:
          left = true;
          break;
        case RIGHT:
          right = true;
          break;
        case UP:
          up = true;
          break;
        case DOWN:
          down = true;
          break;
      }
    }
  }
  /**
   * Handles key release events and special command keys.
   * Processes movement, pause, auto-explore, and pathfinding commands.
   */
  public void keyReleased() {
    if (key == CODED) {
      switch(keyCode) {
        case LEFT:
          left = false;
          break;
        case RIGHT:
          right = false;
          break;
        case UP:
          up = false;
          break;
        case DOWN:
          down = false;
          break;
      }
    }

    if (key == 'p' || key == 'P') {
      pause = !pause;
    }

    if (key == 'a' || key == 'A') {
      team0.toggleAutoExplore();
    }
    if (key == 'r' || key == 'R') {
      tankAgent0.setPathfindingAlgorithm("A*");
      team0.returnAllHome();
    }

    if(key == 'd' || key == 'D'){
      tankAgent0.setPathfindingAlgorithm("Dijkstra");
      team0.returnAllHome();
    }
    if(key == '2'){
      //
    }
  }

  /**
   * Handles mouse press events for debugging and interaction.
   */
  public void mousePressed() {
    println("---------------------------------------------------------");
    println("*** mousePressed() - Musknappen har tryckts ned.");
    mouse_pressed = true;
  }
  /**
   * Main method to start the application.
   *
   * @param passedArgs Command line arguments
   */
  public static void main(String[] passedArgs) {
    String[] appletArgs = new String[]{"tanks_bas_v1_0"};
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}