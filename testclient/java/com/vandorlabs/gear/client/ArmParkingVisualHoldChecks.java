package com.vandorlabs.gear.client;

/** Exercises the actual authority-only client smoother without a world or graphics context. */
public final class ArmParkingVisualHoldChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Arm held visual clock: "+why);}
    public static void main(String[] args){
        GearVisualMotion.check();GearVisualMotion.Pose pose=new GearVisualMotion.Pose();
        need(GearVisualMotion.advance(pose,.4,0)==.4,"Initial stationary authority");
        for(int frame=0;frame<1000;frame++)need(GearVisualMotion.advance(pose,.4,frame/20D)==.4,"Held target cannot predict beyond constant authority");
        for(int packet=0;packet<100;packet++)need(GearVisualMotion.advance(pose,.4,50)==.4,"Repeated packets and paused clock do not restart a hold");
        need(GearVisualMotion.advance(pose,.35,51)==.4,"Release authority starts from prior held visual pose");
        need(Math.abs(GearVisualMotion.advance(pose,.35,80)-.35)<1e-12,"Released motion settles at received authority");
        need(Math.abs(GearVisualMotion.advance(pose,.5,81)-.35)<1e-12,"Reversal retains continuity");
        need(Math.abs(GearVisualMotion.advance(pose,.5,110)-.5)<1e-12,"Reversal settles at new authority");
        System.out.println("Arm parking visual hold PASS checks="+checks+" actual authority smoother held/repeated/pause/release/reversal plus existing20/5TPS clock matrix");
    }
}
