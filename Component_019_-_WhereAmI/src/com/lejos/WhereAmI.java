package com.lejos;

import java.awt.Point;

import lejos.hardware.BrickFinder;
import lejos.hardware.Button;
import lejos.hardware.ev3.EV3;
import lejos.hardware.lcd.GraphicsLCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3UltrasonicSensor;
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
    private static EV3LargeRegulatedMotor leftMotor, rightMotor, rotatorMotor;
    private static MovePilot pilot;
   
    //for mapping and searching
	private static final int right=0,up=1, left=2, down=3;
	private static final int wall=1, notWall=2, white=3, blue=4, red=5, black=6;
	private static int [][] map11;//not used yet
	private static int [][] map19;
	private static int foundCells=36;
	private static int  ccw = -1;
    
    
    public static void main(String[] args) throws Exception {        
    	initializeRobot("5.4","11.5"); 
    	map11 = new int[11][11];
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
			case Button.ID_RIGHT:
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
        pilotProps.setProperty(PilotProps.KEY_WHEELDIAMETER, diameter);//TODO change them
        pilotProps.setProperty(PilotProps.KEY_TRACKWIDTH, tWidth);
        pilotProps.setProperty(PilotProps.KEY_LEFTMOTOR, "A");
        pilotProps.setProperty(PilotProps.KEY_RIGHTMOTOR, "D");
        pilotProps.setProperty(PilotProps.KEY_REVERSE, "false");
        pilotProps.storePersistentValues();
        pilotProps.loadPersistentValues();
        
        leftMotor = new EV3LargeRegulatedMotor(MotorPort.A);
        rightMotor = new EV3LargeRegulatedMotor(MotorPort.D);
        rotatorMotor = new EV3LargeRegulatedMotor(MotorPort.B);
        ultrasonicSensor = new EV3UltrasonicSensor(SensorPort.S1);
        
        leftMotor.resetTachoCount();
        rightMotor.resetTachoCount();
        rotatorMotor.resetTachoCount();
        
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
    	map19 = new int[19][19];
    	for(int i = 0; i < 19; i+=2){
    		for(int j = 0; j < 19; j+=2){
				map19[i][j] = wall;
        	}
    	}
    	int center = 9;
    	int [] boundaries = {center, center, center, center};//set initial boundaries
		Point position = new Point(center, center);//set initial position of the robot
		int direction = right;//set initial direction of the robot
		
		int [] distances = new int[4];//distances from the right, up, left, down walls
		while(true){
			distances = getDistancesfromWalls(direction);
//			put values of walls and "not walls" to the 19*19 grid. and update boundaries
//updatethem if sensor is not capable of measuring long dıstances
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
			//movement part will come here
			
			//check neıghbor cells. get first unknown neighbor cell that there is no wall in between
			//if there is no cell or the current cell is black, backtrack until there reach branchPoint.
			//if there are more than 1 neıghbor cells make thıs cell branchPoint
			//not completed
			
			
			if(checkIdleButton()) return;	
		}
	}
    
    private static void execution() {
		// TODO execution task
		
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

	private static int getCurrentCellColor() {
		// TODO measure cell color and return accordingly
		return white;
	}

	/**
     * eg {1,1,1,9}
     * @return distances of first walls from  right, up, left, down
   */
	static int []distances = new int[4];
	static int dir;
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
	private static float getUltrasonicSensorValue() {
        SampleProvider sampleProvider = ultrasonicSensor.getDistanceMode();
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
