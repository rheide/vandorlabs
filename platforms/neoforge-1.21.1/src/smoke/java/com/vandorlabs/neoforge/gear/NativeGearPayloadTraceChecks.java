package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Actual captured cells on the existing six-mount/regular/Large payload path. */
final class NativeGearPayloadTraceChecks {
    private static final Set<UUID> CHECKED=new HashSet<>();private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Native payload ray: "+why);}
    static void verify(NativeGearEntity owner){
        if(!Boolean.getBoolean("vandorlabs.gearTraceChecks")||owner.payload()==null||owner.payload().materialized()||!CHECKED.add(owner.identity()))return;
        var payload=owner.payload();var cell=payload.cells().getFirst();var shape=payload.shapes(0).getFirst();var source=cell.pos.subtract(owner.getBlockPos());var origin=Vec3.atLowerCornerOf(owner.getBlockPos());
        for(double phase:new double[]{0,.173,.5,.817,1})for(Direction side:Direction.values()){
            var axis=side.getNormal();var center=shape.getCenter().add(source.getX(),source.getY(),source.getZ());double radius=side.getAxis()==Direction.Axis.X?shape.getXsize()/2:side.getAxis()==Direction.Axis.Y?shape.getYsize()/2:shape.getZsize()/2;var surface=center.add(axis.getX()*radius,axis.getY()*radius,axis.getZ()*radius);var frame=payload.transform(owner,phase);var localStart=surface.add(axis.getX()*2,axis.getY()*2,axis.getZ()*2);var localEnd=surface.add(-axis.getX()*.01,-axis.getY()*.01,-axis.getZ()*.01);var start=frame.point(localStart.x,localStart.y,localStart.z).add(origin);var end=frame.point(localEnd.x,localEnd.y,localEnd.z).add(origin);var expected=frame.point(surface.x,surface.y,surface.z).add(origin);var hit=NativeGearPayloadTrace.trace(owner,start,end,phase);
            need(hit!=null&&hit.owner()==owner&&hit.cell()==0,"registered immutable cell at "+owner.mount()+" phase="+phase+" face="+side);need(hit.point().distanceToSqr(expected)<1e-16,"exact transformed shell intersection");var normal=frame.vector(axis.getX(),axis.getY(),axis.getZ());need(hit.face()==Direction.getNearest(normal.x,normal.y,normal.z),"posed face normal");need(Math.abs(hit.distanceSquared()-4)<1e-8,"world-space hit distance");need(NativeGearPayloadTrace.trace(owner,start,start.subtract(end.subtract(start)),phase)==null,"opposite ray misses payload");
        }
        need(NativeGearPayloadTrace.trace(owner,origin,origin,Double.NaN)==null,"nonfinite render phase refused");
        System.out.println("[vandorlabs][gear-payload-trace] PASS owners="+CHECKED.size()+" checks="+checks+" actualCapture/allFaces/fivePhases/immutableView/exactSurface/normal/oppositeRay");
    }
    private NativeGearPayloadTraceChecks(){}
}
