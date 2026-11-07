package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.NativeMountedSwitchBlock;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.screen.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Real vanilla chip use on private production-class hosts; no canonical registration. */
@EventBusSubscriber(modid="vandorlabs")
public final class ScreenChipPlayerFixture {
 public static final BlockPos TRAY=new BlockPos(0,100,0),GROUP=TRAY.east(4),LEAF=GROUP.east(),DONE=GROUP.above(2);
 private static java.util.UUID actorId;
 private static ItemStack original,incoming;
 private static int step,checks,readTicks;
 private static boolean complete,reading;
 public static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").startsWith("screen-chip-player-");}
 private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError("Screen chip player step="+step+": "+message);}
 public static NativeScreenEntity owner(Level level,BlockPos pos){return level.getBlockEntity(pos) instanceof NativeScreenEntity tile?tile:null;}
 private static CompoundTag settings(ScreenFamily family){var catalog=NativeScreenCatalog.INSTANCE;var tag=NativeScreenFactory.choices(family,catalog,catalog.defaultHousing(),catalog.defaultFrame());tag.putInt("PrimarySurfaceTexture",-4);tag.putInt("FrameFront",-1);tag.putInt("displayMode",1);return tag;}
 private static ItemStack journal(int company,String marker){var result=NativeCredentialItems.dataChip(company);var data=new CompoundTag();data.putString("ItemProof",marker);result.set(DataComponents.CUSTOM_DATA,CustomData.of(data));NativeChipJournal.write(result,true,marker,new String[]{"Page one","Page two","Page three"});return result;}
 private static ItemStack stored(NativeScreenEntity tile){return NativeCredentialItems.readStack(tile.saveWithoutMetadata(tile.getLevel().registryAccess()).getCompound("ScreenChipReader").getCompound("InsertedToken"),tile.getLevel().registryAccess());}
 private static int count(ServerPlayer player){int count=0;for(var stack:player.getInventory().items)if(stack.is(NativeCredentialItems.DATA_CHIP.get()))count+=stack.getCount();return count;}
 private static void sync(ServerPlayer player){player.getInventory().selected=0;player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.inventoryMenu.broadcastChanges();}
 public static Vec3 trayTarget(){var frame=ScreenChipMount.frame(ScreenFamily.HALF_DIAGONAL,2,false,false,1);return Vec3.atLowerCornerOf(TRAY).add(frame.center.x,frame.center.y,frame.center.z);}
 public static Vec3 pageTarget(){var layout=ScreenControlLayout.of(ScreenFamily.SCREEN,new ScreenFamily.State(2,false,false,false),0,false,-1,false,-1,false);var q=layout.surface;double u=100D/128,v=(layout.rowTop+5D)/layout.height;return Vec3.atLowerCornerOf(LEAF).add((q.topRight.x+(q.topLeft.x-q.topRight.x)*u)/16,(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v)/16,(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v)/16);}
 private static void aim(ServerPlayer player,Vec3 target,Vec3 offset){var eye=target.add(offset);double dx=target.x-eye.x,dy=target.y-eye.y,dz=target.z-eye.z;player.setGameMode(GameType.SURVIVAL);player.setShiftKeyDown(false);player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));sync(player);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
  if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;
  actorId=player.getUUID();step=0;complete=false;var level=player.serverLevel();
  level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setDayTime(6000);level.setWeatherParameters(0,6000,false,false);
  for(int x=-2;x<=8;x++)for(int z=-4;z<=3;z++){level.setBlock(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState(),18);for(int y=100;y<=105;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);}
  level.setBlock(TRAY,ScreenBlockChecks.block(ScreenFamily.HALF_DIAGONAL).defaultBlockState(),18);original=journal(2,"original chip");var tag=settings(ScreenFamily.HALF_DIAGONAL);var embedded=new CompoundTag();embedded.put("InsertedToken",NativeCredentialItems.writeStack(original,level.registryAccess()));tag.put("ScreenChipReader",embedded);owner(level,TRAY).loadWithComponents(tag,level.registryAccess());
  for(var pos:new BlockPos[]{GROUP,LEAF}){level.setBlock(pos,ScreenBlockChecks.block(ScreenFamily.SCREEN).defaultBlockState(),18);var groupTag=settings(ScreenFamily.SCREEN);groupTag.putInt("ChipDisplayPage",1);owner(level,pos).loadWithComponents(groupTag,level.registryAccess());}
  var readerBlock=(NativeCredentialReaderBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:reader_geometry_fixture_chip"));var readerPos=GROUP.below();level.setBlock(readerPos,readerBlock.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,Direction.DOWN),18);var reader=(NativeCredentialReaderEntity)level.getBlockEntity(readerPos);var readerTag=new CompoundTag();readerTag.putBoolean("ChipSlot",true);readerTag.put("InsertedToken",NativeCredentialItems.writeStack(journal(4,"external journal"),level.registryAccess()));reader.loadWithComponents(readerTag,level.registryAccess());
  NativeRedstoneChannels.flush(level);var group=NativeScreenChipGroups.group(owner(level,GROUP));need(group.owner().equals(GROUP)&&group.members().size()==2&&group.reader().equals(readerPos),"installed external reader and current owner");player.getInventory().clearContent();aim(player,trayTarget(),new Vec3(0,2,0));System.out.println("[vandorlabs][screen-chip-player] setup PASS");
 }
 public record Ack(int value) implements CustomPacketPayload {public static final Type<Ack> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:screen_chip_player_ack"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Payload {
  @SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Ack.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Ack>(){public Ack decode(RegistryFriendlyByteBuf bytes){if(bytes.readableBytes()!=1)throw new IllegalArgumentException("Chip fixture ack length");int value=bytes.readUnsignedByte();if(value>2)throw new IllegalArgumentException("Chip fixture ack step");return new Ack(value);}public void encode(RegistryFriendlyByteBuf bytes,Ack ack){bytes.writeByte(ack.value);}},(ack,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)ack(player,ack.value);}));}
 }
 private static void ack(ServerPlayer player,int value){
  need(player.getUUID().equals(actorId)&&value==step,"current actor and ordered fixture ack");var level=player.serverLevel();var tile=owner(level,TRAY);need(player.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"server survival mode");
  switch(step++){
   case 0->{need(stored(tile).isEmpty()&&count(player)==1&&ItemStack.isSameItemSameComponents(player.getMainHandItem(),original),"empty-hand removal retains one complete player owner");incoming=journal(3,"inserted chip");var held=incoming.copyWithCount(2);player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,held);sync(player);System.out.println("[vandorlabs][screen-chip-player] remove PASS");}
   case 1->{var owned=stored(tile);need(owned.getCount()==1&&count(player)==1&&ItemStack.isSameItemSameComponents(owned,incoming)&&ItemStack.isSameItemSameComponents(player.getMainHandItem(),incoming),"survival insertion transfers exactly one complete chip");player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));aim(player,pageTarget(),new Vec3(0,1.2,-2));System.out.println("[vandorlabs][screen-chip-player] insert PASS");}
   case 2->{need(owner(level,GROUP).chipPage()==0&&owner(level,LEAF).chipPage()==1,"nonowner vanilla page click updates only display owner");var reader=(NativeCredentialReaderEntity)level.getBlockEntity(GROUP.below());need(reader.chipView().reader.occupied()&&reader.chipView().journal.pages.size()==3,"page click retains external reader contents");complete=true;level.setBlock(DONE,Blocks.GOLD_BLOCK.defaultBlockState(),3);System.out.println("[vandorlabs][screen-chip-player] complete PASS checks="+checks);}
   default->throw new IllegalStateException();
  }
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actorId)){need(complete,"completed player path before logout");System.out.println("[vandorlabs][screen-chip-player] logout PASS");}}
 @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event){if(!enabled()||!System.getProperty("vandorlabs.transmitterPhase","").endsWith("-read"))return;for(var pos:new BlockPos[]{TRAY,GROUP,LEAF})event.getServer().overworld().getChunkAt(pos);reading=true;}
 @SubscribeEvent public static void read(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){if(!reading||!(event.getLevel() instanceof ServerLevel level)||level!=level.getServer().overworld()||++readTicks<40)return;var chip=stored(owner(level,TRAY));need(chip.getCount()==1&&chip.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("ItemProof").equals("inserted chip"),"saved inserted owner and opaque item marker");need(owner(level,GROUP).chipPage()==0&&owner(level,LEAF).chipPage()==1,"saved owner and leaf pages after actual click");var reader=(NativeCredentialReaderEntity)level.getBlockEntity(GROUP.below());need(reader!=null&&reader.chipView().reader.occupied(),"external reader survives restart");reading=false;System.out.println("[vandorlabs][screen-chip-player] read PASS");level.getServer().halt(false);}
}
