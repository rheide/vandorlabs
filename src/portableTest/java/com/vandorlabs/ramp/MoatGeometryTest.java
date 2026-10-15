package com.vandorlabs.ramp;
import java.util.List;
/** Exact ordinary-ramp endpoints, native shapes and precise per-tread arc reservations. */
public final class MoatGeometryTest {
    private static void require(boolean ok,String what){if(!ok)throw new AssertionError("Moat: "+what);}
    private static void close(double a,double b){require(Math.abs(a-b)<1e-8,a+" != "+b);}
    private static void same(RampGeometry.Box a,RampGeometry.Box b){close(a.minX,b.minX);close(a.minY,b.minY);close(a.minZ,b.minZ);close(a.maxX,b.maxX);close(a.maxY,b.maxY);close(a.maxZ,b.maxZ);}
    private static void contains(RampGeometry.Box a,RampGeometry.Box b){require(b.minX>=a.minX-1e-8&&b.minY>=a.minY-1e-8&&b.minZ>=a.minZ-1e-8&&b.maxX<=a.maxX+1e-8&&b.maxY<=a.maxY+1e-8&&b.maxZ<=a.maxZ+1e-8,"analytic tread sweep misses arc");}
    public static void run(){
        RigidMoatGeometryTest.run();
        int x=10,z=12,base=20,length=3;
        for(RampGeometry.Direction face:RampGeometry.Direction.values())for(int pixels:new int[]{1,2,4,8,16})for(double[] slab:new double[][]{{0,1},{0,.5},{.5,1}})for(int row=0;row<length;row++)for(int interpolation=0;interpolation<3;interpolation++){
            int dx=face==RampGeometry.Direction.EAST?1:face==RampGeometry.Direction.WEST?-1:0,dz=face==RampGeometry.Direction.SOUTH?1:face==RampGeometry.Direction.NORTH?-1:0;
            List<RampGeometry.Box> pieces=MoatGeometry.pieces(face,new RampGeometry.Box(0,slab[0],0,1,slab[1],1),pixels);require(pieces.size()==ControllerPlatform.treadCount(pixels),"native half slab keeps all horizontal treads");
            for(RampGeometry.Box piece:pieces){int step=MoatGeometry.step(face,piece,pixels);RampGeometry.Box original=MoatGeometry.world(piece,x,base+row,z);
                same(MoatGeometry.transform(face,base,length,pixels,0,0,0,false,interpolation,base+row,step,original),original);
                for(double end:new double[]{-16,-1,0,1,16}){
                    RampGeometry.Box expected=RampGeometry.movingTread(face,x+dx*row,base,z+dz*row,slab[0],slab[1],row,length,0,end,pixels,step,1,1,false,RampGeometry.VERTICAL,false,false,interpolation);
                    same(MoatGeometry.transform(face,base,length,pixels,2,end,1,false,interpolation,base+row,step,original),expected);
                    for(double start:new double[]{-2,0,2}){
                        RampGeometry.Box sweep=MoatGeometry.sweep(face,base,length,pixels,start,end,.03,.97,false,interpolation,base+row,step,original);
                        for(int tick=0;tick<=96;tick++){double pose=.03+.94*tick/96;contains(sweep,MoatGeometry.transform(face,base,length,pixels,start,end,pose,false,interpolation,base+row,step,original));}
                        same(MoatGeometry.sweep(face,base,length,pixels,start,end,.31,.31,false,interpolation,base+row,step,original),MoatGeometry.transform(face,base,length,pixels,start,end,.31,false,interpolation,base+row,step,original));
                    }
                    RampGeometry.Box sweep=MoatGeometry.sweep(face,base,length,pixels,0,end,0,1,false,interpolation,base+row,step,original);
                    require(sweep.minY>=Math.min(original.minY,expected.minY)-1e-8,"never reserves below both actual endpoints");
                    if(dx>0)require(sweep.minX>=x,"no under-anchor reservation");if(dx<0)require(sweep.maxX<=x+1,"no under-anchor reservation");if(dz>0)require(sweep.minZ>=z,"no under-anchor reservation");if(dz<0)require(sweep.maxZ<=z+1,"no under-anchor reservation");
                }
            }
        }
        System.out.println("Moat geometry PASS exact-normal-ramp End -1/+1 every-tread cardinal native-slab thickness all-interpolation simultaneous-arc analytic-piece-sweeps no-under-anchor legacy");
    }
    public static void main(String[] args){run();}
}
