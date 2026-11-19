package com.vandorlabs.turntable;

/** Axis-independent rotation contracts: visual winding, inverse and swept bounds. */
public final class TurntableGeometryTest {
    private static int checks;
    private static void near(double actual,double expected){checks++;if(Math.abs(actual-expected)>1e-8)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        for(TurntableGeometry.Face face:TurntableGeometry.Face.values()){
            near(face.ux*face.vx+face.uy*face.vy+face.uz*face.vz,0);
            double[] clockwise=TurntableGeometry.rotate(face.ux,face.uy,face.uz,face,90);
            near(clockwise[0],face.vx);near(clockwise[1],face.vy);near(clockwise[2],face.vz);
            for(int angle=-360;angle<=360;angle+=7){
                double[] point=TurntableGeometry.rotate(2.3,-4.1,.7,face,angle);
                double[] back=TurntableGeometry.rotate(point[0],point[1],point[2],face,-angle);
                near(back[0],2.3);near(back[1],-4.1);near(back[2],.7);
                double[] box={-3,-2,-1,4,5,6},bounds=TurntableGeometry.bounds(box,face,angle);
                for(int corner=0;corner<8;corner++){
                    double[] p=TurntableGeometry.rotate(box[(corner&1)==0?0:3],box[(corner&2)==0?1:4],box[(corner&4)==0?2:5],face,angle);
                    for(int axis=0;axis<3;axis++){checks++;if(p[axis]<bounds[axis]-1e-9||p[axis]>bounds[axis+3]+1e-9)throw new AssertionError("Missing corner");}
                }
            }
            for(int width=1;width<=32;width++)for(int quarter=-4;quarter<=4;quarter++){
                double mid=(width-1)*.5;
                double[] pivot=face.point(-12+mid,9+mid,27.5);
                for(int axis=0;axis<3;axis++)pivot[axis]+=.5;
                for(int u=0;u<width;u++)for(int v=0;v<width;v+=Math.max(1,width-1)){
                    double[] source=face.point(-12+u,9+v,30);
                    double[] rotated=TurntableGeometry.rotate(source[0]+.5-pivot[0],source[1]+.5-pivot[1],source[2]+.5-pivot[2],face,quarter*90);
                    int[] target=TurntableGeometry.rotateCell((int)source[0],(int)source[1],(int)source[2],pivot[0],pivot[1],pivot[2],face,quarter);
                    for(int axis=0;axis<3;axis++)near(target[axis],Math.round(rotated[axis]+pivot[axis]-.5));
                }
            }
            double[] world=face.point(-12,9,5);near(face.u((int)world[0],(int)world[1],(int)world[2]),-12);near(face.v((int)world[0],(int)world[1],(int)world[2]),9);near(face.plane((int)world[0],(int)world[1],(int)world[2]),5);
        }
        for(int size=1;size<=32;size++){if(!TurntableGeometry.square(-20,8,-21+size,7+size,size*size))throw new AssertionError();if(TurntableGeometry.square(0,0,size-1,size-1,size*size-1))throw new AssertionError();}
        if(TurntableGeometry.square(0,0,32,32,1089)||TurntableGeometry.square(0,0,1,2,6))throw new AssertionError();
        near(TurntableGeometry.degreesPerTick(1)*1200,360);near(TurntableGeometry.wrap(-10),350);
        for(double invalid:new double[]{0,-1,.1,.6,10.5,30,Double.NaN,Double.POSITIVE_INFINITY}){try{TurntableGeometry.degreesPerTick(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        for(int step=1;step<=20;step++)near(TurntableGeometry.degreesPerTick(step/2D),step*.15);
        for(int step=0;step<=50;step++)for(int quarter=-4;quarter<=8;quarter++)for(int sign:new int[]{-1,1}){
            double tolerance=step/10D,angle=quarter*90+sign*tolerance;
            if(!TurntableGeometry.canAutoSnap(angle,tolerance)||TurntableGeometry.canAutoSnap(angle+sign*.01,tolerance))throw new AssertionError("Auto-snap boundary "+angle+" / "+tolerance);
            checks+=2;
        }
        for(double invalid:new double[]{-1,5.1,Double.NaN,Double.POSITIVE_INFINITY}){try{TurntableGeometry.canAutoSnap(90,invalid);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        if(TurntableGeometry.canAutoSnap(Double.NaN,5))throw new AssertionError("Nonfinite heading");
        System.out.println("Turntable geometry: "+checks+" checks passed");
    }
}
