package com.vandorlabs.neoforge.screen;
import com.vandorlabs.screen.ScreenFamily;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Disposable actual S2C projection path. No public Screen registry or renderer acceptance. */
@EventBusSubscriber(modid="vandorlabs")
public final class ScreenPowerClientFixture {
 static final BlockPos ROOT=new BlockPos(0,96,0),ACK=ROOT.east(2);private static java.util.UUID player;private static int stage;
 public static boolean enabled(){return "screen-power-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean value,String reason){if(!value)throw new IllegalStateException("Screen physical projection server: "+reason);}
 private static NativeScreenEntity owner(ServerPlayer actor){return (NativeScreenEntity)actor.serverLevel().getBlockEntity(ROOT);}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;player=actor.getUUID();stage=0;var level=actor.serverLevel();for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=100;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}level.setBlock(ROOT,ScreenBlockChecks.block(ScreenFamily.SCREEN).defaultBlockState(),3);var tile=owner(actor);var settings=tile.saveWithoutMetadata(level.registryAccess());settings.putBoolean("redstoneEnabled",true);settings.putInt("displayMode",1);tile.loadWithComponents(settings,level.registryAccess());level.setBlock(ROOT.north(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);tile.onLoad();NativeRedstoneChannels.flush(level);need(tile.visual().effectivePower()==15&&tile.screenSettings().channelLevel()==0,"actual physical source distinct from received channels");actor.setGameMode(GameType.CREATIVE);actor.getInventory().clearContent();actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.getAbilities().flying=true;actor.onUpdateAbilities();actor.connection.teleport(.5,96,-3,0,0);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.inventoryMenu.broadcastChanges();System.out.println("[vandorlabs][screen-power-client] setup PASS physical15 channel0");}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf bytes){if(bytes.readableBytes()!=1)throw new IllegalArgumentException("Screen projection fixture length");return new Mode(bytes.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf bytes,Mode mode){bytes.writeByte(mode.operation);}},(mode,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)apply(actor,mode.operation);}));}}
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","screen_projection_fixture"));public Mode{if(operation<0||operation>4)throw new IllegalArgumentException("Screen projection operation");}public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 private static void apply(ServerPlayer actor,int operation){need(enabled()&&actor.getUUID().equals(player)&&stage==operation,"current actor/stage "+operation);var level=actor.serverLevel();var tile=owner(actor);stage++;switch(operation){case 0->{need(tile.visual().effectivePower()==15,"negative packet never changes authority");actor.connection.send(ClientboundBlockEntityDataPacket.create(tile,(entity,registries)->{var tag=tile.getUpdateTag(registries);tag.remove("NativeScreenPower");return tag;}));System.out.println("[vandorlabs][screen-power-client] legacy-negative PASS missing physical projection while authority15");}case 1,2->actor.connection.send(tile.getUpdatePacket());case 3->{level.setBlock(ROOT.north(),Blocks.AIR.defaultBlockState(),3);need(tile.visual().effectivePower()==0&&tile.visual().appearance().effectiveMode==0,"actual neighbor withdrawal");}case 4->{need(tile.visual().effectivePower()==0&&!tile.saveWithoutMetadata(level.registryAccess()).contains("NativeScreenPower"),"projection never persists as authority");level.setBlock(ACK,Blocks.STONE.defaultBlockState(),3);System.out.println("[vandorlabs][screen-power-client] authority PASS restore/repeat/withdraw/private-save");}default->throw new IllegalArgumentException();}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(!enabled()||!event.getEntity().getUUID().equals(player))return;need(stage==5,"client completed all projection stages");System.out.println("[vandorlabs][screen-power-client] logout PASS");}
}
