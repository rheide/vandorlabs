package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Render fixture only: production hosts/lifecycle; no substitute registrations or saved private data. */
@EventBusSubscriber(modid="vandorlabs")
public final class SegmentedTerrainWorldFixture {
 public static final BlockPos ROOT=new BlockPos(15,96,0);
 public static boolean enabled(){return "segmented-terrain-world".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static NativeSegmentedDoorEntity root(ServerPlayer p){return (NativeSegmentedDoorEntity)p.serverLevel().getBlockEntity(ROOT);}
 public static BlockPos bank(int column){return new BlockPos((column/32)*40+column%32,96,32);}
 private static void bank(ServerPlayer p){var w=p.serverLevel();for(int c=0;c<128;c++)for(int row=31;row>=0;row--)w.setBlock(bank(c).below(row),NativeSegmentedRegistrations.BLOCK.get().defaultBlockState(),2);for(int c=0;c<128;c++)((NativeSegmentedDoorEntity)w.getBlockEntity(bank(c))).refreshColumn();for(int c=0;c<128;c+=32)((NativeSegmentedDoorEntity)w.getBlockEntity(bank(c))).configureAll(p,ChannelList.EMPTY,5,false,true,-1,NativeSegmentedDoorSettings.DEFAULT);w.setBlock(new BlockPos(76,77,-45),Blocks.STONE.defaultBlockState(),3);p.connection.teleport(76.5,78,-44.5,0,0);System.out.println("[vandorlabs][segmented-terrain-world] server BANK128 ready");}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!enabled()||!(e.getEntity() instanceof ServerPlayer p))return;var w=p.serverLevel();
  w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,w.getServer());w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,w.getServer());w.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,w.getServer());w.setDayTime(6000);w.setWeatherParameters(0,6000,false,false);
  for(int x=0;x<3;x++)for(int row=31;row>=0;row--)w.setBlock(ROOT.offset(x,-row,0),NativeSegmentedRegistrations.BLOCK.get().defaultBlockState(),3);
  for(int x=0;x<3;x++)((NativeSegmentedDoorEntity)w.getBlockEntity(ROOT.east(x))).refreshColumn();
  p.setGameMode(GameType.CREATIVE);root(p).configureAll(p,ChannelList.EMPTY,5,false,true,-1,NativeSegmentedDoorSettings.DEFAULT);
  w.setBlock(new BlockPos(16,77,-24),Blocks.STONE.defaultBlockState(),3);p.connection.teleport(16.5,78,-23.5,0,0);p.getInventory().clearContent();p.getInventory().selected=0;p.inventoryMenu.broadcastChanges();
  System.out.println("[vandorlabs][segmented-terrain-world] server READY 3x32 public hosts across x/y sections");
 }
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){if(!enabled())return;e.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("terrain fixture mode");return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode m){b.writeByte(m.operation);}},(m,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer p){switch(m.operation){case 0->root(p).operate(p,true);case 1->root(p).operate(p,false);case 2->root(p).configureAll(p,ChannelList.EMPTY,5,false,true,-1,new NativeSegmentedDoorSettings(0,true,1,0,false,0,false,NativeSegmentedDoorSettings.DEFAULT.glass,0));case 3->root(p).configureAll(p,ChannelList.EMPTY,5,false,true,-1,NativeSegmentedDoorSettings.DEFAULT);case 4->bank(p);default->throw new IllegalArgumentException("terrain fixture operation");}System.out.println("[vandorlabs][segmented-terrain-world] server operation="+m.operation);}}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:segmented_terrain_world"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
}
