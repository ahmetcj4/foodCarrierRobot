package com.lejos;

import java.awt.Point;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
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
import lejos.hardware.sensor.EV3GyroSensor;
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

	//for PC connection
	private static ServerSocket serverSocket;
	private static DataOutputStream dataOutputStream;

	//robot hardware
	private static SampleProvider ultrasonicSampleProvider;
	private static SampleProvider gyroSampleProvider;

	private static EV3UltrasonicSensor ultrasonicSensor;
	private static EV3ColorSensor colorSensor;
	private static EV3GyroSensor gyroSensor;

	private static ColorAdapter colorAdapter;
	private static EV3LargeRegulatedMotor leftMotor, rightMotor, rotatorMotor,grabMotor;
	private static MovePilot pilot;

	//for mapping and searching
	private static final int mapping=-100,idle=-101,execution = -102,mappingSuccess=-103,mappingBacktrace = -104,mapping2=-105,mappingBacktrace2 = -106;
	private static final int right=0,up=1, left=2, down=3;
	private static final int wall=1, notWall=2, white=3, blue=4, red=5, black=6;//0 is default value, used for visited cells
	private static int [][] map11;//used in task execution
	private static int [][] map19;//used in mapping
	private static int [][] visited19;//used in localization and finding balls
	private static Stack<Point> previous;

	private static int foundCells = 36;//6*6=36 cross section cells of walls and notWalls
	private static int  ccw = -1;
	private static boolean isGrabbed = false;
	private static int ballIsGrabbed = white;


	public static void main(String[] args) throws Exception {        
		initializeRobot(20,"5.5","14",0); 
		drawString("Food Carrying", "Robot", "Connecting to", "PC");
		Sound.playTone(440, 100, 10);
		establishConnection(1234);
		Sound.playTone(440, 100, 10);
		drawString("Food Carrying", "Robot", "Connected!", ":)");

		while (Button.readButtons() != Button.ID_ESCAPE) {
			drawString(
					"Press",
					"UP    -> Mapping  ",
					"DOWN  -> Execution", 
					"ENTER -> Idle     ", 
					"ESC   -> Reset    ");
			Button.waitForAnyPress();
			switch (Button.readButtons()) {
			case Button.ID_UP:
				mapping();
				break;
			case Button.ID_DOWN:
				execution2();
				break;
			case Button.ID_ENTER:
				break;
			case Button.ID_LEFT:
				break;
			case Button.ID_RIGHT:
				break;
			default: 
				break;
			}
		}
		closeConnection();
	}

	private static void closeConnection() throws Exception {
		dataOutputStream.close();
		serverSocket.close();	
	}

	private static void establishConnection(int port) throws Exception {
		serverSocket = new ServerSocket(port);
		Socket client = serverSocket.accept();
		OutputStream outputStream = client.getOutputStream();
		dataOutputStream = new DataOutputStream(outputStream);
	}

	private static void initializeRobot(int speed,String diameter, String tWidth, double errorRate) throws Exception {
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
		rotatorMotor = new EV3LargeRegulatedMotor(MotorPort.C);
		grabMotor = new EV3LargeRegulatedMotor(MotorPort.B);

		ultrasonicSensor = new EV3UltrasonicSensor(SensorPort.S4);
		ultrasonicSampleProvider = ultrasonicSensor.getDistanceMode();

		colorSensor = new EV3ColorSensor(SensorPort.S2);
		colorAdapter = new ColorAdapter(colorSensor);

		gyroSensor = new EV3GyroSensor(SensorPort.S3);
		gyroSampleProvider = gyroSensor.getAngleMode();
		gyroSensor.reset();

		leftMotor.resetTachoCount();
		rightMotor.resetTachoCount();
		rotatorMotor.resetTachoCount();
		grabMotor.resetTachoCount();

		float wheelDiameter = Float.parseFloat(pilotProps.getProperty(PilotProps.KEY_WHEELDIAMETER, diameter)),
				trackWidth = Float.parseFloat(pilotProps.getProperty(PilotProps.KEY_TRACKWIDTH, tWidth));
		boolean reverse = Boolean.parseBoolean(pilotProps.getProperty(PilotProps.KEY_REVERSE, "false"));

		Chassis chassis = new WheeledChassis(new Wheel[]{WheeledChassis.modelWheel(leftMotor,wheelDiameter-errorRate).offset(-trackWidth/2).invert(reverse),
				WheeledChassis.modelWheel(rightMotor,wheelDiameter+errorRate).offset(trackWidth/2).invert(reverse)}, WheeledChassis.TYPE_DIFFERENTIAL);

		pilot = new MovePilot(chassis);
		pilot.setAngularAcceleration(4*5*speed);
		pilot.setLinearAcceleration(4*speed);
		pilot.setLinearSpeed(speed);
		pilot.setAngularSpeed(5*speed);
		pilot.stop();		
		rotatorMotor.setSpeed(8*speed);
	}

	private static void mapping() {
		drawString("Mapping ", "is started");
		grab(true);
		gyroSensor.reset();
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
		int currentCellColor = white;
		boolean isBacktracing=false;
		for (int iteration = 0; iteration < 2*25*25; iteration++) {

			currentCellColor = 	getCurrentCellColor();
			updateMap19(position.x,position.y,currentCellColor);
			if(!isBacktracing){
				distances = getDistancesfromWalls(direction);//TODO put wait

				//put values of walls and "not walls" to the 19*19 grid. and update boundaries
				int j = position.x + 1;
				if (j > boundaries[0]) boundaries[0]= j;
				if (distances[0]<=1) {//right
					updateMap19(j, position.y, wall);
				}else {
					updateMap19(j, position.y, notWall);
				}

				j = position.y - 1;//up
				if (j < boundaries[1]) boundaries[1]= j;
				if (distances[1]<=1) {	
					updateMap19(position.x,j,wall);
				}else {
					updateMap19(position.x,j,notWall);

				}


				j = position.x - 1;//left
				if (j < boundaries[2]) boundaries[2]= j;
				if (distances[2]<=1) {	
					updateMap19(j,position.y,wall);
				}else {
					updateMap19(j, position.y, notWall);
				}

				j = position.y + 1;//down
				if (j > boundaries[3]) boundaries[3]= j;
				if (distances[3]<=1) {
					updateMap19(position.x,j,wall);
				}else {
					updateMap19(position.x,j,notWall);
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

				if (foundCells==121) {//mapping is complete
					saveMap(boundaries);
					congratulate();
					sendCurrentState(mappingSuccess,position, distances,currentCellColor);
					break;
				}
				sendCurrentState(mapping,position, distances,currentCellColor);
			}else{
				sendCurrentState(mappingBacktrace,position, distances,currentCellColor);
			}

			//movement part
			if (currentCellColor==black) {//if current cell is black, go back
				isBacktracing = false;
				pilot.travel(-33);
				if (previous.size()==0) {
				}else position = previous.pop();
			}else if(getAdjacentCell19(position,direction)==0){//go straight if possible and not visited
				isBacktracing = false;
				position = forward(position,direction);
			}else if(getAdjacentCell19(position,(direction+1)%4)==0){//go to left cell if possible and not visited
				isBacktracing = false;
				direction = (direction+1)%4;
				pilot.rotate(-90);
				gyroFix();
				position = forward(position,direction);
			}else if(getAdjacentCell19(position,(direction+2)%4)==0){//go to back cell if possible and not visited
				isBacktracing = false;
				direction = (direction+2)%4;
				pilot.rotate(180);
				gyroFix();
				position = forward(position,direction);
			}else if(getAdjacentCell19(position,(direction+3)%4)==0){//go to right cell if possible and not visited
				isBacktracing = false;
				direction = (direction+3)%4;
				pilot.rotate(90);
				gyroFix();
				position = forward(position,direction);
			}else{ //go back
				isBacktracing = true;
				if (previous.size()==0) {
					saveMap(boundaries);
					congratulate();
					sendCurrentState(mappingSuccess,position, distances,currentCellColor);
					break;
				}
				backward = previous.pop();
				newDirection =(backward.x==position.x)?((backward.y>position.y)?down:up):((backward.x>position.x)?right:left);
				pilot.rotate(-90*(newDirection-direction));
				gyroFix();
				direction = newDirection;
				pilot.travel(33);
				position = backward;
			}
			gyroFix();
			if(checkIdleButton()) {
				sendCurrentState(idle,position, distances,currentCellColor);
				break;	
			}
		}
		saveMap(boundaries);
		congratulate();
		sendCurrentState(mappingSuccess,position, distances,currentCellColor);
		grab(false);
	}

	private static void execution2() {
		loadMap();
		drawString("Execution ", "is started");
		gyroSensor.reset();
		visited19 = new int[19][19];
		map19 = new int[19][19];
		for(int i = 0; i < 19; i+=2){
			for(int j = 0; j < 19; j+=2){
				visited19[i][j] = 1;
				map19[i][j] = wall;	//cross section of walls or notWalls are always considered as wall 
			}						//otherwise it would cause issues on finding nearest path 
		}							//i.e. best path found passes through walls 
		previous = new Stack<Point>();
		int center = 9;
		int [] boundaries = {center, center, center, center};//set initial boundaries(right, up,left,down)
		Point position = new Point(center, center),backward;//set initial position of the robot
		int direction = right,newDirection;//set initial direction of the robot
		int [] distances = new int[4];//distances from the right, up, left, down walls
		int currentCellColor = white;
		boolean isBacktracing=false;
		boolean isLocalized = false;
		for (int iteration = 0; iteration < 2*25*25; iteration++) {
			grab(true);
			currentCellColor = 	getCurrentCellColor();
			if (ballIsGrabbed==white) grab(false);
			updateMap19(position.x,position.y,currentCellColor);
			if(!isBacktracing){
				distances = getDistancesfromWalls(direction);//TODO put wait

				//put values of walls and "not walls" to the 19*19 grid. and update boundaries
				int j = position.x + 1;
				if (j > boundaries[0]) boundaries[0]= j;
				if (distances[0]<=1) {//right
					updateMap19(j, position.y, wall);
				}else {
					updateMap19(j, position.y, notWall);
				}

				j = position.y - 1;//up
				if (j < boundaries[1]) boundaries[1]= j;
				if (distances[1]<=1) {	
					updateMap19(position.x,j,wall);
				}else {
					updateMap19(position.x,j,notWall);

				}


				j = position.x - 1;//left
				if (j < boundaries[2]) boundaries[2]= j;
				if (distances[2]<=1) {	
					updateMap19(j,position.y,wall);
				}else {
					updateMap19(j, position.y, notWall);
				}

				j = position.y + 1;//down
				if (j > boundaries[3]) boundaries[3]= j;
				if (distances[3]<=1) {
					updateMap19(position.x,j,wall);
				}else {
					updateMap19(position.x,j,notWall);
				}
				int totalCount=0,count = 0,lastX=0,lastY=0,lastDir=0;
			//	for(int dir=0;dir<4;dir++){
					for(int y=0;y<11+boundaries[1]-boundaries[3];y++){
						for(int x=0;x<11+boundaries[2]-boundaries[0];x++){
							count = 0;
							for(int b1= boundaries[1];b1<boundaries[3];b1++){
								for(int b2= boundaries[2];b2<boundaries[0];b2++){
									if(map11[x+b2-boundaries[2]][y+b1-boundaries[1]]==map19[b2][b1]||map19[b2][b1]==0){
										count++;
									}
								}
							}//TODO make it general, not for only right
							if(count ==(boundaries[3]-boundaries[1]+1)*
									(boundaries[0]-boundaries[2]+1)){
								totalCount++;
							//	lastDir = dir;
								lastX = x;
								lastY = y;
								Sound.playTone(440, 100, 10);

							}
							count=0;
						}
					}
				//}
				if(totalCount==1){//localization == 1
					//TODO place all map into the cells
					//TODO generalize it not for only right
					for(int x=boundaries[2]-lastX;x==boundaries[2]-lastX+11;x++){
						for(int y=boundaries[1]-lastY;x==boundaries[1]-lastY+11;y++){
							map19[x][y] = map11[x-(boundaries[2]-lastX)][y-(boundaries[1]-lastY)];
						}
					}
					Sound.playTone(440, 100, 10);

					break;
				}
				//TODO write method that checks if localization is done
				//then breaks this for loop
				
				sendCurrentState(mapping2,position, distances,currentCellColor);
			}else{
				sendCurrentState(mappingBacktrace2,position, distances,currentCellColor);
			}

			//movement part
			if (currentCellColor==black) {//if current cell is black, go back
				isBacktracing = false;
				pilot.travel(-33);
				if (previous.size()==0) {
				}else position = previous.pop();
			}else if(getAdjacentCell19(position,direction)==0){//go straight if possible and not visited
				isBacktracing = false;
				position = forward(position,direction);
			}else if(getAdjacentCell19(position,(direction+1)%4)==0){//go to left cell if possible and not visited
				isBacktracing = false;
				direction = (direction+1)%4;
				pilot.rotate(-90);
				gyroFix();
				position = forward(position,direction);
			}else if(getAdjacentCell19(position,(direction+2)%4)==0){//go to back cell if possible and not visited
				isBacktracing = false;
				direction = (direction+2)%4;
				pilot.rotate(180);
				gyroFix();
				position = forward(position,direction);
			}else if(getAdjacentCell19(position,(direction+3)%4)==0){//go to right cell if possible and not visited
				isBacktracing = false;
				direction = (direction+3)%4;
				pilot.rotate(90);
				gyroFix();
				position = forward(position,direction);
			}else{ //go back
				isBacktracing = true;
				if (previous.size()==0) {
					saveMap(boundaries);
					congratulate();
					sendCurrentState(mappingSuccess,position, distances,currentCellColor);
					break;
				}
				backward = previous.pop();
				newDirection =(backward.x==position.x)?((backward.y>position.y)?down:up):((backward.x>position.x)?right:left);
				pilot.rotate(-90*(newDirection-direction));
				gyroFix();
				direction = newDirection;
				pilot.travel(33);
				position = backward;
			}
			gyroFix();
			if(checkIdleButton()) {
				sendCurrentState(idle,position, distances,currentCellColor);
				break;	
			}
		}
		//TODO now localizaiton is done. Use same method for searching ball
		// if any ball is cached  then by using bfs, path is determined.
		// else continue visit unvisited cells until any bell is cached.
		congratulate();
		grab(false);
	}

	
	/**
	 * sends current mode, position, distances to walls and current cell color to connected PC
	 * @param mode
	 * @param position
	 * @param distances
	 * @param currentCellColor
	 */
	private static void sendCurrentState(int mode,Point position, int[] distances, int currentCellColor) {
		try {
			dataOutputStream.writeInt(mode);
			dataOutputStream.writeInt(position.x);
			dataOutputStream.writeInt(position.y);
			dataOutputStream.writeInt(currentCellColor);
			for(int i:distances){
				dataOutputStream.writeInt(i);
			}	
			dataOutputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void execution() {
		loadMap();
		drawString("Execution", "is started");
		gyroSensor.reset();
		previous = new Stack<Point>();
		Stack<PointVector> candidates = new Stack<>();
		int direction=right,newDirection;//set initial direction of the robot
		int [] distances = new int[4];//distances from the right, up, left, down walls
		Point position = new Point(),backward;
		int currentCellColor = white;
		boolean isBacktracing=false;
		boolean isLocalized = false;

		for (int iteration = 0; iteration < 2*25*25; iteration++) {
			grab(true);
			currentCellColor = 	getCurrentCellColor();
			grab(false);
			distances = getDistancesfromWalls(direction);
			for(int i=0;i<distances.length;i++){
				distances[i]=(distances[i]==1)?wall:notWall;
			}

			//movement part
			if (currentCellColor==black) {//if current cell is black, go back
				isBacktracing = false;
				pilot.travel(-33);
				if (previous.size()==0) {
				}else position = previous.pop();
			}else if(distances[direction]==notWall){//go straight if possible and not visited
				isBacktracing = false;
				System.out.println("go: " +distances[0]+", " +distances[1]+", " +distances[2]+", " +distances[3]);
				position = forward(position,direction);
			}else if(distances[(direction+1)%4]==notWall){//go to left cell if possible and not visited
				isBacktracing = false;
				System.out.println("left: " +distances[0]+", " +distances[1]+", " +distances[2]+", " +distances[3]);
				direction = (direction+1)%4; 
				pilot.rotate(-90);
				gyroFix();
				position = forward(position,direction);
			}else if(distances[(direction+3)%4]==0){//go to right cell if possible and not visited
				isBacktracing = false;
				System.out.println("right: " +distances[0]+", " +distances[1]+", " +distances[2]+", " +distances[3]);
				direction = (direction+3)%4;
				pilot.rotate(90);
				gyroFix();
				position = forward(position,direction);
			}else if(distances[(direction+2)%4]==notWall){//go to back cell if possible and not visited
				isBacktracing = false;
				System.out.println("back: " +distances[0]+", " +distances[1]+", " +distances[2]+", " +distances[3]);
				direction = (direction+2)%4;
				pilot.rotate(180);
				gyroFix();
				position = forward(position,direction);
			}else{ //go back
				isBacktracing = true;
				if (previous.size()==0) {
					congratulate();
					break;
				}
				backward = previous.pop();
				newDirection =(backward.x==position.x)?((backward.y>position.y)?down:up):((backward.x>position.x)?right:left);
				pilot.rotate(-90*(newDirection-direction));
				gyroFix();
				direction = newDirection;
				pilot.travel(33);
				position = backward;
			}
			gyroFix();
			System.out.println(iteration+": expected: ("+position.x+", "+position.y+")->"+dir+" ("+distances[0]+", "+distances[1]+", "+distances[2]+", "+distances[3]+") ");

			if (!isLocalized) {
				Sound.playTone(440, 100, 10);

				if(candidates.isEmpty()){//in first iteration, add all possible points
					for(int j= 1;j<11;j+=2){
						for(int i= 1;i<11;i+=2){
							for(int dir=0;dir<4;dir++){
								if (map11[i][j]==currentCellColor&&
										map11[i-1][j]==distances[dir]&&
										map11[i][j-1]==distances[(dir+1)%4]&&
										map11[i+1][j]==distances[(dir+2)%4]&&
										map11[i][j+1]==distances[(dir+3)%4]) {
									candidates.add(new PointVector(i, j, dir));
								System.out.println(iteration+": candidate: ("+i+", "+j+")->"+dir+" ("+distances[0]+", "+distances[1]+", "+distances[2]+", "+distances[3]+")");
								}	
							}
						}
					}
					System.out.println("# of candidates " + candidates.size());
				}else{//in other iterations, remove impossible points TODO update
					Stack<PointVector> newPoints = new Stack<>();
					for(int j= 1;j<11;j+=2){
						for(int i= 1;i<11;i+=2){
							for(int dir=0;dir<4;dir++){
								if (map11[i][j]==currentCellColor&&
										map11[i-1][j]==distances[dir]&&
										map11[i][j-1]==distances[(dir+1)%4]&&
										map11[i+1][j]==distances[(dir+2)%4]&&
										map11[i][j+1]==distances[(dir+3)%4]) {
									System.out.println("sisisis:("+i+", "+j+")->"+dir);
									for(PointVector p:candidates){
										if((p.x==i&&(Math.abs(p.y-j)==2))||
												(p.y==j&&(Math.abs(p.x-i)==2))){
											newPoints.add(new PointVector(i, j, dir));
											System.out.println(iteration+": candidate: ("+i+", "+j+")->"+dir+" ("+distances[0]+", "+distances[1]+", "+distances[2]+", "+distances[3]+")");
											break;
										}	
									}
								}	
							}
						}
					}
					System.out.println("# of candidates p " + candidates.size());
					candidates = newPoints;
					System.out.println("# of candidates a " + candidates.size());

					for(int i=0;i<candidates.size();i++){
						PointVector p = candidates.get(i);
						if (p.x<0||p.x>10||p.y<0||p.y>10||
								!(map11[p.x][p.y]==currentCellColor&&
								map11[p.x-1][p.y]==distances[p.r]&&
								map11[p.x][p.y-1]==distances[(p.r+1)%4]&&
								map11[p.x+1][p.y]==distances[(p.r+2)%4]&&
								map11[p.x][p.y+1]==distances[(p.r+3)%4])) {
							candidates.remove(i--);
							System.out.println(iteration+": candidate removed: ("+p.x+", "+p.y+")->"+p.r);
						}
					}
					if (candidates.size()==1) {
						isLocalized=true;
						PointVector pointVector =candidates.pop();
						position.x = pointVector.x;
						position.y = pointVector.y;
						direction = pointVector.r;
						for(Point p:previous){
							p.x+=position.x;
							p.y+=position.y;
						}
						System.out.println(iteration+": candidate removed: ("+pointVector.x+", "+pointVector.y+")->"+pointVector.r);
						sendCurrentState(execution,position, distances,currentCellColor);
						congratulate();
						break;
					}
				}

			}else{
				sendCurrentState(execution,position, distances,currentCellColor);

			}
			if(checkIdleButton()) {
				sendCurrentState(idle,position, distances,currentCellColor);
				break;	
			}
		}
//		sendCurrentState(mappingSuccess,position, distances,currentCellColor);
	}

	private static int[] changeDirection(int[]duration,int i) {
		for (int j = 0; j < i; j++) {
			int k = duration[3];
			duration[3]=duration[2];
			duration[2]=duration[1];
			duration[1]=duration[0];
			duration[0]=k;
			
		}
		return duration;
	}

	private static void congratulate() {
		// TODO congratulate better
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
			for(int j= boundaries[1];j<=boundaries[3];j++){
				for(int i= boundaries[2];i<=boundaries[0];i++){
					pw.write(map19[i][j]+" ");
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
		map11 = new int[11][11];
		Scanner scanner;
		try {
			scanner = new Scanner(new File("map.txt"));
			for(int j= 0;j<11;j++){
				for(int i= 0;i<11;i++){
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
		grabMotor.rotate(dir*420);
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
		pilot.travel(33,true);
		while (pilot.isMoving()) {
			switch (getCurrentCellColor()) {
			case red:
				if(!isGrabbed){
					grab(true);
					ballIsGrabbed=red;
				}
				break;
			case blue:
				if(!isGrabbed){
					grab(true);
					ballIsGrabbed=blue;
				}
				break;
			default:
				break;
			}
		}
		previous.push(new Point(position));
		if 		(direction==right)	position.x = position.x+2;
		else if (direction==up)		position.y = position.y-2;
		else if (direction==left) 	position.x = position.x-2;
		else if (direction==down) 	position.y = position.y+2;
		return position;
	}

	/**
	 * returns value of cell adjacent to given position in given direction if there is no wall in between
	 * @param position
	 * @param direction
	 * @return
	 */
	private static int getAdjacentCell11(Point position, int direction) {
		if 		(direction==right&&position.x+2<19&& map11[position.x+1][position.y]==notWall) return map11[position.x+2][position.y];
		else if (direction==up&&position.y-2>0&&map11[position.x][position.y-1]==notWall)		return map11[position.x][position.y-2];
		else if (direction==left&&position.x-2>0&&map11[position.x-1][position.y]==notWall) 	return map11[position.x-2][position.y];
		else if (direction==down&&position.y+2<19&&map11[position.x][position.y+1]==notWall) 	return map11[position.x][position.y+2];
		else return -1;
	}
	
	/**
	 * returns value of cell adjacent to given position in given direction if there is no wall in between
	 * @param position
	 * @param direction
	 * @return
	 */
	private static int getAdjacentCell19(Point position, int direction) {
		if 		(direction==right&&position.x+2<19&& map19[position.x+1][position.y]==notWall) return map19[position.x+2][position.y];
		else if (direction==up&&position.y-2>0&&map19[position.x][position.y-1]==notWall)		return map19[position.x][position.y-2];
		else if (direction==left&&position.x-2>0&&map19[position.x-1][position.y]==notWall) 	return map19[position.x-2][position.y];
		else if (direction==down&&position.y+2<19&&map19[position.x][position.y+1]==notWall) 	return map19[position.x][position.y+2];
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
		if(i<0||j<0||i>18||j>18) return;
		if (map19[i][j]==0) foundCells++;//first assignment
		map19[i][j] = cell;
		updateVisited19(i, j);
	}

	private static void updateVisited19(int i, int j) {
		if(i<0||j<0||i>18||j>18) return;
		visited19[i][j] = 1;
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
		rotatorMotor.rotate( ccw * 360,true);
		while(rotatorMotor.isMoving()){
			int angle = (int)rotatorMotor.getPosition();
			if(angle%90==0){
				dir = (-angle/90 + robotDirection)%4;
				if(dir<0)dir+=4;
				distances[dir] = 1+2*(int)(getUltrasonicSensorValue()/0.33);
			}
		}
		ccw=-ccw;
		return distances;
	}

	private static float angle;
	private static void gyroFix(){
		gyroSampleProvider.fetchSample(samples, 0);
		angle = samples[0]%90;
		if (angle!=0) {
			if(angle>45){
				pilot.rotate(angle-90);				 
			}else if(angle<-45){
				pilot.rotate(angle+90);				 
			}else{
				pilot.rotate(angle);
			}
		}
	}
	static float [] samples = new float[1];
	/** 
	 * @return sensed distance in meters
	 */
	private static float getUltrasonicSensorValue() {
		if(ultrasonicSampleProvider.sampleSize() > 0) {
			ultrasonicSampleProvider.fetchSample(samples, 0);
			return samples[0];
		}
		return 0;        
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

	static class PointVector{
		int x;
		int y;
		int r;
		public PointVector(int x, int y, int rotation) {
			this.x = x;
			this.y = y;
			r=rotation;
		}
	}
}
