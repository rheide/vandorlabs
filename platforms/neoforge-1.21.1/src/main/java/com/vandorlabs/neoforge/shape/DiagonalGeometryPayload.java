package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ProgrammableMenu;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Geometry-only17-byte edit; main/frame transactions retain independent saved fields. */
public record DiagonalGeometryPayload(BlockPos pos,int windowId,int mode,int fill,int join,Direction facing,boolean inverted) implements CustomPacketPayload {
    public static final Type<DiagonalGeometryPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","diagonal_geometry"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DiagonalGeometryPayload> CODEC=new StreamCodec<>(){
        public DiagonalGeometryPayload decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=17)throw new IllegalArgumentException("Invalid fixed diagonal payload");var pos=buffer.readBlockPos();int window=buffer.readInt(),mode=buffer.readUnsignedByte(),fill=buffer.readUnsignedByte(),join=buffer.readUnsignedByte(),facing=buffer.readUnsignedByte(),inverted=buffer.readUnsignedByte();if(window<0||mode>2||fill>3||join>11||facing<2||facing>5||inverted>1)throw new IllegalArgumentException("Invalid diagonal geometry fields");return new DiagonalGeometryPayload(pos,window,mode,fill,join,Direction.from3DDataValue(facing),inverted!=0);}
        public void encode(RegistryFriendlyByteBuf buffer,DiagonalGeometryPayload value){buffer.writeBlockPos(value.pos);buffer.writeInt(value.windowId);buffer.writeByte(value.mode);buffer.writeByte(value.fill);buffer.writeByte(value.join);buffer.writeByte(value.facing.get3DDataValue());buffer.writeBoolean(value.inverted);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public boolean valid(){return pos!=null&&windowId>=0&&mode>=0&&mode<=2&&fill>=0&&fill<=3&&join>=0&&join<=11&&facing!=null&&facing.getAxis()!=Direction.Axis.Y;}
    public static boolean apply(ServerPlayer player,DiagonalGeometryPayload value){if(!player.getServer().isSameThread()||!value.valid()||!(player.containerMenu instanceof ProgrammableMenu menu)||menu.containerId!=value.windowId||!menu.stillValid(player)||!menu.tile.getBlockPos().equals(value.pos)||!(menu.tile instanceof NativeWallEntity wall))return false;return wall.configureDiagonal(value.mode,value.fill,value.join,value.facing,value.inverted);}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
