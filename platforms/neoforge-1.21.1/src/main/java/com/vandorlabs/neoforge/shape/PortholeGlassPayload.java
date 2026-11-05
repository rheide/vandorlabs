package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ProgrammableMenu;
import com.vandorlabs.persistence.PanelGlassSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fixed26-byte immediate insert edit; registration waits for the complete porthole renderer. */
public record PortholeGlassPayload(BlockPos pos,int windowId,PanelGlassSettings glass,PanelPlayback playback,boolean joined,int shape) implements CustomPacketPayload {
    public static final Type<PortholeGlassPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","porthole_glass"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PortholeGlassPayload> CODEC=new StreamCodec<>(){
        public PortholeGlassPayload decode(RegistryFriendlyByteBuf buffer){
            if(buffer.readableBytes()!=26)throw new IllegalArgumentException("Invalid fixed porthole payload");
            var pos=buffer.readBlockPos();int window=buffer.readInt(),shade=buffer.readUnsignedByte(),tint=buffer.readUnsignedByte(),material=buffer.readInt(),layout=buffer.readUnsignedByte(),stretch=buffer.readUnsignedByte(),join=buffer.readUnsignedByte(),framed=buffer.readUnsignedByte(),mode=buffer.readUnsignedByte(),speed=buffer.readUnsignedByte(),joined=buffer.readUnsignedByte(),shape=buffer.readUnsignedByte();
            if(stretch>1||join>1||framed>1||joined>1||layout>2)throw new IllegalArgumentException("Invalid porthole flags/layout");
            var result=new PortholeGlassPayload(pos,window,new PanelGlassSettings(shade,material,layout,stretch!=0,tint,framed!=0,join!=0),new PanelPlayback(mode,speed),joined!=0,shape);if(!result.valid())throw new IllegalArgumentException("Invalid porthole settings");return result;
        }
        public void encode(RegistryFriendlyByteBuf buffer,PortholeGlassPayload value){var g=value.glass;buffer.writeBlockPos(value.pos);buffer.writeInt(value.windowId);buffer.writeByte(g.shade);buffer.writeByte(g.tint);buffer.writeInt(g.material);buffer.writeByte(g.textureLayout());buffer.writeBoolean(g.stretch);buffer.writeBoolean(g.textureJoin);buffer.writeBoolean(g.framed);buffer.writeByte(value.playback.mode());buffer.writeByte(value.playback.speed());buffer.writeBoolean(value.joined);buffer.writeByte(value.shape);}
    };
    public boolean valid(){return pos!=null&&windowId>=0&&NativeWallEntity.validPanelGlass(glass)&&playback!=null&&shape>=0&&shape<4;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static boolean apply(ServerPlayer player,PortholeGlassPayload packet){
        if(!player.getServer().isSameThread()||!packet.valid()||!(player.containerMenu instanceof ProgrammableMenu menu)||menu.containerId!=packet.windowId||!menu.stillValid(player)||!menu.tile.getBlockPos().equals(packet.pos)||!(menu.tile instanceof NativeWallEntity wall)||!wall.isPorthole())return false;
        return wall.configurePanelGlass(packet.glass,packet.playback,packet.joined,packet.shape);
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
