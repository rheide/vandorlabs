package com.vandorlabs.render;

import java.util.List;

public final class StairGeometryTest {
    private static final class P {final double x,y,z;P(double x,double y,double z){this.x=x==0?0:x;this.y=y==0?0:y;this.z=z==0?0:z;}}
    private static final class B {final double x0,y0,z0,x1,y1,z1;B(double x0,double y0,double z0,double x1,double y1,double z1){this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;}}
    private static final StairGeometry.Points<P> POINTS=new StairGeometry.Points<P>(){public P create(double x,double y,double z){return new P(x,y,z);}public double x(P p){return p.x;}public double y(P p){return p.y;}public double z(P p){return p.z;}};
    private static final StairGeometry.Boxes<B> BOXES=new StairGeometry.Boxes<B>(){public B create(double x0,double y0,double z0,double x1,double y1,double z1){return new B(x0,y0,z0,x1,y1,z1);}public double minX(B b){return b.x0;}public double minY(B b){return b.y0;}public double minZ(B b){return b.z0;}public double maxX(B b){return b.x1;}public double maxY(B b){return b.y1;}public double maxZ(B b){return b.z1;}};
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static double volume(List<B> boxes){double result=0;for(B b:boxes)result+=(b.x1-b.x0)*(b.y1-b.y0)*(b.z1-b.z0);return result;}
    public static void main(String[] ignored){
        require(StairGeometry.height(0,0,2,StairGeometry.Shape.STRAIGHT)==.5,"literal first tread");require(StairGeometry.height(0,0,2,StairGeometry.Shape.INNER_LEFT)==1,"inner uses higher arm");require(StairGeometry.height(1,1,2,StairGeometry.Shape.OUTER_LEFT)==.5,"outer uses lower arm");
        List<B> normal=StairGeometry.boxes(5,false,StairGeometry.Shape.STRAIGHT,8,POINTS,BOXES);require(normal.size()==4&&normal.get(0).x0==0&&normal.get(0).x1==.5&&normal.get(0).y1==.5&&normal.get(3).x0==.5&&normal.get(3).x1==1&&normal.get(3).y1==1,"literal east step cells");
        P p=StairGeometry.transform(.25,.125,.75,2,true,1,POINTS);require(p.x==.125&&p.y==.75&&p.z==.75,"upper then roll then facing order");
        int poses=0,faces=0;for(int facing:new int[]{2,3,4,5})for(boolean upper:new boolean[]{false,true})for(StairGeometry.Shape shape:StairGeometry.Shape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
            int settings=tread|(roll<<5);List<B> boxes=StairGeometry.boxes(facing,upper,shape,settings,POINTS,BOXES);require(boxes.size()==(16/tread)*(16/tread),"height field cells");double expected=0;int count=16/tread;for(int x=0;x<count;x++)for(int z=0;z<count;z++)expected+=StairGeometry.height(x,z,count,shape)/(count*count);require(Math.abs(volume(boxes)-expected)<1e-12,"roll/reflection keeps volume");for(B b:boxes)require(b.x0>=0&&b.y0>=0&&b.z0>=0&&b.x1<=1&&b.y1<=1&&b.z1<=1,"unit cell bounds");
            for(int side=0;side<6;side++){int unrolled=StairGeometry.unrolledFace(facing,upper,roll,side,POINTS);require(unrolled>=0&&unrolled<6,"unrolled cardinal face");if(tread==16)require(StairGeometry.solidFace(facing,upper,shape,settings,side,POINTS,BOXES),"16px saved pose fills whole cube");}
            final int currentRoll=roll;List<Integer> emitted=StairGeometry.mesh(facing,upper,shape,settings,POINTS,(a,b,c,d,ta,tb,tc,td,side,textureSide)->{require(side>=0&&side<6&&textureSide>=0&&textureSide<6,"emitted cardinal faces");require(StairGeometry.unrolledFace(facing,upper,currentRoll,side,POINTS)==textureSide,"texture face follows inverse roll");return side;});require(!emitted.isEmpty(),"closed emitted mesh");faces+=emitted.size();poses++;
        }
        System.out.println("StairGeometry PASS poses="+poses+" faces="+faces+" literal heights/transform/cells, all shapes/rolls/halves, volume/bounds and unrolled texture faces");
    }
}
