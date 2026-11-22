package com.vandorlabs.render;

/**
 * A joined Large Door leaf swings in true proportions: drawn inside its group's sideways stretch
 * (two modules wide here), a 1.5-wide leaf opened 90 degrees reaches 3 blocks across the doorway,
 * not the 1.5 of one module. Closed leaves and single doors are unchanged.
 */
public final class DoorLeafSwingChecks {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    /** Transformed (x, z) of one vertex at (x, 0, 0); facing 3 keeps the local frame. */
    private static double[] swing(double x,double angle,double across){
        int[] source=new int[8],out=new int[8];
        source[0]=Float.floatToRawIntBits((float)x);
        DoorMeshTransform.write(source,8,out,false,false,3,0,0,0,0,0,0,0,0,0,angle,1,0,across);
        return new double[]{Float.intBitsToFloat(out[0]),Float.intBitsToFloat(out[2])};
    }
    public static void main(String[] args){
        double[] closed=swing(1.5,0,2);
        require(Math.abs(closed[0]-1.5)<1e-6&&Math.abs(closed[1])<1e-6,"closed joined leaf is unchanged: "+closed[0]+","+closed[1]);
        double[] open=swing(1.5,90,2);
        // Inside the group's stretch x is later doubled; z (across the doorway) is not.
        require(Math.abs(open[0]*2)<1e-6&&Math.abs(Math.abs(open[1])-3)<1e-6,"opened joined leaf reaches the group's half width: "+open[0]+","+open[1]);
        double[] single=swing(1.5,90,1);
        require(Math.abs(Math.abs(single[1])-1.5)<1e-6,"a single door's leaf still reaches 1.5: "+single[1]);
        for(int angle=0;angle<=90;angle+=15){
            // The leaf stays rigid: its length in true proportions is 3 at every angle.
            double[] p=swing(1.5,angle,2);double length=Math.hypot(p[0]*2,p[1]);
            require(Math.abs(length-3)<1e-5,"joined leaf keeps its length while swinging at "+angle+": "+length);
        }
        System.out.println("door-leaf-swing portable PASS checks="+checks);
    }
}
