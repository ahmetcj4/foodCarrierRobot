package com.lejos;
import java.io.Serializable;

/**
 * 
 * 
 * This class provides basic functionality of Monte Carlo Localization 
 * algorithm for a 1D world. All distances are measured in centimeters
 * 
 * @author Cetin Mericli
 *
 */

public class MCL implements Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    //Modify these according to your settings
    static final int WORLD_SIZE = 100; //the length of the world in centimeters
    static final double DELTA = 5; // Amount of random distortion while cloning
    

    int NUM_PARTICLES; //number of particles
    
    Particle particles[]; //array for holding the particles
    Particle resampled[]; //temporary array for holding resampled particles
    Particle pose;
    Map map; //map of the environment
    
    MCL( int numParticles, Map map )
    {
        NUM_PARTICLES = numParticles;
        particles = new Particle[ NUM_PARTICLES ];
        resampled = new Particle[ NUM_PARTICLES ];
        pose = new Particle();
        
        for ( int i = 0 ; i < NUM_PARTICLES ; i++ )
        {
            particles[ i ] = new Particle();
            resampled[ i ] = new Particle();
            
        }
        this.map = map;
        reset();
    }
    
    void motionUpdate( int movement )
    {
        for ( int i = 0; i < NUM_PARTICLES ; i++ )
        {
            particles[ i ].location += motionModel( movement );
            if ( particles[ i ].location > WORLD_SIZE )
            {
                particles[ i ].location = WORLD_SIZE;
            }
        }
        calculatePose();
    }
    
    void measurementUpdate( int sonarDistance )
    {
        for ( int i = 0; i < NUM_PARTICLES ; i++ )
        {
            particles[ i ].belief = measurementModel( sonarDistance, particles[ i ].location );
            
        }
        resample();
        calculatePose();
        
    }
    
    double measurementModel( int sensorReading, int mean ) 
    {
        
        double sigma = 0;
        for (Particle vals : particles) {
            sigma += (vals.location-mean)*(vals.location-mean);
        }
        
        sigma=sigma/particles.length;
        double varience = sigma;
        sigma = Math.sqrt(sigma);
        
        return Math.pow(Math.E, - (sensorReading-mean)*(sensorReading-mean)/(2*varience))
                /(Math.sqrt(2*varience*Math.PI));
    }    
    
    double motionModel( int motion )
    {
        double ret = motion + DELTA*2*(Math.random()-1);  //random numbers generated between -1 and 1
        
        return ret;
    }
    
    void resample()
    {
        int i;
        
        int numOfClones; //how many clones of a particle will be generated?
        double sumBelief = 0; //for normalization
        
        
        int index = 0; //particle index for temporary array
        
        //calculating the sum of the beliefs to be used in normalization
        for ( i = 0 ; i < NUM_PARTICLES ; i++ )
        {
            sumBelief += particles[ i ].belief;
        }
        
        //resample all the particles
        for ( i = 0 ; i < NUM_PARTICLES ; i++ )
        {
            numOfClones = ( int ) ( NUM_PARTICLES * particles[ i ].belief / sumBelief ); //the number of clones are proportional to the particle's belief
            if ( numOfClones > 0 ) //if the particle will be cloned
            {

                //!!! IMPORTANT !!! Modify DELTA according to your approach
                //generate a clone of the particle with a random distortion
                resampled[ index ].location = ( int )( particles[ i ].location + DELTA * Math.random() );
                
                if ( resampled[ index ].location > WORLD_SIZE )
                {
                    resampled[ index ].location = WORLD_SIZE;
                }
                resampled[ index ].belief = particles[ i ].belief; 
                index++;
            }
        }
        //if there is a gap between the cloned particles and the number of particles, populate random particles to fill it
        for ( i = index; i < NUM_PARTICLES ; i++ )
        {
            resampled[ i ].location = ( int )( Math.random() * WORLD_SIZE );
            resampled[ i ].belief = 1 / NUM_PARTICLES;
        }
        
        //copy the resampled particle set to the particles array
        for ( i = 0 ; i < NUM_PARTICLES ; i++ )
        {
            particles[ i ].location = resampled[ i ].location;
            particles[ i ].belief = resampled[ i ].belief;
        }
    }
    
    void reset()
    {
        //assign random locations to the particles
        for ( int i = 0; i < NUM_PARTICLES ; i++ )
        {
            particles[ i ].location = ( int ) ( Math.random() * WORLD_SIZE );
            particles[ i ].belief = 1 / NUM_PARTICLES;
        }
    }
    
    
    void calculatePose()
    {
        pose.location=0;
        pose.belief=1;
        //calculate and return the average location and belief as the position estimation
       
        double location = 0,sumBelief=0;
        for (Particle vals : particles) {
           sumBelief += vals.belief; 
           location += vals.location*vals.belief; 
           }
        pose.location =(int)(location / sumBelief); 

    }
}
