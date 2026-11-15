package com.vandorlabs.neoforge.surface;

import java.util.*;
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

/** One ordinary four-cell pad, dedicated settings route, root mining and physical repair. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeSurfaceClientFixture {
    public static final BlockPos ROOT=new BlockPos(0,96,0),ACK=new BlockPos(6,96,0);
    private static UUID actorId;private static int stage;private static long idleScans,idlePackets;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.surfaceClientChecks");}
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Surface player server stage="+stage+": "+why);}
    public record Mode(int operation) implements CustomPacketPayload {public static final Type<Mode> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:surface_client_fixture"));public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD) public static final class Packets {@SubscribeEvent public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(enabled())event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){public Mode decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=1)throw new IllegalArgumentException("Surface fixture packet bound");return new Mode(buffer.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf buffer,Mode mode){buffer.writeByte(mode.operation);}},(mode,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer actor)try{apply(actor,mode.operation);}catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][surface-client] FAIL "+failure);actor.getServer().halt(false);}}));}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer actor))return;actorId=actor.getUUID();var level=actor.serverLevel();for(int x=-4;x<=8;x++)for(int z=-5;z<=6;z++){level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),3);for(int y=96;y<=101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}actor.setGameMode(GameType.SURVIVAL);actor.getInventory().clearContent();actor.getInventory().setItem(0,new ItemStack(NativeSurfaceBindings.BLOCK,8));actor.getInventory().setItem(1,new ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()));actor.getInventory().setItem(2,new ItemStack(Items.STONE_PICKAXE));select(actor,0);actor.connection.teleport(.5,96,3.5,180,25);System.out.println("[vandorlabs][surface-client] setup PASS ordinarySurvivalPad");}
    private static void select(ServerPlayer actor,int slot){actor.getInventory().selected=slot;actor.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(slot));for(int index=0;index<9;index++)actor.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2,0,index,actor.getInventory().getItem(index).copy()));actor.inventoryMenu.broadcastChanges();}
    private static NativeSurfaceEntity tile(ServerPlayer actor,int x,int z){need(actor.serverLevel().getBlockEntity(ROOT.offset(x,0,z)) instanceof NativeSurfaceEntity,"native Surface member");return (NativeSurfaceEntity)actor.serverLevel().getBlockEntity(ROOT.offset(x,0,z));}
    private static long scans(ServerPlayer actor){long sum=0;for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(actor.serverLevel().getBlockEntity(ROOT.offset(x,0,z)) instanceof NativeSurfaceEntity tile)sum+=tile.discoveryScans;return sum;}
    private static long packets(ServerPlayer actor){long sum=0;for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(actor.serverLevel().getBlockEntity(ROOT.offset(x,0,z)) instanceof NativeSurfaceEntity tile)sum+=tile.visualPackets;return sum;}
    private static void apply(ServerPlayer actor,int operation){need(actor.getUUID().equals(actorId)&&operation==stage,"actor and ordered journey");var level=actor.serverLevel();need(actor.gameMode.getGameModeForPlayer()==GameType.SURVIVAL&&actor.getOffhandItem().isEmpty(),"actual Survival actor/empty offhand");switch(operation){
        case 0->{need(actor.getInventory().getItem(0).getCount()==4,"four ordinary placements consume exactly four");for(int x=0;x<2;x++)for(int z=0;z<2;z++)need(tile(actor,x,z).normal()==Direction.UP&&tile(actor,x,z).layout().cells.size()==4,"clicked-face complete pad");select(actor,3);}
        case 1->{for(int x=0;x<2;x++)for(int z=0;z<2;z++)need(tile(actor,x,z).layoutMode()==1&&tile(actor,x,z).inactiveArtwork().equals(NativeSurfaceArtwork.fromChoice(NativeSurfaceArtwork.choice("material:floor_floor_tile_green"))),"actual picker and previous-right C2S applied to whole pad");select(actor,1);}
        case 2->{need(actor.containerMenu==actor.inventoryMenu,"dedicated Configurizer dialog closed");select(actor,2);actor.connection.teleport(.5,96,-2.5,0,25);}
        case 3->{need(level.isEmptyBlock(ROOT),"actual drawing root mined");need(actor.getInventory().getItem(2).getDamageValue()==1,"one native tool-wear unit");need(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(ROOT).inflate(3)).stream().filter(item->item.getItem().is(NativeSurfaceBindings.BLOCK.asItem())).mapToInt(item->item.getItem().getCount()).sum()==1,"one ordinary shell drop");for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(x!=0||z!=0)need(tile(actor,x,z).appearanceLayout().cells.size()==1,"remaining L-shaped pad has no missing drawing root");idleScans=scans(actor);idlePackets=packets(actor);}
        case 4->{need(scans(actor)==idleScans&&packets(actor)==idlePackets,"settled survivors do not scan or send updates continuously");select(actor,0);}
        case 5->{need(actor.getInventory().getItem(0).getCount()==3,"repair consumes one ordinary Surface");select(actor,1);}
        case 6->{for(int x=0;x<2;x++)for(int z=0;z<2;z++)need(tile(actor,x,z).layout().cells.size()==4&&tile(actor,x,z).layoutMode()==1,"ordinary repaired/configured pad rebuilt");need(actor.getInventory().getItem(2).getDamageValue()==1,"UI and repair do not wear tool");}
        default->throw new AssertionError("Unknown Surface journey operation");
    }level.setBlock(ACK.east(stage),Blocks.STONE.defaultBlockState(),3);stage++;}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled()&&event.getEntity().getUUID().equals(actorId)){need(stage==7,"complete bounded journey");System.out.println("[vandorlabs][surface-client] logout PASS");}}
}
