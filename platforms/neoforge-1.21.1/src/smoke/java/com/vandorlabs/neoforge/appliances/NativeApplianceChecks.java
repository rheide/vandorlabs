package com.vandorlabs.neoforge.appliances;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

/** Dedicated structural checks for the two appliance housings, separate from actual player acceptance. */
public final class NativeApplianceChecks {
 public static void run(ServerLevel world,ServerPlayer actor){int checks=0;BlockPos base=new BlockPos(80,120,80);world.getChunkAt(base);var previous=actor.position();actor.setPos(80.5,120,77.5);
  try{int offset=0;for(var entry:NativeApplianceBindings.BLOCKS.entrySet()){var block=entry.getValue();var pos=base.east(offset++*3);for(var face:Direction.Plane.HORIZONTAL){var state=block.defaultBlockState().setValue(NativeApplianceBlock.FACING,face);world.setBlock(pos,state,3);need(state.hasBlockEntity()&&world.getBlockEntity(pos) instanceof NativeApplianceEntity tile&&!tile.inert()&&tile.scanner()==entry.getKey().equals("scanner"),"healthy appliance ownership tile");need(state.getProperties().size()==1,"facing is sole saved property");checks+=2;need(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state),"appliance mineable tool tag");checks++;var box=state.getCollisionShape(world,pos).bounds();need(box.minX==0&&box.minY==0&&box.minZ==0&&box.maxX==1&&box.maxY==1&&box.maxZ==1,"full cube collision");checks++;for(var side:Direction.values()){need(state.isFaceSturdy(world,pos,side),"solid attachment face");checks++;}var tag=NbtUtils.writeBlockState(state);var decoded=com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(world.registryAccess(),tag,Map.of());need(state==decoded,"strict facing palette roundtrip");checks++;for(var rotation:Rotation.values()){need(state.rotate(rotation).getValue(NativeApplianceBlock.FACING)==rotation.rotate(face),"horizontal rotation");checks++;}for(var mirror:Mirror.values()){need(state.mirror(mirror).getValue(NativeApplianceBlock.FACING)==mirror.mirror(face),"horizontal mirror");checks++;}var drops=Block.getDrops(state,world,pos,null,actor,new ItemStack(Items.DIAMOND_PICKAXE));need(drops.size()==1&&drops.getFirst().is(block.asItem())&&drops.getFirst().getCount()==1,"one plain block drop");checks++;var dialogActor=new net.neoforged.neoforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(new java.util.UUID(0,211127),"ApplianceDialogCheck"));dialogActor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-2);need(com.vandorlabs.neoforge.CubeConfiguration.open(dialogActor,world,pos)&&new NativeApplianceMenu(1,dialogActor.getInventory(),(NativeApplianceEntity)world.getBlockEntity(pos)).getType()==NativeApplianceBindings.MENU,"ordinary configurizer opens appliance dialog");dialogActor.closeContainer();checks++;var invalid=tag.copy();invalid.getCompound("Properties").putString("facing","up");boolean rejected=false;try{com.vandorlabs.neoforge.transfer.NativeRawCell.decodePalette(world.registryAccess(),invalid,Map.of());}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"vertical facing refused");checks++;}world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
  for(var block:java.util.List.of(com.vandorlabs.neoforge.navigation.NativeNavigationTableBindings.FLAT,com.vandorlabs.neoforge.navigation.NativeNavigationTableBindings.ANGLED,com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.CORES.get("standalone_dish"),com.vandorlabs.neoforge.navigation.observer.NativeObserverBindings.CORES.get("standalone_antenna"))){need(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),"navigation machine mineable tool tag");checks++;}
  System.out.println("[vandorlabs][appliances] structural PASS checks="+checks+" PrinterScanner/all4Facings/fullCube/healthyBE/dedicatedDialog/plainDrops/rotation/mirror/strictPalette");
  }finally{actor.setPos(previous.x,previous.y,previous.z);}
 }
 private static void need(boolean value,String why){if(!value)throw new AssertionError("Appliance: "+why);}
 private NativeApplianceChecks(){}
}
