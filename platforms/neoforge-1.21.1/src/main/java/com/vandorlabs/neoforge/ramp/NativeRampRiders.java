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
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeRampRiders {
    private record Carrying(Level level,BlockPos root){}
    /** Mutable only on the owning world thread; values never retain an entity or world. */
    private static final class Support {
        java.lang.ref.WeakReference<Level> level;long tick;double x,z,height;
        void update(Entity entity){if(level==null||level.get()!=entity.level())level=new java.lang.ref.WeakReference<>(entity.level());tick=entity.level().getGameTime();x=entity.getX();z=entity.getZ();height=entity.getBoundingBox().minY;}
    }
    private static final NativeEntityMemo<Support> SERVER_SUPPORT=new NativeEntityMemo<>(8192),CLIENT_SUPPORT=new NativeEntityMemo<>(8192);
    private static final class Sequence {long value;}
    private static final NativeEntityMemo<Sequence> SENT=new NativeEntityMemo<>(8192);
    private static java.util.function.BooleanSupplier clientThread=()->false;
    public static void bindClientThread(java.util.function.BooleanSupplier check){clientThread=Objects.requireNonNull(check);}
    public static void requireThread(Level level){if(level instanceof ServerLevel server){if(!server.getServer().isSameThread())throw new IllegalStateException("Ramp support requires server thread");}else if(!level.isClientSide||!clientThread.getAsBoolean())throw new IllegalStateException("Ramp support requires client thread");}
    private static NativeEntityMemo<Support> support(Entity entity){return entity.level().isClientSide?CLIENT_SUPPORT:SERVER_SUPPORT;}
    public static void supported(Entity entity){requireThread(entity.level());var memo=support(entity);var recent=memo.get(entity);if(recent==null){recent=new Support();recent.update(entity);memo.put(entity,recent);}else recent.update(entity);}
    public static AABB recentCollision(AABB box,Entity entity){
        if(!available(entity))return box;requireThread(entity.level());var recent=support(entity).get(entity);if(recent==null||recent.level.get()!=entity.level()||!PlatformSupportPolicy.recent(true,entity.level().getGameTime()-recent.tick,entity.getX(),entity.getZ(),recent.x,recent.z)||!PlatformSupportPolicy.provenFloor(box.minX,box.maxX,box.minZ,box.maxZ,box.maxY,recent.x,recent.z,recent.height))return box;
        double feet=entity.getBoundingBox().minY;if(!(box.maxY>feet&&box.maxY-feet<=.5))return box;double top=PlatformSupportPolicy.supportTop(box.minY,box.maxY,feet);return Double.isNaN(top)?null:new AABB(box.minX,box.minY,box.minZ,box.maxX,top,box.maxZ);
    }
    /** No wrapper/allocation for ordinary queries without a live recent support record. */
    public static Iterable<net.minecraft.world.phys.shapes.VoxelShape> recentCollisions(Level level,Entity entity,Iterable<net.minecraft.world.phys.shapes.VoxelShape> shapes){
        if(!available(entity)||entity.level()!=level)return shapes;
        if(level instanceof ServerLevel server?!server.getServer().isSameThread():!clientThread.getAsBoolean())return shapes;
        var recent=support(entity).get(entity);
        if(recent==null||recent.level.get()!=level||!PlatformSupportPolicy.recent(true,level.getGameTime()-recent.tick,entity.getX(),entity.getZ(),recent.x,recent.z))return shapes;
        return ()->new Iterator<>(){
            private final Iterator<net.minecraft.world.phys.shapes.VoxelShape> source=shapes.iterator();
            public boolean hasNext(){return source.hasNext();}
            public net.minecraft.world.phys.shapes.VoxelShape next(){return recentShape(source.next(),entity);}
        };
    }
    public static List<net.minecraft.world.phys.shapes.VoxelShape> recentEntityCollisions(Level level,Entity entity,List<net.minecraft.world.phys.shapes.VoxelShape> shapes){
        var corrected=recentCollisions(level,entity,shapes);if(corrected==shapes)return shapes;
        ArrayList<net.minecraft.world.phys.shapes.VoxelShape> result=null;int index=0;
        for(var shape:corrected){if(result==null&&shape!=shapes.get(index)){result=new ArrayList<>(shapes.size());result.addAll(shapes.subList(0,index));}if(result!=null)result.add(shape);index++;}
        return result==null?shapes:List.copyOf(result);
    }
    private static net.minecraft.world.phys.shapes.VoxelShape recentShape(net.minecraft.world.phys.shapes.VoxelShape original,Entity entity){
        // Complex foreign shapes retain their solid collision rather than expanding an unbounded grid.
        long coordinates=1;for(var axis:net.minecraft.core.Direction.Axis.values()){coordinates*=original.getCoords(axis).size();if(coordinates>4096)return original;}
        var boxes=original.toAabbs();if(boxes.size()>64)return original;ArrayList<AABB> clipped=null;
        for(int i=0;i<boxes.size();i++){var box=boxes.get(i);var next=recentCollision(box,entity);if(next!=box&&clipped==null){clipped=new ArrayList<>(boxes.size());for(int j=0;j<i;j++)clipped.add(boxes.get(j));}if(clipped!=null&&next!=null)clipped.add(next);}
        return clipped==null?original:com.vandorlabs.neoforge.NativeVoxelShapes.union(clipped,1024*1024,0);
    }
    record SupportState(java.lang.ref.WeakReference<Level> level,long tick,double x,double z,double height){}
    static SupportState captureSupport(Entity entity){requireThread(entity.level());var value=support(entity).get(entity);return value==null?null:new SupportState(value.level,value.tick,value.x,value.z,value.height);}
    static void restoreSupport(Entity entity,SupportState previous){requireThread(entity.level());var memo=support(entity);if(previous==null){memo.remove(entity);return;}var value=memo.get(entity);if(value==null){value=new Support();memo.put(entity,value);}value.level=previous.level;value.tick=previous.tick;value.x=previous.x;value.z=previous.z;value.height=previous.height;}
    @FunctionalInterface public interface Sync {void send(net.minecraft.server.level.ServerPlayer player,NativeRampMotionPayload payload);}
    public record Transport(int entity,Vec3 displacement,double supportDisplacement,NativeRampMotionPayload correction){}
    public static Transport move(Entity entity,double dx,double dy,double dz,Sync sync){return move(entity,dx,dy,dz,dy,sync);}
    public static Transport move(Entity entity,double dx,double dy,double dz,double supportDy,Sync sync){
        if(!(entity.level() instanceof ServerLevel server)||!server.getServer().isSameThread())throw new IllegalStateException("Ramp transport requires server thread");
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz)||!Double.isFinite(supportDy))throw new IllegalArgumentException("Ramp transport delta");
        NativeRampMotionPayload packet=null;if(entity instanceof net.minecraft.server.level.ServerPlayer player){Objects.requireNonNull(sync);var sequence=SENT.get(player);if(sequence==null){sequence=new Sequence();SENT.put(player,sequence);}double feet=player.getBoundingBox().minY;packet=new NativeRampMotionPayload(server.dimension().location(),++sequence.value,player.getX(),player.getZ(),feet+dy-supportDy,feet+dy,dx,dz);}
        entity.setPos(entity.getX()+dx,entity.getY()+dy,entity.getZ()+dz);entity.setOnGround(true);entity.fallDistance=0;var velocity=entity.getDeltaMovement();entity.setDeltaMovement(velocity.x,0,velocity.z);supported(entity);
        if(packet!=null)sync.send((net.minecraft.server.level.ServerPlayer)entity,packet);return new Transport(entity.getId(),new Vec3(dx,dy,dz),supportDy,packet);
    }
    static void beltItemState(Entity entity){if(entity instanceof ItemEntity item){item.noPhysics=false;item.setDeltaMovement(Vec3.ZERO);item.hasImpulse=true;}}
    static void beltItemTracking(Entity entity){if(entity instanceof ItemEntity item)((ServerLevel)item.level()).getChunkSource().broadcastAndSend(item,new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(item));}
    public static Transport moveBelt(Entity entity,double dx,double dy,double dz,Sync sync){var result=move(entity,dx,dy,dz,sync);beltItemState(entity);beltItemTracking(entity);return result;}
    @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof Level level){SERVER_SUPPORT.clearWorld(level);CLIENT_SUPPORT.clearWorld(level);SENT.clearWorld(level);NativeRampMotionPayload.unload(level);}}
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
        try{var result=new ArrayList<AABB>();for(var shape:level.getCollisions(rider,swept))for(var box:shape.toAabbs()){if(result.size()>=131072)throw new IllegalArgumentException("Ramp rider obstacle budget");var corrected=recentCollision(box,rider);if(corrected!=null)result.add(corrected);}return List.copyOf(result);}
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
