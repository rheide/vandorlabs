package com.vandorlabs.neoforge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Existing public stair/menu path in a disposable actual client/server scene. */
@EventBusSubscriber(modid="vandorlabs")
public final class StairSliderClientFixture {
 static final BlockPos ROOT=new BlockPos(0,96,0),ACK=ROOT.east(2);private static java.util.UUID player;private static boolean complete;
 public static boolean enabled(){return "stair-slider-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean value,String reason){if(!value)throw new IllegalStateException("Stair slider server: "+reason);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;player=actor.getUUID();var world=actor.serverLevel();for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=100;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.setBlock(ROOT,VandorLabsBootstrap.PROGRAMMABLE_STAIRS.get().defaultBlockState(),3);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.teleport(.5,96,-3,0,0);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.inventoryMenu.broadcastChanges();System.out.println("[vandorlabs][stair-slider-client] setup PASS synchronized survival actor");}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {@SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1||b.readUnsignedByte()!=0)throw new IllegalArgumentException("Stair slider fixture length/mode");return new Mode();}public void encode(RegistryFriendlyByteBuf b,Mode mode){b.writeByte(0);}},(mode,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor){need(actor.getUUID().equals(player)&&actor.containerMenu instanceof ProgrammableMenu,"actual installed menu");var tile=(com.vandorlabs.neoforge.shape.ProgrammableShapeEntity)actor.serverLevel().getBlockEntity(ROOT);need(tile.stairState().tread==8,"actual drag packet reaches authoritative tread8");complete=true;actor.serverLevel().setBlock(ACK,Blocks.STONE.defaultBlockState(),3);System.out.println("[vandorlabs][stair-slider-client] authority PASS tread8");}}));}}
 public record Mode() implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","stair_slider_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(player)){need(complete,"actual drag stage complete");System.out.println("[vandorlabs][stair-slider-client] logout PASS");}}
}
