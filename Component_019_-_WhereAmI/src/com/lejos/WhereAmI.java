package com.lejos;

import java.awt.Point;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.Stack;

import lejos.hardware.BrickFinder;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import lejos.hardware.ev3.EV3;
import lejos.hardware.lcd.GraphicsLCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.robotics.Color;
import lejos.robotics.ColorAdapter;
import lejos.robotics.SampleProvider;
import lejos.robotics.chassis.Chassis;
import lejos.robotics.chassis.Wheel;
import lejos.robotics.chassis.WheeledChassis;
import lejos.robotics.navigation.MovePilot;
import lejos.utility.Delay;
import lejos.utility.PilotProps;

public class WhereAmI {
    private static EV3 ev3;
    //for drawing on the screen
    private static GraphicsLCD graphicsLCD;
    private static int width;
    private static int anchor;
    
    private static EV3UltrasonicSensor ultrasonicSensor;
    private static SampleProvider sampleProvider;
    private static EV3ColorSensor colorSensor;
    private static ColorAdapter colorAdapter;
    private static EV3LargeRegulatedMotor leftMotor, rightMotor, rotatorMotor,grabMotor;
    private static MovePilot pilot;
   
    //for mapping and searching
	private static final int right=0,up=1, left=2, down=3;
	private static final int wall=1, notWall=2, white=3, blue=4, red=5, black=6;//0 is default value, used for visited cells
	private static int [][] map11;//used in task execution
	private static int [][] map19;//used in mapping
	private static Stack<Point> previous;
	
	private static int foundCells = 36;//6*6=36 cross section cells of walls and notWalls
	private static int  ccw = -1;
	private static boolean isGrabbed = false;
    
    
    public static void main(String[] args) throws Exception {        
    	initializeRobot("5.4","11.5"); //TODO change them
		drawString("Food Carrying", "Robot", "Please", "push button");
		Button.waitForAnyPress();
		while (Button.readButtons() != Button.ID_ESCAPE) {
			drawString(
					"Press",
					"UP    for Mapping   ",
					"DOWN  for Execution ", 
					"ENTER for Idle      ", 
					"ESC   for Reset     ");
			Button.waitForAnyPress();
			switch (Button.readButtons()) {
			case Button.ID_UP:
				mapping();
				break;
			case Button.ID_DOWN:
				execution();
				break;
			case Button.ID_ENTER:
				grab(true);
				break;
			case Button.ID_LEFT:
				grab(false);
				break;
			case Button.ID_RIGHT:
				while(Button.readButtons() != Button.ID_ESCAPE){	
					drawString("color is " + getCurrentCellColor());
				}
//				while(Button.readButtons() != Button.ID_ESCAPE){
//					getUltrasonicSensorValue();
//				}
				break;
			}
		}
    }

	private static void initializeRobot(String diameter, String tWidth) throws Exception {
    	ev3 = (EV3) BrickFinder.getDefault();
    	graphicsLCD = ev3.getGraphicsLCD();
    	width = graphicsLCD.getWidth()/2;
    	anchor = GraphicsLCD.HCENTER;
    	
    	PilotProps pilotProps = new PilotProps();
        pilotProps.setProperty(PilotProps.KEY_WHEELDIAMETER, diameter);
        pilotProps.setProperty(PilotProps.KEY_TRACKWIDTH, tWidth);
        pilotProps.setProperty(PilotProps.KEY_LEFTMOTOR, "A");
        pilotProps.setProperty(PilotProps.KEY_RIGHTMOTOR, "D");
        pilotProps.setProperty(PilotProps.KEY_REVERSE, "false");
        pilotProps.storePersistentValues();
        pilotProps.loadPersistentValues();
        
        leftMotor = new EV3LargeRegulatedMotor(MotorPort.A);
        rightMotor = new EV3LargeRegulatedMotor(MotorPort.D);
     //   rotatorMotor = new EV3LargeRegulatedMotor(MotorPort.B);
        grabMotor = new EV3LargeRegulatedMotor(MotorPort.C);
    
        ultrasonicSensor = new EV3UltrasonicSensor(SensorPort.S1);
        sampleProvider = ultrasonicSensor.getDistanceMode();

        colorSensor = new EV3ColorSensor(SensorPort.S2);
        colorAdapter = new ColorAdapter(colorSensor);
       
        leftMotor.resetTachoCount();
        rightMotor.resetTachoCount();
     //   rotatorMotor.resetTachoCount();
        grabMotor.resetTachoCount();
        
        float wheelDiameter = Float.parseFloat(pilotProps.getProperty(PilotProps.KEY_WHEELDIAMETER, diameter)),
                trackWidth = Float.parseFloat(pilotProps.getProperty(PilotProps.KEY_TRACKWIDTH, tWidth));
        boolean reverse = Boolean.parseBoolean(pilotProps.getProperty(PilotProps.KEY_REVERSE, "false"));
        
        Chassis chassis = new WheeledChassis(new Wheel[]{WheeledChassis.modelWheel(leftMotor,wheelDiameter).offset(-trackWidth/2).invert(reverse),
        		WheeledChassis.modelWheel(rightMotor,wheelDiameter).offset(trackWidth/2).invert(reverse)}, WheeledChassis.TYPE_DIFFERENTIAL);
        
        pilot = new MovePilot(chassis);
        pilot.setLinearSpeed(10);
        pilot.setAngularSpeed(10);
        pilot.stop();		
	}

    private static void mapping() {
    	drawString("Mapping ", "is started");
    	grab(true);
    	map19 = new int[19][19];
    	for(int i = 0; i < 19; i+=2){
    		for(int j = 0; j < 19; j+=2){
				map19[i][j] = wall;	//cross section of walls or notWalls are always considered as wall 
    		}						//otherwise it would cause issues on finding nearest path 
    	}							//i.e. best path found passes through walls 
    
    	previous = new Stack<Point>();
    	int center = 9;
    	int [] boundaries = {center, center, center, center};//set initial boundaries(right, up,left,down)
		Point position = new Point(center, center),backward;//set initial position of the robot
		int direction = right,newDirection;//set initial direction of the robot
		int [] distances = new int[4];//distances from the right, up, left, down walls
		for (int iteraiton = 0; iteraiton < 2*25*25; iteraiton++) {
			distances = getDistancesfromWalls(direction);
			//put values of walls and "not walls" to the 19*19 grid. and update boundaries
			//TODO update them if sensor is not capable of measuring long distances
			for (int i = 1; i < distances[0]; i+=2) {
				int j = position.x + i;
				if (i==distances[0]) {
					if (j > boundaries[0]) {
						boundaries[0]= j;
					}
					updateMap19(j, position.y, wall);
				}else {
					updateMap19(j, position.y, notWall);
				}
			}
			for (int i = 1; i < distances[1]; i+=2) {
				int j = position.y - i;
				if (i==distances[1]) {
					if (j < boundaries[1]) {
						boundaries[1]= j;
					}
					updateMap19(position.x,j,wall);
				}else {
					updateMap19(position.x,j,notWall);

				}
			}
			
			for (int i = 1; i < distances[2]; i+=2) {
				int j = position.x - i;
				if (i==distances[2]) {
					if (j < boundaries[2]) {
						boundaries[2]= j;
					}
					updateMap19(j,position.y,wall);
				}else {
					updateMap19(j, position.y, notWall);
				}
			}
			for (int i = 1; i < distances[3]; i+=2) {
				int j = position.y + i;
				if (i==distances[3]) {
					if (j > boundaries[3]) {
						boundaries[3]= j;
					}
					updateMap19(position.x,j,wall);
				}else {
					updateMap19(position.x,j,notWall);
				}
			}
			
			//if boundaries are changed and new boundaries produce 11*11 cell grid 
			//then we found exact place of the 11*11 grid in 19*19 grid. 
			//then fill 1's to the boundaries and midpoints of this 11*11 grid.
			if(boundaries[0]-boundaries[2]==10&&boundaries[3]-boundaries[1]==10){
				for(int i = boundaries[2]+1; i < boundaries[0]; i+=2){
	        		updateMap19(i, boundaries[1], wall);
	        		updateMap19(i, boundaries[3], wall);
		    	}
				for(int i = boundaries[1]+1; i < boundaries[3]; i+=2){
	        		updateMap19(boundaries[0], i, wall);
	        		updateMap19(boundaries[2], i, wall);
		    	}
			}
			
			updateMap19(position.x,position.y,getCurrentCellColor());
			
			if (foundCells==121) {//mapping is complete
				saveMap(boundaries);
				congratulate();
				break;
			}
			
			//movement part
			if (getCurrentCellColor()==black) {//if current cell is black, go back
		    	pilot.travel(-33);
		    	position = previous.pop();
			}else if(distances[direction]>1&&getAdjacentCell(position,direction)==0){//go straight if possible and not visited
				position = forward(position,direction);
			}else if(distances[(direction+1)%4]>1&&getAdjacentCell(position,(direction+1)%4)==0){//go to left cell if possible and not visited
				direction = (direction+1)%4;
				pilot.rotate(90);
				position = forward(position,direction);
			}else if(distances[(direction-1)%4]>1&&getAdjacentCell(position,(direction-1)%4)==0){//go to right cell if possible and not visited
				direction = (direction-1)%4;
				pilot.rotate(-90);
				position = forward(position,direction);
			}else{ //go back
				if (previous.size()==0) {//TODO trivial solution for the case where there are some reachable cells,should be improved vastly.
					saveMap(boundaries);
					congratulate();
					break;
				}
				backward = previous.pop();
		    	newDirection =(backward.x==position.x)?((backward.y>position.y)?down:up):((backward.x>position.x)?right:left);
		    	pilot.rotate(90*(newDirection-direction));
		    	direction = newDirection;
		    	pilot.travel(33);
		    	position = backward;
			}
			
			if(checkIdleButton()) break;	
		}
		grab(false);
	}
	
    private static void execution() {
		// TODO execution task
    	loadMap();
    	//lookaround and current cell color
	}

    private static void congratulate() {
		// TODO congragulate better
		try {
			Sound.playTone(440, 100, 10);
			Thread.sleep(100);
			Sound.playTone(440, 100, 10);
			Thread.sleep(100);
			Sound.playTone(440, 100, 10);
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
    
    /**
     * saves 19*19 map to 11*11 map with the help of boundaries(right, up,left,down)
     * @param boundaries
     */
	private static void saveMap(int[] boundaries) {
		try {
			PrintWriter pw = new PrintWriter("map.txt");
			for(int i= boundaries[2];i<=boundaries[0];i++){
				for(int j= boundaries[1];j<=boundaries[3];j++){
					pw.write(map19[boundaries[2]+i][boundaries[1]+j]+" ");
				}
				pw.write("\n");
			}
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * loads 11*11 map to map11 array
	 */
	private static void loadMap() {
	    	Scanner scanner;
			try {
				scanner = new Scanner(new File("map.txt"));
				for(int i= 0;i<11;i++){
					for(int j= 0;j<11;j++){
						if(scanner.hasNextInt()){
					    	   map11[i][j]= scanner.nextInt();
					    }
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}    	
		}

    /**
     * grabs the food by rotating down the grab mechanism grab == true
     * @param grab
     */
	private static void grab(boolean grab) {
		if (grab==isGrabbed) return;	
		int dir = grab?-1:1;
		grabMotor.rotate(dir*430);
		isGrabbed = grab;
	}
	/**
     * goes 1 cell forward and increment and return position with given direction 
     * pushes current position to stack for backtracking
     * @param position
     * @param direction
     * @return
     */
	private static Point forward(Point position, int direction) {
    	pilot.travel(33);
		previous.push(new Point(position));
		if 		(direction==right)	position.x = position.x+2;
		else if (direction==up)		position.y = position.y-2;
		else if (direction==left) 	position.x = position.x-2;
		else if (direction==down) 	position.y = position.y+2;
		return position;
	}

	/**
     * returns value of cell adjacent to given position in given direction
     * @param position
     * @param direction
     * @return
     */
    private static int getAdjacentCell(Point position, int direction) {
		if 		(direction==right&&position.x+2<19) return map19[position.x+2][position.y];
		else if (direction==up&&position.y-2>0)		return map19[position.x][position.y-2];
		else if (direction==left&&position.x-2>0) 	return map19[position.x-2][position.y];
		else if (direction==down&&position.y+2<19) 	return map19[position.x][position.y+2];
		else return -1;
	}

	/**
     * used for reset to idle state
     * @return
     */
    private static boolean checkIdleButton() {
    	return (Button.readButtons() == Button.ID_ENTER);		
	}

	private static void updateMap19(int i, int j, int cell) {
		if (map19[i][j]==0) foundCells++;//first assignment
    	map19[i][j] = cell;
	}

	/**
	 * @return returns colorId of given cell
	 */
	private static int getCurrentCellColor() {
		switch (colorAdapter.getColorID()) {
		case Color.WHITE: return white;
		case Color.BLACK: return black;
		case Color.RED: return red;
		case Color.BLUE: return blue;
		default: return 0;
		}

	}

	static int []distances = new int[4];
	static int dir;
	/**
     * eg {1,1,1,9}
     * @return distances of first walls from  right, up, left, down
   */
	private static int[] getDistancesfromWalls(int robotDirection) {
		for(int i = 0;i<4;i++){
			int angle = (int)rotatorMotor.getPosition();
			if(angle%90==0){
				dir = (-angle/90 + robotDirection)%4;
				distances[dir] = 1+2*(int)(getUltrasonicSensorValue()/0.33);
			}
			rotatorMotor.rotate( ccw * 90);
		}
		ccw=-ccw;
		return distances;
	}

    static float [] samples = new float[1];
	/** 
	 * @return sensed distance in meters
	 */
    private static float getUltrasonicSensorValue() {
        if(sampleProvider.sampleSize() > 0) {
            sampleProvider.fetchSample(samples, 0);
            return samples[0];
        }
        return -1;        
    }
    
	/**
     * prints the screen of {@link EV3} with given msg strings
     */
    public static void drawString(String ... msg){
        graphicsLCD.clear();
        for (int i = 0; i < msg.length; i++) {
            graphicsLCD.drawString(msg[i], width, 10 + (i) * 20 , anchor );            
        }
    }
}
