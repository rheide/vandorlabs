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
  var original=tile.stack(2);var inventory=actor.getInventory().getItem(9).copy();int selection=tile.selectedDesign(),activation=tile.activation();var channels=tile.channels();
  try{
   need(menu.slots.size()==40&&menu.stillValid(actor),"four owner slots and36 player slots");
   need(!menu.slots.get(1).mayPlace(new ItemStack(Items.STONE)),"output rejects insertion");
   var battery=new ItemStack(NativeRackBindings.BATTERY);NativeBatteryModule.transfer(battery,6500,false,true);tile.setItem(2,ItemStack.EMPTY);actor.getInventory().setItem(9,battery);
   need(!menu.quickMoveStack(actor,4).isEmpty()&&actor.getInventory().getItem(9).isEmpty()&&tile.batteryEnergy()==6500,"shift-click moves one charged battery owner");
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
  }finally{tile.pending(false);tile.setItem(2,original);actor.getInventory().setItem(9,inventory);tile.selectDesign(selection);tile.configureRedstone(activation,channels);actor.containerMenu=previous;}
 }
 private NativeBuilderMenuChecks(){}
}
