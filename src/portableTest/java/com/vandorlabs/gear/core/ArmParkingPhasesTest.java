package com.vandorlabs.gear.core;

import java.util.*;

public final class ArmParkingPhasesTest {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static boolean integral(double value){return Math.abs(value-Math.rint(value))<1e-8;}
    public static void main(String[] args){
        for(int half=-16;half<=16;half++){
            double stroke=half/2D;double[] phases=ArmParkingPhases.linear(new double[]{0,0,0},new double[]{0,stroke,0});Set<Integer> found=new HashSet<>();
            for(double p:phases){double at=stroke*GearMotion.smoothstep(p);if(integral(at))found.add((int)Math.rint(at));}
            for(int n=(int)Math.ceil(Math.min(0,stroke));n<=Math.floor(Math.max(0,stroke));n++)need(found.contains(n),"Every intermediate extender block crossing "+stroke+" / "+n);
            need(phases.length<=19,"Source extender phase bound");
        }
        for(int a=-180;a<=180;a+=15)for(int b=-180;b<=180;b+=15){
            double[] phases=ArmParkingPhases.rotating(a,b);Set<Integer> found=new HashSet<>();
            for(double p:phases){double angle=GearMotion.pose(GearMotion.Mode.ROTATE,GearMotion.Direction.WEST,3,p,0,0,13/16D,a,b).angleDegrees();if(integral(angle/90))found.add((int)Math.rint(angle/90));}
            for(int turn=(int)Math.ceil(Math.min(a,b)/90D);turn<=Math.floor(Math.max(a,b)/90D);turn++)need(found.contains(turn),"Every actual eased cardinal angle "+a+" / "+b+" / "+turn);
            need(phases.length<=7,"Source rigid rotation phase bound");
        }
        double[] phases=ArmParkingPhases.leveling(new double[]{0,0,0},new double[]{5,0,0},new double[]{0,5,0},0,180);Set<String> lattice=new HashSet<>();boolean nonQuarter=false;
        for(double p:phases){double angle=Math.PI*GearMotion.smoothstep(p),x=5*Math.cos(angle),y=5*Math.sin(angle);if(integral(x)&&integral(y)){lattice.add(Math.round(x)+","+Math.round(y));if(Math.round(x)==3&&Math.round(y)==4)nonQuarter=Math.abs(p*4-Math.rint(p*4))>1e-4;}}
        need(lattice.equals(new HashSet<>(Arrays.asList("5,0","4,3","3,4","0,5","-3,4","-4,3","-5,0"))),"Exact circle lattice crossings");need(nonQuarter,"Nonuniform valid intermediate circle phase is retained");
        phases=ArmParkingPhases.leveling(new double[]{0,0,0},new double[]{256,0,0},new double[]{0,256,0},-180,180);need(phases.length<=ArmParkingPhases.MAX_PHASES,"Worst bounded roots");
        boolean failed=false;try{ArmParkingPhases.leveling(new double[]{0,0,0},new double[]{257,0,0},new double[]{0,257,0},-180,180);}catch(IllegalArgumentException expected){failed=true;}need(failed,"Unbounded coordinate span refuses, never truncates");
        failed=false;try{ArmParkingPhases.rotating(Double.NaN,90);}catch(IllegalArgumentException expected){failed=true;}need(failed,"Nonfinite angle refused");
        for(int i=0;i<=10000;i++){double p=i/10000D;need(Math.abs(ArmParkingPhases.inverseEase(GearMotion.smoothstep(p))-p)<1e-10,"Actual smoothstep inversion");}
        System.out.println("Arm parking critical phases PASS checks="+checks+" eased integer crossings/cardinal bases/circle lattice/bounded refusal; no uniform phase sampler");
    }
}
