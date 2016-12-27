package com.lejos;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

public class WhereAmIPC extends JFrame {
    
    private static final long serialVersionUID = 2280874288993963333L;
    static List<Point> particles; 
    static int pose;
    
    static InputStream inputStream;
    static DataInputStream dataInputStream;
            
    public WhereAmIPC() {
        super("Localization Monitor for CmpE 434");
        setSize( 700, 500 );
        setVisible( true );
        pose = 0;
        particles = new ArrayList<>();
    }
    
    public static void main(String[] args) throws Exception    {
        
        int motion;
        int distance;
        
        WhereAmIPC monitor = new WhereAmIPC();
        
        monitor.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        String ip = "10.0.1.1";
        
        @SuppressWarnings("resource")
        Socket socket = new Socket(ip, 1234);
        System.out.println("Connected!");
        
        inputStream = socket.getInputStream();
        dataInputStream = new DataInputStream(inputStream);
        
        while( true ){
            motion = dataInputStream.readInt();
            distance = (int)(dataInputStream.readFloat()*100);
            pose = motion;
            particles.add(new Point(distance, motion));
            System.out.println(motion + " " + distance);
            monitor.repaint();
        }
    }

    
    public void paint( Graphics g ) {
        super.paint( g );
        displayMap( g); 
        displayParticles( particles, g );
        displayPose( pose, g );        
    }

    public void displayMap( Graphics g ){
        Graphics2D g2 = ( Graphics2D ) g;
        g2.setPaint( Color.BLUE );
        g2.setStroke( new BasicStroke( 5.0f ));     	
        g2.draw( new Rectangle(250 ,150 ,200 ,200));
        
    }
    
    public void displayParticles( List<Point> particles, Graphics g ) {
        for ( int i = 0; i < particles.size() ; i++ ){
            displayParticle( particles.get(i), g );
        }
    }
    public void displayParticle( Point p, Graphics g ){
        Graphics2D g2 = ( Graphics2D ) g;
        g2.setPaint( Color.red );
        g2.setStroke( new BasicStroke( 5.0f ));
        int i = p.y%400;
        int j = p.x;
        if ( i >=0 && i < 100 ) {
            g2.draw( new Line2D.Double( 250 +2*i,150-j,250+2*i,150-j));
		}
		else if ( i >=100 && i < 200 )	{
	        g2.draw( new Line2D.Double( 450 + j  ,150 +(i-100) * 2,450 + j ,150 +(i-100)*2));
		}
		else if ( i >=200 && i < 300 ) {
	        g2.draw( new Line2D.Double( 450-(i-200) * 2 ,350  + j,450-(i-200) * 2 ,350+ j ));
		} 
	
		else {
	        g2.draw( new Line2D.Double( 250 - j,350 - (i-300) * 2 ,250 - j ,350 - (i-300) * 2));
		}
    }
    
    public void displayPose( int particle, Graphics g ){
        Graphics2D g2 = ( Graphics2D ) g;
        g2.setPaint( Color.BLACK );
        g2.setStroke( new BasicStroke( 15.0f ));
        
        int i = pose%400;
        if ( i >=0 && i < 100 ) {
            g2.draw( new Line2D.Double( 250 +2*i,150,250+2*i,150));
		}
		else if ( i >=100 && i < 200 )	{
	        g2.draw( new Line2D.Double( 450  ,150 +(i-100) * 2,450 ,150 +(i-100) * 2));
		}
		else if ( i >=200 && i < 300 ) {
	        g2.draw( new Line2D.Double( 450-(i-200) * 2 ,350 ,450-(i-200) * 2 ,350));
		} 
	
		else {
	        g2.draw( new Line2D.Double( 250 ,350 - (i-300) * 2 ,250 ,350 - (i-300) * 2));
		}    }

}


