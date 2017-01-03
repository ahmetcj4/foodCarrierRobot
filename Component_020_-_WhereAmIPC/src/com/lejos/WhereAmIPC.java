package com.lejos;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.Stack;

import javax.swing.JFrame;

public class WhereAmIPC extends JFrame {

	private static final long serialVersionUID = 2280874288993963333L;
	private static final int wallSize=4,cellSize=100,start = 100;
	private static final int mapping=-100,idle=-101,execution = -102,mappingSuccess=-103;
	private static int mode = mapping;
	private static final int right=0,up=1, left=2, down=3;
	private static final int wall=1, notWall=2, white=3, blue=4, red=5, black=6;//0 is default value, used for visited cells
	private static int [][] map11;//used in task execution
	private static int [][] map19;//used in mapping
	private static Point position;
	private static int foundCells = 36;//6*6=36 cross section cells of walls and notWalls

	static InputStream inputStream;
	static DataInputStream dataInputStream;

	public WhereAmIPC() {
		super("Localization Monitor for CmpE 434");
		int size = 2*start + 9*cellSize + 10*wallSize;
		map19 = new int[19][19];
		map11 = new int[11][11];
		position = new Point(9,9);
//		loadMap();
//		mode =execution;
		setSize( size, size );
		setVisible( true );
	}

	public static void main(String[] args) throws Exception    {
		WhereAmIPC monitor = new WhereAmIPC();
		monitor.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		String ip = "10.0.1.1";

		@SuppressWarnings("resource")
		Socket socket = new Socket(ip, 1234);
		System.out.println("Connected!");

		inputStream = socket.getInputStream();
		dataInputStream = new DataInputStream(inputStream);
		Stack<PointVector> candidates = new Stack<>();

		int currentCellColor = white;
		int [] distances = new int[4];
		int center = 9;
		position = new Point(center,center);
		int [] boundaries = {center, center, center, center};
		boolean isLocalized=false;
		int iteration=0;

		while( true ){
			mode = dataInputStream.readInt();
			position.x =   dataInputStream.readInt();
			position.y =   dataInputStream.readInt();
			currentCellColor = dataInputStream.readInt();
			for(int i=0;i<distances.length;i++){
				distances[i] = dataInputStream.readInt();
			}	
			System.out.println(mode +", "+position.x +", "+position.y +", "+currentCellColor +", "+distances[0] +", "+distances[1] +", "+distances[2] +", "+distances[3]);

			switch (mode) {
			case mapping:				
				updateMap19(position.x,position.y,currentCellColor);
				//put values of walls and "not walls" to the 19*19 grid. and update boundaries

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
				break;

			case mappingSuccess:
				map19 = new int[19][19];
				for(int x = 0; x < 19; x+=2){
					for(int y = 0; y < 19; y+=2){
						map19[x][y] = wall;	//cross section of walls or notWalls are always considered as wall 
					}						//otherwise it would cause issues on finding nearest path 
				}							//i.e. best path found passes through walls 
				saveMap(boundaries);
				loadMap();
				position.x -=boundaries[2]; 
				position.y -=boundaries[1]; 
				break;
			case idle:
				break;
			case execution:
				loadMap();
			
				break;
			default:
				break;
			}

			monitor.repaint();
		}
	}

	private static void updateMap19(int i, int j, int cell) {
		if(i<0||j<0||i>18||j>18) return;
		if (map19[i][j]==0) foundCells++;//first assignment
		map19[i][j] = cell;
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

	public void paint( Graphics g ) {
		super.paint( g );
		if (mode==execution) {
			displayCells(map11, g );
		}else{
			displayCells(map19, g );
		}
		displayPose(position, g );        
	}

	public void displayCells(int [][] map, Graphics g ) {
		Graphics2D g2 = ( Graphics2D ) g;
		int x=0,y=0;
		for ( int i = 0; i < map.length; i++ ){
			for ( int j = 0; j < map[0].length; j++ ){
				switch (map[i][j]) {
				case black:
					g2.setPaint( Color.darkGray );
					break;
				case red:
					g2.setPaint( Color.red );
					break;
				case blue:
					g2.setPaint( Color.blue );
					break;
				case wall:
					g2.setPaint( Color.black );
					break;
				case white:
					g2.setPaint( Color.white );
					break;	
				case notWall:
					g2.setPaint( Color.white );
					break;
				default:
					g2.setPaint( Color.lightGray );
					break;
				}
				if ( i%2==1 && j%2==1 ) {
					g2.setStroke( new BasicStroke( cellSize ));
					x=start+(wallSize+cellSize)*(j/2) + wallSize/2 + cellSize/2;
					y=start+(wallSize+cellSize)*(i/2) + wallSize/2 + cellSize/2;
					g2.draw( new Line2D.Double(y, x, y, x));

				}else if (i%2==0 && j%2==1)	{
					g2.setStroke( new BasicStroke( wallSize ));
					x=start+(wallSize+cellSize)*(j/2) + wallSize/2;
					y=start+(wallSize+cellSize)*(i/2) ; 
					g2.draw( new Line2D.Double(y,x+wallSize/2,y, x+cellSize-wallSize/2));
				}else if (i%2==1 && j%2==0)	{
					g2.setStroke( new BasicStroke( wallSize ));
					x=start+(wallSize+cellSize)*(j/2) ;
					y=start+(wallSize+cellSize)*(i/2) + wallSize/2; 
					g2.draw( new Line2D.Double(y+wallSize/2, x, y+cellSize-wallSize/2,x));
				}

			}
		}
	}

	public void displayPose( Point pos, Graphics g ){
		Graphics2D g2 = ( Graphics2D ) g;
		g2.setPaint( Color.magenta );
		g2.setStroke( new BasicStroke( wallSize ));
		int x=start+(wallSize+cellSize)*(pos.x/2) + wallSize/2 + cellSize/3;
		int y=start+(wallSize+cellSize)*(pos.y/2) + wallSize/2 + cellSize/3;
		g2.drawOval(x, y, cellSize/3, cellSize/3);
		g2.drawOval(x + cellSize/12, y+ cellSize/12 , cellSize/6, cellSize/6);
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

