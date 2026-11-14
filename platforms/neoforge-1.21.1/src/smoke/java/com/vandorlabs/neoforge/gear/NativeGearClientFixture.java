package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid="vandorlabs")
public final class NativeGearClientFixture {
    public static final BlockPos ROOT=new BlockPos(0,99,0),SUPPORT=ROOT.above(),ACK=ROOT.east(3);private static java.util.UUID actorId;private static int stage;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.gearClientCheck");}
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Native gear actual server stage="+stage+": "+why);}
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:gear_client_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)apply(actor,p.operation);}));}}
    private static void held(ServerPlayer actor,ItemStack stack){actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;actorId=actor.getUUID();var world=actor.serverLevel();for(int x=-4;x<=8;x++)for(int z=-4;z<=4;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=103;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.setBlock(SUPPORT,Blocks.STONE.defaultBlockState(),3);actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();held(actor,new ItemStack(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id),2));actor.connection.teleport(.5,96,-3,0,0);System.out.println("[vandorlabs][gear-client] setup PASS actualSurvival/heldSlot/physicalSupport");}
    private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage,"actor/order");var tile=NativeGearFormation.at(actor.level(),ROOT);need(tile!=null&&tile.controller()&&!tile.inert(),"healthy exact controller");switch(operation){case 0->{need(actor.getMainHandItem().is(NativeGearBindings.ITEMS.get(NativeGearDefinition.ROTATING.id))&&actor.getMainHandItem().getCount()==1&&tile.mount()==Direction.DOWN,"real placed consumed exactly one");held(actor,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));System.out.println("[vandorlabs][gear-client] placement PASS");}case 1->{need(tile.progress()>0&&tile.progress()<1&&!tile.target()&&tile.settings().direction()==3&&tile.settings().middleHinge(),"actual menu choices and moving phase");held(actor,ItemStack.EMPTY);System.out.println("[vandorlabs][gear-client] roundtrip PASS nativeMenu/boundedC2S/S2C/previousValue");}case 2->{need(tile.progress()==1&&tile.target()&&!tile.blocked(),"real manual reversal settles at rest");held(actor,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));System.out.println("[vandorlabs][gear-client] reversal PASS");}case 3->{need(actor.containerMenu==actor.inventoryMenu,"stale editor is closed");need(tile.settings().direction()==3,"closed window rejected settings");System.out.println("[vandorlabs][gear-client] stale PASS");}case 4->{need(actor.containerMenu==actor.inventoryMenu,"finished exact player menu");System.out.println("[vandorlabs][gear-client] completed PASS");}default->throw new IllegalArgumentException("Gear fixture operation");}actor.serverLevel().setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actorId)){need(stage==5,"complete interaction journey");System.out.println("[vandorlabs][gear-client] logout PASS");}}
}
