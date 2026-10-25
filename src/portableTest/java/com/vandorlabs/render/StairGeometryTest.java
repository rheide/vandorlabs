package com.vandorlabs.render;

import com.vandorlabs.render.ControlMount.Box;
import com.vandorlabs.render.ControlMount.Point;
import java.util.List;

public final class StairGeometryTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Stair geometry "+checks);}
    private static boolean filled(List<Box> boxes,Point p){for(Box b:boxes)if(p.x>b.minX&&p.x<b.maxX&&p.y>b.minY&&p.y<b.maxY&&p.z>b.minZ&&p.z<b.maxZ)return true;return false;}
    private static Point normal(int side){return side==0?new Point(0,-1,0):side==1?new Point(0,1,0):side==2?new Point(0,0,-1):side==3?new Point(0,0,1):side==4?new Point(-1,0,0):new Point(1,0,0);}
    private static double volume(List<Box> boxes){double n=0;for(Box b:boxes)n+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);return n;}
    public static void main(String[] args){
        int poses=0,faces=0;
        for(int tread:new int[]{1,2,4,8,16})for(StairGeometry.Shape shape:StairGeometry.Shape.values()){
            double expected=volume(StairGeometry.boxes(5,false,shape,tread));if(shape==StairGeometry.Shape.STRAIGHT)check(expected==(16D/tread+1)/(2*(16D/tread)));
            for(int facing:new int[]{2,3,4,5})for(boolean upper:new boolean[]{false,true})for(int roll=0;roll<3;roll++){
                poses++;int settings=tread|(roll<<5);List<Box> boxes=StairGeometry.boxes(facing,upper,shape,settings);check(boxes.size()==(16/tread)*(16/tread));check(volume(boxes)==expected);
                for(Box b:boxes){check(b.minX>=0&&b.minY>=0&&b.minZ>=0&&b.maxX<=1&&b.maxY<=1&&b.maxZ<=1);check(b.minX<b.maxX&&b.minY<b.maxY&&b.minZ<b.maxZ);}
                for(StairGeometry.Face face:StairGeometry.mesh(facing,upper,shape,settings)){
                    faces++;Point center=new Point(0,0,0);for(int i=0;i<4;i++){center=center.add(face.vertices[i].scale(.25));check(face.u[i]>=0&&face.u[i]<=16&&face.v[i]>=0&&face.v[i]<=16);}
                    Point n=normal(face.face);check(filled(boxes,center.subtract(n.scale(1e-7))));check(!filled(boxes,center.add(n.scale(1e-7))));check(face.textureFace==StairGeometry.unrolledFace(facing,upper,roll,face.face));
                    Point cross=face.vertices[1].subtract(face.vertices[0]).cross(face.vertices[2].subtract(face.vertices[0]));check(cross.dot(n)>0);
                }
                int support=roll==0?(upper?1:0):StairGeometry.mesh(facing,upper,shape,settings).get(1).face;check(StairGeometry.solidFace(facing,upper,shape,settings,support));
                if(tread==16)for(int side=0;side<6;side++)check(StairGeometry.solidFace(facing,upper,shape,settings,side));
            }
        }
        for(int face=0;face<6;face++)for(float hit:new float[]{0,.5F,.50001F,1}){check(SlabMergePolicy.upperHalf(face,hit)==(face==0||face!=1&&hit>.5F));for(boolean upper:new boolean[]{false,true}){check(SlabMergePolicy.directMerge(upper,face)==(face==(upper?0:1)));check(SlabMergePolicy.target(true,upper,face,true)==(face==(upper?0:1)?SlabMergePolicy.Target.DIRECT:SlabMergePolicy.Target.ADJACENT));check(SlabMergePolicy.target(false,upper,face,false)==SlabMergePolicy.Target.NONE);}}
        System.out.println("StairGeometryTest PASS "+poses+" poses "+faces+" exposed faces "+checks+" checks");
    }
}
