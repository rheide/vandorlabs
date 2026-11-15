package com.vandorlabs.neoforge.rack;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.rack.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One ordinary Survival Rack construction, module editor and mining journey. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRackClientFixture {
    public static final BlockPos RACK=new BlockPos(0,96,0),ACK=new BlockPos(7,96,0);
    private static java.util.UUID actorId;private static int stage;private static CompoundTag privateModule,oldUpdate;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.rackClientCheck");}
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Native Rack player server stage="+stage+": "+why);}
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:rack_client_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException();return new Mode(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Mode p){b.writeByte(p.operation);}},(p,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)try{apply(actor,p.operation);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][rack-client] FAIL "+failure);actor.getServer().halt(false);}}));}}
    private static void held(ServerPlayer actor,ItemStack stack){actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,0,stack.copy()));actor.inventoryMenu.broadcastChanges();}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;need(System.getProperty("vandorlabs.moduleRack")==null&&NativeRackBindings.enabled(),"ordinary public Rack registration without enable flag");actorId=actor.getUUID();var world=actor.serverLevel();for(int x=-4;x<=20;x++)for(int z=-5;z<=6;z++){world.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=102;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();held(actor,new ItemStack(NativeRackBindings.BLOCK));actor.connection.teleport(.5,96,3.5,180,40);System.out.println("[vandorlabs][rack-client] setup PASS realSurvival/held/standingSupport");}
    private static NativeRackEntity rack(ServerPlayer actor){need(actor.level().getBlockEntity(RACK) instanceof NativeRackEntity,"installed Rack");return (NativeRackEntity)actor.level().getBlockEntity(RACK);}
    private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage,"actor/order");var world=actor.serverLevel();var rack=operation==6?null:rack(actor);switch(operation){
        case 0->{need(rack.getBlockState().getValue(NativeRackBlock.FACING)==Direction.SOUTH&&actor.getMainHandItem().isEmpty()&&rack.occupied()==0,"real shallow floor placement faces viewer and consumes one");held(actor,new ItemStack(NativeRackBindings.BLANK));actor.connection.teleport(.5,96,3.5,180,18);}
        case 1->{oldUpdate=rack.getUpdateTag(world.registryAccess());need(rack.occupied()==1&&actor.getMainHandItem().isEmpty(),"one Blank inserted only bay0 and consumed once");var module=new ItemStack(NativeRackBindings.AUTOPILOT);privateModule=new CompoundTag();privateModule.putByteArray("OpaqueRouteBoard",new byte[257]);module.set(DataComponents.CUSTOM_DATA,CustomData.of(privateModule));held(actor,module);}
        case 2->{actor.connection.send(rack.getUpdatePacket());actor.connection.send(rack.getUpdatePacket());need(rack.occupied()==5&&rack.kind(0)==1&&rack.kind(2)==2&&actor.getMainHandItem().isEmpty(),"typed Autopilot inserted in selected bay2");need(NativeRackData.data(rack.module(2)).getByteArray("OpaqueRouteBoard").length==257,"private module data retained");}
        case 3->{actor.connection.send(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(rack,(tile,registries)->oldUpdate.copy()));need(rack.occupied()==4&&actor.getMainHandItem().is(NativeRackBindings.BLANK)&&actor.getMainHandItem().getCount()==1,"empty hand retrieves exactly one Blank");actor.getInventory().setItem(1,actor.getMainHandItem().copy());held(actor,ItemStack.EMPTY);actor.getInventory().setItem(2,new ItemStack(Items.RED_WOOL,3));actor.inventoryMenu.broadcastChanges();}
        case 4->{need(actor.containerMenu==actor.inventoryMenu,"ordinary dedicated editor closed");var config=rack.moduleSettings(2);need(config.channels.equals(ChannelList.of(8,9))&&config.activation==2&&config.marker.equals("minecraft:red_wool")&&config.markerMetadata==0,"actual per-module General/Redstone previous cycling");need(actor.getInventory().getItem(2).is(Items.RED_WOOL)&&actor.getInventory().getItem(2).getCount()==3,"marker sample not consumed");need(rack.housing()==1&&rack.emits()&&rack.transmission().channels().equals(ChannelList.of(41010))&&!rack.transmission().local(),"same standard Housing/transmission controls");need(com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.level(world,RACK,41010)==4,"one occupied bay emits proportional wireless level4");held(actor,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));}
        case 5->{need(actor.containerMenu==actor.inventoryMenu&&rack.housing()==1&&rack.moduleSettings(2).channels.equals(ChannelList.of(8,9)),"stale closed-window edit refused");held(actor,new ItemStack(Items.IRON_PICKAXE));actor.connection.teleport(.5,96,3.5,180,15);}
        case 6->{need(world.getBlockState(RACK).isAir(),"ordinary Survival mining removes Rack");var drops=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(RACK).inflate(3));int shell=0,module=0,blank=0;for(var drop:drops){var item=drop.getItem();if(item.is(NativeRackBindings.BLOCK.asItem())){shell+=item.getCount();need(!item.has(DataComponents.BLOCK_ENTITY_DATA),"shell cannot duplicate stored modules");}if(item.is(NativeRackBindings.AUTOPILOT)){module+=item.getCount();need(NativeRackData.data(item).getByteArray("OpaqueRouteBoard").length==257&&NativeRackData.settings(item).channels.equals(ChannelList.of(8,9)),"mined module retains private data and configured choices");}if(item.is(NativeRackBindings.BLANK))blank+=item.getCount();}need(shell==1&&module==1&&blank==0,"exactly one shell+one owned Autopilot, extracted Blank remains inventory");need(actor.getInventory().getItem(1).is(NativeRackBindings.BLANK)&&actor.getInventory().getItem(1).getCount()==1,"extracted module remains sole inventory owner");need(actor.getMainHandItem().getDamageValue()==1,"one native mining tool wear");need(com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.level(world,RACK,41010)==0,"mined output unregistered");System.out.println("[vandorlabs][rack-client] ownership PASS actualMine/shellOnce/moduleOnce/privateData/outputUnregistered");}
        default->throw new IllegalArgumentException("Rack fixture operation");}
        world.setBlock(ACK.east(operation),Blocks.STONE.defaultBlockState(),3);stage++;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actorId)){need(stage==7,"complete Rack player journey");System.out.println("[vandorlabs][rack-client] logout PASS");}}
}
