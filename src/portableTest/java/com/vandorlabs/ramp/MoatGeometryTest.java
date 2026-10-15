package com.vandorlabs.ramp;

import java.util.List;

/** Physical tower endpoints, native slab gaps, rigid chunks and analytic swept extrema. */
public final class MoatGeometryTest {
    private static void require(boolean ok,String what){if(!ok)throw new AssertionError("Vertical Moat: "+what);}
    private static void close(double a,double b){require(Math.abs(a-b)<1e-8,"coordinate "+a+" != "+b);}
    private static void same(RampGeometry.Box a,RampGeometry.Box b){close(a.minX,b.minX);close(a.minY,b.minY);close(a.minZ,b.minZ);close(a.maxX,b.maxX);close(a.maxY,b.maxY);close(a.maxZ,b.maxZ);}
    private static double volume(List<RampGeometry.Box> boxes){double result=0;for(RampGeometry.Box b:boxes)result+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);return result;}
    private static double distance(double[] a,double[] b){double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return Math.sqrt(x*x+y*y+z*z);}
    private static void contains(RampGeometry.Box outer,RampGeometry.Box inner){require(inner.minX>=outer.minX-1e-8&&inner.minY>=outer.minY-1e-8&&inner.minZ>=outer.minZ-1e-8&&inner.maxX<=outer.maxX+1e-8&&inner.maxY<=outer.maxY+1e-8&&inner.maxZ<=outer.maxZ+1e-8,"analytic sweep encloses intermediate rotated source");}
    public static void run(){
        LegacyMoatGeometryTest.run();
        for(RampGeometry.Direction face:RampGeometry.Direction.values()){
            int x=10,z=12,baseY=20,length=3,dx=face==RampGeometry.Direction.EAST?1:face==RampGeometry.Direction.WEST?-1:0,dz=face==RampGeometry.Direction.SOUTH?1:face==RampGeometry.Direction.NORTH?-1:0,min=x*dx+z*dz;double hinge=min-(dx<0||dz<0?1:0);
            for(int pixels:new int[]{1,2,4,8,16})for(double[] slab:new double[][]{{0,1},{0,.5},{.5,1}})for(int row=0;row<length;row++){
                double low=slab[0],high=slab[1];RampGeometry.Box original=new RampGeometry.Box(x,baseY+row+low,z,x+1,baseY+row+high,z+1);
                same(MoatGeometry.box(face,min,baseY,length,0,0,0,false,original),original);
                List<RampGeometry.Box> closed=MoatGeometry.boxes(face,min,baseY,length,pixels,low,high,0,0,0,false,x,baseY+row,z),open=MoatGeometry.boxes(face,min,baseY,length,pixels,low,high,0,0,1,false,x,baseY+row,z);
                close(volume(closed),high-low);close(volume(open),high-low);require(closed.size()==(int)Math.ceil((high-low)*16/pixels),"tread pixels controls vertical source chunk count");
                RampGeometry.Box deployed=MoatGeometry.box(face,min,baseY,length,0,0,1,false,original);close(deployed.minY,baseY-1);close(deployed.maxY,baseY);
                double alongMin=dx>0?deployed.minX:dx<0?-deployed.maxX:dz>0?deployed.minZ:-deployed.maxZ,alongMax=dx>0?deployed.maxX:dx<0?-deployed.minX:dz>0?deployed.maxZ:-deployed.minZ;
                close(alongMin,hinge+row+low);close(alongMax,hinge+row+high);
                for(double[] offsets:new double[][]{{0,0},{2,1},{-2,-1},{16,-16},{-16,16}}){
                    double start=offsets[0],end=offsets[1];RampGeometry.Box sweep=MoatGeometry.swept(face,min,baseY,length,pixels,low,high,start,end,.07,.94,false,x,baseY+row,z);
                    for(int step=0;step<=128;step++){
                        double pose=.07+.87*step/128;RampGeometry.Box at=MoatGeometry.box(face,min,baseY,length,start,end,pose,false,original);contains(sweep,at);
                        double[] p=MoatGeometry.vertex(face,min,baseY,length,start,end,pose,false,x+.3,baseY+row+low+(high-low)*.4,z+.7),back=MoatGeometry.inverse(face,min,baseY,length,start,end,pose,false,p[0],p[1],p[2]);close(back[0],x+.3);close(back[1],baseY+row+low+(high-low)*.4);close(back[2],z+.7);
                    }
                    same(MoatGeometry.swept(face,min,baseY,length,pixels,low,high,start,end,.31,.31,false,x,baseY+row,z),MoatGeometry.box(face,min,baseY,length,start,end,.31,false,original));
                }
            }
            double nearX=x+(dx<0?1:dx==0?.3:0),nearZ=z+(dz<0?1:dz==0?.3:0);
            double[] tip=MoatGeometry.vertex(face,min,baseY,length,2,-1,0,false,nearX,baseY+length,nearZ);close(tip[0]*dx+tip[2]*dz-hinge,length*Math.sin(Math.atan2(2,length)));close(tip[1]-baseY,length*Math.cos(Math.atan2(2,length)));
            tip=MoatGeometry.vertex(face,min,baseY,length,2,-1,1,false,nearX,baseY+length,nearZ);close(tip[0]*dx+tip[2]*dz-hinge,length*Math.cos(Math.atan2(-1,length)));close(tip[1]-baseY,length*Math.sin(Math.atan2(-1,length)));
            for(double pose:new double[]{0,.25,.5,.75,1}){
                double[] a={x+.1,baseY+.2,z+.3},b={x+.7,baseY+2.8,z+.9};double[] p=MoatGeometry.vertex(face,min,baseY,length,-2,3,pose,false,a[0],a[1],a[2]),q=MoatGeometry.vertex(face,min,baseY,length,-2,3,pose,false,b[0],b[1],b[2]);close(distance(p,q),distance(a,b));
            }
            RampGeometry.Box first=MoatGeometry.swept(face,min,baseY,length,8,0,1,0,0,0,1,false,x,baseY,z);double minimum=dx>0?first.minX:dx<0?-first.maxX:dz>0?first.minZ:-first.maxZ;close(minimum,hinge);
        }
        System.out.println("Moat geometry PASS: vertical tower identity, physical downward deck, native slab gaps, tread chunks, angular offsets, rigidity, inverse and exact swept extrema");
    }
    public static void main(String[] args){run();}
}