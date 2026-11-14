package com.vandorlabs.neoforge.gear;

import java.util.Objects;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;

/** Ray tests use the immutable public geometry; no inventory-bearing tile is constructed on a client. */
public final class NativeGearPayloadTrace {
    public record Hit(NativeGearEntity owner,int cell,Vec3 point,Direction face,double distanceSquared) {
        public Hit {Objects.requireNonNull(owner);Objects.requireNonNull(point);Objects.requireNonNull(face);}
    }
    public static Hit trace(NativeGearEntity owner,Vec3 start,Vec3 end,double phase){
        var view=owner.payloadView();if(view==null||view.materialized()||owner.inert()||!owner.controller()||!Double.isFinite(phase)||phase<0||phase>1)return null;
        var transform=NativeGearPayload.relativeTransform(owner,phase,view.reference());var inverse=transform.inverse();var origin=Vec3.atLowerCornerOf(owner.getBlockPos());var a=start.subtract(origin);var b=end.subtract(origin);var localStart=inverse.point(a.x,a.y,a.z);var localEnd=inverse.point(b.x,b.y,b.z);Hit best=null;int index=0;
        for(var cell:view.cells()){
            for(var box:cell.boxes()){
                var local=Shapes.create(box.move(cell.source())).clip(localStart,localEnd,BlockPos.ZERO);if(local==null)continue;
                var p=local.getLocation();var point=transform.point(p.x,p.y,p.z).add(origin);double distance=start.distanceToSqr(point);if(best!=null&&distance>=best.distanceSquared)continue;
                var normal=local.getDirection().getNormal();var rotated=transform.vector(normal.getX(),normal.getY(),normal.getZ());var face=Direction.getNearest(rotated.x,rotated.y,rotated.z);best=new Hit(owner,index,point,face,distance);
            }index++;
        }
        return best;
    }
    /** Loaded-only broad phase. Caller chooses the authoritative or pause-aware visual phase explicitly. */
    public static Hit nearest(Level world,Vec3 start,Vec3 end,java.util.function.ToDoubleFunction<NativeGearEntity> phase){
        Hit[] best={null};NativeGearTargets.visit(world,new AABB(start,end).inflate(.001),owner->{var hit=trace(owner,start,end,phase.applyAsDouble(owner));if(hit!=null&&(best[0]==null||hit.distanceSquared<best[0].distanceSquared))best[0]=hit;});return best[0];
    }
    private NativeGearPayloadTrace(){}
}
