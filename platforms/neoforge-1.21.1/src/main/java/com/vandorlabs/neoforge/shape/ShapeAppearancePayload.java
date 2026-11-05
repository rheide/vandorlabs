package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Fixed48-byte immediate shape edit, bound to an exact open server-owned editor session. */
public record ShapeAppearancePayload(BlockPos pos,int windowId,ProgrammableShapeEntity.Appearance appearance) implements CustomPacketPayload {
    public static final Type<ShapeAppearancePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","shape_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShapeAppearancePayload> CODEC=new StreamCodec<>(){
        public ShapeAppearancePayload decode(RegistryFriendlyByteBuf buffer){
            if(buffer.readableBytes()!=48)throw new IllegalArgumentException("Invalid fixed-size shape appearance payload");
            BlockPos pos=buffer.readBlockPos();int window=buffer.readInt(),main=buffer.readInt(),side=buffer.readInt(),layout=buffer.readUnsignedByte(),enabled=buffer.readUnsignedByte();
            if(window<0||!ModernFinishCatalog.validChoice(main)||side!=-1&&!ModernFinishCatalog.validChoice(side)||layout>2||enabled>1)throw new IllegalArgumentException("Invalid shape appearance header");
            for(int i=0;i<6;i++){int face=buffer.getInt(buffer.readerIndex()+4*i);if(face!=-1&&!ModernFinishCatalog.validChoice(face))throw new IllegalArgumentException("Invalid shape face");}
            int tread=buffer.getUnsignedByte(buffer.readerIndex()+24),roll=buffer.getUnsignedByte(buffer.readerIndex()+25);if(!StairState.valid(tread,roll))throw new IllegalArgumentException("Invalid stair geometry");
            int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=buffer.readInt();buffer.readUnsignedByte();buffer.readUnsignedByte();
            return new ShapeAppearancePayload(pos,window,new ProgrammableShapeEntity.Appearance(new ProgrammableBlockDialog.State(main,layout,enabled!=0,faces,-1),side,new StairState(tread,roll)));
        }
        public void encode(RegistryFriendlyByteBuf buffer,ShapeAppearancePayload value){
            var state=value.appearance;buffer.writeBlockPos(value.pos);buffer.writeInt(value.windowId);buffer.writeInt(state.housing().mainTexture);buffer.writeInt(state.sideTexture());buffer.writeByte(state.housing().layout);buffer.writeBoolean(state.housing().facesEnabled);for(int face:state.housing().faces())buffer.writeInt(face);buffer.writeByte(state.stairs().tread);buffer.writeByte(state.stairs().roll);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public boolean valid(){return pos!=null&&windowId>=0&&appearance!=null&&appearance.valid();}
    public static boolean apply(ServerPlayer player,ShapeAppearancePayload packet){
        if(!player.getServer().isSameThread()||!packet.valid()||!(player.containerMenu instanceof ProgrammableMenu menu)||menu.containerId!=packet.windowId||!menu.stillValid(player)||!menu.tile.getBlockPos().equals(packet.pos)||!(menu.tile instanceof ProgrammableShapeEntity tile))return false;
        var block=player.serverLevel().getBlockState(packet.pos).getBlock();if(!(block instanceof NativeProgrammableSlab)&&!(block instanceof NativeProgrammableStairs))return false;
        return tile.applyShape(packet.appearance);
    }
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(packet,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)apply(player,packet);}));}
}
