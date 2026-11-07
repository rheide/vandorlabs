package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Actual private production reader placement/use/menu path; no public registration. */
@EventBusSubscriber(modid="vandorlabs")
public final class StandaloneReaderWorldFixture {
 static final BlockPos SUPPORT=new BlockPos(0,96,0),POS=SUPPORT.north(),ACK=SUPPORT.east(4);
 static int step,checks;private static java.util.UUID actor;private static boolean collecting;private static final java.util.List<net.minecraft.world.entity.item.ItemEntity> transitionDrops=new java.util.ArrayList<>();
 static boolean canonical(){return "canonical-reader-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 static boolean enabled(){return canonical()||"standalone-reader-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 static NativeCredentialReaderBlock block(int step){return canonical()?(NativeCredentialReaderBlock)net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:"+(step>=6?"data_chip_slot":"access_card_slot"))):StandaloneReaderGeometryChecks.fixture(step>=6);}
 static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Reader world step="+step+": "+message);}
 public record Ack(int step) implements CustomPacketPayload {
  static final Type<Ack> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:reader_world_ack"));public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("vandorlabs:access_card_slot"))){ReaderSettingsPayload.register(event);ReaderTransmissionPayload.register(event);}event.registrar("1").playToServer(Ack.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Ack>(){public Ack decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Reader fixture ack");int value=b.readUnsignedByte();if(value>=12)throw new IllegalArgumentException("Reader fixture step");return new Ack(value);}public void encode(RegistryFriendlyByteBuf b,Ack value){b.writeByte(value.step);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)ack(player,p.step);}));}
 }
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(enabled()&&collecting&&event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)transitionDrops.add(item);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();var level=player.serverLevel();level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,player.getServer());level.setDayTime(6000);level.setWeatherParameters(20000,0,false,false);level.getChunkAt(POS);for(int x=-4;x<=5;x++)for(int z=-5;z<=3;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}level.setBlock(SUPPORT,Blocks.STONE.defaultBlockState(),3);setup(player);System.out.println("[vandorlabs][reader-world] setup PASS");}
 static ItemStack held(int step){return switch(step%6){case 0->new ItemStack(block(step),2);case 1,5->step>=6?NativeCredentialItems.dataChip(2).copyWithCount(2):NativeCredentialItems.accessCard(2).copyWithCount(2);case 4->new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get());default->ItemStack.EMPTY;};}
 static void setup(ServerPlayer player){var level=player.serverLevel();if(step%6==0){transitionDrops.clear();collecting=step>0;try{level.removeBlock(POS,false);}finally{collecting=false;}need(level.getBlockState(POS).isAir(),"previous occupied reader removal leaves air before next BlockItem placement");if(step>0){need(transitionDrops.size()==1&&transitionDrops.get(0).getItem().is(NativeCredentialItems.ACCESS_CARD.get())&&transitionDrops.get(0).getItem().getCount()==1,"occupied kind transition ejects exactly one known token");for(var drop:transitionDrops)drop.discard();}}player.setGameMode(GameType.SURVIVAL);player.setShiftKeyDown(false);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,held(step));player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getAbilities().flying=false;player.onUpdateAbilities();var bounds=step%6==0?new net.minecraft.world.phys.AABB(0,0,0,1,1,1):level.getBlockState(POS).getShape(level,POS).bounds();double tx=.5,ty=POS.getY()+(bounds.minY+bounds.maxY)/2,tz=POS.getZ()+(bounds.minZ+bounds.maxZ)/2,x=.5,y=96,z=-3.5,dx=tx-x,dy=ty-y-player.getEyeHeight(),dz=tz-z;player.connection.teleport(x,y,z,(float)-Math.toDegrees(Math.atan2(dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.inventoryMenu.broadcastChanges();level.setBlock(ACK,Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE,step),3);System.out.println("[vandorlabs][reader-world] ready step="+step+" held="+player.getMainHandItem());}
 static void ack(ServerPlayer player,int received){need(actor.equals(player.getUUID())&&received==step&&!player.isCreative(),"current actor/step/survival");var level=player.serverLevel();need(level.getBlockEntity(POS) instanceof NativeCredentialReaderEntity,"installed exact host");var tile=(NativeCredentialReaderEntity)level.getBlockEntity(POS);need(tile.getBlockState().getBlock()==block(step)&&tile.getBlockState().getValue(NativeMountedSwitchBlock.FACING)==Direction.NORTH,"actual placement identity/orientation");switch(step%6){case 0->need(player.getMainHandItem().getCount()==1&&!tile.visual().contents().occupied(),"survival BlockItem consumes exactly one");case 1,5->need(player.getMainHandItem().getCount()==1&&tile.visual().contents().occupied()&&tile.outputLevel()==15,"credential ownership/output");case 2->need(player.getMainHandItem().getCount()==1&&!tile.visual().contents().occupied(),"empty-hand returns sole physical credential");case 3->need(player.containerMenu instanceof NativeCredentialReaderMenu,"survival sneak dedicated menu");case 4->{need(tile.mountSettings().height==1&&tile.mountSettings().tilt==2&&tile.mountSettings().direction==3,"actual editor payload mounted choice");need(player.containerMenu==player.inventoryMenu,"tool menu closed before next action");}}
  if(step%6==3)player.closeContainer();step++;if(step<12)setup(player);else{level.setBlock(ACK,Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE,12),3);System.out.println("[vandorlabs][reader-world] server PASS checks="+checks);}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(step==12,"all actual player phases");System.out.println("[vandorlabs][reader-world] logout PASS");}}
}
