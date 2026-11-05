package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidRegions;
import com.vandorlabs.render.DiagonalWallGeometry;
import com.vandorlabs.render.PanelCornerGeometry;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Current diagonal collision projection, including conservative slices, fills and loaded-neighbour cuts. */
public final class NativeDiagonalWallGeometry {
    public record Join(Boolean frontRight,Boolean backRight,boolean straightLeft,boolean straightRight,Double frontFlat,Double backFlat){
        public PanelCornerGeometry.Corner corner(){var result=new PanelCornerGeometry.Corner(frontRight,backRight,straightLeft,straightRight);result.frontFlat=frontFlat;result.backFlat=backFlat;return result;}
    }
    public static final class Clip {
        public static final Clip NONE=new Clip(-2,-2,-2,3,3,3);
        private final double[] values;private final int hash;
        public Clip(double... values){if(values.length<6||values.length%6!=0)throw new IllegalArgumentException("Diagonal clip coordinates");this.values=values.clone();for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite clip");hash=Arrays.hashCode(this.values);}
        public int size(){return values.length;}
        public double at(int index){return values[index];}
        public double[] scaled(double scale){double[] result=values.clone();for(int i=0;i<result.length;i++)result[i]*=scale;return result;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof Clip clip&&Arrays.equals(values,clip.values);}
    }
    public record Pose(Direction facing,boolean inverted,int mode,int fill,Join join,double lowerEnd,double upperEnd,boolean hideLower,boolean hideUpper,Clip clip,NativePanelLayout layout){
        public Pose {if(facing.getAxis()==Direction.Axis.Y||mode<0||mode>2||fill<0||fill>3||Double.isInfinite(lowerEnd)||Double.isInfinite(upperEnd))throw new IllegalArgumentException("Diagonal pose");Objects.requireNonNull(clip);Objects.requireNonNull(layout);}
        public Pose withLayout(NativePanelLayout next){return new Pose(facing,inverted,mode,fill,join,lowerEnd,upperEnd,hideLower,hideUpper,clip,next);}
    }
    public static final class Geometry implements NativeWallGeometry {
        private final Pose pose;private final List<AABB> boxes,fluidBoxes;private final AABB bounds,selection;
        private volatile NativeFluidRegions plainRegions,portholeRegions;
        private Geometry(Pose pose,List<AABB> boxes,List<AABB> fluidBoxes,AABB bounds,AABB selection){this.pose=pose;this.boxes=List.copyOf(boxes);this.fluidBoxes=List.copyOf(fluidBoxes);this.bounds=bounds;this.selection=selection;}
        public Pose pose(){return pose;}
        public List<AABB> boxes(){return boxes;}public List<AABB> fluidBoxes(){return fluidBoxes;}
        public AABB bounds(){return bounds;}public AABB selectionBounds(){return selection;}
        public Direction facing(){return pose.facing;}public int depth(){return 0;}public boolean diagonal(){return true;}
        public NativePanelLayout layout(){return pose.layout;}
        public Geometry withLayout(NativePanelLayout next){if(pose.layout.equals(next))return this;var result=new Geometry(pose.withLayout(next),boxes,fluidBoxes,bounds,selection);result.plainRegions=plainRegions;result.portholeRegions=portholeRegions;return result;}
        public int side(double x,double y,double z){double normal=pose.facing==Direction.EAST?1-x:pose.facing==Direction.SOUTH?1-z:pose.facing==Direction.WEST?x:z;
            double coordinate=normal,mid=DiagonalWallGeometry.near(pose.mode,pose.inverted,y,pose.lowerEnd,pose.upperEnd)+.125;
            if(pose.mode==2){coordinate=y;mid=DiagonalWallGeometry.band(pose.mode,pose.inverted)+DiagonalWallGeometry.near(pose.mode,pose.inverted,normal)+.125;}
            return coordinate<mid?0:1;
        }
        public Direction[] placementFaces(){return pose.mode==2?new Direction[]{Direction.DOWN,Direction.UP}:NativeWallGeometry.super.placementFaces();}
        public NativeFluidRegions fluidRegions(boolean porthole){NativeFluidRegions value=porthole?portholeRegions:plainRegions;if(value==null)synchronized(this){value=porthole?portholeRegions:plainRegions;if(value==null){value=NativeFluidRegions.diagonal(pose.facing,pose.inverted,pose.mode,pose.fill,pose.lowerEnd,pose.upperEnd,porthole);if(porthole)portholeRegions=value;else plainRegions=value;}}return value;}
        @Override public boolean equals(Object other){return other instanceof Geometry g&&pose.equals(g.pose);}
        @Override public int hashCode(){return pose.hashCode();}
    }
    private static final AABB UNIT=new AABB(0,0,0,1,1,1);
    public static Geometry build(Pose pose){
        List<AABB> boxes=new ArrayList<>();var corner=pose.join==null?null:pose.join.corner();boolean half=pose.mode==2;double base=half&&pose.inverted?.5:0;
        for(int slice=0;slice<16;slice++){
            double a=16*DiagonalWallGeometry.near(pose.mode,pose.inverted,slice/16D,pose.lowerEnd,pose.upperEnd),b=16*DiagonalWallGeometry.near(pose.mode,pose.inverted,(slice+1)/16D,pose.lowerEnd,pose.upperEnd);
            double near0=Math.min(a,b),near1=Math.max(a,b),left=corner==null?0:Math.min(corner.left(near0),corner.left(near1)),right=corner==null?16:Math.max(corner.right(near0),corner.right(near1));
            boxes.add(diagonalBox(new AABB(left/16,slice/16D,near0/16,right/16,(slice+1)/16D,(near1+4)/16),pose));
            if(half){
                if((pose.fill&1)!=0)boxes.add(rotate(new AABB(0,0,slice/16D,1,Math.max(0,base+(near1+4)/16),(slice+1)/16D),pose.facing));
                if((pose.fill&2)!=0)boxes.add(rotate(new AABB(0,Math.min(1,base+near0/16),slice/16D,1,1,(slice+1)/16D),pose.facing));
            }else{
                if((pose.fill&1)!=0)boxes.add(rotate(new AABB(left/16,slice/16D,0,right/16,(slice+1)/16D,Math.max(0,near1/16)),pose.facing));
                if((pose.fill&2)!=0)boxes.add(rotate(new AABB(left/16,slice/16D,Math.min(1,(near0+4)/16),right/16,(slice+1)/16D,1),pose.facing));
            }
            if(corner!=null){if(corner.frontRight!=null)arm(boxes,pose,corner,corner.frontRight,true,slice,near0,near1);if(corner.backRight!=null)arm(boxes,pose,corner,corner.backRight,false,slice,near0,near1);}
        }
        boxes=clip(boxes,pose);AABB bounds=boxes.isEmpty()?UNIT:boxes.getFirst();for(AABB box:boxes)bounds=bounds.minmax(box);
        List<AABB> fluid=new ArrayList<>();for(AABB box:boxes)if(box.intersects(UNIT)){AABB cut=box.intersect(UNIT);if(cut.getXsize()>0&&cut.getYsize()>0&&cut.getZsize()>0)fluid.add(cut);}
        double low=-.125+base,high=((pose.mode==1?16:8)+2)/16D+base;
        if(pose.fill!=0||corner!=null){low=Math.min(0,low);high=Math.max(1,high);}
        AABB selection=half?new AABB(0,low,0,1,high,1):rotate(new AABB(corner==null?0:-.125,0,low,corner==null?1:1.125,1,high),pose.facing);
        return new Geometry(pose,boxes,fluid,bounds,selection);
    }
    private static void arm(List<AABB> boxes,Pose pose,PanelCornerGeometry.Corner corner,boolean right,boolean front,int slice,double near0,double near1){
        if(front?near1<=0:near0+4>=16)return;double x0=Math.min(corner.armLeft(near0,right,front),corner.armLeft(near1,right,front)),x1=Math.max(corner.armLeft(near0,right,front),corner.armLeft(near1,right,front))+4;
        boxes.add(diagonalBox(new AABB(x0/16,slice/16D,(front?0:near0)/16,x1/16,(slice+1)/16D,(front?near1+4:16)/16),pose));
    }
    private static AABB diagonalBox(AABB box,Pose pose){if(pose.mode==2){double base=pose.inverted?.5:0;box=new AABB(box.minX,base+box.minZ,box.minY,box.maxX,base+box.maxZ,box.maxY);}return rotate(box,pose.facing);}
    private static AABB rotate(AABB box,Direction face){return NativeFlatWallGeometry.rotate(box,face);}
    private static AABB clipBox(Clip clip,int offset,Direction facing){return rotate(new AABB(clip.at(offset),clip.at(offset+1),clip.at(offset+2),clip.at(offset+3),clip.at(offset+4),clip.at(offset+5)),facing);}
    private static List<AABB> clip(List<AABB> boxes,Pose pose){List<AABB> pieces=new ArrayList<>();AABB clearance=clipBox(pose.clip,0,pose.facing);for(AABB b:boxes)if(b.intersects(clearance))pieces.add(b.intersect(clearance));
        for(int offset=6;offset<pose.clip.size();offset+=6){AABB obstacle=clipBox(pose.clip,offset,pose.facing);List<AABB> next=new ArrayList<>();for(AABB b:pieces){if(!b.intersects(obstacle)){next.add(b);continue;}AABB c=b.intersect(obstacle);
            add(next,b.minX,b.minY,b.minZ,c.minX,b.maxY,b.maxZ);add(next,c.maxX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);add(next,c.minX,b.minY,b.minZ,c.maxX,c.minY,b.maxZ);add(next,c.minX,c.maxY,b.minZ,c.maxX,b.maxY,b.maxZ);add(next,c.minX,c.minY,b.minZ,c.maxX,c.maxY,c.minZ);add(next,c.minX,c.minY,c.maxZ,c.maxX,c.maxY,b.maxZ);
        }pieces=next;}return pieces;
    }
    private static void add(List<AABB> out,double x0,double y0,double z0,double x1,double y1,double z1){if(x1>x0&&y1>y0&&z1>z0)out.add(new AABB(x0,y0,z0,x1,y1,z1));}
    private NativeDiagonalWallGeometry(){}
}
