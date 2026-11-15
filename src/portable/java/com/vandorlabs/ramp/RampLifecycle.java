package com.vandorlabs.ramp;

/** World-free controller timing and signal decisions shared by the two engine adapters.
 * Preparing or restoring ownership is scheduled work, never an activation refusal. */
public final class RampLifecycle {
    public static final double ENDPOINT_EPSILON=1e-8;
    public static final int LOAD=1, OWNERSHIP=2, MOTION=4, BELT=8, CANOPY=16;

    private RampLifecycle() { }

    public static double signalTarget(boolean analog,boolean escalator,boolean powerOn,
            int physical,int received) {
        int level=Math.max(clampSignal(physical),clampSignal(received));
        if(analog&&!escalator)return (powerOn?level:15-level)/15D;
        return (level>0)==powerOn?1D:0D;
    }
    private static int clampSignal(int level){return Math.max(0,Math.min(15,level));}
    public static int duration(int length,int startHalf,int endHalf,int speed){
        return ControllerPlatform.duration(length,(int)Math.ceil(Math.abs((endHalf-startHalf)/2D)),speed);
    }
    public static boolean moving(double from,double target){return Math.abs(from-target)>ENDPOINT_EPSILON;}
    public static boolean arrived(double pose,double target){return Math.abs(pose-target)<ENDPOINT_EPSILON;}
    public static int work(boolean load,boolean ownership,boolean moving,boolean belt,boolean canopy){
        return (load?LOAD:0)|(ownership?OWNERSHIP:0)|(moving?MOTION:0)|(belt?BELT:0)|(canopy?CANOPY:0);
    }

    /** observed is the last physically committed frame, not a prediction from wall time. */
    public static final class Timeline {
        public final long sequence,started,lastTick;
        public final double from,target,observed;
        public final int duration;
        public final boolean moving;
        public Timeline(long sequence,long started,long lastTick,double from,double target,
                double observed,int duration,boolean moving){
            if(sequence<0||!pose(from)||!pose(target)||!pose(observed)||duration<1||duration>8192)
                throw new IllegalArgumentException("Ramp timeline");
            this.sequence=sequence;this.started=started;this.lastTick=lastTick;this.from=from;
            this.target=target;this.observed=observed;this.duration=duration;this.moving=moving;
        }
        public double sample(long tick,double partial){
            return moving?ControllerPlatform.pose(from,target,(double)tick-started+partial,duration):observed;
        }
        public Timeline request(double next,long tick,int ticks){
            if(sequence==Long.MAX_VALUE)throw new IllegalStateException("Ramp timeline sequence exhausted");
            return new Timeline(sequence+1,tick,tick,observed,next,observed,ticks,RampLifecycle.moving(observed,next));
        }
        public Timeline committed(long tick,double current){
            if(!pose(current))throw new IllegalArgumentException("Ramp committed pose");
            boolean active=moving&&!arrived(current,target);
            return new Timeline(sequence,started,tick,active?from:current,target,current,duration,active);
        }
        /** A refused frame freezes at the last committed position. */
        public Timeline stopped(long tick){return new Timeline(sequence,started,tick,observed,target,observed,duration,false);}
    }
    private static boolean pose(double value){return Double.isFinite(value)&&value>=0&&value<=1;}
}
