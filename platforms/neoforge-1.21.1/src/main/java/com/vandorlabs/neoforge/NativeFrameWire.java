package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.FrameSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Existing fixed frame representation, shared by panel and door transactions. */
public final class NativeFrameWire {
    public static final int BYTES=25;
    private NativeFrameWire(){}
    public static FrameSettings read(RegistryFriendlyByteBuf buffer){
        int front=buffer.readInt(),interior=buffer.readInt(),exterior=buffer.readInt(),version=buffer.readUnsignedByte(),top=buffer.readInt(),bottom=buffer.readInt();
        int tile=buffer.readUnsignedByte(),fit=buffer.readUnsignedByte(),split=buffer.readUnsignedByte(),bare=buffer.readUnsignedByte();
        if(version>1||tile>1||fit>1||split>1||bare>1)throw new IllegalArgumentException("Invalid frame flags/version");
        return new FrameSettings(front,interior,exterior,version,top,bottom,tile!=0,fit!=0,split!=0,bare!=0);
    }
    public static void write(RegistryFriendlyByteBuf buffer,FrameSettings f){
        if(f==null||f.artworkVersion<0||f.artworkVersion>1)throw new IllegalArgumentException("Invalid frame version");
        buffer.writeInt(f.front);buffer.writeInt(f.interior);buffer.writeInt(f.exterior);buffer.writeByte(f.artworkVersion);buffer.writeInt(f.splitTop);buffer.writeInt(f.splitBottom);buffer.writeBoolean(f.tile);buffer.writeBoolean(f.displayFit);buffer.writeBoolean(f.split);buffer.writeBoolean(f.bare);
    }
}
