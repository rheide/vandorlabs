package com.vandorlabs.segmented;

import net.minecraft.util.math.*;
import net.minecraft.world.World;
import com.vandorlabs.physics.RigidTransform;

/** A bounded loaded-tile search reaches panels whose stationary cell is now empty. */
public final class SegmentedDoorInteractions {
    public static final com.vandorlabs.world.LoadedTileIndex<SegmentedDoorTile> INDEX=new com.vandorlabs.world.LoadedTileIndex<>(SegmentedDoorTile.class);
    private static final ThreadLocal<int[]> GATE_MASKS=ThreadLocal.withInitial(()->new int[32]);
    private static final ThreadLocal<SegmentedGateShape.Hit> GATE_HIT=ThreadLocal.withInitial(SegmentedGateShape.Hit::new);
    private static final AxisAlignedBB PANEL=new AxisAlignedBB(0,-.5,-SegmentedDoorGeometry.HALF_THICKNESS,1,.5,SegmentedDoorGeometry.HALF_THICKNESS);
    public static RigidTransform transform(SegmentedDoorTile tile){return transform(tile,com.vandorlabs.VandorLabs.proxy.segmentedSelectionPose(tile.root()));}
    public static RigidTransform transform(SegmentedDoorTile tile,double progress){
        SegmentedDoorTile root=tile.root();int rotation=((int)(180-tile.facing().getHorizontalAngle())/90)&3;
        return SegmentedHitGeometry.transform(root.getPos().getX(),root.getPos().getY(),root.getPos().getZ(),rotation,
                tile.topOffset,tile.height,tile.column,tile.columns,progress,root.settings.direction,root.settings.straight,root.settings.depthOffset());
    }
    public static AxisAlignedBB bounds(SegmentedDoorTile tile){double[] b=new double[6];SegmentedHitGeometry.bounds(transform(tile),b);return new AxisAlignedBB(b[0],b[1],b[2],b[3],b[4],b[5]);}
    public static RayTraceResult hit(SegmentedDoorTile tile,Vec3d start,Vec3d end){return hit(tile,start,end,com.vandorlabs.VandorLabs.proxy.segmentedSelectionPose(tile.root()));}
    public static RayTraceResult hit(SegmentedDoorTile tile,Vec3d start,Vec3d end,double progress){
        RigidTransform t=transform(tile,progress);double[] a=t.inverse(start.x,start.y,start.z),b=t.inverse(end.x,end.y,end.z);
        RayTraceResult local=PANEL.calculateIntercept(new Vec3d(a[0],a[1],a[2]),new Vec3d(b[0],b[1],b[2])),hit=null;
        int gate=SegmentedArtwork.gate(tile.effectiveTexture());
        if(gate>=0){int[] masks=GATE_MASKS.get();com.vandorlabs.VandorLabs.proxy.segmentedGateMasks(tile.root(),progress,masks);int row=tile.topOffset,mask=masks[row];
            com.vandorlabs.render.LegacyBoxTrace.Hit exact=SegmentedHitGeometry.traceGate(t,SegmentedGateModels.model(gate,mask),SegmentedGateModels.connectors(gate,mask,row+1<tile.height?masks[row+1]:0,false),SegmentedGateModels.connectors(gate,mask,row>0?masks[row-1]:0,true),GATE_HIT.get(),start.x,start.y,start.z,end.x,end.y,end.z);
            local=null;if(exact!=null)hit=new RayTraceResult(new Vec3d(exact.x,exact.y,exact.z),net.minecraft.util.EnumFacing.getFront(exact.face),tile.getPos());
        }
        if(local!=null){double[] p=t.point(local.hitVec.x,local.hitVec.y,local.hitVec.z),normal=t.vector(local.sideHit.getFrontOffsetX(),local.sideHit.getFrontOffsetY(),local.sideHit.getFrontOffsetZ());hit=new RayTraceResult(new Vec3d(p[0],p[1],p[2]),net.minecraft.util.EnumFacing.getFacingFromVector((float)normal[0],(float)normal[1],(float)normal[2]),tile.getPos());}
        AxisAlignedBB header=SegmentedDoorBlock.headerBounds(tile);RayTraceResult fixed=header==null?null:header.calculateIntercept(start,end);
        return fixed!=null&&(hit==null||start.squareDistanceTo(fixed.hitVec)<start.squareDistanceTo(hit.hitVec))?new RayTraceResult(fixed.hitVec,fixed.sideHit,tile.getPos()):hit;
    }
    private static boolean nearRay(SegmentedDoorTile tile,AxisAlignedBB ray){
        if(!tile.isRoot()||!tile.ready()||!tile.getRenderBoundingBox().intersects(ray))return false;
        if(com.vandorlabs.VandorLabs.proxy.segmentedSelectionPose(tile)<=0)return false;
        // A sideways column is one rigid vertical strip. Reject it before
        // tracing every row, including wide doors whose swept bounds overlap.
        if(tile.settings.direction>=2){AxisAlignedBB top=bounds(tile);return top.union(top.offset(0,1-tile.height,0)).intersects(ray);}
        return true;
    }
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end){
        AxisAlignedBB ray=new AxisAlignedBB(Math.min(start.x,end.x),Math.min(start.y,end.y),Math.min(start.z,end.z),Math.max(start.x,end.x),Math.max(start.y,end.y),Math.max(start.z,end.z)).grow(.001);RayTraceResult nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(SegmentedDoorTile root:INDEX.query(world,ray.grow(34),t->nearRay(t,ray)))for(int row=0;row<root.height;row++){SegmentedDoorTile tile=SegmentedDoorTile.at(world,root.getPos().down(row));if(tile==null)continue;RayTraceResult hit=hit(tile,start,end);if(hit!=null&&start.squareDistanceTo(hit.hitVec)<distance){nearest=hit;distance=start.squareDistanceTo(hit.hitVec);}}
        return nearest;
    }
    private SegmentedDoorInteractions(){}
}
