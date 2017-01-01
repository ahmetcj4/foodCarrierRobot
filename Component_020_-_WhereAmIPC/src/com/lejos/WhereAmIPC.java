package com.lejos;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.swing.JFrame;

public class WhereAmIPC extends JFrame {

	private static final long serialVersionUID = 2280874288993963333L;
	private static final int wallSize=4,cellSize=100,start = 100;
	private static final int mapping=-100,idle=-101,execution = -102;
	private static final int right=0,up=1, left=2, down=3;
	private static final int wall=1, notWall=2, white=3, blue=4, red=5, black=6;//0 is default value, used for visited cells
	private static int [][] map11;//used in task execution
	private static int [][] map19;//used in mapping
	private static Point position;

	static InputStream inputStream;
	static DataInputStream dataInputStream;

	public WhereAmIPC() {
		super("Localization Monitor for CmpE 434");
		int size = 2*start + 9*cellSize + 10*wallSize;
		setSize( size, size );
		setVisible( true );
	}

	public static void main(String[] args) throws Exception    {

		position = new Point(1,3);
		loadMap();
		WhereAmIPC monitor = new WhereAmIPC();

		monitor.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		//        String ip = "10.0.1.1";
		//        
		//        @SuppressWarnings("resource")
		//        Socket socket = new Socket(ip, 1234);
		//        System.out.println("Connected!");
		//        
		//        inputStream = socket.getInputStream();
		//        dataInputStream = new DataInputStream(inputStream);
		//        while( true ){
		//            motion = dataInputStream.readInt();
		//            distance = (int)(dataInputStream.readFloat()*100);
		//            pose = motion;
		//            particles.add(new Point(distance, motion));
		//            System.out.println(motion + " " + distance);
		//            monitor.repaint();
		//        }
	}

	/**
	 * loads 11*11 map to map11 array
	 */
	private static void loadMap() {
		Scanner scanner;
		map11 = new int[11][11];
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
	public void paint( Graphics g ) {
		super.paint( g );
		displayCells(map11, g );
		displayPose(position, g );        
	}

	public void displayMap( Graphics g ){
		Graphics2D g2 = ( Graphics2D ) g;
		g2.setPaint( Color.BLUE );
		g2.setStroke( new BasicStroke( 5.0f ));     	
		g2.draw( new Rectangle(250 ,150 ,20 ,20));

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
				default:
					g2.setPaint( Color.white );
					break;
				}
				if ( i%2==1 && j%2==1 ) {
					g2.setStroke( new BasicStroke( cellSize ));
					x=start+(wallSize+cellSize)*(j/2) + wallSize/2 + cellSize/2;
					y=start+(wallSize+cellSize)*(i/2) + wallSize/2 + cellSize/2;
					g2.draw( new Line2D.Double(x, y, x, y));

				}else if (i%2==0 && j%2==1)	{
					g2.setStroke( new BasicStroke( wallSize ));
					x=start+(wallSize+cellSize)*(j/2) + wallSize/2;
					y=start+(wallSize+cellSize)*(i/2) ; 
					g2.draw( new Line2D.Double(x+wallSize/2,y, x+cellSize-wallSize/2, y));
				}else if (i%2==1 && j%2==0)	{
					g2.setStroke( new BasicStroke( wallSize ));
					x=start+(wallSize+cellSize)*(j/2) ;
					y=start+(wallSize+cellSize)*(i/2) + wallSize/2; 
					g2.draw( new Line2D.Double(x,y+wallSize/2, x, y+cellSize-wallSize/2));
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
}

