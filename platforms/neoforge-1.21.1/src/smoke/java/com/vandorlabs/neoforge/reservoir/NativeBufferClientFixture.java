package com.vandorlabs.neoforge.reservoir;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** One actual dedicated menu/configuration journey for each own-storage family. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeBufferClientFixture {
 public static final BlockPos BATTERY=new BlockPos(0,96,0),TANK=new BlockPos(2,96,0),ACK=new BlockPos(6,96,0);
 private static UUID actorId;private static int stage,casing;
 public static boolean enabled(){return Boolean.getBoolean("vandorlabs.bufferClientCheck");}
 private static void need(boolean yes,String why){if(!yes)throw new AssertionError("Own storage client server stage="+stage+": "+why);}
 public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:buffer_client_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {
  @SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){if(enabled())e.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer actor)apply(actor,p.operation);}));}
 }
 private static void held(ServerPlayer actor,ItemStack stack){actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!enabled()||!(e.getEntity() instanceof ServerPlayer actor))return;actorId=actor.getUUID();var level=actor.serverLevel();for(int x=-4;x<=8;x++)for(int z=-4;z<=4;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}level.setBlock(BATTERY,NativeBufferBindings.BATTERY.defaultBlockState().setValue(NativeBufferBlock.FACING,Direction.NORTH),3);level.setBlock(TANK,NativeBufferBindings.TANK.defaultBlockState().setValue(NativeBufferBlock.FACING,Direction.UP),3);var battery=(NativeBatteryEntity)level.getBlockEntity(BATTERY);var tank=(NativeHexTankEntity)level.getBlockEntity(TANK);battery.onLoad();com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.flush(level);int received=battery.energy(Direction.NORTH).receiveEnergy(370,false);need(received==370,"real battery input370 received="+received+" installed="+battery.installed()+" inert="+battery.inert()+" member="+battery.isChannelMemberActive()+" policy="+battery.portSettings().face(Direction.NORTH.get3DDataValue()));need(tank.fill(new FluidStack(Fluids.WATER,5000),FluidAction.EXECUTE)==5000,"real tank input5000");actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();held(actor,ItemStack.EMPTY);actor.connection.teleport(.5,96,-3,0,18);System.out.println("[vandorlabs][buffer-client] setup PASS actualSurvival/installedResourceOwners");}
 private static NativeBufferEntity owner(ServerPlayer actor,BlockPos pos){need(actor.level().getBlockEntity(pos) instanceof NativeBufferEntity,"installed own storage");return (NativeBufferEntity)actor.level().getBlockEntity(pos);}
 private static void menu(ServerPlayer actor,BlockPos pos){var cell=owner(actor,pos);need(cell instanceof NativeBatteryEntity?actor.containerMenu instanceof com.vandorlabs.neoforge.ports.NativePortMenu dialog&&dialog.tile.portBlockEntity()==cell:actor.containerMenu instanceof NativeBufferMenu bufferDialog&&bufferDialog.tile==cell,"same dedicated menu "+pos);}
 private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage,"actor/order");switch(operation){
  case 0->{menu(actor,BATTERY);need(owner(actor,BATTERY).amount()==370,"opened battery unchanged");}
  case 1->{var cell=owner(actor,BATTERY);need(actor.containerMenu==actor.inventoryMenu&&!cell.blockShape()&&cell.casing()>=0&&cell.amount()==370,"real shape/material C2S roundtrip preserves energy");casing=cell.casing();held(actor,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));}
  case 2->{menu(actor,BATTERY);need(owner(actor,BATTERY).casing()==casing&&!owner(actor,BATTERY).blockShape(),"Configurator shares battery choices");}
  case 3->{need(actor.containerMenu==actor.inventoryMenu,"battery closed");held(actor,ItemStack.EMPTY);actor.connection.teleport(2.5,96,-3,0,18);}
  case 4->{menu(actor,TANK);need(owner(actor,TANK).amount()==5000,"tank status owner unchanged");}
  case 5->{var cell=owner(actor,TANK);need(actor.containerMenu==actor.inventoryMenu&&cell.blockShape()&&cell.settings().layout==2&&cell.amount()==5000,"real tank shape/layout C2S preserves local fluid");held(actor,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));}
  case 6->{menu(actor,TANK);need(owner(actor,TANK).blockShape()&&owner(actor,TANK).settings().layout==2,"Configurator shares tank choices");}
  default->throw new IllegalArgumentException("Own storage operation");
 }actor.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(enabled()&&e.getEntity().getUUID().equals(actorId)){need(stage==7,"complete own-storage journey");System.out.println("[vandorlabs][buffer-client] logout PASS configuration/resources preserved");}}
}
