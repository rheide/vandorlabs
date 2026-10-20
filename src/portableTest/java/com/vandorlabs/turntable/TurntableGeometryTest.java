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
            double[] world=face.point(-12,9,5);near(face.u((int)world[0],(int)world[1],(int)world[2]),-12);near(face.v((int)world[0],(int)world[1],(int)world[2]),9);near(face.plane((int)world[0],(int)world[1],(int)world[2]),5);
        }
        for(int size=1;size<=16;size++){if(!TurntableGeometry.square(-20,8,-21+size,7+size,size*size))throw new AssertionError();if(TurntableGeometry.square(0,0,size-1,size-1,size*size-1))throw new AssertionError();}
        if(TurntableGeometry.square(0,0,16,16,289)||TurntableGeometry.square(0,0,1,2,6))throw new AssertionError();
        near(TurntableGeometry.degreesPerTick(1)*1200,360);near(TurntableGeometry.wrap(-10),350);
        for(double invalid:new double[]{0,-1,.1,.6,10.5,30,Double.NaN,Double.POSITIVE_INFINITY}){try{TurntableGeometry.degreesPerTick(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        for(int step=1;step<=20;step++)near(TurntableGeometry.degreesPerTick(step/2D),step*.15);
        System.out.println("Turntable geometry: "+checks+" checks passed");
    }
}
