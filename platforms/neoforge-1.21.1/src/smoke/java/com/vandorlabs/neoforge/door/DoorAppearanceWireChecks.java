package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.NativeFrameWire;
import com.vandorlabs.persistence.FrameSettings;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Frozen frame bytes and bounded door transports; no world, packet handler or registry activation. */
public final class DoorAppearanceWireChecks {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static RegistryFriendlyByteBuf buffer(byte[] bytes){return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),RegistryAccess.EMPTY);}
    private static <T> byte[] encode(StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{codec.encode(b,value);byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return bytes;}finally{b.release();}}
    private static <T> void roundtrip(StreamCodec<RegistryFriendlyByteBuf,T> codec,T value,int length){var bytes=encode(codec,value);need(bytes.length==length,"fixed wire size");var b=buffer(bytes);try{need(Arrays.equals(bytes,encode(codec,codec.decode(b)))&&b.readableBytes()==0,"exact roundtrip");}finally{b.release();}}
    private static <T> void refuse(StreamCodec<RegistryFriendlyByteBuf,T> codec,byte[] bytes){var b=buffer(bytes);try{boolean rejected=false;try{codec.decode(b);}catch(IllegalArgumentException failure){rejected=true;}need(rejected,"invalid representation refused");}finally{b.release();}}
    public static void main(String[] args){
        FrameSettings last=null;for(int version=0;version<2;version++)for(int flags=0;flags<16;flags++){
            var f=new FrameSettings(-1,-1,-1,version,-1,-1,(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);last=f;
            var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{NativeFrameWire.write(b,f);var old=ByteBuffer.allocate(25).putInt(-1).putInt(-1).putInt(-1).put((byte)version).putInt(-1).putInt(-1);for(int bit=0;bit<4;bit++)old.put((byte)((flags>>>bit)&1));byte[] bytes=new byte[25];b.getBytes(0,bytes);need(Arrays.equals(bytes,old.array()),"unchanged existing 25-byte frame order");var parsed=NativeFrameWire.read(b);need(parsed.artworkVersion==version&&parsed.tile==f.tile&&parsed.displayFit==f.displayFit&&parsed.split==f.split&&parsed.bare==f.bare,"all dormant frame flags retained");}finally{b.release();}
            roundtrip(DoorFramePayload.CODEC,new DoorFramePayload(new BlockPos(-17,64,31),12,f),37);
        }
        var frameBytes=encode(DoorFramePayload.CODEC,new DoorFramePayload(BlockPos.ZERO,0,last));for(int at:new int[]{24,33,34,35,36}){var bad=frameBytes.clone();bad[at]=2;refuse(DoorFramePayload.CODEC,bad);}for(int length=0;length<41;length++)if(length!=37)refuse(DoorFramePayload.CODEC,Arrays.copyOf(frameBytes,length));
        for(int material:new int[]{-2,-1,0,0x40001234})for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++)roundtrip(DoorGlassPayload.CODEC,new DoorGlassPayload(new BlockPos(1,2,3),4,new NativeDoorGlass(material,0,tint,layout)),19);
        var glassBytes=encode(DoorGlassPayload.CODEC,new DoorGlassPayload(BlockPos.ZERO,0,NativeDoorGlass.DEFAULT));for(int at:new int[]{16,17,18}){var bad=glassBytes.clone();bad[at]=(byte)255;refuse(DoorGlassPayload.CODEC,bad);}for(int length=0;length<24;length++)if(length!=19)refuse(DoorGlassPayload.CODEC,Arrays.copyOf(glassBytes,length));
        System.out.println("[vandorlabs][door-appearance-wire] CPU PASS checks="+checks+" unchanged25-byte frame body,37/19-byte fixed door packets, strict flags/bounds, original-glass sentinel; no handler activation");
    }
}
