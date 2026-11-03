package com.vandorlabs.ramp;
import java.util.*;
final class VerticalRampGeometryTest {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Y ramp: "+why);}
    private static double volume(RampGeometry.Box b){return (b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);}
    private static boolean inside(RampGeometry.Box a,RampGeometry.Box b){return b.minX>=a.minX-1e-7&&b.minY>=a.minY-1e-7&&b.minZ>=a.minZ-1e-7&&b.maxX<=a.maxX+1e-7&&b.maxY<=a.maxY+1e-7&&b.maxZ<=a.maxZ+1e-7;}
    static void run(){
        for(int offset:new int[]{-3,3}){
            require(VerticalRampGeometry.beltSign(true,offset,1)==1,"Y Up climbs for either offset sign");
            require(VerticalRampGeometry.beltSign(true,offset,2)==-1,"Y Down descends for either offset sign");
            require(VerticalRampGeometry.beltSign(false,offset,1)==(offset>0?1:-1),"legacy uphill travel retained");
            require(VerticalRampGeometry.beltSign(false,offset,2)==(offset>0?-1:1),"legacy downhill travel retained");
        }
        for(RampGeometry.Direction face:RampGeometry.Direction.values()){
            ControllerPlatform.Cell seed=new ControllerPlatform.Cell(0,64,0);
            Set<ControllerPlatform.Cell> panel=ControllerPlatform.discoverPanel(seed,face,c->true);
            require(panel.size()==128,"bounded complete panel");
            for(ControllerPlatform.Cell c:panel)require(face==RampGeometry.Direction.EAST||face==RampGeometry.Direction.WEST?c.x==0:c.z==0,"no source depth growth");
            require(panel.stream().anyMatch(c->c.y<64)&&panel.stream().anyMatch(c->c.y>64),"middle seed grows both ways");
            for(int pixels:new int[]{1,2,4,8,16})for(boolean lift:new boolean[]{false,true})for(boolean fill:new boolean[]{false,true})for(int travel=0;travel<=2;travel++)for(int style=0;style<3;style++)for(int sign:new int[]{-1,1})for(int row=0;row<3;row++){
                RampGeometry.Box shape=new RampGeometry.Box(0,.5,0,1,1,1);
                List<VerticalRampGeometry.Piece> nativePieces=VerticalRampGeometry.pieces(face,2,64+row,3,shape,64,3,pixels,0,sign*3,0,lift,travel,fill,false,style,false,0);
                double nativeVolume=0;for(VerticalRampGeometry.Piece piece:nativePieces){nativeVolume+=volume(piece.box);require(inside(new RampGeometry.Box(2,64+row+.5,3,3,65+row,4),piece.box),"native top-slab orientation");}
                require(Math.abs(nativeVolume-.5)<1e-8,"native endpoint exact volume");
                for(double pose:new double[]{.25,.5,1})for(VerticalRampGeometry.Piece piece:VerticalRampGeometry.pieces(face,2,64+row,3,shape,64,3,pixels,0,sign*3,pose,lift,travel,fill,false,style,false,0)){
                    boolean covered=false;for(int step=0;step<(lift?1:ControllerPlatform.treadCount(pixels));step++)if(inside(VerticalRampGeometry.sweep(face,2,64+row,3,.5,1,64,3,pixels,step,0,sign*3,0,1,lift,travel,fill,false,style,false),piece.box))covered=true;
                    require(covered,"swept collision contains emitted piece");require(volume(piece.box)>0,"nondegenerate piece");
                }
            }
            for(int pixels:new int[]{1,2,4,8,16})for(int phase=0;phase<64;phase++){
                double volume=0;for(int row=0;row<3;row++)for(VerticalRampGeometry.Piece piece:VerticalRampGeometry.pieces(face,2,64+row,3,new RampGeometry.Box(0,0,0,1,1,1),64,3,pixels,0,2,1,false,0,false,false,0,true,phase/32D)){
                    require(piece.box.minY>=64-1e-8&&piece.box.maxY<=67+1e-8,"belt seam clipped to panel");volume+=volume(piece.box);
                }
                require(Math.abs(volume-3)<1e-7,"belt conserves source volume through seam");
            }
        }
        System.out.println("Vertical ramp geometry PASS "+checks+" native slab endpoints, all faces/modes/travel/profile/treads, swept coverage and belt seam volume");
    }
    private VerticalRampGeometryTest(){}
}
