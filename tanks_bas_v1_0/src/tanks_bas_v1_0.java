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

  ExplorationManager explorationManager;
  Collisions collisions;
  boolean left, right, up, down;
  boolean mouse_pressed;

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
   * Sets up the size of the game window.
   */
  public void settings()
  {
    size(800, 800);
  }
  /**
   * Initializes the game, creating all necessary objects and setting initial state.
   * Creates tanks, trees, collision manager, and exploration manager.
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

    tank3 = new Tank(this,"tank3", team1_tank0_startpos,tank_size, team1Color );
    tank4 = new Tank(this,"tank4", team1_tank1_startpos,tank_size, team1Color );
    tank5 = new Tank(this,"tank5", team1_tank2_startpos,tank_size, team1Color );

    allTanks[0] = tank0;
    allTanks[1] = tank1;
    allTanks[2] = tank2;
    allTanks[3] = tank3;
    allTanks[4] = tank4;
    allTanks[5] = tank5;

    // Create the exploration manager with a step size of 25
    explorationManager = new ExplorationManager(this, 25f);
    explorationManager.initializeFog();
    explorationManager.setTank(tank0);

    collisions = new Collisions(this, explorationManager);
  }
  /**
   * Main game loop function.
   * Updates game logic, checks for collisions, and renders the game state.
   */
  public void draw()
  {
    frameRate(60);
    background(200);
    checkForInput();

    if (!gameOver && !pause) {
      // UPDATE LOGIC
      updateTanksLogic();
      // CHECK FOR COLLISIONS
      checkForCollisions();

      if(explorationManager.exploredPercent >= 80){ //ändra senare
        gameOver = true;
      }else{
        // Update exploration
        explorationManager.updateTankPosition();
        explorationManager.navigation();
      }


    }

    // UPDATE DISPLAY
    displayHomeBase();
    displayTrees();
    explorationManager.display(); // This displays both nodes and fog
    displayTanks();
    displayGUI();
  }

  /**
   * Checks for keyboard input and updates the tank's state accordingly.
   * Does not update the controlled tank if auto-explore is active.
   */
  void checkForInput() {
    if (explorationManager.isAutoExploreActive()) {
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
   * Updates the logic for all tanks.
   * Calls the update method for each tank to update position and state.
   */
  void updateTanksLogic() {
    // Update all tanks
    for (Tank tank : allTanks) {
      if (tank != null) {
        tank.update();
      }
    }
  }

  /**
   * Checks for collisions between game entities.
   * Uses the Collisions manager to handle all collision detection and resolution.
   */
  void checkForCollisions() {
    collisions.checkAllCollisions(allTanks, allTrees);
  }



  /**
   * Displays the home bases for both teams.
   * Draws rectangles with team colors at the appropriate locations.
   */
  // Följande bör ligga i klassen Team
  void displayHomeBase() {
    strokeWeight(1);

    fill(team0Color, 15);    // Base Team 0(red)
    rect(0, 0, 150, 350);

    fill(team1Color, 15);    // Base Team 1(blue)
    rect(width - 151, height - 351, 150, 350);
  }

  /**
   * Displays all trees in the game world.
   */
  void displayTrees() {
    for (Tree tree : allTrees) {
      if (tree != null) {
        tree.display();
      }
    }
  }
  /**
   * Displays all tanks in the game world.
   */
  void displayTanks() {
    for (Tank tank : allTanks) {
      if (tank != null) {
        tank.display();
      }
    }
  }
  /**
   * Displays the game GUI, including pause and game over messages.
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
   * Handles key press events.
   * Updates movement direction flags when arrow keys are pressed.
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
   * Handles key release events.
   * Updates movement direction flags and handles special keys like 'p', 'a', and 'r'.
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

    if (key == 'p') {
      pause = !pause;
    }

    if (key == 'a' || key == 'A') {
      explorationManager.toggleAutoExplore();
    }
    if (key == 'r' || key == 'R') {
      explorationManager.testDijkstra = false;
      explorationManager.testReturnHome();
    }

    if(key == 'd' || key == 'D'){
      explorationManager.testDijkstra = true;
      explorationManager.testReturnHome();
    }

  }

  /**
   * Handles mouse press events.
   * Updates the mouse_pressed flag and prints a message.
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