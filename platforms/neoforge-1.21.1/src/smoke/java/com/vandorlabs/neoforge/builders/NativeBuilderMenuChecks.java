package com.vandorlabs.neoforge.builders;
import com.vandorlabs.neoforge.rack.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
/** Menu-specific ownership/authentication checks; durable disk phases are tested by the formation fixture. */
public final class NativeBuilderMenuChecks{
 private static void need(boolean value,String why){if(!value)throw new AssertionError("Builder menu: "+why);}
 public static void run(ServerPlayer actor,NativeBuilderEntity tile){
  var previous=actor.containerMenu;var menu=new NativeBuilderMenu(NativeBuilderBindings.MENU,37,actor.getInventory(),tile);actor.containerMenu=menu;
  var originals=java.util.List.of(tile.stack(2),tile.stack(4),tile.stack(5),tile.stack(6));var original=tile.stack(2);var inventory=actor.getInventory().getItem(9).copy();int selection=tile.selectedDesign(),activation=tile.activation();var channels=tile.channels();
  try{
   need(menu.slots.size()==43&&menu.stillValid(actor),"seven owner slots and36 player slots");
   need(!menu.slots.get(1).mayPlace(new ItemStack(Items.STONE)),"output rejects insertion");
   var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,6500,false,true);tile.setItem(2,ItemStack.EMPTY);actor.getInventory().setItem(9,battery);
   need(!menu.quickMoveStack(actor,7).isEmpty()&&actor.getInventory().getItem(9).isEmpty()&&tile.batteryEnergy()==6500,"shift-click moves one charged battery owner");
   long sequence=tile.batterySequence(0);tile.receiveBatteryEnergy(100,false);need(tile.batterySequence(0)==sequence,"RF updates retain insertion sequence");tile.extractBatteryEnergy(100,false);
   for(int bay=1;bay<4;bay++){var extra=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(extra,10000,false,true);tile.setItem(NativeBuilderEntity.batterySlot(bay),extra);}
   need(tile.batteryCapacity()==40000&&tile.batteryEnergy()==36500&&menu.value(3)==36500&&menu.value(4)==40000,"four banks and unsigned aggregate network values");
   var saved=tile.saveWithoutMetadata(actor.registryAccess());var loaded=new NativeBuilderEntity(tile.getBlockPos(),tile.getBlockState());loaded.setLevel(actor.level());loaded.loadWithComponents(saved,actor.registryAccess());need(!loaded.inert()&&loaded.batteryEnergy()==36500&&loaded.getContainerSize()==7,"seven slot saved inventory");
   var legacy=saved.copy();legacy.remove("BatterySequences");var legacyRows=legacy.getCompound("Inventory").getList("Items",net.minecraft.nbt.Tag.TAG_COMPOUND);for(int i=legacyRows.size()-1;i>=0;i--)if(legacyRows.getCompound(i).getInt("Slot")>=4)legacyRows.remove(i);var previousOwner=new NativeBuilderEntity(tile.getBlockPos(),tile.getBlockState());previousOwner.setLevel(actor.level());previousOwner.loadWithComponents(legacy,actor.registryAccess());need(!previousOwner.inert()&&previousOwner.getContainerSize()==7&&previousOwner.batteryEnergy()==6500&&previousOwner.getItem(4).isEmpty()&&previousOwner.getItem(6).isEmpty(),"previous four-slot layout preserves old battery and appends empty bays");
   for(int badSlot:new int[]{4,5,6}){var broken=saved.copy();for(var row:broken.getCompound("Inventory").getList("Items",net.minecraft.nbt.Tag.TAG_COMPOUND))if(((net.minecraft.nbt.CompoundTag)row).getInt("Slot")==badSlot)((net.minecraft.nbt.CompoundTag)row).getCompound("Stack").putString("id","minecraft:stone");var failed=new NativeBuilderEntity(tile.getBlockPos(),tile.getBlockState());failed.setLevel(actor.level());failed.loadWithComponents(broken,actor.registryAccess());need(failed.inert()&&NativeBuilderData.sameSnapshots(broken,failed.saveWithoutMetadata(actor.registryAccess())),"bad appended bank retains inert original slot="+badSlot);}
   var immutableSnapshot=saved.copy();tile.setItem(4,ItemStack.EMPTY);need(saved.equals(immutableSnapshot),"later physical removal cannot mutate frozen sequence snapshot");
   for(int malformed=0;malformed<2;malformed++){var bad=saved.copy();bad.putLongArray("BatterySequences",malformed==0?new long[]{1,2,3}:new long[]{1,2,-1,4});var failed=new NativeBuilderEntity(tile.getBlockPos(),tile.getBlockState());failed.setLevel(actor.level());failed.loadWithComponents(bad,actor.registryAccess());need(failed.inert()&&NativeBuilderData.sameSnapshots(bad,failed.saveWithoutMetadata(actor.registryAccess())),"malformed sequence retains original"+malformed);}
   for(int bay=1;bay<4;bay++)tile.setItem(NativeBuilderEntity.batterySlot(bay),ItemStack.EMPTY);
   var before=tile.saveWithoutMetadata(actor.registryAccess());tile.pending(true);
   need(menu.stillValid(actor),"pending disk keeps read-only menu session");
   menu.clicked(2,0,ClickType.PICKUP,actor);need(menu.getCarried().isEmpty()&&tile.batteryEnergy()==6500,"pending click cannot remove owner");
   need(menu.quickMoveStack(actor,2).isEmpty()&&!NativeBuilderMenu.apply(actor,new NativeBuilderEdit(37,tile.getBlockPos(),0,2,ChannelList.EMPTY)),"pending transfer rejects mutations without closing session");tile.pending(false);
   need(before.equals(tile.saveWithoutMetadata(actor.registryAccess())),"pending menu retains exact saved item data");
   need(!NativeBuilderMenu.apply(actor,new NativeBuilderEdit(38,tile.getBlockPos(),0,2,ChannelList.EMPTY)),"wrong window");need(!NativeBuilderMenu.apply(actor,new NativeBuilderEdit(37,tile.getBlockPos().above(),0,2,ChannelList.EMPTY)),"wrong position");
   need(NativeBuilderMenu.apply(actor,new NativeBuilderEdit(37,tile.getBlockPos(),0,1,ChannelList.EMPTY))&&tile.selectedDesign()==1,"authorized design choice");
   actor.containerMenu=actor.inventoryMenu;need(!NativeBuilderMenu.apply(actor,new NativeBuilderEdit(37,tile.getBlockPos(),0,2,ChannelList.EMPTY))&&tile.selectedDesign()==1,"stale closed menu");actor.containerMenu=menu;
   for(int operation=0;operation<2;operation++)for(int value=0;value<3;value++){
    var edit=new NativeBuilderEdit(37,tile.getBlockPos(),operation,value,operation==0?ChannelList.EMPTY:ChannelList.of(7,8));var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),actor.registryAccess());try{NativeBuilderPayload.CODEC.encode(buffer,new NativeBuilderPayload(edit));need(NativeBuilderPayload.CODEC.decode(buffer).edit().equals(edit),"payload round trip");}finally{buffer.release();}
   }
   for(int mutation=0;mutation<4;mutation++){
    var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),actor.registryAccess());try{
     NativeBuilderPayload.CODEC.encode(buffer,new NativeBuilderPayload(new NativeBuilderEdit(37,tile.getBlockPos(),0,0,ChannelList.EMPTY)));
     if(mutation==0)buffer.writeByte(0);else if(mutation==1)buffer.setByte(14,65);else if(mutation==2)buffer.setByte(12,2);else buffer.setByte(13,3);
     boolean rejected=false;try{NativeBuilderPayload.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}need(rejected,"bounded malformed payload "+mutation);
    }finally{buffer.release();}
   }
   System.out.println("[vandorlabs][builder-menu] PASS chargedSlotOwnership/outputFilter/authenticatedWindowPosition/staleSession/pendingReadOnly/boundedCodec");
  }finally{tile.pending(false);for(int bay=0;bay<4;bay++)tile.setItem(NativeBuilderEntity.batterySlot(bay),originals.get(bay));actor.getInventory().setItem(9,inventory);tile.selectDesign(selection);tile.configureRedstone(activation,channels);actor.containerMenu=previous;}
 }
 private NativeBuilderMenuChecks(){}
}
