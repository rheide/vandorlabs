package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.motion.PlatformSupportPolicy;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Source support/carrying primitives. Controller-specific selection and motion remain separate. */
public final class NativeRampRiders {
    private record Carrying(Level level,BlockPos root){}
    private static final ThreadLocal<Carrying> CARRYING=new ThreadLocal<>();
    public static boolean available(Entity entity){return entity!=null&&PlatformSupportPolicy.available(entity.isRemoved(),entity.noPhysics,entity.isPassenger(),entity instanceof Player player&&player.getAbilities().flying);}
    public static boolean eligible(Entity entity){return entity!=null&&PlatformSupportPolicy.eligible(available(entity),entity.getDeltaMovement().y);}
    public static AABB supportCollision(AABB box,Entity entity,boolean moving){
        if(!moving||!(entity instanceof Player||entity instanceof ItemEntity)||!eligible(entity))return box;
        double top=PlatformSupportPolicy.supportTop(box.minY,box.maxY,entity.getBoundingBox().minY);
        return Double.isNaN(top)?null:top==box.maxY?box:new AABB(box.minX,box.minY,box.minZ,box.maxX,top,box.maxZ);
    }
    public static boolean carrying(Level level,BlockPos root){var current=CARRYING.get();return current!=null&&current.level==level&&current.root.equals(root);}
    /** Materialize vanilla's lazy collision iterable while own-root suppression is still in scope. */
    public static List<AABB> obstacles(Level level,BlockPos root,Entity rider,AABB swept){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread())throw new IllegalStateException("Ramp rider obstacles require server thread");
        Objects.requireNonNull(root);Objects.requireNonNull(rider);if(rider.level()!=level)throw new IllegalArgumentException("Ramp rider level");
        if(!coordinate(swept.minX)||!coordinate(swept.minY)||!coordinate(swept.minZ)||!coordinate(swept.maxX)||!coordinate(swept.maxY)||!coordinate(swept.maxZ))throw new IllegalArgumentException("Ramp rider query bounds");
        int minX=Mth.floor(swept.minX-1.0000001)>>4,maxX=Mth.floor(swept.maxX+1.0000001)>>4,minZ=Mth.floor(swept.minZ-1.0000001)>>4,maxZ=Mth.floor(swept.maxZ+1.0000001)>>4;
        if((long)(maxX-minX+1)*(maxZ-minZ+1)>64)throw new IllegalArgumentException("Ramp rider query work budget");
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(server.getChunkSource().getChunkNow(x,z)==null)throw new IllegalArgumentException("Ramp rider clearance waits for loaded chunks");
        var previous=CARRYING.get();CARRYING.set(new Carrying(level,root.immutable()));
        try{var result=new ArrayList<AABB>();for(var shape:level.getCollisions(rider,swept))for(var box:shape.toAabbs()){if(result.size()>=131072)throw new IllegalArgumentException("Ramp rider obstacle budget");result.add(box);}return List.copyOf(result);}
        finally{if(previous==null)CARRYING.remove();else CARRYING.set(previous);}
    }
    /** Vanilla old AABB order is Y, then X, then Z; preserve exact strict overlap tests. */
    public static Vec3 clipped(AABB body,Vec3 delta,List<AABB> obstacles){
        double y=delta.y;for(var b:obstacles)y=PlatformSupportPolicy.axisOffset(b.minY,b.maxY,body.minY,body.maxY,y,b.maxX>body.minX&&b.minX<body.maxX&&b.maxZ>body.minZ&&b.minZ<body.maxZ);
        double lowY=body.minY+y,highY=body.maxY+y,x=delta.x;
        for(var b:obstacles)x=PlatformSupportPolicy.axisOffset(b.minX,b.maxX,body.minX,body.maxX,x,b.maxY>lowY&&b.minY<highY&&b.maxZ>body.minZ&&b.minZ<body.maxZ);
        double lowX=body.minX+x,highX=body.maxX+x,z=delta.z;
        for(var b:obstacles)z=PlatformSupportPolicy.axisOffset(b.minZ,b.maxZ,body.minZ,body.maxZ,z,b.maxX>lowX&&b.minX<highX&&b.maxY>lowY&&b.minY<highY);
        return new Vec3(x,y,z);
    }
    public static boolean clear(Entity entity,Vec3 delta,List<AABB> obstacles){var clipped=clipped(entity.getBoundingBox(),delta,obstacles);return Math.abs(clipped.x-delta.x)<1e-6&&Math.abs(clipped.y-delta.y)<1e-6&&Math.abs(clipped.z-delta.z)<1e-6;}
    private static boolean coordinate(double value){return Double.isFinite(value)&&Math.abs(value)<=30000001;}
    private NativeRampRiders(){}
}
