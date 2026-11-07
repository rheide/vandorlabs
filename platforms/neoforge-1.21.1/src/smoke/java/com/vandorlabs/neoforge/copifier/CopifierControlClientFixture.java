package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.neoforge.*;
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

/** Canonical numeric controls plus a private tool/menu; actual client interactions drive edits. */
@EventBusSubscriber(modid="vandorlabs")
public final class CopifierControlClientFixture {
 static final BlockPos SOURCE=new BlockPos(0,96,0),TARGET=SOURCE.east(2),PEER=TARGET.east(),ACK=SOURCE.east(5);private static NativeSignalControlBlockEntity first,second;private static java.util.UUID actor;private static int stage;
 static boolean enabled(){return "copifier-control-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
 private static void need(boolean yes,String why){if(!yes)throw new IllegalStateException("Copifier control server stage="+stage+": "+why);}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)public static final class Registration {
  @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();int n=b.readUnsignedByte();if(n>2)throw new IllegalArgumentException();return new Mode(n);}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)apply(player,p.operation);}));}
 }
 public record Mode(int operation) implements CustomPacketPayload {static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:copifier_control_fixture_mode"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();stage=0;var level=player.serverLevel();for(int x=-4;x<=10;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(var pos:new BlockPos[]{SOURCE,TARGET,PEER}){level.setBlock(pos,VandorLabsBootstrap.WALL_SLIDER.get().defaultBlockState().setValue(NativeSignalControlBlock.FACING,Direction.UP),3);var owner=(NativeSignalControlBlockEntity)level.getBlockEntity(pos);var raw=owner.saveWithoutMetadata(level.registryAccess());raw.putString("CopifierUnrelatedMarker","retained");owner.loadWithComponents(raw,level.registryAccess());}
 var source=(NativeSignalControlBlockEntity)level.getBlockEntity(SOURCE);need(source.configureSignal(ControlMountSettings.of(2,2,3),2,12,-1),"source settings");source.configureCopiedRotation(3);source.setStep(2);first=(NativeSignalControlBlockEntity)level.getBlockEntity(TARGET);second=(NativeSignalControlBlockEntity)level.getBlockEntity(PEER);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;var tool=new ItemStack(NativeCopifierItemChecks.ITEM);player.setItemInHand(InteractionHand.MAIN_HAND,tool);player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,tool.copy()));player.inventoryMenu.broadcastChanges();player.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][copifier-control-client] setup PASS Survival/tool/source/twoTargets");}
 private static void apply(ServerPlayer player,int operation){need(player.getUUID().equals(actor)&&operation==stage,"actor/order");need(player.serverLevel().getBlockEntity(TARGET)==first&&player.serverLevel().getBlockEntity(PEER)==second,"same physical owners");for(var owner:new NativeSignalControlBlockEntity[]{first,second})need(owner.saveWithoutMetadata(player.registryAccess()).getString("CopifierUnrelatedMarker").equals("retained"),"opaque host fields");var clipboard=NativeCopifierClipboard.settings(NativeCopifierItem.data(player.getMainHandItem()));need(clipboard.getInt("active")==2&&clipboard.getCompound("signal_settings").getInt("LowLimit")==2,"source clipboard retained");
  if(operation==0){for(var owner:new NativeSignalControlBlockEntity[]{first,second})need(owner.getStep()==2&&owner.getLowLimit()==2&&owner.getHighLimit()==12&&owner.mountRotation()==3&&owner.mountSettings().height==2&&owner.mountSettings().tilt==2&&owner.mountSettings().direction==3,"real connected detent/limits/mount/rotation");System.out.println("[vandorlabs][copifier-control-client] applied PASS realSourceCapture/connectedUse/sameOwners");}
  else if(operation==1){for(var owner:new NativeSignalControlBlockEntity[]{first,second})need(owner.getStep()==0&&owner.getLowLimit()==5&&owner.getHighLimit()==15&&owner.mountRotation()==0&&owner.mountSettings().height==0&&owner.mountSettings().tilt==0&&owner.mountSettings().direction==0,"real undo restores before");System.out.println("[vandorlabs][copifier-control-client] undo PASS realEditorButton/owners/clipboard");}
  else{need(player.containerMenu==player.inventoryMenu,"menu closed");System.out.println("[vandorlabs][copifier-control-client] completed PASS");}player.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
 }
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actor)){need(stage==3,"all stages complete");System.out.println("[vandorlabs][copifier-control-client] logout PASS");}}
}
