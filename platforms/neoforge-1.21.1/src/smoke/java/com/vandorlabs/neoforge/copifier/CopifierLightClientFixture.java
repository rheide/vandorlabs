package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.light.*;
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

/** Canonical programmable Lights plus a private tool/menu; actual client interactions drive edits. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierLightClientFixture {
 static final BlockPos SOURCE=new BlockPos(0,96,0),TARGET=SOURCE.east(2),PEER=TARGET.east(),ACK=SOURCE.east(5);private static NativeLightEntity first,second;private static java.util.UUID actor;private static int stage;
 static boolean enabled(){return "copifier-light-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Copifier Light server stage="+stage+": "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;NativeCopifierPayload.register(event);NativeCopifierUndoPayload.register(event);event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int n=b.readUnsignedByte();if(n>2)throw new IllegalArgumentException();return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_light_fixture_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();stage=0;var level=player.serverLevel();for(int x=-4;x<=10;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(var pos:new BlockPos[]{SOURCE,TARGET,PEER}){level.setBlock(pos,NativeLightRegistrations.BLOCK.get().defaultBlockState(),3);var owner=(NativeLightEntity)level.getBlockEntity(pos);var raw=owner.saveWithoutMetadata(level.registryAccess());raw.putBoolean("LightJoin",true);raw.putBoolean("LightOn",!pos.equals(SOURCE));raw.putInt("LightLevel",pos.equals(SOURCE)?7:15);raw.putInt("LightTrigger",0);raw.putString("CopifierUnrelatedMarker","retained");owner.loadWithComponents(raw,level.registryAccess());}
 for(var pos:new BlockPos[]{SOURCE,TARGET,PEER})NativeLightService.refresh((NativeLightEntity)level.getBlockEntity(pos));first=(NativeLightEntity)level.getBlockEntity(TARGET);second=(NativeLightEntity)level.getBlockEntity(PEER);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;var tool=new ItemStack(NativeCopifierItemChecks.ITEM);player.setItemInHand(InteractionHand.MAIN_HAND,tool);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));player.inventoryMenu.broadcastChanges();player.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][copifier-light-client] setup PASS Survival/tool/source/twoTargets");}
 private static void apply(ServerPlayer player,int operation){need(player.getUUID().equals(actor)&&operation==stage,"actor/order");need(player.serverLevel().getBlockEntity(TARGET)==first&&player.serverLevel().getBlockEntity(PEER)==second,"same physical owners");for(var owner:new NativeLightEntity[]{first,second})need(owner.saveWithoutMetadata(player.registryAccess()).getString("CopifierUnrelatedMarker").equals("retained"),"opaque host fields");var clipboard=NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));need(!clipboard.getBoolean("active")&&clipboard.getInt("light_level")==7,"source clipboard retained");
  if(operation==0){for(var owner:new NativeLightEntity[]{first,second})need(!owner.settings().light().manualOn&&owner.settings().light().configuredLevel==7,"real connected Light manual/level fields");System.out.println("[vandorlabs][copifier-light-client] applied PASS realSourceCapture/connectedUse/sameOwners");}
  else if(operation==1){for(var owner:new NativeLightEntity[]{first,second})need(owner.settings().light().manualOn&&owner.settings().light().configuredLevel==15,"real undo restores before");System.out.println("[vandorlabs][copifier-light-client] undo PASS realEditorButton/owners/clipboard");}
  else{need(player.containerMenu==player.inventoryMenu,"menu closed");System.out.println("[vandorlabs][copifier-light-client] completed PASS");}player.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(stage==3,"all stages complete");System.out.println("[vandorlabs][copifier-light-client] logout PASS");}}
}
