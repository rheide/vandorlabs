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
public final class LargeDoorWorldClientFixture {
 public static final BlockPos POS=new BlockPos(0,95,0);private static int canonicalDrops;private static boolean completed;
 public static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").startsWith("large-door-world-client");}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Canonical Large Door client server: "+why);}
 private static NativeLargeDoorEntity host(ServerPlayer player){return (NativeLargeDoorEntity)player.serverLevel().getBlockEntity(POS);}
 public static Item item(){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeDoorSettings.LARGE_ID));}
 private static void pose(ServerPlayer player,double x,double y,double z,Vec3 target){double eye=y+player.getEyeHeight(),dx=target.x-x,dy=target.y-eye,dz=target.z-z;player.setGameMode(GameType.SURVIVAL);player.getInventory().selected=0;player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.connection.teleport(x,y,z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
 private static void bodyPose(ServerPlayer player){pose(player,1.5,POS.getY(),3,new Vec3(2.5,POS.getY()+2.5,.5));}
 private static boolean complete(ServerPlayer player){var root=host(player);return root!=null&&NativeLargeDoorLifecycle.complete(player.serverLevel(),root);}
 private static boolean allAir(ServerPlayer player){for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(!player.serverLevel().getBlockState(POS.east(x).above(y)).isAir())return false;return true;}
 private static void swap(ServerPlayer player,int slot){var old=player.getMainHandItem();player.getInventory().setItem(0,player.getInventory().getItem(slot));player.getInventory().setItem(slot,old);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;var world=player.serverLevel();need(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeDoorSettings.LARGE_ID)),"canonical activation required");for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){world.setBlock(new BlockPos(x,POS.getY()-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=POS.getY();y<POS.getY()+8;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,world.getServer());world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,world.getServer());world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,world.getServer());world.setDayTime(6000);world.setWeatherParameters(0,6000,false,false);player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(item(),2));player.getInventory().setItem(1,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));player.getInventory().setItem(2,new ItemStack(Items.STONE_PICKAXE));player.getInventory().setItem(7,NativeCredentialItems.accessCard(1));pose(player,1.5,POS.getY(),3,new Vec3(1.5,POS.getY(),.5));System.out.println("[vandorlabs][large-door-world-client] fixture READY actual canonical survival placement inventory");}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf bytes){if(bytes.readableBytes()!=1)throw new IllegalArgumentException("Canonical client fixture mode length");return new Mode(bytes.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf bytes,Mode mode){bytes.writeByte(mode.operation());}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)mode(player,p.operation());}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","large_door_world_client_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 private static void mode(ServerPlayer player,int operation){var world=player.serverLevel();var tile=host(player);
  switch(operation){
   case 0->{need(complete(player)&&tile.getType()==net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("vandorlabs",NativeDoorSettings.LARGE_ID))&&player.getMainHandItem().is(item())&&player.getMainHandItem().getCount()==1,"real survival center packet placed all9 and consumed one");System.out.println("[vandorlabs][large-door-world-client] placement PASS");swap(player,1);bodyPose(player);}
   case 1->{need(tile.settings().securityMode()==2&&tile.settings().securityAllowed()==2&&tile.settings().door().sliding&&tile.settings().door().direction==3&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"member dialog selected X motion/security/filter without physical transfer");System.out.println("[vandorlabs][large-door-world-client] configuration PASS");swap(player,7);bodyPose(player);}
   case 2->{need(tile.readerProjection().front().visual().occupied()&&tile.securityOpen()&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==0,"actual far-member insertion transfers one card");System.out.println("[vandorlabs][large-door-world-client] reader-insert PASS");var box=tile.rootGeometrySnapshot().boxes().getLast();var point=new Vec3((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);var normal=tile.settings().door().sliding?net.minecraft.core.Direction.WEST:net.minecraft.core.Direction.EAST;pose(player,point.x+normal.getStepX()*2.5,POS.getY(),point.z+normal.getStepZ()*2.5,point);}
   case 3->{need(!tile.readerProjection().front().visual().occupied()&&!tile.securityOpen()&&player.getInventory().countItem(NativeCredentialItems.ACCESS_CARD.get())==1,"empty-hand pad returns sole card");System.out.println("[vandorlabs][large-door-world-client] reader-eject PASS");pose(player,1.5,POS.getY(),3,new Vec3(1.5,POS.getY()+2.65,.5));}
   case 4->{need(player.getMainHandItem().is(NativeCredentialItems.ACCESS_CARD.get()),"returned card retained");player.getInventory().setItem(4,player.getMainHandItem());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);swap(player,7);bodyPose(player);}
   case 7->{need(tile.settings().securityMode()==0&&tile.settings().door().sliding&&tile.settings().door().direction==1,"actual member editor switches X to vertical motion");swap(player,7);bodyPose(player);}
   case 8->{need(tile.getBlockState().getValue(NativeLargeDoorBlock.OPEN),"real empty-hand use opens vertical panels");for(int x=0;x<3;x++)for(int y=0;y<3;y++)need(world.getBlockState(POS.east(x).above(y)).getValue(NativeLargeDoorBlock.OPEN),"raw OPEN reaches every member");var box=tile.rootGeometrySnapshot().boxes().stream().max(java.util.Comparator.comparingDouble(value->value.maxY)).orElseThrow();double x=(box.minX+box.maxX)/2,z=(box.minZ+box.maxZ)/2;pose(player,x,box.maxY+.15,z,new Vec3(x,box.maxY-.1,z));}
   case 9->{need(!tile.getBlockState().getValue(NativeLargeDoorBlock.OPEN)&&player.getInventory().getItem(4).is(NativeCredentialItems.ACCESS_CARD.get()),"out-of-cell vertical use closes and retains card");System.out.println("[vandorlabs][large-door-world-client] motion PASS X/vertical/out-of-cell use");bodyPose(player);player.setGameMode(GameType.CREATIVE);player.inventoryMenu.broadcastChanges();}
   case 5->{var picked=player.getMainHandItem();need(picked.is(item())&&picked.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("SpaceDoorSettings").getBoolean("SpaceDoorSliding"),"creative far-member configured pick reaches server");need(player.getInventory().getItem(4).is(NativeCredentialItems.ACCESS_CARD.get())&&player.getInventory().getItem(4).getCount()==1,"configured pick preserves returned credential");System.out.println("[vandorlabs][large-door-world-client] configured-pick PASS");player.getInventory().setItem(5,picked);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);swap(player,2);bodyPose(player);}
   case 6->{need(allAir(player)&&canonicalDrops==1&&player.getInventory().getItem(4).is(NativeCredentialItems.ACCESS_CARD.get()),"real stone-pickaxe mining removes all9 and drops one canonical Large item");completed=true;System.out.println("[vandorlabs][large-door-world-client] harvest PASS");}
   default->throw new IllegalArgumentException("Canonical Large client fixture operation");
  }
 }
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&!event.getLevel().isClientSide()&&!event.isCanceled()&&event.getEntity() instanceof ItemEntity item&&item.getItem().is(item())&&item.position().distanceToSqr(Vec3.atCenterOf(POS))<36)canonicalDrops+=item.getItem().getCount();}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][large-door-world-client] logout "+(completed?"PASS":"INCOMPLETE"));}
}
