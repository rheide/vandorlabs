package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;

/** Rebase a newly loaded real player before vanilla publishes its login position. Vanilla
 * loads its inventory once; this adapter changes only physical position and its private stamp. */
public final class NativeRampPlayerAdoption {
 public static Optional<CompoundTag> loaded(ServerPlayer player,Optional<CompoundTag> input){
  if(input.isEmpty()||!input.get().getCompound("NeoForgeData").contains(NativeRampSupportStamp.KEY))return input;
  var server=player.getServer();if(server==null||!server.isSameThread())throw new IllegalStateException("Ramp player adoption requires the login server thread");if(server.getPlayerList().getPlayers().contains(player))throw new IllegalStateException("Ramp player adoption is restricted to pre-publication login");var original=input.get();var data=original.getCompound("NeoForgeData");if(!data.contains(NativeRampSupportStamp.KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Unknown saved Ramp support envelope");var stamp=NativeRampSupportStamp.read(data.getCompound(NativeRampSupportStamp.KEY));
  NativeRampPlayerRecord.identity(original,player.getGameProfile().getId(),stamp.dimension);if(!stamp.actor.equals(player.getGameProfile().getId())||!stamp.type.equals("minecraft:player"))throw new IllegalArgumentException("Saved Ramp player identity or coordinate changed");
  var dimension=ResourceLocation.tryParse(stamp.dimension);if(dimension==null)throw new IllegalArgumentException("Unknown saved Ramp dimension");var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));if(level==null)throw new IllegalArgumentException("Saved Ramp dimension is unavailable");var operation=NativeRampJournal.supportedOperation(level,stamp.operation);
  if(operation==null){var copy=original.copy();var metadata=data.copy();metadata.remove(NativeRampSupportStamp.KEY);if(metadata.isEmpty())copy.remove("NeoForgeData");else copy.put("NeoForgeData",metadata);player.getPersistentData().remove(NativeRampSupportStamp.KEY);return Optional.of(copy);}
  if(!stamp.position.equals(player.position()))throw new IllegalArgumentException("Saved active Ramp player coordinate changed");
  if(!original.contains("playerGameType",Tag.TAG_INT)||original.getInt("playerGameType")<0||original.getInt("playerGameType")>2||!original.contains("abilities",Tag.TAG_COMPOUND)||!original.getCompound("abilities").contains("flying",Tag.TAG_BYTE)||original.getCompound("abilities").getByte("flying")!=0||original.contains("RootVehicle")||original.contains("SleepingX")||original.contains("SleepingY")||original.contains("SleepingZ")||!NativeRampActorRecord.unscaled(original)||player.isSleeping()||player.isPassenger()||player.isFallFlying()||player.getScale()!=1)throw new IllegalArgumentException("Saved Ramp player physical role changed");
  var owner=NativeRampJournal.activeOwner(level,operation);if(owner==null||!operation.root().equals(stamp.root)||!NativeRampJournal.rootIdentity(level,operation).owner.equals(stamp.owner)||!Arrays.equals(NativeRampJournal.recipeIdentity(operation),stamp.recipe))throw new IllegalArgumentException("Saved Ramp player support owner/recipe changed");
  var standing=EntityType.PLAYER.getDimensions().makeBoundingBox(stamp.position.x,stamp.position.y,stamp.position.z);var crouching=net.minecraft.world.entity.EntityDimensions.scalable(.6F,1.5F).makeBoundingBox(stamp.position.x,stamp.position.y,stamp.position.z);if(!stamp.body.equals(standing)&&!stamp.body.equals(crouching))throw new IllegalArgumentException("Saved Ramp player body is not a supported native role");
  var controller=owner.controller();var delta=NativeRampSupportGeometry.rebase(operation.assembly(),stamp,controller.committedPose(),controller.committedBeltDistance());player.setServerLevel(level);var body=player.getBoundingBox();var swept=body.expandTowards(delta).inflate(1e-7);loaded(level,swept);var obstacles=NativeRampRiders.obstacles(level,operation.root(),player,swept);var destination=body.move(delta).deflate(1e-7);if(!NativeRampRiders.clear(player,delta,obstacles))throw new IllegalArgumentException("Saved Ramp player path is obstructed");for(var box:obstacles)if(box.intersects(destination))throw new IllegalArgumentException("Saved Ramp standing destination is obstructed");
  var current=player.position().add(delta);player.setPos(current.x,current.y,current.z);player.setOnGround(true);player.fallDistance=0;var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,0,velocity.z);NativeRampRiders.supported(player);var updated=NativeRampSupportStamps.capture(player,operation);if(updated==null)throw new IllegalStateException("Adopted player has no exact installed support witness");var encoded=updated.write();player.getPersistentData().put(NativeRampSupportStamp.KEY,encoded.copy());
  var result=original.copy();var pos=new ListTag();pos.add(DoubleTag.valueOf(current.x));pos.add(DoubleTag.valueOf(current.y));pos.add(DoubleTag.valueOf(current.z));result.put("Pos",pos);var metadata=data.copy();metadata.put(NativeRampSupportStamp.KEY,encoded);result.put("NeoForgeData",metadata);System.out.println("[vandorlabs][ramp-adoption] player support rebased pose="+stamp.pose+"->"+controller.committedPose()+" before="+stamp.position+" after="+current);return Optional.of(result);
 }
 private static void loaded(ServerLevel level,AABB box){int x0=Mth.floor(box.minX-1.0000001)>>4,x1=Mth.floor(box.maxX+1.0000001)>>4,z0=Mth.floor(box.minZ-1.0000001)>>4,z1=Mth.floor(box.maxZ+1.0000001)>>4;if((long)(x1-x0+1)*(z1-z0+1)>64)throw new IllegalArgumentException("Saved Ramp player clearance chunk budget");for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)level.getChunk(x,z);}
 private NativeRampPlayerAdoption(){}
}
