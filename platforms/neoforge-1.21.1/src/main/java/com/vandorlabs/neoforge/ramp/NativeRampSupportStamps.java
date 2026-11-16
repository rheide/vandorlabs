package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;

/** Save-time actor support: actual installed collision and independent recipe agreement.
 * No world or entity crosses an I/O boundary and no actor resource fields are rewritten. */
public final class NativeRampSupportStamps {
 static NativeRampSupportStamp capture(Entity actor,NativeRampJournal.Operation expected){
  if(!supportedRole(actor)||!(actor.level() instanceof ServerLevel level)||!level.getServer().isSameThread()||!NativeRampRiders.available(actor)||actor.getDeltaMovement().y>.1)return null;var body=actor.getBoundingBox();if(!NativeRampJournal.mightSupport(level,body))return null;
  int x0=Mth.floor(body.minX),x1=Mth.floor(body.maxX-1e-7),z0=Mth.floor(body.minZ),z1=Mth.floor(body.maxZ-1e-7),y1=Mth.floor(body.minY);long cells=2L*(x1-x0+1)*(z1-z0+1);if(cells<1||cells>32)return null;
  NativeRampSupportStamp selected=null;
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)for(int y=y1-1;y<=y1;y++){
   var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)return null;var pos=new BlockPos(x,y,z);var raw=chunk.getBlockEntities().get(pos);if(!(raw instanceof NativeRampPartEntity part)||!part.ownershipKnown()||part.publication()==null)continue;var value=part.publication();boolean supported=false;
   for(var box:value.worldBoxes())if(box.maxX>body.minX+1e-7&&box.minX<body.maxX-1e-7&&box.maxZ>body.minZ+1e-7&&box.minZ<body.maxZ-1e-7&&Math.abs(body.minY-box.maxY)<1e-6){supported=true;break;}if(!supported)continue;
   var operation=NativeRampJournal.atRoot(level,part.controller());if(operation==null||expected!=null&&expected!=operation||!part.belongs(operation.id(),operation.root())||!NativeRampJournal.supportsStamp(level,operation,actor.getUUID()))return null;
   if(NativeRampJournal.returningActors(operation)&&!operation.requiresActorAlignment())return null;
   var identity=NativeRampJournal.rootIdentity(level,operation);double distance=value.beltGeometry()==null?value.data().belt().observed():value.beltGeometry().distance();var stamp=new NativeRampSupportStamp(actor.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()).toString(),level.dimension().location().toString(),operation.id(),identity.owner,operation.root(),NativeRampJournal.recipeIdentity(operation),value.revision(),value.pose(),distance,actor.position(),body);
   if(!NativeRampSupportGeometry.supported(operation.assembly(),stamp.pose,stamp.distance,stamp.body))throw new IllegalStateException("Installed Ramp support differs from its durable recipe");
   if(selected!=null&&(!selected.operation.equals(stamp.operation)||selected.revision!=stamp.revision||Double.compare(selected.pose,stamp.pose)!=0||Double.compare(selected.distance,stamp.distance)!=0))return null;selected=stamp;
  }return selected;
 }
 static void requireInitial(Entity actor,NativeRampJournal.Operation operation){var stamp=capture(actor,operation);if(stamp==null)throw new IllegalStateException("Initial Ramp actor has no exact saved support witness");actor.getPersistentData().put(NativeRampSupportStamp.KEY,stamp.write());}
 /** Hooked after vanilla finishes serializing the same actor. */
 public static void saving(Entity actor,CompoundTag tag){
  if(!supportedRole(actor)||!(actor.level() instanceof ServerLevel level))return;var data=tag.getCompound("NeoForgeData");boolean old=data.contains(NativeRampSupportStamp.KEY);if(!level.getServer().isSameThread()){if(old)throw new IllegalStateException("Saved Ramp support cannot be refreshed by a worker");return;}if(!old&&!NativeRampJournal.mightSupport(level,actor.getBoundingBox()))return;
  NativeRampSupportStamp previous=null;if(old){if(!data.contains(NativeRampSupportStamp.KEY,Tag.TAG_COMPOUND))return;try{previous=NativeRampSupportStamp.read(data.getCompound(NativeRampSupportStamp.KEY));}catch(IllegalArgumentException unknown){return;}}
  var current=capture(actor,null);if(current!=null){var encoded=current.write();actor.getPersistentData().put(NativeRampSupportStamp.KEY,encoded.copy());var copy=data.copy();copy.put(NativeRampSupportStamp.KEY,encoded);tag.put("NeoForgeData",copy);}
  else if(previous!=null){actor.getPersistentData().remove(NativeRampSupportStamp.KEY);var copy=data.copy();copy.remove(NativeRampSupportStamp.KEY);if(copy.isEmpty())tag.remove("NeoForgeData");else tag.put("NeoForgeData",copy);}
 }
 static boolean supportedRole(Entity actor){return actor instanceof ServerPlayer||actor instanceof net.minecraft.world.entity.item.ItemEntity&&!actor.isPassenger()&&!actor.isVehicle()||actor instanceof net.minecraft.world.entity.animal.Pig pig&&!pig.isBaby()&&!pig.isSaddled()&&!pig.isPassenger()&&!pig.isVehicle()&&pig.getScale()==1;}
 private NativeRampSupportStamps(){}
}
