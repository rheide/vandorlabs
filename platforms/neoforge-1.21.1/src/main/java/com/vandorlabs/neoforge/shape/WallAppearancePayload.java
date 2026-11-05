package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ProgrammableMenu;
import com.vandorlabs.persistence.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded79-byte wall/frame transaction, sharing the existing fixed housing header codec. */
public record WallAppearancePayload(BlockPos pos,int windowId,ProgrammableShapeEntity.Appearance base,WallSettings walls,FrameSettings frames) implements CustomPacketPayload {
    public static final Type<WallAppearancePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","wall_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WallAppearancePayload> CODEC=new StreamCodec<>(){
        public WallAppearancePayload decode(RegistryFriendlyByteBuf buffer){
            if(buffer.readableBytes()!=79)throw new IllegalArgumentException("Invalid fixed wall appearance payload");
            // Borrow the fixed-size view; its lifetime belongs to the enclosing network buffer.
            var header=ShapeAppearancePayload.CODEC.decode(new RegistryFriendlyByteBuf(buffer.readSlice(48),buffer.registryAccess()));
            int full=buffer.readUnsignedByte(),half=buffer.readUnsignedByte(),fill=buffer.readUnsignedByte(),join=buffer.readUnsignedByte(),portholes=buffer.readUnsignedByte(),shape=buffer.readUnsignedByte();
            int front=buffer.readInt(),interior=buffer.readInt(),exterior=buffer.readInt(),version=buffer.readUnsignedByte(),splitTop=buffer.readInt(),splitBottom=buffer.readInt();
            int tile=buffer.readUnsignedByte(),fit=buffer.readUnsignedByte(),split=buffer.readUnsignedByte(),bare=buffer.readUnsignedByte();
            if(full>1||half>1||portholes>1||tile>1||fit>1||split>1||bare>1)throw new IllegalArgumentException("Invalid wall flag");
            var walls=new WallSettings(full!=0,half!=0,fill,join,portholes!=0,shape);var frames=new FrameSettings(front,interior,exterior,version,splitTop,splitBottom,tile!=0,fit!=0,split!=0,bare!=0);
            if(!NativeWallEntity.valid(walls)||!NativeWallEntity.valid(frames))throw new IllegalArgumentException("Invalid wall/frame settings");
            return new WallAppearancePayload(header.pos(),header.windowId(),header.appearance(),walls,frames);
        }
        public void encode(RegistryFriendlyByteBuf buffer,WallAppearancePayload value){
            ShapeAppearancePayload.CODEC.encode(buffer,new ShapeAppearancePayload(value.pos,value.windowId,value.base));var w=value.walls;var f=value.frames;
            buffer.writeBoolean(w.fullWidth);buffer.writeBoolean(w.halfHeight);buffer.writeByte(w.fill);buffer.writeByte(w.join);buffer.writeBoolean(w.joinPortholes);buffer.writeByte(w.portholeShape);
            buffer.writeInt(f.front);buffer.writeInt(f.interior);buffer.writeInt(f.exterior);buffer.writeByte(f.artworkVersion);buffer.writeInt(f.splitTop);buffer.writeInt(f.splitBottom);buffer.writeBoolean(f.tile);buffer.writeBoolean(f.displayFit);buffer.writeBoolean(f.split);buffer.writeBoolean(f.bare);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public boolean valid(){return pos!=null&&windowId>=0&&base!=null&&base.valid()&&NativeWallEntity.valid(walls)&&NativeWallEntity.valid(frames);}
    public static boolean apply(ServerPlayer player,WallAppearancePayload packet){
        if(!player.getServer().isSameThread()||!packet.valid()||!(player.containerMenu instanceof ProgrammableMenu menu)||menu.containerId!=packet.windowId||!menu.stillValid(player)||!menu.tile.getBlockPos().equals(packet.pos)||!(menu.tile instanceof NativeWallEntity wall))return false;
        return wall.configure(packet.base,packet.walls,packet.frames);
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
