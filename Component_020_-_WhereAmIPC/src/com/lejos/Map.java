package com.lejos;

/**
 * @author Cetin Mericli
 *
 */
public class Map {
	
	public int x1(int location){
		int ret = 0;

		if ( location >=0 && location < 100 ) {
			ret = 250 + 2* location;
		}
		else if ( location >=100 && location < 200 )	{
			ret = 450;
		}
		else if ( location >=200 && location < 300 ) {
			ret = 450 - 2* location;
		} 
		
		else {
			ret = 250;
		}
		return ret;
	}
	public int x2(int location){
		int ret = 0;

		if ( location >=0 && location < 100 ) {
			ret = 250 + 2* location;
		}
		else if ( location >=100 && location < 200 )	{
			ret = 450;
		}
		else if ( location >=200 && location < 300 ) {
			ret = 450 - 2* location;
		} 
		
		else {
			ret = 250;
		}
		return ret;
	}
	public int y1(int location){return 0;}
	public int y2(int location){return 0;}
}
