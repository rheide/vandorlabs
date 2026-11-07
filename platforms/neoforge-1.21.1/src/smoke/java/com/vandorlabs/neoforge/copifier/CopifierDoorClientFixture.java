package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.door.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Canonical ordinary Doors plus a private tool/menu; actual client interactions drive edits. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierDoorClientFixture {
 static boolean large(){return "copifier-large-door-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 static final BlockPos SOURCE=new BlockPos(large()?-2:0,96,0),TARGET=SOURCE.east(large()?4:2),PEER=TARGET.east(large()?3:1),ACK=SOURCE.east(large()?11:5);private static NativeProgrammableDoorEntity first,second;private static java.util.UUID actor;private static int stage;
 static boolean enabled(){return large()||"copifier-door-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Copifier Door server stage="+stage+": "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;NativeCopifierPayload.register(event);NativeCopifierUndoPayload.register(event);event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int n=b.readUnsignedByte();if(n>2)throw new IllegalArgumentException();return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_door_fixture_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();stage=0;var level=player.serverLevel();for(int x=-4;x<=10;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(var pos:new BlockPos[]{SOURCE,TARGET,PEER}){var state=(large()?NativeLargeDoorRegistrations.BLOCK.get():NativeDoorRegistrations.ORDINARY.get()).defaultBlockState().setValue(NativeProgrammableDoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);if(large()){for(int x=0;x<3;x++)for(int y=0;y<3;y++){var cell=pos.west(x).above(y);level.setBlock(cell,state,18);((NativeLargeDoorEntity)level.getBlockEntity(cell)).assign(pos);}}else{level.setBlock(pos,state,18);level.setBlock(pos.above(),state.setValue(NativeProgrammableDoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),18);}var owner=(NativeProgrammableDoorEntity)level.getBlockEntity(pos);var raw=owner.saveWithoutMetadata(level.registryAccess());raw.putString("CopifierUnrelatedMarker","retained");owner.loadWithComponents(raw,level.registryAccess());}
 var source=(NativeProgrammableDoorEntity)level.getBlockEntity(SOURCE);var edit=new CompoundTag();edit.putInt("door_design",15);edit.putInt("door_detail",0);edit.putInt("door_face_texture",7);edit.putInt("door_depth",2);edit.putInt("door_texture_layout",2);edit.putBoolean("framed",false);edit.putInt("door_panel",0);need(source.configureCopied(edit),"source settings");first=(NativeProgrammableDoorEntity)level.getBlockEntity(TARGET);second=(NativeProgrammableDoorEntity)level.getBlockEntity(PEER);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;var tool=new ItemStack(NativeCopifierItemChecks.ITEM);player.setItemInHand(InteractionHand.MAIN_HAND,tool);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));player.inventoryMenu.broadcastChanges();player.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][copifier-door-client] setup PASS Survival/tool/source/twoTargets");}
 private static void apply(ServerPlayer player,int operation){need(player.getUUID().equals(actor)&&operation==stage,"actor/order");need(player.serverLevel().getBlockEntity(TARGET)==first&&player.serverLevel().getBlockEntity(PEER)==second,"same logical lower owners");for(var owner:new NativeProgrammableDoorEntity[]{first,second})need(owner.saveWithoutMetadata(player.registryAccess()).getString("CopifierUnrelatedMarker").equals("retained"),"opaque host fields");var clipboard=NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));need(clipboard.getInt("door_design")==15&&clipboard.getInt("door_face_texture")==7,"source clipboard retained");
  if(operation==0){for(var owner:new NativeProgrammableDoorEntity[]{first,second})need(owner.settings().door().design==15&&owner.settings().faceTexture()==7&&owner.settings().depth()==2&&owner.settings().textureLayout()==2&&!owner.settings().door().framed&&owner.settings().panelMode()==0,"real connected Door appearance");System.out.println("[vandorlabs][copifier-door-client] applied PASS realUpperCapture/upperConnectedUse/twoLowerOwners");}
  else if(operation==1){for(var owner:new NativeProgrammableDoorEntity[]{first,second})need(owner.settings().door().design==(large()?23:2)&&owner.settings().faceTexture()==-1&&owner.settings().depth()==(large()?1:0)&&owner.settings().door().framed,"real undo restores lower owners");System.out.println("[vandorlabs][copifier-door-client] undo PASS realEditorButton/lowerOwners/clipboard");}
  else{need(player.containerMenu==player.inventoryMenu,"menu closed");System.out.println("[vandorlabs][copifier-door-client] completed PASS");}player.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(stage==3,"all stages complete");System.out.println("[vandorlabs][copifier-door-client] logout PASS");}}
}
