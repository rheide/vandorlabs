package com.vandorlabs.neoforge.ramp;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Private transport/camera scene on a known vanilla support plane, not controller scheduling. */
@EventBusSubscriber(modid="vandorlabs")
public final class RampRiderClientWorldFixture {
    public static final BlockPos PLATFORM=new BlockPos(0,80,0);
    private static Consumer<NativeRampMotionPayload> client=ignored->{};
    private static UUID actor;
    private static int next;
    private static boolean completed;
    public static boolean enabled(){return "ramp-rider-client-world".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    public static void client(Consumer<NativeRampMotionPayload> receiver){client=receiver;}
    public record Action(int step) implements CustomPacketPayload {
        public Action{if(step<0||step>7)throw new IllegalArgumentException("Rider fixture step");}
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.parse("vandorlabs:ramp_rider_client_step"));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){if(!enabled())return;var registrar=event.registrar("1");registrar.playToClient(NativeRampMotionPayload.TYPE,NativeRampMotionPayload.CODEC,(packet,context)->context.enqueueWork(()->client.accept(packet)));registrar.playToServer(Action.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Action>(){public Action decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=1)throw new IllegalArgumentException("Rider fixture action length");return new Action(b.readUnsignedByte());}public void encode(RegistryFriendlyByteBuf b,Action action){b.writeByte(action.step());}},(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)action(player,packet.step());}));}
    }
    private static void need(boolean yes,String text){if(!yes)throw new AssertionError("Rider fixture server: "+text);}
    @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent event){if(!enabled())return;var world=event.getServer().overworld();for(var rule:java.util.List.of(GameRules.RULE_DOMOBSPAWNING,GameRules.RULE_DAYLIGHT,GameRules.RULE_WEATHER_CYCLE))world.getGameRules().getRule(rule).set(false,event.getServer());world.setDayTime(6000);world.setWeatherParameters(0,6000,false,false);for(var pos:BlockPos.betweenClosed(new BlockPos(-6,79,-6),new BlockPos(6,85,7)))world.setBlock(pos,pos.getY()==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);for(int x=-2;x<=2;x++)for(int y=80;y<82;y++)world.setBlock(new BlockPos(x,y,-2),Blocks.STONE.defaultBlockState(),18);world.setBlock(new BlockPos(0,81,3),Blocks.LIME_CONCRETE.defaultBlockState(),18);world.setBlock(new BlockPos(0,82,3),Blocks.RED_CONCRETE.defaultBlockState(),18);world.setBlock(new BlockPos(0,83,3),Blocks.GOLD_BLOCK.defaultBlockState(),18);System.out.println("[vandorlabs][ramp-rider-client] actual server PID="+ProcessHandle.current().pid());}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;actor=player.getUUID();next=0;player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getAbilities().flying=false;player.onUpdateAbilities();player.teleportTo(player.serverLevel(),.5,80,.5,0,0);player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);System.out.println("[vandorlabs][ramp-rider-client] setup PASS synchronized Survival actor/slot/empty hands/known floor/pick rows/low rear wall");}
    private static void send(ServerPlayer player,NativeRampMotionPayload packet){need(Math.abs(player.getBoundingBox().minY-packet.after())<1e-8,"correction sent after physical move");PacketDistributor.sendToPlayer(player,packet);}
    private static void action(ServerPlayer player,int step){need(enabled()&&player.serverLevel().getServer().isSameThread()&&player.getUUID().equals(actor)&&step==next,"current actor and command "+step+" expected="+next);next++;
        if(step==0)player.serverLevel().getServer().tickRateManager().setTickRate(5);
        switch(step){
            case 0,1,4,5->{need(NativeRampRiders.move(player,0,0,0,RampRiderClientWorldFixture::send)!=null,"actual zero-displacement cadence probe");}
            case 2,6->{player.serverLevel().setBlock(PLATFORM,Blocks.STONE_SLAB.defaultBlockState(),3);player.connection.send(new ClientboundBlockUpdatePacket(player.serverLevel(),PLATFORM));double before=player.getBoundingBox().minY;var moved=NativeRampRiders.move(player,0,.5,0,RampRiderClientWorldFixture::send);need(moved!=null&&Math.abs(player.getBoundingBox().minY-before-.5)<1e-8,"actual half-block transport");}
            case 3->{player.serverLevel().setBlock(PLATFORM,Blocks.AIR.defaultBlockState(),3);player.connection.send(new ClientboundBlockUpdatePacket(player.serverLevel(),PLATFORM));player.teleportTo(player.serverLevel(),.5,80,.5,0,0);player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);}
            case 7->{player.serverLevel().getServer().tickRateManager().setTickRate(20);player.serverLevel().setBlock(new BlockPos(4,80,0),Blocks.EMERALD_BLOCK.defaultBlockState(),3);player.connection.send(new ClientboundBlockUpdatePacket(player.serverLevel(),new BlockPos(4,80,0)));completed=true;System.out.println("[vandorlabs][ramp-rider-client] complete PASS actual support corrections then client acceptance");}
            default->throw new IllegalArgumentException();
        }
        System.out.println("[vandorlabs][ramp-rider-client] action "+step+" PASS");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][ramp-rider-client] logout "+(completed?"PASS":"INCOMPLETE"));}
}
