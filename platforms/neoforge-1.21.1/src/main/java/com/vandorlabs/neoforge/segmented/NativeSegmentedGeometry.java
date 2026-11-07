package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.segmented.SegmentedDoorGeometry;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Local stationary collision/fluid housing; animated panel selection is resolved separately. */
public final class NativeSegmentedGeometry {
    private NativeSegmentedGeometry(){}
    public static final class Snapshot {
        private final com.vandorlabs.physics.RigidTransform transform;
        private final AABB bounds,header;
        private Snapshot(com.vandorlabs.physics.RigidTransform transform,AABB bounds,AABB header){this.transform=transform;this.bounds=bounds;this.header=header;}
        public AABB bounds(){return bounds;}public AABB header(){return header;}
        public double[] matrix(){return transform.columnMajor();}
        public com.vandorlabs.render.LegacyBoxTrace.Hit traceGate(int kind,int mask,int belowMask,int aboveMask,com.vandorlabs.segmented.SegmentedGateShape.Hit scratch,double sx,double sy,double sz,double ex,double ey,double ez){
            var body=com.vandorlabs.segmented.SegmentedHitGeometry.traceGate(transform,NativeSegmentedGateModels.model(kind,mask),NativeSegmentedGateModels.connectors(kind,mask,belowMask,false),NativeSegmentedGateModels.connectors(kind,mask,aboveMask,true),scratch,sx,sy,sz,ex,ey,ez);
            var fixed=header==null?null:com.vandorlabs.render.LegacyBoxTrace.clip(header.minX,header.minY,header.minZ,header.maxX,header.maxY,header.maxZ,sx,sy,sz,ex,ey,ez);
            return fixed!=null&&(body==null||fixed.distance(sx,sy,sz)<body.distance(sx,sy,sz))?fixed:body;
        }
        public com.vandorlabs.render.LegacyBoxTrace.Hit trace(double sx,double sy,double sz,double ex,double ey,double ez){
            var body=com.vandorlabs.segmented.SegmentedHitGeometry.trace(transform,sx,sy,sz,ex,ey,ez);
            var fixed=header==null?null:com.vandorlabs.render.LegacyBoxTrace.clip(header.minX,header.minY,header.minZ,header.maxX,header.maxY,header.maxZ,sx,sy,sz,ex,ey,ez);
            return fixed!=null&&(body==null||fixed.distance(sx,sy,sz)<body.distance(sx,sy,sz))?fixed:body;
        }
    }
    public static Snapshot snapshot(net.minecraft.core.BlockPos root,Direction facing,int position,int row,int height,int column,int width,double progress,int direction,boolean straight){
        int rotation=switch(facing){case NORTH->0;case EAST->3;case SOUTH->2;case WEST->1;default->throw new IllegalArgumentException("Horizontal facing required");};
        var t=com.vandorlabs.segmented.SegmentedHitGeometry.transform(root.getX(),root.getY(),root.getZ(),rotation,row,height,column,width,progress,direction,straight,depth(position));
        double[] b=new double[6];com.vandorlabs.segmented.SegmentedHitGeometry.bounds(t,b);
        var header=header(facing,direction,position,height,row,column,width);
        return new Snapshot(t,new AABB(b[0],b[1],b[2],b[3],b[4],b[5]),header==null?null:header.move(root.getX(),root.getY()-row,root.getZ()));
    }
    private static double depth(int position){return position==1?-13D/32:position==2?13D/32:0;}
    public static AABB closedPanel(Direction facing,int position){
        return rotated(new AABB(0,0,.5-SegmentedDoorGeometry.HALF_THICKNESS,1,1,
                .5+SegmentedDoorGeometry.HALF_THICKNESS).move(0,0,depth(position)),facing);
    }
    public static AABB header(Direction facing,int direction,int position,int height,int row,int column,int width){
        double depth=depth(position);AABB box=null;
        if(direction==0 && row==0)box=new AABB(0,.877,.375+depth,1,1.002,.625+depth);
        if(direction==1 && row==height-1)box=new AABB(0,-.002,.375+depth,1,.123,.625+depth);
        if(direction==2 && column==0)box=new AABB(-.002,0,.375+depth,.123,1,.625+depth);
        if(direction==3 && column==width-1)box=new AABB(.877,0,.375+depth,1.002,1,.625+depth);
        return box==null?null:rotated(box,facing);
    }
    private static AABB rotated(AABB box,Direction facing){
        return switch(facing){
            case SOUTH -> new AABB(1-box.maxX,box.minY,1-box.maxZ,1-box.minX,box.maxY,1-box.minZ);
            case WEST -> new AABB(box.minZ,box.minY,1-box.maxX,box.maxZ,box.maxY,1-box.minX);
            case EAST -> new AABB(1-box.maxZ,box.minY,box.minX,1-box.minZ,box.maxY,box.maxX);
            case NORTH -> box;
            default -> throw new IllegalArgumentException("Horizontal Segmented Door facing required");
        };
    }
}
