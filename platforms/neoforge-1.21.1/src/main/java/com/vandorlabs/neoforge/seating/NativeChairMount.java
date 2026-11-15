package com.vandorlabs.neoforge.seating;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Invisible vanilla riding host. The cushion is a modern passenger attachment marker. */
public final class NativeChairMount extends Entity {
    private static final EntityDataAccessor<BlockPos> CHAIR=SynchedEntityData.defineId(NativeChairMount.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> HEIGHT=SynchedEntityData.defineId(NativeChairMount.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> VALID=SynchedEntityData.defineId(NativeChairMount.class,EntityDataSerializers.BOOLEAN);
    private CompoundTag retained=new CompoundTag();private boolean inert;
    public NativeChairMount(EntityType<? extends NativeChairMount> type,Level level){super(type,level);noPhysics=true;setInvisible(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder data){data.define(CHAIR,BlockPos.ZERO);data.define(HEIGHT,0F);data.define(VALID,false);}
    public BlockPos chairPos(){return entityData.get(VALID)?entityData.get(CHAIR):null;}
    public void updatePose(NativeChairEntity owner){entityData.set(CHAIR,owner.getBlockPos());entityData.set(HEIGHT,(float)owner.cushion());entityData.set(VALID,true);setPos(owner.getBlockPos().getX()+.5,owner.getBlockPos().getY(),owner.getBlockPos().getZ()+.5);setYRot((float)owner.yaw());for(var passenger:getPassengers())positionRider(passenger);}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale){return new Vec3(0,entityData.get(HEIGHT),0);}
    @Override protected boolean canAddPassenger(Entity passenger){return !inert&&getPassengers().isEmpty();}
    @Override public boolean canBeCollidedWith(){return false;}@Override public boolean isPushable(){return false;}
    @Override public void tick(){super.tick();if(level().isClientSide)return;if(inert){ejectPassengers();return;}var at=chairPos();if(at==null||!level().hasChunkAt(at)){ejectPassengers();discard();return;}
        if(!(level().getBlockEntity(at) instanceof NativeChairEntity owner)||owner.inert()||owner.getBlockState().getValue(NativeSeatBlock.UPPER)){ejectPassengers();discard();return;}if(!((NativeSeatBlock)owner.getBlockState().getBlock()).complete(level(),at)){if(owner.kind()==NativeSeatKind.PILOT&&NativeSeatBlock.loaded(level(),at.above())&&level().getBlockState(at.above()).isAir()){NativeSeatUpdates.queue(owner);}else {ejectPassengers();discard();return;}}
        if(tickCount>5&&getPassengers().isEmpty()){discard();return;}if(Math.abs(entityData.get(HEIGHT)-owner.cushion())>1e-6||getYRot()!=owner.yaw())updatePose(owner);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){var at=chairPos();if(at!=null){var facing=net.minecraft.core.Direction.fromYRot(getYRot());for(var offset:DismountHelper.offsetsForDirection(facing)){var candidate=at.offset(offset[0],0,offset[1]);if(!level().hasChunkAt(candidate))continue;for(int dy:new int[]{0,1,-1}){var point=DismountHelper.findSafeDismountLocation(passenger.getType(),level(),candidate.offset(0,dy,0),true);if(point!=null&&DismountHelper.canDismountTo(level(),point,passenger,Pose.STANDING))return point;}}}return super.getDismountLocationForPassenger(passenger);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){retained=tag.copy();entityData.set(VALID,false);try{com.vandorlabs.neoforge.NativePlatformComponents.requireSupported(tag,level().registryAccess());}catch(IllegalArgumentException unsupported){inert=true;return;}boolean modern=tag.contains("NativeChairMountSchema");inert=!tag.contains("ChairPos",Tag.TAG_LONG)||modern&&(!tag.contains("NativeChairMountSchema",Tag.TAG_INT)||tag.getInt("NativeChairMountSchema")!=1)||tag.contains("SeatY")&&(!tag.contains("SeatY",Tag.TAG_FLOAT)||!Float.isFinite(tag.getFloat("SeatY"))||tag.getFloat("SeatY")<0||tag.getFloat("SeatY")>2)||modern&&!tag.contains("SeatY",Tag.TAG_FLOAT);
        if(!inert){for(String key:java.util.List.of("id","UUID","Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown","CustomName","CustomNameVisible","Silent","NoGravity","Glowing","Tags","Passengers","NeoForgeData","neoforge:attachments"))retained.remove(key);long saved=tag.getLong("ChairPos");entityData.set(CHAIR,modern?BlockPos.of(saved):new BlockPos(com.vandorlabs.persistence.LegacyBlockPosition.x(saved),com.vandorlabs.persistence.LegacyBlockPosition.y(saved),com.vandorlabs.persistence.LegacyBlockPosition.z(saved)));entityData.set(HEIGHT,tag.contains("SeatY",Tag.TAG_FLOAT)?tag.getFloat("SeatY"):0F);entityData.set(VALID,true);}}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.merge(retained);if(inert)return;tag.putInt("NativeChairMountSchema",1);var at=chairPos();if(at!=null)tag.putLong("ChairPos",at.asLong());tag.putFloat("SeatY",entityData.get(HEIGHT));}
}
