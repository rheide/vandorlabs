package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.redstone.NativeTransmitterItem;
import com.vandorlabs.neoforge.redstone.TransmitterMenu;
import com.vandorlabs.persistence.TriggerState;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Disposable ordinary-interaction fixture. Commands synchronize setup; edits and toggles use production packets. */
@EventBusSubscriber(modid="vandorlabs")
public final class TriggerClientFixture {
    public static final BlockPos POS=new BlockPos(0,80,0),CHEST=new BlockPos(1,80,0);
    static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("trigger-client");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Trigger client server fixture: "+message);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;
        var level=player.serverLevel();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
            level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=80;y<=84;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        level.setBlock(POS,VandorLabsBootstrap.TRIGGER_BLOCK.get().defaultBlockState(),3);level.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),3);
        var tile=(NativeTriggerBlockEntity)level.getBlockEntity(POS);
        tile.configure(8,new TriggerState(9,false,-1,10,11,false,12,new int[]{0,2,1,2},new int[]{0,2,1,0}),ChannelList.of(31,32));
        player.teleportTo(.5,80,-3.5);player.setYRot(0);player.setXRot(12);player.getInventory().selected=0;
        player.getInventory().setItem(7,new ItemStack(net.minecraft.world.item.Items.STONE,17));
        setup(player,0);level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        System.out.println("[vandorlabs][trigger] client fixture READY");
    }
    private static void setup(ServerPlayer player,int operation){
        player.setGameMode(operation==0||operation==2?GameType.CREATIVE:GameType.SURVIVAL);
        player.getInventory().selected=0;player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        ItemStack held=operation==2?ItemStack.EMPTY:new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get());
        if(operation==3||operation==4){held=NativeTransmitterItem.variant(VandorLabsBootstrap.REDSTONE_TRANSMITTER.get(),0);NativeTransmitterItem.configure(held,operation==3?ChannelList.of(7):ChannelList.of(31,32));}
        if(operation==4){player.setItemInHand(InteractionHand.OFF_HAND,held);held=ItemStack.EMPTY;}
        player.setItemInHand(InteractionHand.MAIN_HAND,held);player.inventoryMenu.broadcastChanges();
    }
    public record Mode(int operation) implements CustomPacketPayload {
        static final Type<Mode> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","trigger_smoke_mode"));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void payload(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){
            event.registrar("1").playToServer(Mode.TYPE,new StreamCodec<RegistryFriendlyByteBuf,Mode>(){
                public Mode decode(RegistryFriendlyByteBuf b){return new Mode(b.readUnsignedByte());}
                public void encode(RegistryFriendlyByteBuf b,Mode mode){b.writeByte(mode.operation);}
            },(packet,context)->context.enqueueWork(()->{
                if(!enabled()||!(context.player() instanceof ServerPlayer player))return;
                if(packet.operation<=4){setup(player,packet.operation);return;}
                var tile=(NativeTriggerBlockEntity)player.serverLevel().getBlockEntity(POS);
                if(packet.operation==20){require(tile.signalLevel()==15,"ordinary handheld ON not delivered");System.out.println("[vandorlabs][trigger] handheld-on PASS");}
                if(packet.operation==21){require(tile.signalLevel()==0,"ordinary handheld OFF not delivered");System.out.println("[vandorlabs][trigger] handheld-off PASS");}
                if(packet.operation==22){require(player.containerMenu instanceof TransmitterMenu,"chest replaced handheld editor");require(NativeTransmitterItem.channels(player.getMainHandItem()).equals(ChannelList.of(7)),"editor mutated item before save");System.out.println("[vandorlabs][trigger] handheld-editor PASS");}
                if(packet.operation==24){require(NativeTransmitterItem.channels(player.getMainHandItem()).equals(ChannelList.of(31,32)),"real editor did not replace original channel 7");System.out.println("[vandorlabs][trigger] handheld-save PASS");}
                if(packet.operation==23){require(tile.getRedstoneChannels().equals(ChannelList.of(31,32)),"Trigger channel draft not authoritative");System.out.println("[vandorlabs][trigger] dialog-roundtrip PASS");}
            }));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;
        require(player.serverLevel().getBlockEntity(POS) instanceof NativeTriggerBlockEntity,"final Trigger missing");
        var state=((NativeTriggerBlockEntity)player.serverLevel().getBlockEntity(POS)).triggerState();
        require(java.util.Arrays.equals(state.artworkModes(),new int[]{0,2,1,2})&&java.util.Arrays.equals(state.artworkSpeeds(),new int[]{0,2,1,0}),"editing changed dormant playback arrays");
        System.out.println("[vandorlabs][trigger] client-logout PASS");
    }
}
