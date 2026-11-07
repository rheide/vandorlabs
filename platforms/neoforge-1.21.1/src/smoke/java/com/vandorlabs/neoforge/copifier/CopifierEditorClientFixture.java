package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.items.DuplifierOptionCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Private real held-tool window and packets. Production registration remains separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierEditorClientFixture {
 static final BlockPos ROOT=new BlockPos(0,96,0),PEER=ROOT.offset(1,1,1),ACK=new BlockPos(4,96,0);private static com.vandorlabs.neoforge.ProgrammableBlockEntity rootOwner,peerOwner;private static java.util.UUID player;private static int stage;
 static boolean enabled(){return "copifier-editor-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Copifier editor server stage="+stage+": "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;NativeCopifierPayload.register(event);event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int operation=b.readUnsignedByte();if(operation>2)throw new IllegalArgumentException();return new Mode(operation);}public void encode(RegistryFriendlyByteBuf b,Mode mode){b.writeByte(mode.operation);}},(mode,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)apply(actor,mode.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_editor_fixture_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;player=actor.getUUID();stage=0;var level=actor.serverLevel();for(int x=-4;x<=8;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=100;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(var pos:new BlockPos[]{ROOT,PEER})level.setBlock(pos,com.vandorlabs.neoforge.VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);rootOwner=(com.vandorlabs.neoforge.ProgrammableBlockEntity)level.getBlockEntity(ROOT);peerOwner=(com.vandorlabs.neoforge.ProgrammableBlockEntity)level.getBlockEntity(PEER);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();actor.getInventory().selected=0;var stack=new ItemStack(NativeCopifierItemChecks.ITEM);var tag=new CompoundTag();tag.putString("ForeignToolMarker","retained");var settings=new CompoundTag();settings.putInt("wall_texture",7);tag=NativeCopifierClipboard.copied(tag,settings,"Source housing");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();actor.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][copifier-editor-client] setup PASS Survival/slot/tool/position");}
 private static void unchanged(ServerPlayer actor){var tool=actor.getMainHandItem();var root=NativeCopifierItem.data(tool);need(tool.getCount()==1&&root.getString("ForeignToolMarker").equals("retained")&&NativeCopifierClipboard.settings(root).getInt("wall_texture")==7,"item/clipboard unchanged");need(NativeCopifierOptions.connected(root)&&NativeCopifierOptions.ignoreDirectionality(root),"flags roundtrip");var words=NativeCopifierOptions.maskWords(root);need((words[1]&1)==0,"appended option64 toggled");need(java.util.Arrays.stream(actor.getInventory().items.toArray(new ItemStack[0])).filter(s->s.is(NativeCopifierItemChecks.ITEM)).count()==1,"preview never duplicates item");}
 private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(player)&&operation==stage,"actor/order");unchanged(actor);switch(operation){case 0->{need(actor.containerMenu instanceof NativeCopifierMenu,"actual held menu");need(actor.serverLevel().getBlockEntity(ROOT)==rootOwner&&actor.serverLevel().getBlockEntity(PEER)==peerOwner&&rootOwner.settings().mainTexture==7&&peerOwner.settings().mainTexture==7,"actual connected item applies both corner-adjacent owners without replacement");actor.setItemInHand(InteractionHand.MAIN_HAND,actor.getMainHandItem().copy());actor.inventoryMenu.broadcastChanges();System.out.println("[vandorlabs][copifier-editor-client] roundtrip PASS widgets/22byteC2S/previewS2C/actualConnectedItem");}case 1->{need(actor.containerMenu==actor.inventoryMenu,"changed exact object closed menu");System.out.println("[vandorlabs][copifier-editor-client] stale-window PASS");}case 2->{need(actor.containerMenu==actor.inventoryMenu,"closed session");System.out.println("[vandorlabs][copifier-editor-client] completed PASS");}default->throw new IllegalArgumentException();}actor.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(!enabled()||!event.getEntity().getUUID().equals(player))return;need(stage==3,"all stages complete");System.out.println("[vandorlabs][copifier-editor-client] logout PASS");}
}
