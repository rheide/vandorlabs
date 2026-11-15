package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Unknown saved escrow markers retain unusable ownership without crashing native entity admission. */
final class NativeGearEscrowChecks {
 static int run(ServerLevel world,ServerPlayer actor){int checks=0;var future=new CompoundTag();future.putInt("Schema",99);future.putUUID("Operation",new UUID(991,17));future.putLong("Root",new BlockPos(8,110,8).asLong());for(Tag marker:List.of(future,StringTag.valueOf("future-escrow"),new CompoundTag())){
  var item=new ItemEntity(world,8.5,115,12.5,new ItemStack(Items.GOLD_INGOT,3));item.getPersistentData().put(NativeGearDropEscrow.KEY,marker.copy());item.getPersistentData().putString("UnrelatedOpaque","retained");need(world.addFreshEntity(item),"actual unknown-marker EntityJoin admission");checks++;
  need(NativeGearDropEscrow.held(item),"unknown marker remains unusable");checks++;var tick=NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(item));need(tick.isCanceled(),"actual native pre-tick is canceled");checks++;
  var pickup=NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(actor,item));need(pickup.canPickup()==TriState.FALSE,"actual pickup event refuses unknown ownership");checks++;
  need(item.isNoGravity()&&item.isInvulnerable()&&item.getDeltaMovement().lengthSqr()==0,"inert escrow cannot age/fall/hurt");checks++;need(!item.hurt(world.damageSources().generic(),10),"unknown escrow ignores actual ordinary damage");checks++;var saved=new CompoundTag();need(item.save(saved),"native escrow saves");checks++;
  need(marker.equals(saved.getCompound("NeoForgeData").get(NativeGearDropEscrow.KEY))&&saved.getCompound("NeoForgeData").getString("UnrelatedOpaque").equals("retained"),"original marker and unrelated data survive save");checks++;item.discard();
 }System.out.println("[vandorlabs][gear-escrow] PASS checks="+checks+" actualJoin/tick/pickup/inertSave/futureAndWrongTypeRetained");return checks;}
 private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Native escrow retention: "+why);}
 private NativeGearEscrowChecks(){}
}
