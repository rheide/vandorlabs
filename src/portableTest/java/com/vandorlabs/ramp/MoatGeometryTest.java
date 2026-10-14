package com.vandorlabs.ramp;

public final class MoatGeometryTest {
    private static void require(boolean ok,String what){if(!ok)throw new AssertionError("Moat: "+what);}
    private static void close(double a,double b){require(Math.abs(a-b)<1e-8,"coordinate "+a+" != "+b);}
    public static void run(){
        for(RampGeometry.Direction face:RampGeometry.Direction.values())for(double thickness:new double[]{.5,1}){
            int x=10,z=12,y=20,dx=face==RampGeometry.Direction.EAST?1:face==RampGeometry.Direction.WEST?-1:0,dz=face==RampGeometry.Direction.SOUTH?1:face==RampGeometry.Direction.NORTH?-1:0,min=x*dx+z*dz;
            for(int row=0;row<4;row++){
                int sx=x+dx*row,sz=z+dz*row;RampGeometry.Box original=new RampGeometry.Box(sx,y,sz,sx+1,y+thickness,sz+1),open=MoatGeometry.box(face,min,y,thickness,1.5,-2,1,false,original);
                close(open.minX,original.minX);close(open.maxX,original.maxX);close(open.minZ,original.minZ);close(open.minY,y-2);close(open.maxY,y+thickness-2);
                RampGeometry.Box closed=MoatGeometry.box(face,min,y,thickness,1.5,-2,0,false,original);close(closed.minY,y+thickness+row+1.5);close(closed.maxY,closed.minY+1);
                RampGeometry.Box sweep=MoatGeometry.swept(face,min,y,0,thickness,1.5,-2,.13,.91,false,sx,y,sz);
                for(int i=0;i<=500;i++){double pose=.13+(.91-.13)*i/500;RampGeometry.Box at=MoatGeometry.box(face,min,y,thickness,1.5,-2,pose,false,original);require(at.minX>=sweep.minX-1e-8&&at.maxX<=sweep.maxX+1e-8&&at.minY>=sweep.minY-1e-8&&at.maxY<=sweep.maxY+1e-8&&at.minZ>=sweep.minZ-1e-8&&at.maxZ<=sweep.maxZ+1e-8,"sweep encloses intermediate arc");
                    double[] p=MoatGeometry.vertex(face,min,y,thickness,1.5,-2,pose,false,sx+.3,y+thickness*.4,sz+.7),back=MoatGeometry.inverse(face,min,y,thickness,1.5,-2,pose,false,p[0],p[1],p[2]);close(back[0],sx+.3);close(back[1],y+thickness*.4);close(back[2],sz+.7);
                }
            }
        }
        System.out.println("Moat geometry PASS: cardinal endpoints, slab thickness, signed offsets, inverse transform and full swept arcs");
    }
    public static void main(String[] args){run();}
}
