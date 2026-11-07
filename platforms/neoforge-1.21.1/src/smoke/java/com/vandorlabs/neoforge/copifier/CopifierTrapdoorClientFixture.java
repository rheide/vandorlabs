package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.trapdoor.*;
import com.vandorlabs.persistence.ControlMountSettings;
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

/** Canonical Trapdoor artwork plus a private tool/menu; actual client interactions drive edits. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierTrapdoorClientFixture {
 static final BlockPos SOURCE=new BlockPos(0,96,0),TARGET=SOURCE.east(2),PEER=TARGET.east(),ACK=SOURCE.east(5);private static NativeTrapdoorEntity first,second;private static java.util.UUID actor;private static int stage;
 static boolean diagonal(){return "copifier-diagonal-trapdoor-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 static boolean enabled(){return diagonal()||"copifier-trapdoor-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Copifier Trapdoor server stage="+stage+": "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;NativeCopifierPayload.register(event);NativeCopifierUndoPayload.register(event);event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int n=b.readUnsignedByte();if(n>2)throw new IllegalArgumentException();return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_trapdoor_fixture_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();stage=0;var level=player.serverLevel();for(int x=-4;x<=10;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(var pos:new BlockPos[]{SOURCE,TARGET,PEER}){level.setBlock(pos,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("vandorlabs:"+(diagonal()?"programmable_diagonal_trapdoor":"programmable_trapdoor"))).defaultBlockState(),3);var owner=(NativeTrapdoorEntity)level.getBlockEntity(pos);var raw=owner.saveWithoutMetadata(level.registryAccess());raw.putString("CopifierUnrelatedMarker","retained");owner.loadWithComponents(raw,level.registryAccess());}
 var source=(NativeTrapdoorEntity)level.getBlockEntity(SOURCE);var edit=new CompoundTag();edit.putInt("wall_texture",7);edit.putBoolean("door_sliding",true);edit.putInt("trapdoor_texture_layout",2);edit.putInt("redstone_trigger",0);edit.putInt("redstone_channel",31);if(diagonal()){var geometry=new CompoundTag();geometry.putInt("mode",1);edit.put("diagonal_geometry",geometry);edit.putBoolean("trapdoor_slide_into_wall",true);edit.putInt("trapdoor_hinge_side",2);edit.putBoolean("trapdoor_rotation_flipped",true);}else{edit.putInt("trapdoor_position",1);edit.putBoolean("trapdoor_slide_over_surface",true);}need(source.configureCopied(edit,player),"source settings");first=(NativeTrapdoorEntity)level.getBlockEntity(TARGET);second=(NativeTrapdoorEntity)level.getBlockEntity(PEER);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;var tool=new ItemStack(NativeCopifierItemChecks.ITEM);player.setItemInHand(InteractionHand.MAIN_HAND,tool);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));player.inventoryMenu.broadcastChanges();player.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][copifier-trapdoor-client] setup PASS Survival/tool/source/twoTargets");}
 private static void apply(ServerPlayer player,int operation){need(player.getUUID().equals(actor)&&operation==stage,"actor/order");need(player.serverLevel().getBlockEntity(TARGET)==first&&player.serverLevel().getBlockEntity(PEER)==second,"same physical owners");for(var owner:new NativeTrapdoorEntity[]{first,second})need(owner.saveWithoutMetadata(player.registryAccess()).getString("CopifierUnrelatedMarker").equals("retained"),"opaque host fields");var clipboard=NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));need(clipboard.getInt("wall_texture")==7&&clipboard.getBoolean("door_sliding"),"source clipboard retained");
  if(operation==0){for(var owner:new NativeTrapdoorEntity[]{first,second})need(owner.settings().choices().texture==7&&owner.geometryPosition()==1&&owner.sliding()&&owner.settings().choices().layout()==2&&owner.trigger()==0&&owner.settings().channels().equals(com.vandorlabs.redstone.ChannelList.of(31))&&(diagonal()?owner.settings().choices().intoWall&&owner.settings().hingeSide()==2&&owner.settings().rotationFlipped():owner.settings().choices().overSurface),"real connected Trapdoor geometry/artwork/channels");System.out.println("[vandorlabs][copifier-trapdoor-client] applied PASS realSourceCapture/connectedUse/typedLeafChoices");}
  else if(operation==1){for(var owner:new NativeTrapdoorEntity[]{first,second})need(owner.settings().choices().texture==NativeTrapdoorSettings.defaultTexture()&&owner.geometryPosition()==0&&!owner.sliding()&&owner.settings().choices().layout()==1&&owner.trigger()==1&&owner.settings().channels().isEmpty()&&owner.settings().hingeSide()==0&&!owner.settings().rotationFlipped(),"real undo restores before");System.out.println("[vandorlabs][copifier-trapdoor-client] undo PASS realEditorButton/owners/clipboard");}
  else{need(player.containerMenu==player.inventoryMenu,"menu closed");System.out.println("[vandorlabs][copifier-trapdoor-client] completed PASS");}player.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(stage==3,"all stages complete");System.out.println("[vandorlabs][copifier-trapdoor-client] logout PASS");}}
}
