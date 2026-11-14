package com.vandorlabs.neoforge.gear;

import java.util.function.ToDoubleFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;

/** Actual linkage/foot geometry only; captured ordinary blocks retain their separate interaction identity. */
public final class NativeGearMechanismTrace {
    public record Hit(NativeGearEntity owner,net.minecraft.core.BlockPos source,Vec3 worldPoint,Direction worldFace,double distanceSq){public Hit(NativeGearEntity owner,Vec3 point,Direction face,double distance){this(owner,owner.getBlockPos(),point,face,distance);}}
    public static Hit trace(NativeGearEntity owner,Vec3 start,Vec3 end,double phase){Hit nearest=null;var boxes=owner.interactionBoxes(phase);var sources=owner.interactionSources();if(sources.size()!=boxes.size())throw new IllegalStateException("Arm interaction geometry/source correspondence");for(int i=0;i<boxes.size();i++){var hit=Shapes.create(boxes.get(i)).clip(start,end,net.minecraft.core.BlockPos.ZERO);if(hit!=null){double distance=start.distanceToSqr(hit.getLocation());if(nearest==null||distance<nearest.distanceSq)nearest=new Hit(owner,sources.get(i),hit.getLocation(),hit.getDirection(),distance);}}return nearest;}
    /** Continuous recent server movement includes the cosmetic interpolation between its received endpoints. */
    public static Hit recent(NativeGearEntity owner,Vec3 start,Vec3 end){var phases=owner.recentInteractionPhases();Hit nearest=null;for(int n=0;n<phases.length;n++){var hit=trace(owner,start,end,phases[n]);if(hit!=null&&(nearest==null||hit.distanceSq<nearest.distanceSq))nearest=hit;if(n==0)continue;var before=owner.interactionBoxes(phases[n-1]);var after=owner.interactionBoxes(phases[n]);if(before.size()!=after.size())continue;for(int i=0;i<before.size();i++){var swept=NativeGearGeometry.swept(owner,before.get(i),after.get(i),phases[n-1],phases[n]).inflate(.0002);var ray=Shapes.create(swept).clip(start,end,net.minecraft.core.BlockPos.ZERO);if(ray!=null){double distance=start.distanceToSqr(ray.getLocation());if(nearest==null||distance<nearest.distanceSq)nearest=new Hit(owner,owner.interactionSources().get(i),ray.getLocation(),ray.getDirection(),distance);}}}return nearest;}
    public static Hit nearest(Level world,Vec3 start,Vec3 end,ToDoubleFunction<NativeGearEntity> phase){Hit[] nearest={null};NativeGearTargets.visit(world,new AABB(start.x,start.y,start.z,end.x,end.y,end.z),owner->{var hit=trace(owner,start,end,phase.applyAsDouble(owner));if(hit!=null&&(nearest[0]==null||hit.distanceSq<nearest[0].distanceSq))nearest[0]=hit;});return nearest[0];}
    private NativeGearMechanismTrace(){}
}
