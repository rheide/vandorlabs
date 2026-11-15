package com.vandorlabs.gear.core;

/** Cache identity, complete input invalidation, bounded retention and non-owner-thread isolation. */
public final class GearPoseCacheTest {
    private static int checks;
    private static GearMotion.Pose pose(GearPoseCache cache,double phase,double active,double start){return cache.get(GearMotion.Mode.ROTATE,GearMotion.Direction.NORTH,2,phase,.1875,0,0,active,start,ArmHinge.Mode.DOUBLE,GearMotion.Direction.EAST,2);}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        GearPoseCache cache=new GearPoseCache();GearMotion.Pose initial=pose(cache,.25,90,0);
        need(initial==pose(cache,.25,90,0),"same inputs reuse immutable pose");
        need(initial!=pose(cache,.25,120,0),"active angle invalidates lookup");
        need(initial!=pose(cache,.25,90,15),"start angle invalidates lookup");
        need(initial!=pose(cache,.75,90,0),"phase invalidates lookup");
        need(initial==pose(cache,.25,90,0),"angle probes retain ordinary motion entries");
        for(int changed=0;changed<9;changed++){
            initial=pose(cache,.25,90,0);int c=changed;
            GearMotion.Pose different=cache.get(c==0?GearMotion.Mode.ROTATE_LEVEL:GearMotion.Mode.ROTATE,c==1?GearMotion.Direction.SOUTH:GearMotion.Direction.NORTH,c==2?3:2,.25,c==3?.25:.1875,c==4?.125:0,c==5?.5:0,90,0,c==6?ArmHinge.Mode.SINGLE:ArmHinge.Mode.DOUBLE,c==7?GearMotion.Direction.WEST:GearMotion.Direction.EAST,c==8?1:2);
            need(initial!=different,"kinematic field "+changed+" invalidates");
        }
        initial=pose(cache,.25,90,0);final GearMotion.Pose retained=initial;final GearMotion.Pose[] foreign=new GearMotion.Pose[2];
        Thread worker=new Thread(()->{foreign[0]=pose(cache,.25,90,0);foreign[1]=pose(cache,.25,90,0);});worker.start();worker.join();
        need(foreign[0]!=retained&&foreign[0]!=foreign[1],"non-owner thread does not consume mutable slots");
        need(retained==pose(cache,.25,90,0),"non-owner leaves the owner cache unchanged");
        try{pose(cache,Double.NaN,90,0);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
        need(retained==pose(cache,.25,90,0),"invalid phase cannot replace a valid pose");
        for(int i=0;i<1000;i++)pose(cache,i/999D,90,0);
        need(retained!=pose(cache,.25,90,0),"old entries evict within fixed eight slots");
        need(cache.retainedBytes()<=4096,"retained-memory bound");
        System.out.println("Gear pose cache PASS checks="+checks+" eightSlots/completeInputs/threadIsolation/noWorldRetention");
    }
}
