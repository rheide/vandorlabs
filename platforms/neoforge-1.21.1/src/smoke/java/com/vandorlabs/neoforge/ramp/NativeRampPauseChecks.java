package com.vandorlabs.neoforge.ramp;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** A real scheduled pause must stop the public controls without moving terrain or an item owner. */
final class NativeRampPauseChecks {
 private final ServerLevel world;private final NativeRampEntity owner;private final NativeRampJournal.Operation operation;private final Map<BlockPos,NativeRampPartEntity> parts;
 private final double pose,distance;private final NativeRampController.Phase phase;private final ItemEntity item;private final Vec3 position;private final CompoundTag stack;
 NativeRampPauseChecks(ServerLevel world,NativeRampEntity owner){this.world=world;this.owner=owner;operation=NativeRampJournal.atRoot(world,owner.getBlockPos());if(operation==null||NativeRampJournal.supportedOperation(world,operation.id())!=operation)throw new AssertionError("Pause requires its durable ACTIVE owner");var controller=owner.controller();pose=controller.committedPose();distance=controller.committedBeltDistance();phase=controller.phase();var map=new LinkedHashMap<BlockPos,NativeRampPartEntity>();for(var pos:operation.assembly().potential())if(world.getBlockEntity(pos) instanceof NativeRampPartEntity part&&part.belongs(operation.id(),owner.getBlockPos()))map.put(pos,part);parts=Map.copyOf(map);var surface=parts.values().stream().flatMap(part->part.publication().worldBoxes().stream()).filter(box->box.getXsize()>.4&&box.getZsize()>.4).max(Comparator.comparingDouble(box->box.maxY)).orElseThrow();var payload=new CompoundTag();payload.putString("UnknownRecipe","pause:opaque");payload.putByteArray("Board",new byte[257]);var owned=new ItemStack(Items.DIAMOND,17);owned.set(DataComponents.CUSTOM_DATA,CustomData.of(payload));item=new ItemEntity(world,(surface.minX+surface.maxX)/2,surface.maxY,(surface.minZ+surface.maxZ)/2,owned);item.setNoGravity(true);item.setNeverPickUp();item.setUnlimitedLifetime();item.setDeltaMovement(Vec3.ZERO);item.setOnGround(true);if(!world.addFreshEntity(item))throw new AssertionError("Pause item admission");position=item.position();stack=com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(owned,world.registryAccess());}
 void paused(){var runtime=owner.controller();need(runtime.phase()==phase&&runtime.committedPose()==pose&&runtime.committedBeltDistance()==distance,"paused authoritative phase/pose/distance unchanged");need(NativeRampJournal.atRoot(world,owner.getBlockPos())==operation,"same durable original owner retained");var current=new HashSet<BlockPos>();for(var pos:operation.assembly().potential())if(world.getBlockEntity(pos) instanceof NativeRampPartEntity part&&part.belongs(operation.id(),owner.getBlockPos()))current.add(pos);need(current.equals(parts.keySet()),"pause neither adds nor removes reserved cells");for(var entry:parts.entrySet()){var part=entry.getValue();need(world.getBlockEntity(entry.getKey())==part&&part.belongs(operation.id(),owner.getBlockPos()),"pause retains exact physical part identity");var dto=part.getUpdateTag(world.registryAccess());var data=NativeRampPartData.read(dto.getCompound("NativeRampPartData"),entry.getKey(),world.registryAccess());need(!data.motion().moving()&&!data.belt().running(),"public/client projection is stopped while saving disabled");need(data.motion().pose(world.getGameTime()+200,0)==pose&&data.belt().observed()==distance&&data.belt().distance()==distance,"public projection cannot predict through paused time");}need(item.position().equals(position),"pause does not transport item owner");resources();}
 void resources(){need(!item.isRemoved()&&com.vandorlabs.neoforge.inventory.NativeInventoryItems.write(item.getItem(),world.registryAccess()).equals(stack),"exact17-diamond owner and private components retained");}
 void cleanup(){resources();item.discard();}
 private static void need(boolean value,String message){if(!value)throw new AssertionError("Ramp pause: "+message);}
}
