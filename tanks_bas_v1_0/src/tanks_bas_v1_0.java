import processing.core.*;
public class tanks_bas_v1_0 extends PApplet{
  // Följande kan användas som bas inför uppgiften.
// Syftet är att sammanställa alla varabelvärden i scenariet.
// Variabelnamn har satts för att försöka överensstämma med exempelkoden.
// Klassen Tank är minimal och skickas mer med som koncept(anrop/states/vektorer).

  Fog fog;
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

  //======================================
  public void settings()
  {
    size(800, 800);
  }

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

    allTanks[0] = tank0;                         // Symbol samma som index!
    allTanks[1] = tank1;
    allTanks[2] = tank2;
    allTanks[3] = tank3;
    allTanks[4] = tank4;
    allTanks[5] = tank5;

    fog = new Fog(this);
    fog.initialize();  // Initialize fog after window is properly sized
  }

  public void draw()
  {
    background(200);
    checkForInput(); // Kontrollera inmatning.

    if (!gameOver && !pause) {

      // UPDATE LOGIC
      updateTanksLogic();

      // CHECK FOR COLLISIONS
      checkForCollisions();

    }

    // UPDATE DISPLAY
    displayHomeBase();
    displayTrees();
    displayTanks();
    displayGUI();

    displayFog();


  }

  //======================================
  void checkForInput() {

    if (up) {
      if (!pause && !gameOver) {
        tank0.state=1; // moveForward
      }
    } else
    if (down) {
      if (!pause && !gameOver) {
        tank0.state=2; // moveBackward
      }
    }

    if (right) {
    } else
    if (left) {
    }

    if (!up && !down) {
      tank0.state=0;
    }
  }

  //======================================
  void updateTanksLogic() {
    tank0.update();
  }

  void checkForCollisions() {
    //println("*** checkForCollisions()");
    for (Tank tank : allTanks) {
      tank.checkForCollisions(tank1);
      tank.checkForCollisions(new PVector(width, height));
    }
  }

  //======================================
// Följande bör ligga i klassen Team
  void displayHomeBase() {
    strokeWeight(1);

    fill(team0Color, 15);    // Base Team 0(red)
    rect(0, 0, 150, 350);

    fill(team1Color, 15);    // Base Team 1(blue)
    rect(width - 151, height - 351, 150, 350);
  }

  // Följande bör ligga i klassen Tree
  void displayTrees() {
    imageMode(CENTER);
    image(tree_img, tree1_pos.x, tree1_pos.y);
    image(tree_img, tree2_pos.x, tree2_pos.y);
    image(tree_img, tree3_pos.x, tree3_pos.y);
    imageMode(CORNER);
  }

  void displayTanks() {
    for (Tank tank : allTanks) {
      tank.display();
    }
  }


  void displayFog() {
    // No need to resetFog() anymore
    // Just clear around tank and display
    fog.clearAroundTank(tank0);
    fog.display();
  }

  void displayGUI() {
    if (pause) {
      textSize(36);
      fill(30);
      text("...Paused! (\'p\'-continues)\n(upp/ner-change velocity)", (float) (width/1.7-100), (float) (height/2.5));
    }

    if (gameOver) {
      textSize(36);
      fill(30);
      text("Game Over!", width/2-100, height/2);
    }
  }

  //======================================
  public void keyPressed() {
    System.out.println("keyPressed!");

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

  public void keyReleased() {
    System.out.println("keyReleased!");
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
          //tank0.stopMoving();
          break;
        case DOWN:
          down = false;
          //tank0.stopMoving();
          break;
      }

    }

    if (key == 'p') {
      pause = !pause;
    }
  }

  // Mousebuttons
  public void mousePressed() {
    println("---------------------------------------------------------");
    println("*** mousePressed() - Musknappen har tryckts ned.");

    mouse_pressed = true;

  }

  public static void main(String[] passedArgs) {
    String[] appletArgs = new String[]{"tanks_bas_v1_0"};
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }

  }

}