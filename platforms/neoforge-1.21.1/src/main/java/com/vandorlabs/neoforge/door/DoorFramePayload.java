package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.persistence.FrameSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fixed frame-editor transport applied through the authenticated Door menu. */
public record DoorFramePayload(BlockPos pos,int window,FrameSettings frames) implements CustomPacketPayload {
    public static final Type<DoorFramePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("vandorlabs","door_frame"));
    public static boolean valid(FrameSettings f){return f!=null&&NativeDoorSettings.frame(f.front)&&NativeDoorSettings.surface(f.interior)&&NativeDoorSettings.surface(f.exterior)&&(f.artworkVersion==0||f.artworkVersion==1)&&(f.splitTop==-1||ModernFinishCatalog.validChoice(f.splitTop))&&(f.splitBottom==-1||ModernFinishCatalog.validChoice(f.splitBottom));}
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorFramePayload> CODEC=new StreamCodec<>(){
        public DoorFramePayload decode(RegistryFriendlyByteBuf b){if(b.readableBytes()!=12+NativeFrameWire.BYTES)throw new IllegalArgumentException("Invalid door frame length");var pos=b.readBlockPos();int window=b.readInt();var frames=NativeFrameWire.read(b);if(window<0||!valid(frames))throw new IllegalArgumentException("Invalid door frame settings");return new DoorFramePayload(pos,window,frames);}
        public void encode(RegistryFriendlyByteBuf b,DoorFramePayload p){if(p.pos==null||p.window<0||!valid(p.frames))throw new IllegalArgumentException("Invalid door frame settings");b.writeBlockPos(p.pos);b.writeInt(p.window);NativeFrameWire.write(b,p.frames);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
