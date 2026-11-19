package com.vandorlabs.neoforge.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The Join choice is bounded and owned by the authenticated Large Door menu. */
public record LargeDoorJoinPayload(BlockPos pos,int window,boolean enabled) implements CustomPacketPayload {
    public static final Type<LargeDoorJoinPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","large_door_join"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LargeDoorJoinPayload> CODEC=new StreamCodec<>(){
        @Override public LargeDoorJoinPayload decode(RegistryFriendlyByteBuf input){
            if(input.readableBytes()!=13)throw new IllegalArgumentException("Invalid Large Door Join payload size");
            var position=input.readBlockPos();
            int window=input.readInt(),flag=input.readUnsignedByte();
            if(window<0||flag>1)throw new IllegalArgumentException("Invalid Large Door Join choice");
            return new LargeDoorJoinPayload(position,window,flag!=0);
        }

        @Override public void encode(RegistryFriendlyByteBuf output,LargeDoorJoinPayload packet){
            output.writeBlockPos(packet.pos);
            output.writeInt(packet.window);
            output.writeBoolean(packet.enabled);
        }
    };

    @Override public Type<LargeDoorJoinPayload> type(){return TYPE;}
}
