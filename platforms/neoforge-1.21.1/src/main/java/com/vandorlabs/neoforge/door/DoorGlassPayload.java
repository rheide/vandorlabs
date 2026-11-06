package com.vandorlabs.neoforge.door;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed glazing transport; original Door artwork retains its -2 sentinel. */
public record DoorGlassPayload(BlockPos pos,int window,NativeDoorGlass glass) implements CustomPacketPayload {
    public static final Type<DoorGlassPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_glass"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorGlassPayload> CODEC=new StreamCodec<>(){
        public DoorGlassPayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=19)throw new IllegalArgumentException("Invalid door glass length");var pos=b.readBlockPos();int window=b.readInt();var glass=new NativeDoorGlass(b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte());if(window<0)throw new IllegalArgumentException("Invalid door window");return new DoorGlassPayload(pos,window,glass);}
        public void encode(RegistryFriendlyByteBuf b,DoorGlassPayload p){if(p.pos==null||p.window<0||p.glass==null)throw new IllegalArgumentException("Invalid door glazing");b.writeBlockPos(p.pos);b.writeInt(p.window);b.writeInt(p.glass.material);b.writeByte(p.glass.shade);b.writeByte(p.glass.tint);b.writeByte(p.glass.layout);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
