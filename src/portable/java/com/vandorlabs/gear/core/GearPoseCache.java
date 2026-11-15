package com.vandorlabs.gear.core;

/** Small caller-owned kinematic cache. Other threads evaluate directly and never share mutable slots. */
public final class GearPoseCache {
    private static final int LIMIT=8;
    private final long ownerThread=Thread.currentThread().getId();
    private final GearMotion.Pose[] poses=new GearMotion.Pose[LIMIT];
    private final long[] phases=new long[LIMIT],activeAngles=new long[LIMIT],startAngles=new long[LIMIT];
    private GearMotion.Mode mode;private GearMotion.Direction fold,kink;private ArmHinge.Mode hinge;
    private int height,width,next;private long stowed,drop,socket;private boolean initialized;
    public GearMotion.Pose get(GearMotion.Mode mode,GearMotion.Direction fold,int height,double phase,double stowed,double drop,double socket,double active,double start,ArmHinge.Mode hinge,GearMotion.Direction kink,int width){
        if(Thread.currentThread().getId()!=ownerThread)return GearMotion.pose(mode,fold,height,phase,stowed,drop,socket,active,start,hinge,kink,width);
        long stowedBits=Double.doubleToLongBits(stowed),dropBits=Double.doubleToLongBits(drop),socketBits=Double.doubleToLongBits(socket);
        if(!initialized||this.mode!=mode||this.fold!=fold||this.height!=height||this.stowed!=stowedBits||this.drop!=dropBits||this.socket!=socketBits||this.hinge!=hinge||this.kink!=kink||this.width!=width){
            java.util.Arrays.fill(poses,null);next=0;this.mode=mode;this.fold=fold;this.height=height;this.stowed=stowedBits;this.drop=dropBits;this.socket=socketBits;this.hinge=hinge;this.kink=kink;this.width=width;initialized=true;
        }
        long phaseBits=Double.doubleToLongBits(phase),activeBits=Double.doubleToLongBits(active),startBits=Double.doubleToLongBits(start);
        for(int i=0;i<LIMIT;i++)if(poses[i]!=null&&phases[i]==phaseBits&&activeAngles[i]==activeBits&&startAngles[i]==startBits)return poses[i];
        GearMotion.Pose result=GearMotion.pose(mode,fold,height,phase,stowed,drop,socket,active,start,hinge,kink,width);
        phases[next]=phaseBits;activeAngles[next]=activeBits;startAngles[next]=startBits;poses[next]=result;next=(next+1)%LIMIT;
        return result;
    }
    /** Includes all eight Double poses and their fixed scratch arrays; retains no world, tile or NBT. */
    public long retainedBytes(){return 4096;}
}
