package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.credentials.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Actual canonical player inventory/world setup. No replacement blocks, menus or renderers are registered. */
@EventBusSubscriber(modid="vandorlabs")
public final class DoorWorldClientFixture {
 public static final BlockPos POS=new BlockPos(0,80,0);private static int canonicalDrops;private static boolean completed;
 public static boolean enabled(){return "door-world-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Canonical Door client server: "+why);}
 private static NativeProgrammableDoorEntity host(ServerPlayer player){return (NativeProgrammableDoorEntity)player.serverLevel().getBlockEntity(POS);}
 private static void pose(ServerPlayer player,double x,double z,Vec3 target){double eye=POS.getY()+player.getEyeHeight(),dx=target.x-x,dy=target.y-eye,dz=target.z-z;player.setGameMode(GameType.SURVIVAL);player.getInventory().selected=0;player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.connection.teleport(x,POS.getY(),z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
 private static void bodyPose(ServerPlayer player){pose(player,.5,-3,new Vec3(.5,81,.5));}
 private static void swap(ServerPlayer player,int slot){var old=player.getMainHandItem();player.getInventory().setItem(0,player.getInventory().getItem(slot));player.getInventory().setItem(slot,old);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;var world=player.serverLevel();need(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_door")),"canonical activation required");for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){world.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);for(int y=80;y<85;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,world.getServer());world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,world.getServer());world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,world.getServer());world.setDayTime(6000);world.setWeatherParameters(0,6000,false,false);player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(NativeDoorRegistrations.ITEM.get(),2));player.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.getInventory().setItem(2,new ItemStack(Items.STONE_PICKAXE));player.getInventory().setItem(7,NativeCredentialItems.accessCard(1));pose(player,.5,-3,new Vec3(.5,80,.5));System.out.println("[vandorlabs][door-world-client] fixture READY actual canonical survival placement inventory");}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf bytes){if(bytes.readableBytes()!=1)throw new IllegalArgumentException("Canonical client fixture mode length");return new Mode(bytes.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf bytes,Mode mode){bytes.writeByte(mode.operation());}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)mode(player,p.operation());}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_world_client_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 private static void mode(ServerPlayer player,int operation){var world=player.serverLevel();var tile=host(player);
  switch(operation){
   case 0->{need(tile!=null&&tile.getType()==NativeDoorRegistrations.ENTITY.get()&&NativeDoorLifecycle.complete(world,POS,NativeDoorRegistrations.ORDINARY.get())&&player.getMainHandItem().is(NativeDoorRegistrations.ITEM.get())&&player.getMainHandItem().getCount()==1,"actual survival NativeDoorItem packet placed two parts and consumed one");System.out.println("[vandorlabs][door-world-client] placement PASS");swap(player,1);bodyPose(player);}
   case 1->{need(tile.settings().securityMode()==2&&tile.settings().securityAllowed()==2&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"actual dialog/picker payload retains physical inventory");System.out.println("[vandorlabs][door-world-client] configuration PASS");swap(player,7);bodyPose(player);}
   case 2->{need(tile.readerProjection().front().visual().occupied()&&tile.securityOpen()&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==0,"actual player insertion transfers one card to canonical reader");System.out.println("[vandorlabs][door-world-client] reader-insert PASS");var state=NativeDoorLifecycle.resolved(world,POS,tile.getBlockState());var side=NativeDoorLifecycle.panelSide(tile,state);var face=state.getValue(NativeProgrammableDoorBlock.FACING);var normal=side==com.vandorlabs.render.SpaceDoorControlPanel.Side.RIGHT?face.getClockWise():face.getCounterClockWise();var box=NativeDoorGeometry.panel(tile.settings(),face,side,false,false);var point=new Vec3(POS.getX()+(box.minX+box.maxX)/2,POS.getY()+(box.minY+box.maxY)/2,POS.getZ()+(box.minZ+box.maxZ)/2);pose(player,point.x+normal.getStepX()*2.5,point.z+normal.getStepZ()*2.5,point);}
   case 3->{need(!tile.readerProjection().front().visual().occupied()&&!tile.securityOpen()&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"actual empty-hand pad returns sole physical card");System.out.println("[vandorlabs][door-world-client] reader-eject PASS");pose(player,.5,-2,new Vec3(.5,81.9,.5));}
   case 4->{need(player.getMainHandItem().is(NativeCredentialItems.ACCESS_CARD.get()),"card owner retained before configured pick");player.getInventory().setItem(4,player.getMainHandItem());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);bodyPose(player);player.setGameMode(GameType.CREATIVE);player.inventoryMenu.broadcastChanges();}
   case 5->{var picked=player.getMainHandItem();need(picked.is(NativeDoorRegistrations.ITEM.get())&&picked.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("SpaceDoorSettings").getInt("DoorSecurityMode")==2,"actual creative configured pick arrived at server");need(player.getInventory().getItem(4).is(NativeCredentialItems.ACCESS_CARD.get())&&player.getInventory().getItem(4).getCount()==1,"configured pick preserves returned credential");System.out.println("[vandorlabs][door-world-client] configured-pick PASS");player.getInventory().setItem(5,picked);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);swap(player,2);bodyPose(player);}
   case 6->{need(world.getBlockState(POS).isAir()&&world.getBlockState(POS.above()).isAir()&&canonicalDrops==1&&player.getInventory().getItem(4).is(NativeCredentialItems.ACCESS_CARD.get()),"actual survival stone-pickaxe mining removes both parts and drops one canonical Door");completed=true;System.out.println("[vandorlabs][door-world-client] harvest PASS");}
   default->throw new IllegalArgumentException("Canonical client fixture operation");
  }
 }
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&!event.getLevel().isClientSide()&&!event.isCanceled()&&event.getEntity() instanceof ItemEntity item&&item.getItem().is(NativeDoorRegistrations.ITEM.get())&&item.position().distanceToSqr(Vec3.atCenterOf(POS))<16)canonicalDrops+=item.getItem().getCount();}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][door-world-client] logout "+(completed?"PASS":"INCOMPLETE"));}
}
