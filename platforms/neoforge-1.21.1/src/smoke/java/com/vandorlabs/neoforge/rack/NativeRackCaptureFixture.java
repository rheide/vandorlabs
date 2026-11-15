package com.vandorlabs.neoforge.rack;

import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Two ordinary module interactions for the mid-stroke world-render witness only. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRackCaptureFixture {
    public static final BlockPos RACK=new BlockPos(0,96,0),ACK=new BlockPos(7,96,0);private static java.util.UUID actorId;private static int stage;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.rackCaptureCheck");}
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Rack capture server stage="+stage+": "+why);}
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:rack_capture_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)try{apply(actor,p.operation);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][rack-capture] FAIL "+failure);actor.getServer().halt(false);}}));}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;actorId=actor.getUUID();var world=actor.serverLevel();for(int x=-4;x<=12;x++)for(int z=-5;z<=6;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=102;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}world.setBlock(RACK,NativeRackBindings.BLOCK.defaultBlockState().setValue(NativeRackBlock.FACING,Direction.SOUTH),3);need(((NativeRackEntity)world.getBlockEntity(RACK)).configure(0,false,NativeRackData.Transmission.fresh()),"custom outside-only housing");actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();actor.getInventory().selected=0;var stack=new ItemStack(NativeRackBindings.AUTOPILOT);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();actor.connection.teleport(.5,96,3.5,180,18);System.out.println("[vandorlabs][rack-capture] setup PASS twoRealModuleActions/standingSupport");}
    private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage,"actor/order");var world=actor.serverLevel();need(world.getBlockEntity(RACK) instanceof NativeRackEntity,"native Rack");var rack=(NativeRackEntity)world.getBlockEntity(RACK);need(rack.occupied()==(operation==0?1:0),"exact occupied bay");need(operation==0?actor.getMainHandItem().isEmpty():actor.getMainHandItem().is(NativeRackBindings.AUTOPILOT)&&actor.getMainHandItem().getCount()==1,"exact module item custody");need(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(RACK).inflate(4)).isEmpty(),"no loose module duplicates");world.setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actorId)){need(stage==2,"both real interactions completed");System.out.println("[vandorlabs][rack-capture] logout PASS");}}
}
